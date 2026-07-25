package ch.admin.foitt.openid4vc.domain.model.vcSdJwt

import ch.admin.foitt.openid4vc.domain.model.jwk.Jwks
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class JwtVcIssuerMetadata(
    @SerialName("issuer")
    val issuer: String,
    @SerialName("jwks")
    val jwks: Jwks,
)
