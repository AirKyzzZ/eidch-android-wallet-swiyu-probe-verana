package ch.admin.foitt.wallet.platform.veranaTrust.domain.model

import kotlinx.serialization.json.JsonObject

data class VeranaVerificationMethod(
    val id: String,
    val type: String?,
    val publicKeyJwk: JsonObject?,
    val publicKeyMultibase: String?,
)

data class VeranaDidDocument(
    val id: String,
    val assertionMethods: List<VeranaVerificationMethod>,
    val authenticationMethods: List<VeranaVerificationMethod>,
)

enum class VeranaDidKeyBinding {
    PROVEN,
    NOT_PROVEN,
    UNAVAILABLE,
}
