package ch.admin.foitt.wallet.platform.actorMetadata.domain.usecase.implementation

import ch.admin.foitt.openid4vc.domain.model.anycredential.AnyCredential
import ch.admin.foitt.openid4vc.domain.model.vcSdJwt.VcSdJwt
import ch.admin.foitt.wallet.platform.actorMetadata.domain.model.ActorMetaDataError
import ch.admin.foitt.wallet.platform.actorMetadata.domain.model.FetchAndCacheIssuerDisplayDataError
import ch.admin.foitt.wallet.platform.actorMetadata.domain.model.toFetchAndCacheIssuerDisplayDataError
import ch.admin.foitt.wallet.platform.actorMetadata.domain.usecase.ActorUpdateGate
import ch.admin.foitt.wallet.platform.actorMetadata.domain.usecase.CacheIssuerDisplayData
import ch.admin.foitt.wallet.platform.actorMetadata.domain.usecase.FetchAndCacheIssuerDisplayData
import ch.admin.foitt.wallet.platform.credential.domain.model.AnyIssuerDisplay
import ch.admin.foitt.wallet.platform.credential.domain.model.GetAllAnyCredentialsByCredentialIdError
import ch.admin.foitt.wallet.platform.credential.domain.usecase.FetchTrustForIssuance
import ch.admin.foitt.wallet.platform.credential.domain.usecase.GetAllAnyCredentialsByCredentialId
import ch.admin.foitt.wallet.platform.credential.domain.util.entityNames
import ch.admin.foitt.wallet.platform.locale.domain.usecase.GetLocalizedDisplay
import ch.admin.foitt.wallet.platform.navigation.domain.model.ComponentScope
import ch.admin.foitt.wallet.platform.nonCompliance.domain.usecase.FetchNonComplianceData
import ch.admin.foitt.wallet.platform.ssi.domain.model.CredentialIssuerDisplayRepositoryError
import ch.admin.foitt.wallet.platform.ssi.domain.repository.CredentialIssuerDisplayRepo
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustEvidence
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustResolution
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustRole
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustStatus
import ch.admin.foitt.wallet.platform.veranaTrust.domain.usecase.EvaluateVeranaTrust
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.coroutines.runSuspendCatching
import com.github.michaelbull.result.mapError
import javax.inject.Inject

internal class FetchAndCacheIssuerDisplayDataImpl @Inject constructor(
    private val getAllAnyCredentialsByCredentialId: GetAllAnyCredentialsByCredentialId,
    private val fetchTrustForIssuance: FetchTrustForIssuance,
    private val evaluateVeranaTrust: EvaluateVeranaTrust,
    private val credentialIssuerDisplayRepo: CredentialIssuerDisplayRepo,
    private val getLocalizedDisplay: GetLocalizedDisplay,
    private val fetchNonComplianceData: FetchNonComplianceData,
    private val cacheIssuerDisplayData: CacheIssuerDisplayData,
    private val actorUpdateGate: ActorUpdateGate,
) : FetchAndCacheIssuerDisplayData {
    override suspend operator fun invoke(
        credentialId: Long,
    ): Result<Unit, FetchAndCacheIssuerDisplayDataError> = coroutineBinding {
        val actorUpdateGeneration = actorUpdateGate.begin(ComponentScope.CredentialIssuer)
        val anyCredentials = getAllAnyCredentialsByCredentialId(credentialId)
            .mapError(GetAllAnyCredentialsByCredentialIdError::toFetchAndCacheIssuerDisplayDataError)
            .bind()

        val anyCredential = runSuspendCatching {
            anyCredentials.first()
        }.mapError { ActorMetaDataError.Unexpected(it) }.bind()

        val trustCheckResult = fetchTrustForIssuance(
            issuerDid = anyCredential.issuer,
            vcSchemaId = anyCredential.vcSchemaId,
        )
        val veranaTrustEvidence = evaluateVeranaTrust(anyCredentials)
        val actorTrustStatement = trustCheckResult.actorTrustStatement
        val savedIssuerDisplays = credentialIssuerDisplayRepo.getIssuerDisplays(credentialId)
            .mapError(CredentialIssuerDisplayRepositoryError::toFetchAndCacheIssuerDisplayDataError)
            .bind()

        val localizedIssuerDisplays: List<AnyIssuerDisplay> = if (actorTrustStatement != null) {
            // if trust statement is available only use information from there (no fallback to metadata)
            actorTrustStatement.entityNames()?.map { (locale, entityName) ->
                val savedDisplay = getLocalizedDisplay(
                    displays = savedIssuerDisplays,
                    preferredLocaleString = locale,
                )

                AnyIssuerDisplay(
                    locale = locale,
                    name = entityName,
                    logo = savedDisplay?.image, // exception: use logo from metadata
                    logoAltText = savedDisplay?.imageAltText, // exception: use logo alt text from metadata
                )
            }.orEmpty()
        } else {
            // if trust statement is not available use metadata
            savedIssuerDisplays.map { display ->
                AnyIssuerDisplay(
                    locale = display.locale,
                    name = display.name,
                    logo = display.image,
                    logoAltText = display.imageAltText,
                )
            }
        }

        val nonComplianceData = fetchNonComplianceData(actorDid = anyCredential.issuer)

        actorUpdateGate.publishIfCurrent(
            componentScope = ComponentScope.CredentialIssuer,
            generation = actorUpdateGeneration,
        ) {
            cacheIssuerDisplayData(
                trustCheckResult = trustCheckResult,
                issuerDisplays = localizedIssuerDisplays,
                nonComplianceData = nonComplianceData,
                veranaTrustEvidence = veranaTrustEvidence,
            )
        }
    }

    private suspend fun evaluateVeranaTrust(anyCredentials: List<AnyCredential>): VeranaTrustEvidence? {
        val issuers = anyCredentials.map { it.issuer }.distinct()
        val issuerDid = issuers.firstOrNull { it.startsWith(DID_PREFIX) } ?: return null
        val vcSdJwts = anyCredentials.map { it as? VcSdJwt }
        val vcts = vcSdJwts.map { it?.vct }.distinct()
        val certificateKeys = vcSdJwts.map { credential -> credential?.takeIf { it.isX5cIssuerKey }?.x5cPublicKey }.distinct()

        if (issuers.size != 1 || vcts.size != 1 || certificateKeys.size != 1) {
            return VeranaTrustEvidence(
                role = VeranaTrustRole.ISSUER,
                did = issuerDid,
                vct = vcts.singleOrNull(),
                resolution = VeranaTrustResolution.unresolved(issuerDid, VeranaTrustStatus.UNVERIFIED),
                accreditation = null,
            )
        }
        return evaluateVeranaTrust(
            role = VeranaTrustRole.ISSUER,
            did = issuerDid,
            vct = vcts.single(),
            certificateKey = certificateKeys.single(),
        )
    }

    private companion object {
        const val DID_PREFIX = "did:"
    }
}
