package ch.admin.foitt.wallet.platform.actorMetadata.domain.usecase.implementation

import ch.admin.foitt.openid4vc.domain.model.presentationRequest.AuthorizationRequest
import ch.admin.foitt.openid4vc.domain.model.presentationRequest.ClientMetaData
import ch.admin.foitt.wallet.platform.actorEnvironment.domain.model.ActorEnvironment
import ch.admin.foitt.wallet.platform.actorEnvironment.domain.usecase.GetActorEnvironment
import ch.admin.foitt.wallet.platform.actorMetadata.domain.model.ActorDisplayData
import ch.admin.foitt.wallet.platform.actorMetadata.domain.model.ActorField
import ch.admin.foitt.wallet.platform.actorMetadata.domain.model.ActorType
import ch.admin.foitt.wallet.platform.actorMetadata.domain.usecase.ActorUpdateGate
import ch.admin.foitt.wallet.platform.actorMetadata.domain.usecase.FetchAndCacheVerifierDisplayData
import ch.admin.foitt.wallet.platform.actorMetadata.domain.usecase.InitializeActorForScope
import ch.admin.foitt.wallet.platform.credential.domain.usecase.GetAllAnyCredentialsByCredentialId
import ch.admin.foitt.wallet.platform.credential.domain.util.entityNames
import ch.admin.foitt.wallet.platform.credentialPresentation.domain.model.VerificationProcessType
import ch.admin.foitt.wallet.platform.navigation.domain.model.ComponentScope
import ch.admin.foitt.wallet.platform.nonCompliance.domain.model.ActorComplianceState
import ch.admin.foitt.wallet.platform.nonCompliance.domain.model.NonComplianceReasonDisplay
import ch.admin.foitt.wallet.platform.nonCompliance.domain.usecase.FetchNonComplianceData
import ch.admin.foitt.wallet.platform.trustRegistry.domain.model.TrustCheckResult
import ch.admin.foitt.wallet.platform.trustRegistry.domain.model.TrustStatementActor
import ch.admin.foitt.wallet.platform.trustRegistry.domain.model.TrustStatus
import ch.admin.foitt.wallet.platform.trustRegistry.domain.model.VcSchemaTrustStatus
import ch.admin.foitt.wallet.platform.trustRegistry.domain.usecase.FetchVcSchemaTrustStatus
import ch.admin.foitt.wallet.platform.trustRegistry.domain.usecase.ProcessIdentityV1TrustStatement
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustEvidence
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustRole
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustVerdict
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaVerifierTrustContext
import ch.admin.foitt.wallet.platform.veranaTrust.domain.usecase.EvaluateVeranaTrust
import com.github.michaelbull.result.get
import com.github.michaelbull.result.getOrElse
import timber.log.Timber
import uniffi.heidi_dcql_rust.Meta
import javax.inject.Inject

