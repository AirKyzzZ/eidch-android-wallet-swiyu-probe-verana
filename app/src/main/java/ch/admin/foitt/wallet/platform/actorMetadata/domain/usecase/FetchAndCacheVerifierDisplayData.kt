package ch.admin.foitt.wallet.platform.actorMetadata.domain.usecase

import ch.admin.foitt.openid4vc.domain.model.presentationRequest.AuthorizationRequest
import ch.admin.foitt.wallet.platform.credentialPresentation.domain.model.VerificationProcessType
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaVerifierTrustContext

interface FetchAndCacheVerifierDisplayData {
    suspend operator fun invoke(
        authorizationRequest: AuthorizationRequest,
        verificationProcessType: VerificationProcessType,
        verifierAttestationTrusted: Boolean?,
    ) = invoke(
        authorizationRequest = authorizationRequest,
        verificationProcessType = verificationProcessType,
        verifierAttestationTrusted = verifierAttestationTrusted,
        veranaTrustContext = null,
    )

    suspend operator fun invoke(
        authorizationRequest: AuthorizationRequest,
        verificationProcessType: VerificationProcessType,
        verifierAttestationTrusted: Boolean?,
        veranaTrustContext: VeranaVerifierTrustContext?,
    )
}
