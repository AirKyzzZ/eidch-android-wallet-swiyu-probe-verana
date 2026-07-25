package ch.admin.foitt.wallet.platform.veranaTrust.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class VeranaTrustDetails(
    val summary: VeranaTrustSummary,
    val credentials: List<VeranaTrustCredential>,
)

@Serializable
data class VeranaTrustCredential(
    val result: String,
    val ecsType: String?,
    val presentedBy: String,
    val issuedBy: String,
    val id: String,
    val type: String,
    val format: String,
    val claims: List<VeranaTrustClaim>,
    val permissionChain: List<VeranaPermissionChainEntry>,
)

@Serializable
data class VeranaTrustClaim(
    val name: String,
    val values: List<String>,
    val safeHttpUrl: String? = null,
)

@Serializable
data class VeranaPermissionChainEntry(
    val permissionId: Long,
    val type: String,
    val did: String,
    val didIsTrustedVerifiableService: Boolean,
    val deposit: String,
    val permissionState: String,
    val serviceName: String? = null,
    val organizationName: String? = null,
    val countryCode: String? = null,
    val legalJurisdiction: String? = null,
    val effectiveFrom: String? = null,
    val effectiveUntil: String? = null,
)
