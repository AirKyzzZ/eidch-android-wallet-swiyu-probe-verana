package ch.admin.foitt.wallet.platform.veranaTrust.presentation.model

import androidx.annotation.StringRes
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaAuthorizationEvidence
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaPermissionChainEntry
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustRole
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustSummary
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustVerdict

enum class VeranaTrustTone {
    POSITIVE,
    WARNING,
    NEGATIVE,
    NEUTRAL,
}

enum class VeranaTrustAction {
    OPEN_DETAILS,
    RETRY,
    NONE,
}

data class VeranaTrustClaimUiState(
    val name: String,
    val values: List<String>,
    val safeHttpUrl: String?,
)

data class VeranaTrustCredentialUiState(
    val result: String,
    val ecsType: String?,
    val presentedBy: String,
    val issuedBy: String,
    val id: String,
    val type: String,
    val format: String,
    val claims: List<VeranaTrustClaimUiState>,
    val permissionChain: List<VeranaPermissionChainEntry>,
)

data class VeranaTrustUiState(
    @param:StringRes val titleResId: Int,
    @param:StringRes val descriptionResId: Int,
    @param:StringRes val roleResId: Int,
    val tone: VeranaTrustTone,
    val action: VeranaTrustAction,
    val role: VeranaTrustRole,
    val verdict: VeranaTrustVerdict,
    val did: String,
    val schemaIds: List<String>,
    val summary: VeranaTrustSummary?,
    val authorizations: List<VeranaAuthorizationEvidence>,
    val resolverUrl: String,
    val credentials: List<VeranaTrustCredentialUiState>,
)

sealed interface VeranaTrustDetailsLoadState {
    val trust: VeranaTrustUiState

    data class Loading(
        override val trust: VeranaTrustUiState,
    ) : VeranaTrustDetailsLoadState

    data class Loaded(
        override val trust: VeranaTrustUiState,
    ) : VeranaTrustDetailsLoadState

    data class Unavailable(
        override val trust: VeranaTrustUiState,
    ) : VeranaTrustDetailsLoadState
}
