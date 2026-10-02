package ch.admin.foitt.wallet.platform.credentialPresentation.domain.usecase.implementation

import ch.admin.foitt.openid4vc.domain.model.SigningAlgorithm
import ch.admin.foitt.openid4vc.domain.model.anycredential.Validity
import ch.admin.foitt.openid4vc.domain.model.credentialoffer.metadata.CredentialFormat
import ch.admin.foitt.openid4vc.domain.model.jwk.Jwk
import ch.admin.foitt.openid4vc.domain.model.jwt.Jwt
import ch.admin.foitt.openid4vc.domain.model.presentationRequest.AuthorizationRequest
import ch.admin.foitt.openid4vc.domain.model.presentationRequest.ClientIdentifier
import ch.admin.foitt.openid4vc.domain.model.presentationRequest.RequestObject
import ch.admin.foitt.openid4vc.domain.model.presentationRequest.RequestObjectVerificationOutcome
import ch.admin.foitt.openid4vc.domain.model.vcSdJwt.VcSdJwtError
import ch.admin.foitt.openid4vc.domain.model.x509.didSubjectAlternativeName
import ch.admin.foitt.openid4vc.domain.model.x509.dnsSubjectAlternativeNames
import ch.admin.foitt.openid4vc.domain.model.x509.toP256Jwk
import ch.admin.foitt.openid4vc.domain.model.x509.x5cLeafCertificate
import ch.admin.foitt.openid4vc.domain.usecase.VerifyRequestObjectSignature
import ch.admin.foitt.wallet.platform.credentialPresentation.domain.model.CredentialPresentationError
import ch.admin.foitt.wallet.platform.credentialPresentation.domain.model.PresentationRequestWithRaw
import ch.admin.foitt.wallet.platform.credentialPresentation.domain.model.ValidatePresentationRequestError
import ch.admin.foitt.wallet.platform.credentialPresentation.domain.model.VerificationProcessType
import ch.admin.foitt.wallet.platform.credentialPresentation.domain.model.toValidatePresentationRequestError
import ch.admin.foitt.wallet.platform.credentialPresentation.domain.usecase.ValidatePresentationRequest
import ch.admin.foitt.wallet.platform.environmentSetup.domain.repository.EnvironmentSetupRepository
import ch.admin.foitt.wallet.platform.utils.JsonParsingError
import ch.admin.foitt.wallet.platform.utils.SafeJson
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.coroutines.runSuspendCatching
import com.github.michaelbull.result.mapError
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import javax.inject.Inject

