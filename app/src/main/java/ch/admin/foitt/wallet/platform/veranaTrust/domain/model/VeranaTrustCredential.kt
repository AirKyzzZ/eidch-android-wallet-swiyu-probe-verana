package ch.admin.foitt.wallet.platform.veranaTrust.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class VeranaTrustCredential(
    val ecsSchema: String,
    val ecosystemId: Long? = null,
    val id: String? = null,
    val claims: List<VeranaTrustClaim>,
)

@Serializable
data class VeranaTrustClaim(
    val name: String,
    val values: List<String>,
    val safeHttpUrl: String? = null,
)