internal class FetchAndCacheVerifierDisplayDataImpl @Inject constructor(
    private val getActorEnvironment: GetActorEnvironment,
    private val processIdentityV1TrustStatement: ProcessIdentityV1TrustStatement,
    private val fetchVcSchemaTrustStatus: FetchVcSchemaTrustStatus,
    private val fetchNonComplianceData: FetchNonComplianceData,
    private val initializeActorForScope: InitializeActorForScope,
    private val getAllAnyCredentialsByCredentialId: GetAllAnyCredentialsByCredentialId,
    private val evaluateVeranaTrust: EvaluateVeranaTrust,
    private val actorUpdateGate: ActorUpdateGate,
) : FetchAndCacheVerifierDisplayData {
    override suspend fun invoke(
        authorizationRequest: AuthorizationRequest,
        verificationProcessType: VerificationProcessType,
        verifierAttestationTrusted: Boolean?,
        veranaTrustContext: VeranaVerifierTrustContext?,
    ) {
        val actorUpdateGeneration = actorUpdateGate.begin(ComponentScope.Verifier)
        val verifierNameDisplay = authorizationRequest.clientMetaData?.toVerifierName()
        val verifierLogoDisplay = authorizationRequest.clientMetaData?.toVerifierLogo()

        if (verificationProcessType == VerificationProcessType.PROXIMITY) {
            cacheProximityVerifierFromMetadata(
                verifierNameDisplay = verifierNameDisplay,
                verifierLogoDisplay = verifierLogoDisplay,
                verifierAttestationTrusted = verifierAttestationTrusted,
                actorUpdateGeneration = actorUpdateGeneration,
            )
            return
        }

        val trustCheckResult = fetchTrustForVerification(authorizationRequest)

        val trustStatement = trustCheckResult.actorTrustStatement

        Timber.d("${trustStatement ?: "trust statement not evaluated or failed"}")

        val trustStatus = getTrustStatus(trustCheckResult)

        val vcSchemaTrustStatus = trustCheckResult.vcSchemaTrustStatus

        val verifierTrustNameDisplay: List<ActorField<String>>? = if (trustStatement == null) {
            verifierNameDisplay // trust statement not available -> use metadata
        } else {
            trustStatement.entityNames()?.toActorField() // trust statement available -> use it without metadata as default
        }
        val verifierTrustLogoDisplay: List<ActorField<String>>? = verifierLogoDisplay

        val nonComplianceData = fetchNonComplianceData(actorDid = authorizationRequest.clientId)
        val nonComplianceReason: List<ActorField<String>>? = nonComplianceData.reasonDisplays?.toNonComplianceReason()
        val veranaTrustEvidence = evaluateVeranaTrust(veranaTrustContext)

        val presentationVerifierDisplay = ActorDisplayData(
            name = verifierTrustNameDisplay,
            image = verifierTrustLogoDisplay,
            trustStatus = trustStatus,
            vcSchemaTrustStatus = vcSchemaTrustStatus,
            preferredLanguage = null,
            actorType = ActorType.VERIFIER,
            actorComplianceState = nonComplianceData.state,
            nonComplianceReason = nonComplianceReason,
            veranaTrustEvidence = veranaTrustEvidence,
        )

        actorUpdateGate.publishIfCurrent(
            componentScope = ComponentScope.Verifier,
            generation = actorUpdateGeneration,
        ) {
            initializeActorForScope(
                actorDisplayData = presentationVerifierDisplay,
                componentScope = ComponentScope.Verifier,
            )
        }
    }

    private suspend fun cacheProximityVerifierFromMetadata(
        verifierNameDisplay: List<ActorField<String>>?,
        verifierLogoDisplay: List<ActorField<String>>?,
        verifierAttestationTrusted: Boolean?,
        actorUpdateGeneration: Long,
    ) {
        val trustStatus = if (verifierAttestationTrusted == true) {
            TrustStatus.TRUSTED_PROXIMITY_VERIFIER
        } else {
            TrustStatus.NOT_TRUSTED_PROXIMITY_VERIFIER
        }

        val vcSchemaTrustStatus = if (verifierAttestationTrusted == true) {
            VcSchemaTrustStatus.TRUSTED
        } else {
            VcSchemaTrustStatus.NOT_TRUSTED
        }

        actorUpdateGate.publishIfCurrent(
            componentScope = ComponentScope.Verifier,
            generation = actorUpdateGeneration,
        ) {
            initializeActorForScope(
                actorDisplayData = ActorDisplayData(
                    name = verifierNameDisplay,
                    image = verifierLogoDisplay,
                    trustStatus = trustStatus,
                    vcSchemaTrustStatus = vcSchemaTrustStatus,
                    preferredLanguage = null,
                    actorType = ActorType.VERIFIER,
                    actorComplianceState = ActorComplianceState.UNKNOWN,
                    nonComplianceReason = null,
                    veranaTrustEvidence = null,
                ),
                componentScope = ComponentScope.Verifier,
            )
        }
    }

    private suspend fun evaluateVeranaTrust(
        context: VeranaVerifierTrustContext?,
    ): VeranaTrustEvidence? {
        context ?: return null

        val verifierDid = context.authenticatedVerifierDid
        val credentials = getAllAnyCredentialsByCredentialId(context.credentialId).get()
        val schemaIds = credentials.orEmpty().map { it.vcSchemaId.trim() }
        val distinctSchemaIds = schemaIds.filter { it.isNotBlank() }.toSet()
        val hasValidDid = verifierDid.isNotBlank() &&
            verifierDid == verifierDid.trim() &&
            verifierDid.startsWith("did:")
        val hasValidSchemas = credentials != null &&
            schemaIds.isNotEmpty() &&
            schemaIds.none { it.isBlank() }

        return if (hasValidDid && hasValidSchemas) {
            evaluateVeranaTrust(
                role = VeranaTrustRole.VERIFIER,
                did = verifierDid,
                vcSchemaIds = distinctSchemaIds,
            )
        } else {
            VeranaTrustEvidence(
                role = VeranaTrustRole.VERIFIER,
                did = verifierDid,
                vcSchemaIds = distinctSchemaIds.sorted(),
                verdict = VeranaTrustVerdict.UNTRUSTED,
                summary = null,
                authorizations = emptyList(),
            )
        }
    }

    private fun ClientMetaData.toVerifierName(): List<ActorField<String>> = clientNameList.map { entry ->
        ActorField(
            value = entry.clientName,
            locale = entry.locale,
        )
    }

    private fun ClientMetaData.toVerifierLogo(): List<ActorField<String>> = logoUriList.map { entry ->
        ActorField(
            value = entry.logoUri,
            locale = entry.locale,
        )
    }

    private suspend fun fetchTrustForVerification(authorizationRequest: AuthorizationRequest): TrustCheckResult {
        val verifierDid = authorizationRequest.clientId
        val environment = getActorEnvironment(verifierDid)

        val identityTrustStatement = when (environment) {
            ActorEnvironment.PRODUCTION, ActorEnvironment.BETA -> {
                processIdentityV1TrustStatement(verifierDid).get()
            }

            ActorEnvironment.EXTERNAL -> null
        }

        val vcSchemaId = getVcSchemaId(authorizationRequest)

        val verificationTrustStatus = vcSchemaId?.let {
            fetchVcSchemaTrustStatus(
                trustStatementActor = TrustStatementActor.VERIFIER,
                actorDid = verifierDid,
                vcSchemaId = vcSchemaId,
            ).getOrElse { VcSchemaTrustStatus.UNPROTECTED }
        } ?: VcSchemaTrustStatus.UNPROTECTED

        return TrustCheckResult(
            actorEnvironment = environment,
            actorTrustStatement = identityTrustStatement,
            vcSchemaTrustStatus = verificationTrustStatus
        )
    }

    private fun getVcSchemaId(
        authorizationRequest: AuthorizationRequest
    ) = authorizationRequest.dcqlQuery?.credentials?.firstNotNullOfOrNull { credentialQuery ->
        (credentialQuery.meta as? Meta.SdjwtVc)?.vctValues?.firstOrNull()
    }

    private fun getTrustStatus(trustCheckResult: TrustCheckResult?) = when (trustCheckResult?.actorEnvironment) {
        ActorEnvironment.PRODUCTION, ActorEnvironment.BETA -> {
            if (trustCheckResult.actorTrustStatement != null) {
                TrustStatus.TRUSTED
            } else {
                TrustStatus.NOT_TRUSTED
            }
        }

        ActorEnvironment.EXTERNAL -> TrustStatus.EXTERNAL
        null -> TrustStatus.UNKNOWN
    }

    private fun <T> Map<String, T>.toActorField(): List<ActorField<T>> = map { entry ->
        ActorField(
            value = entry.value,
            locale = entry.key,
        )
    }

    private fun List<NonComplianceReasonDisplay>.toNonComplianceReason(): List<ActorField<String>> = map { entry ->
        ActorField(
            value = entry.reason,
            locale = entry.locale,
        )
    }
}
