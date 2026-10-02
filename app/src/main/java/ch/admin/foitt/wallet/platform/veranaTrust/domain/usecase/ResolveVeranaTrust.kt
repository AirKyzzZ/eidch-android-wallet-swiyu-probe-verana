package ch.admin.foitt.wallet.platform.veranaTrust.domain.usecase

import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustResolution

fun interface ResolveVeranaTrust {
    suspend operator fun invoke(did: String): VeranaTrustResolution
}
