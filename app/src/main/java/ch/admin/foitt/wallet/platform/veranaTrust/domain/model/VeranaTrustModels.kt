package ch.admin.foitt.wallet.platform.veranaTrust.domain.model

import kotlinx.serialization.Serializable

const val VeranaTrustResolverUrl = "https://resolver.testnet.verana.network"

// [UW-CFG-2] label source; never the resolver's production flag, which is wrong
// on testnet (verana-resolver#148).
const val VeranaNetworkProduction = false

@Serializable
enum class VeranaTrustRole {
    ISSUER,
    VERIFIER,
}

@Serializable
enum class VeranaTrustVerdict {
    TRUSTED_AUTHORIZED,
    TRUSTED_NOT_AUTHORIZED,
    UNTRUSTED,
    UNVERIFIED,
    RESOLVER_UNAVAILABLE,
}

@Serializable
data class VeranaTrustSummary(
    val did: String,
    val trustStatus: String,
    val production: Boolean,
    val evaluatedAt: String,
    val evaluatedAtBlock: Long,
    val expiresAt: String,
)

@Serializable
data class VeranaAuthorizationEvidence(
    val did: String,
    val vcSchemaId: String,
    val authorized: Boolean,
    val evaluatedAt: String,
    val evaluatedAtBlock: Long,
)

@Serializable
data class VeranaTrustEvidence(
    val role: VeranaTrustRole,
    val did: String,
    val vcSchemaIds: List<String>,
    val verdict: VeranaTrustVerdict,
    val summary: VeranaTrustSummary?,
    val authorizations: List<VeranaAuthorizationEvidence>,
    val credentials: List<VeranaTrustCredential> = emptyList(),
    val resolverUrl: String = VeranaTrustResolverUrl,
)

val VeranaTrustEvidence?.blocksAccept: Boolean
    get() = this?.verdict == VeranaTrustVerdict.UNTRUSTED || this?.verdict == VeranaTrustVerdict.TRUSTED_NOT_AUTHORIZED

@Serializable
data class VeranaVerifierTrustContext(
    val authenticatedVerifierDid: String,
    val credentialId: Long,
)

sealed interface VeranaResolverResult<out T> {
    data class Success<T>(val value: T) : VeranaResolverResult<T>
    data object NotFound : VeranaResolverResult<Nothing>
    data object Unavailable : VeranaResolverResult<Nothing>
}
