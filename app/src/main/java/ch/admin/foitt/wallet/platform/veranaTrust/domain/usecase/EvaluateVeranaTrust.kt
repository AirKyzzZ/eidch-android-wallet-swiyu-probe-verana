package ch.admin.foitt.wallet.platform.veranaTrust.domain.usecase

import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustEvidence
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustRole

fun interface EvaluateVeranaTrust {
    suspend operator fun invoke(
        role: VeranaTrustRole,
        did: String,
        vcSchemaIds: Set<String>,
    ): VeranaTrustEvidence
}
