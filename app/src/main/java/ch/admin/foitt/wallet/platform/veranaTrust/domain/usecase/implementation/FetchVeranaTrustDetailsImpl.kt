package ch.admin.foitt.wallet.platform.veranaTrust.domain.usecase.implementation

import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaResolverResult
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustDetails
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustEvidence
import ch.admin.foitt.wallet.platform.veranaTrust.domain.repository.VeranaTrustResolverRepository
import ch.admin.foitt.wallet.platform.veranaTrust.domain.usecase.FetchVeranaTrustDetails
import javax.inject.Inject

class FetchVeranaTrustDetailsImpl @Inject constructor(
    private val repository: VeranaTrustResolverRepository,
) : FetchVeranaTrustDetails {
    override suspend fun invoke(evidence: VeranaTrustEvidence): VeranaResolverResult<VeranaTrustDetails> {
        val summary = evidence.summary ?: return VeranaResolverResult.Unavailable
        if (evidence.did != summary.did) {
            return VeranaResolverResult.Unavailable
        }
        return repository.fetchDetails(
            did = evidence.did,
            expectedSummary = summary,
        )
    }
}
