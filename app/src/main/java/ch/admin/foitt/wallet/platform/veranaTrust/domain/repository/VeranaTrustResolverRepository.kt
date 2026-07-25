package ch.admin.foitt.wallet.platform.veranaTrust.domain.repository

import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaAuthorizationEvidence
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaResolverResult
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustDetails
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustRole
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustSummary

interface VeranaTrustResolverRepository {
    suspend fun fetchSummary(did: String): VeranaResolverResult<VeranaTrustSummary>

    suspend fun fetchAuthorization(
        role: VeranaTrustRole,
        did: String,
        vcSchemaId: String,
    ): VeranaResolverResult<VeranaAuthorizationEvidence>

    suspend fun fetchDetails(
        did: String,
        expectedSummary: VeranaTrustSummary,
    ): VeranaResolverResult<VeranaTrustDetails>
}
