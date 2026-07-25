package ch.admin.foitt.openid4vc.domain.usecase.vcSdJwt.implementation

import ch.admin.foitt.openid4vc.domain.model.credentialoffer.metadata.CredentialFormat
import ch.admin.foitt.openid4vc.domain.model.jwk.hasSameCurveAs
import ch.admin.foitt.openid4vc.domain.model.jwt.JwtError
import ch.admin.foitt.openid4vc.domain.model.jwt.VerifyJwtSignatureError
import ch.admin.foitt.openid4vc.domain.model.jwt.VerifyJwtSignatureFromDidError
import ch.admin.foitt.openid4vc.domain.model.keyBinding.KeyBinding
import ch.admin.foitt.openid4vc.domain.model.vcSdJwt.FetchJwtVcIssuerMetadataError
import ch.admin.foitt.openid4vc.domain.model.vcSdJwt.VcSdJwtCredential
import ch.admin.foitt.openid4vc.domain.model.vcSdJwt.VcSdJwtError
import ch.admin.foitt.openid4vc.domain.model.vcSdJwt.VerifyVcSdJwtSignatureError
import ch.admin.foitt.openid4vc.domain.model.vcSdJwt.toVerifyVcSdJwtSignatureError
import ch.admin.foitt.openid4vc.domain.repository.CredentialOfferRepository
import ch.admin.foitt.openid4vc.domain.usecase.jwt.VerifyJwtSignature
import ch.admin.foitt.openid4vc.domain.usecase.jwt.VerifyJwtSignatureFromDid
import ch.admin.foitt.openid4vc.domain.usecase.vcSdJwt.VerifyVcSdJwtSignature
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.coroutines.runSuspendCatching
import com.github.michaelbull.result.mapError
import java.net.URI
import java.net.URL
import javax.inject.Inject

internal class VerifyVcSdJwtSignatureImpl @Inject constructor(
    private val verifyJwtSignatureFromDid: VerifyJwtSignatureFromDid,
    private val credentialOfferRepository: CredentialOfferRepository,
    private val verifyJwtSignature: VerifyJwtSignature,
) : VerifyVcSdJwtSignature {
    override suspend operator fun invoke(
        keyBinding: KeyBinding?,
        payload: String,
        format: CredentialFormat,
    ): Result<VcSdJwtCredential, VerifyVcSdJwtSignatureError> = coroutineBinding {
        runSuspendCatching {
            val credential = VcSdJwtCredential(
                keyBinding = keyBinding,
                payload = payload,
                format = format,
            )
            if (credential.isX5cIssuerKey) {
                verifyX5cCredential(credential).bind()
            } else {
                verifyJwtSignatureFromDid(
                    kid = credential.kid,
                    jwt = credential,
                ).mapError(VerifyJwtSignatureFromDidError::toVerifyVcSdJwtSignatureError).bind()
            }

            credential
        }.mapError { throwable ->
            throwable.toVerifyVcSdJwtSignatureError()
        }.bind()
    }

    private suspend fun verifyX5cCredential(
        credential: VcSdJwtCredential,
    ): Result<Unit, VerifyVcSdJwtSignatureError> = coroutineBinding {
        val issuerDid = credential.x5cIssuerDid ?: Err(VcSdJwtError.InvalidDid).bind()
        val certificateKey = credential.x5cPublicKey ?: Err(VcSdJwtError.InvalidJwt).bind()
        val issuerUrl = credential.iss?.toIssuerUrl() ?: Err(VcSdJwtError.IssuerValidationFailed).bind()

        val issuerHost = issuerUrl.host.lowercase()
        val didMatchesIssuer = issuerDid.toWebDidHost()?.lowercase() == issuerHost
        val certificateMatchesIssuer = credential.x5cDnsNames.any { it.equals(issuerHost, ignoreCase = true) }
        if (!didMatchesIssuer || !certificateMatchesIssuer) {
            Err(VcSdJwtError.IssuerValidationFailed).bind<Unit>()
        }

        val issuerMetadata = credentialOfferRepository.fetchJwtVcIssuerMetadata(issuerUrl)
            .mapError(::mapFetchJwtVcIssuerMetadataError)
            .bind()
        val issuerMatchesMetadata = issuerMetadata.issuer == credential.iss
        val certificateKeyIsPublished = issuerMetadata.jwks.keys.any { it.hasSameCurveAs(certificateKey) }
        if (!issuerMatchesMetadata || !certificateKeyIsPublished) {
            Err(VcSdJwtError.IssuerValidationFailed).bind<Unit>()
        }

        verifyJwtSignature(
            jwt = credential,
            publicKey = certificateKey,
        ).mapError(::mapVerifyJwtSignatureError).bind()
    }

    private fun String.toIssuerUrl(): URL? = runCatching {
        val uri = URI(this)
        require(
            uri.scheme == HTTPS_SCHEME &&
                uri.host != null &&
                uri.userInfo == null &&
                uri.query == null &&
                uri.fragment == null
        )
        uri.toURL()
    }.getOrNull()

    private fun String.toWebDidHost(): String? = when {
        startsWith(DID_WEB_PREFIX) -> removePrefix(DID_WEB_PREFIX).substringBefore(':')
        startsWith(DID_WEBVH_PREFIX) -> removePrefix(DID_WEBVH_PREFIX).split(':').getOrNull(1)
        else -> null
    }

    private fun mapVerifyJwtSignatureError(error: VerifyJwtSignatureError): VerifyVcSdJwtSignatureError = when (error) {
        JwtError.InvalidJwt -> VcSdJwtError.InvalidJwt
        is JwtError.Unexpected -> VcSdJwtError.Unexpected(error.throwable)
    }

    private fun mapFetchJwtVcIssuerMetadataError(
        error: FetchJwtVcIssuerMetadataError,
    ): VerifyVcSdJwtSignatureError = when (error) {
        VcSdJwtError.IssuerValidationFailed -> VcSdJwtError.IssuerValidationFailed
        VcSdJwtError.NetworkError -> VcSdJwtError.NetworkError
        is VcSdJwtError.Unexpected -> VcSdJwtError.Unexpected(error.cause)
    }

    private companion object {
        const val HTTPS_SCHEME = "https"
        const val DID_WEB_PREFIX = "did:web:"
        const val DID_WEBVH_PREFIX = "did:webvh:"
    }
}
