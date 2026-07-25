package ch.admin.foitt.wallet.platform.veranaTrust.presentation

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import ch.admin.foitt.wallet.R
import ch.admin.foitt.wallet.platform.composables.presentation.spaceBarKeyClickable
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustEvidence
import ch.admin.foitt.wallet.platform.veranaTrust.presentation.adapter.mapVeranaTrustUiState
import ch.admin.foitt.wallet.platform.veranaTrust.presentation.model.VeranaTrustAction
import ch.admin.foitt.wallet.platform.veranaTrust.presentation.model.VeranaTrustTone
import ch.admin.foitt.wallet.platform.veranaTrust.presentation.model.VeranaTrustUiState
import ch.admin.foitt.wallet.theme.Sizes
import ch.admin.foitt.wallet.theme.WalletTexts
import ch.admin.foitt.wallet.theme.WalletTheme

@Composable
fun VeranaTrustCard(
    evidence: VeranaTrustEvidence?,
    onOpenDetails: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
) {
    if (evidence == null && !isLoading) return
    val state = evidence?.takeUnless { isLoading }?.let(::mapVeranaTrustUiState)
    val action = state.action(onOpenDetails, onRetry)
    val actionLabel = state.actionLabelResId()?.let { stringResource(it) }
    val title = stringResource(state?.titleResId ?: R.string.verana_trust_loading_title)
    val description = stringResource(state?.descriptionResId ?: R.string.verana_trust_loading_description)
    val colors = cardColors(state?.tone ?: VeranaTrustTone.NEUTRAL)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (action == null) {
                    Modifier
                } else {
                    Modifier
                        .clickable(role = Role.Button, onClick = action)
                        .spaceBarKeyClickable(action)
                }
            )
            .semantics {
                contentDescription = listOfNotNull(title, description, actionLabel).joinToString(". ")
                if (action != null) role = Role.Button
            },
        color = colors.container,
        contentColor = colors.content,
        shape = RoundedCornerShape(Sizes.s04),
    ) {
        Row(
            modifier = Modifier.padding(Sizes.s04),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                painter = painterResource(colors.icon),
                contentDescription = null,
                modifier = Modifier.size(Sizes.s06),
                tint = colors.content,
            )
            Spacer(Modifier.width(Sizes.s03))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Sizes.s02),
            ) {
                WalletTexts.TitleMediumEmphasized(text = title, color = colors.content)
                WalletTexts.BodyMedium(text = description, color = colors.content)
                state?.did?.let { did ->
                    WalletTexts.LabelMedium(
                        text = did,
                        color = colors.content,
                    )
                }
                state?.schemaIds?.forEach { schemaId ->
                    WalletTexts.LabelSmall(text = schemaId, color = colors.content)
                }
            }
            VeranaTrustCardEnd(state?.action, colors)
        }
    }
}

private fun VeranaTrustUiState?.action(
    onOpenDetails: () -> Unit,
    onRetry: () -> Unit,
): (() -> Unit)? = when (this?.action) {
    VeranaTrustAction.OPEN_DETAILS -> onOpenDetails
    VeranaTrustAction.RETRY -> onRetry
    VeranaTrustAction.NONE, null -> null
}

@StringRes
private fun VeranaTrustUiState?.actionLabelResId(): Int? = when (this?.action) {
    VeranaTrustAction.OPEN_DETAILS -> R.string.verana_trust_open_details
    VeranaTrustAction.RETRY -> R.string.verana_trust_retry
    VeranaTrustAction.NONE, null -> null
}

@Composable
private fun VeranaTrustCardEnd(
    action: VeranaTrustAction?,
    colors: VeranaCardColors,
) = when (action) {
    VeranaTrustAction.OPEN_DETAILS -> Icon(
        painter = painterResource(R.drawable.wallet_ic_chevron_right),
        contentDescription = null,
        modifier = Modifier.size(Sizes.s06),
    )

    VeranaTrustAction.RETRY -> WalletTexts.LabelLargeEmphasized(
        text = stringResource(R.string.verana_trust_retry),
        color = colors.content,
    )

    VeranaTrustAction.NONE -> Unit
    null -> CircularProgressIndicator(
        modifier = Modifier.size(Sizes.s06),
        color = colors.content,
        strokeWidth = Sizes.line02,
    )
}

@Composable
private fun cardColors(tone: VeranaTrustTone): VeranaCardColors = when (tone) {
    VeranaTrustTone.POSITIVE -> VeranaCardColors(
        container = WalletTheme.colorScheme.tertiaryContainer,
        content = WalletTheme.colorScheme.onTertiaryContainer,
        icon = R.drawable.wallet_ic_check_circle_complete_thin,
    )

    VeranaTrustTone.WARNING -> VeranaCardColors(
        container = WalletTheme.colorScheme.errorContainer,
        content = WalletTheme.colorScheme.onErrorContainer,
        icon = R.drawable.wallet_ic_warning,
    )

    VeranaTrustTone.NEGATIVE -> VeranaCardColors(
        container = WalletTheme.colorScheme.surfaceContainerHighest,
        content = WalletTheme.colorScheme.onSurface,
        icon = R.drawable.wallet_ic_not_trusted,
    )

    VeranaTrustTone.NEUTRAL -> VeranaCardColors(
        container = WalletTheme.colorScheme.surfaceContainerHigh,
        content = WalletTheme.colorScheme.onSurface,
        icon = R.drawable.wallet_ic_info,
    )
}

private data class VeranaCardColors(
    val container: Color,
    val content: Color,
    val icon: Int,
)
