package ch.admin.foitt.wallet.platform.veranaTrust.domain.usecase

import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaResolverResult
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustDetails
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustEvidence

fun interface FetchVeranaTrustDetails {
    suspend operator fun invoke(evidence: VeranaTrustEvidence): VeranaResolverResult<VeranaTrustDetails>
}
