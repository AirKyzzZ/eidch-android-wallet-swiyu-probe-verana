package ch.admin.foitt.wallet.platform.veranaTrust.domain.usecase

import ch.admin.foitt.openid4vc.domain.model.jwk.Jwk
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaDidKeyBinding
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustRole

fun interface VerifyVeranaDidKeyBinding {
    suspend operator fun invoke(
        did: String,
        role: VeranaTrustRole,
        certificateKey: Jwk,
    ): VeranaDidKeyBinding
}
