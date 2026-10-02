package ch.admin.foitt.wallet.platform.veranaTrust.domain.usecase

import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaAccreditation
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustRole

fun interface CheckVeranaAccreditation {
    suspend operator fun invoke(
        did: String,
        role: VeranaTrustRole,
        vct: String?,
    ): VeranaAccreditation
}
