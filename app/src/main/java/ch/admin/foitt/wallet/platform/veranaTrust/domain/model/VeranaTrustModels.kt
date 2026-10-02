package ch.admin.foitt.wallet.platform.veranaTrust.domain.model

import ch.admin.foitt.openid4vc.domain.model.jwk.Jwk
import kotlinx.serialization.Serializable

@Serializable
enum class VeranaTrustRole {
    ISSUER,
    VERIFIER,
}

@Serializable
enum class VeranaTrustStatus {
    TRUSTED,
    UNTRUSTED,
    UNVERIFIED,
}

@Serializable
enum class VeranaUntrustedReason {
    NO_DID_DOCUMENT,
    NOT_REGISTERED,
    NOT_TRUSTED,
    ECOSYSTEM_NOT_TRUSTED,
    DID_NOT_PROVEN,
}

@Serializable
data class VeranaTrustResolution(
    val did: String,
    val status: VeranaTrustStatus,
    val reason: VeranaUntrustedReason? = null,
    val network: VeranaNetwork? = null,
    val evaluatedAt: String? = null,
    val expiresAt: String? = null,
    val credentials: List<VeranaTrustCredential> = emptyList(),
    val unresolvableCredentialIds: List<String> = emptyList(),
) {
    companion object {
        fun unresolved(
            did: String,
            status: VeranaTrustStatus,
            reason: VeranaUntrustedReason? = null,
            network: VeranaNetwork? = null,
        ) = VeranaTrustResolution(
            did = did,
            status = status,
            reason = reason,
            network = network,
        )
    }
}

@Serializable
enum class VeranaAccreditationStatus {
    GRANTED,
    REFUSED,
    UNDETERMINED,
}

@Serializable
enum class VeranaAccreditationReason {
    ACTIVE_PARTICIPANT,
    NO_ACTIVE_PARTICIPANT,
    NO_VERANA_SCHEMA,
    SCHEMA_CREDENTIAL_MALFORMED,
    SCHEMA_CREDENTIAL_INVALID,
    UNKNOWN_NETWORK,
    ECOSYSTEM_MISMATCH,
    REGISTRY_UNREACHABLE,
}

@Serializable
data class VeranaAccreditation(
    val status: VeranaAccreditationStatus,
    val reason: VeranaAccreditationReason,
    val credentialName: String? = null,
    val ecosystemName: String? = null,
    val schemaId: String? = null,
    val networkId: String? = null,
)

@Serializable
data class VeranaTrustEvidence(
    val role: VeranaTrustRole,
    val did: String,
    val vct: String?,
    val resolution: VeranaTrustResolution,
    val accreditation: VeranaAccreditation?,
)

val VeranaTrustEvidence?.blocksAccept: Boolean
    get() = this != null &&
        !(resolution.status == VeranaTrustStatus.TRUSTED && accreditation?.status == VeranaAccreditationStatus.GRANTED)

val VeranaTrustEvidence.canRetry: Boolean
    get() = resolution.status == VeranaTrustStatus.UNVERIFIED ||
        accreditation?.status == VeranaAccreditationStatus.UNDETERMINED

@Serializable
data class VeranaVerifierTrustContext(
    val authenticatedVerifierDid: String,
    val certificateKey: Jwk?,
    val credentialId: Long,
)

sealed interface VeranaIndexerAnswer {
    data class Resolved(val resolution: VeranaTrustResolution) : VeranaIndexerAnswer
    data object NotRegistered : VeranaIndexerAnswer
    data object Unanswered : VeranaIndexerAnswer
}