class ValidatePresentationRequestImpl @Inject constructor(
    private val safeJson: SafeJson,
    private val environmentSetupRepository: EnvironmentSetupRepository,
    private val verifyRequestObjectSignature: VerifyRequestObjectSignature,
) : ValidatePresentationRequest {
    override suspend fun invoke(
        verificationProcessType: VerificationProcessType,
        requestObject: RequestObject
    ): Result<PresentationRequestWithRaw, ValidatePresentationRequestError> = coroutineBinding {
        val presentationRequestWithRaw = validateRequestObject(verificationProcessType, requestObject).bind()

        validateAuthorizationRequest(
            authorizationRequest = presentationRequestWithRaw.authorizationRequest,
        ).bind()

        presentationRequestWithRaw
    }

    private suspend fun validateRequestObject(
        verificationProcessType: VerificationProcessType,
        requestObject: RequestObject
    ): Result<PresentationRequestWithRaw, ValidatePresentationRequestError> = coroutineBinding {
        val jwt = requestObject.jwt

        runSuspendCatching {
            check(jwt.type == JWT_HEADER_TYP)
        }.mapError { throwable ->
            CredentialPresentationError.Unexpected(throwable)
        }.bind()

        // request object clientId must be equal to authorization request clientId
        requestObject.clientId?.let {
            runSuspendCatching {
                val authorizationRequestClientId = jwt.payloadJson["client_id"]?.jsonPrimitive?.content
                checkNotNull(authorizationRequestClientId)
                check(requestObject.clientId == authorizationRequestClientId)
            }.mapError { throwable ->
                CredentialPresentationError.Unexpected(throwable)
            }.bind()
        }

        val responseUri = runSuspendCatching {
            if (verificationProcessType == VerificationProcessType.NETWORK) {
                checkNotNull(jwt.payloadJson[CLAIM_RESPONSE_URI]?.jsonPrimitive?.content)
            } else {
                null
            }
        }.mapError { throwable ->
            CredentialPresentationError.Unexpected(throwable)
        }.bind()

        val clientIdentifier = parseNetworkClientIdentifier(
            verificationProcessType = verificationProcessType,
            requestObject = requestObject,
            responseUri = responseUri,
        ).bind()

        runSuspendCatching {
            check(jwt.algorithm == SigningAlgorithm.ES256.stdName)
            if (verificationProcessType == VerificationProcessType.NETWORK &&
                clientIdentifier?.clientIdPrefix != ClientIdentifier.ClientIdPrefix.X509Hash
            ) {
                checkNotNull(jwt.keyId) { "keyId is missing" }
            }
            check(jwt.jwtValidity == Validity.Valid) { "jwt is not yet valid or expired" }
            // aud is currently optional due to expand phase
            val aud = jwt.payloadJson[CLAIM_AUDIENCE]?.jsonPrimitive?.content
            aud?.let {
                check(aud == "https://self-issued.me/v2" || aud == jwt.iss) { "neither static nor dynamic discovery is used" }
            }
        }.mapError { throwable ->
            throwable.toValidatePresentationRequestError(responseUri = responseUri, message = "validatePresentationRequest error")
        }.bind()

        val verificationOutcome =
            if (environmentSetupRepository.verifyRequestObjectSignature) {
                verifyRequestObjectSignature(
                    requestObject = requestObject,
                    trustedAttestationDids = environmentSetupRepository.attestationsServiceTrustedDids,
                ).mapError { error ->
                    error.toValidatePresentationRequestError(responseUri)
                }.bind()
            } else {
                null
            }

        if (verificationOutcome == RequestObjectVerificationOutcome.ATTESTATION_UNTRUSTED &&
            verificationProcessType == VerificationProcessType.NETWORK
        ) {
            Err(VcSdJwtError.IssuerValidationFailed.toValidatePresentationRequestError(responseUri)).bind<Unit>()
        }

        runSuspendCatching {
            check(jwt.payloadJson[CLAIM_TRANSACTION_DATA] == null) { "authorization request contains transaction_data field" }
        }.mapError { _ ->
            CredentialPresentationError.InvalidTransactionData(responseUri)
        }.bind()

        val authorizationRequest = safeJson.safeDecodeElementTo<AuthorizationRequest>(jwt.payloadJson)
            .mapError(JsonParsingError::toValidatePresentationRequestError)
            .bind()

        val authenticatedVerifier = getAuthenticatedVerifier(
            verificationProcessType = verificationProcessType,
            verificationOutcome = verificationOutcome,
            clientIdentifier = clientIdentifier,
            jwt = jwt,
            responseUri = responseUri,
        )

        PresentationRequestWithRaw(
            verificationProcessType = verificationProcessType,
            authorizationRequest = authorizationRequest,
            rawPresentationRequest = jwt.rawJwt,
            verifierAttestationTrusted = when (verificationOutcome) {
                RequestObjectVerificationOutcome.ATTESTATION_TRUSTED -> true
                RequestObjectVerificationOutcome.ATTESTATION_UNTRUSTED -> false
                RequestObjectVerificationOutcome.DID_PATH,
                RequestObjectVerificationOutcome.X509_HASH_PATH,
                null -> null
            },
            authenticatedVerifierDid = authenticatedVerifier?.did,
            authenticatedVerifierCertificateKey = authenticatedVerifier?.certificateKey,
        )
    }

    private suspend fun parseNetworkClientIdentifier(
        verificationProcessType: VerificationProcessType,
        requestObject: RequestObject,
        responseUri: String?,
    ): Result<ClientIdentifier?, ValidatePresentationRequestError> = if (
        verificationProcessType == VerificationProcessType.NETWORK
    ) {
        coroutineBinding {
            ClientIdentifier.fromRequestObject(requestObject)
                .mapError { CredentialPresentationError.InvalidRequest(responseUri) }
                .bind()
                .also { clientIdentifier ->
                    if (clientIdentifier.clientIdPrefix == ClientIdentifier.ClientIdPrefix.VerifierAttestationJwt) {
                        Err(CredentialPresentationError.InvalidRequest(responseUri)).bind<Unit>()
                    }
                }
        }
    } else {
        Ok(null)
    }

    private fun getAuthenticatedVerifier(
        verificationProcessType: VerificationProcessType,
        verificationOutcome: RequestObjectVerificationOutcome?,
        clientIdentifier: ClientIdentifier?,
        jwt: Jwt,
        responseUri: String?,
    ): AuthenticatedVerifier? {
        if (!environmentSetupRepository.verifyRequestObjectSignature ||
            verificationProcessType != VerificationProcessType.NETWORK
        ) {
            return null
        }

        return when (verificationOutcome) {
            RequestObjectVerificationOutcome.DID_PATH -> clientIdentifier?.clientId?.takeIf { clientId ->
                clientId.isNotBlank() && clientId.startsWith(DID_PREFIX)
            }?.let { did -> AuthenticatedVerifier(did = did, certificateKey = null) }

            RequestObjectVerificationOutcome.X509_HASH_PATH -> getCertificateVerifier(
                jwt = jwt,
                responseUri = responseUri,
            )

            else -> null
        }
    }

    private fun getCertificateVerifier(jwt: Jwt, responseUri: String?): AuthenticatedVerifier? = runCatching {
        val responseHost = checkNotNull(URI(responseUri).host)
        val leafCertificate = jwt.x5cLeafCertificate()
        check(leafCertificate.dnsSubjectAlternativeNames().any { it.equals(responseHost, ignoreCase = true) }) {
            "x5c leaf certificate does not name the response_uri host"
        }
        leafCertificate.didSubjectAlternativeName()?.let { did ->
            AuthenticatedVerifier(did = did, certificateKey = leafCertificate.toP256Jwk())
        }
    }.getOrNull()

    private data class AuthenticatedVerifier(
        val did: String,
        val certificateKey: Jwk?,
    )

    @Suppress("CyclomaticComplexMethod")
    private fun validateAuthorizationRequest(
        authorizationRequest: AuthorizationRequest,
    ): Result<Unit, ValidatePresentationRequestError> {
        val validationError = Err(CredentialPresentationError.InvalidRequest(authorizationRequest.responseUri))
        return when {
            authorizationRequest.responseType != VP_TOKEN -> validationError
            authorizationRequest.responseMode !in SUPPORTED_RESPONSE_MODES -> validationError
            authorizationRequest.responseMode == DIRECT_POST_JWT && authorizationRequest.clientMetaData == null -> validationError
            authorizationRequest.dcqlQuery == null -> validationError
            authorizationRequest.isClaimsEmpty() -> validationError
            authorizationRequest.hasInvalidStateField() -> validationError
            else -> Ok(Unit)
        }
    }

    private fun AuthorizationRequest.isClaimsEmpty() = dcqlQuery?.let { dcqlQuery ->
        dcqlQuery.credentials?.let { credentialQueries ->
            credentialQueries.any {
                (it.format == CredentialFormat.VC_SD_JWT.format || it.format == CredentialFormat.DC_SD_JWT.format) &&
                    it.claims?.isEmpty() == true
            }
        } ?: false
    } ?: false

    // State field must be provided if no holder binding is requested
    // see https://openid.net/specs/openid-4-verifiable-presentations-1_0.html#section-5.3
    private fun AuthorizationRequest.hasInvalidStateField(): Boolean {
        val hasCredentialWithoutBinding = dcqlQuery?.credentials?.any {
            it.requireCryptographicHolderBinding == false
        } ?: false
        return hasCredentialWithoutBinding && state == null
    }

    private companion object {
        const val JWT_HEADER_TYP = "oauth-authz-req+jwt"
        const val VP_TOKEN = "vp_token"
        const val DIRECT_POST = "direct_post"
        const val DIRECT_POST_JWT = "direct_post.jwt"
        const val DC_API_JWT = "dc_api.jwt"
        val SUPPORTED_RESPONSE_MODES = listOf(DIRECT_POST, DIRECT_POST_JWT, DC_API_JWT)
        const val CLAIM_RESPONSE_URI = "response_uri"
        const val CLAIM_AUDIENCE = "aud"
        const val CLAIM_TRANSACTION_DATA = "transaction_data"
        const val DID_PREFIX = "did:"
    }
}
