package ch.admin.foitt.wallet.platform.veranaTrust.presentation.model

import androidx.annotation.StringRes
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustEvidence

enum class VeranaTrustTone {
    POSITIVE,
    WARNING,
    NEGATIVE,
    NEUTRAL,
}

data class VeranaTrustClaimUiState(
    val name: String,
    val values: List<String>,
    val safeHttpUrl: String?,
)

data class VeranaTrustCredentialUiState(
    val ecsSchema: String,
    val id: String?,
    val claims: List<VeranaTrustClaimUiState>,
)

data class VeranaTrustUiState(
    @param:StringRes val titleResId: Int,
    @param:StringRes val descriptionResId: Int,
    @param:StringRes val roleResId: Int,
    val tone: VeranaTrustTone,
    val evidence: VeranaTrustEvidence,
    val credentials: List<VeranaTrustCredentialUiState>,
)
