package ch.admin.foitt.wallet.platform.credentialPresentation.domain.model

import ch.admin.foitt.openid4vc.domain.model.jwk.Jwk

data class AuthenticatedVerifier(
    val did: String,
    val certificateKey: Jwk?,
)
