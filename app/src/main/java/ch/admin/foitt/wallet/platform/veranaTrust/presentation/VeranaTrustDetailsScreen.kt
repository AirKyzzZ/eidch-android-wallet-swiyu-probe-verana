package ch.admin.foitt.wallet.platform.veranaTrust.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import ch.admin.foitt.wallet.R
import ch.admin.foitt.wallet.platform.composables.Buttons
import ch.admin.foitt.wallet.platform.scaffold.presentation.LocalScaffoldPaddings
import ch.admin.foitt.wallet.platform.utils.openLink
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaAccreditation
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaAccreditationStatus
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustEvidence
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustResolution
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustRole
import ch.admin.foitt.wallet.platform.veranaTrust.presentation.model.VeranaTrustCredentialUiState
import ch.admin.foitt.wallet.platform.veranaTrust.presentation.model.VeranaTrustUiState
import ch.admin.foitt.wallet.theme.Sizes
import ch.admin.foitt.wallet.theme.WalletTexts
import ch.admin.foitt.wallet.theme.WalletTheme
import java.net.URLEncoder

@Composable
fun VeranaTrustDetailsScreen(
    viewModel: VeranaTrustDetailsViewModel,
) {
    val context = LocalContext.current
    VeranaTrustDetailsContent(
        trust = viewModel.uiState,
        onOpenLink = context::openLink,
    )
}

@Composable
private fun VeranaTrustDetailsContent(
    trust: VeranaTrustUiState,
    onOpenLink: (String) -> Unit,
) {
    val evidence = trust.evidence
    val topPadding = LocalScaffoldPaddings.current.calculateTopPadding()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WalletTheme.colorScheme.surfaceContainerLow),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            contentPadding = PaddingValues(
                start = Sizes.s04,
                top = topPadding + Sizes.s04,
                end = Sizes.s04,
                bottom = Sizes.s08,
            ),
            verticalArrangement = Arrangement.spacedBy(Sizes.s04),
        ) {
            item {
                WalletTexts.LabelLargeEmphasized(text = stringResource(trust.roleResId))
                Spacer(Modifier.height(Sizes.s01))
                WalletTexts.TitleScreen(text = stringResource(trust.titleResId))
                Spacer(Modifier.height(Sizes.s02))
                WalletTexts.BodyLarge(text = stringResource(trust.descriptionResId))
            }

            item {
                VeranaTrustCard(
                    evidence = evidence,
                    onOpenDetails = null,
                    onRetry = null,
                )
            }

            item {
                IdentitySection(evidence = evidence, onOpenLink = onOpenLink)
            }

            item {
                DetailsSection(title = stringResource(R.string.verana_trust_credential_types)) {
                    DetailRow(stringResource(R.string.verana_trust_type), evidence.vct ?: NotAvailable)
                    evidence.accreditation?.credentialName?.let {
                        DetailRow(stringResource(R.string.verana_trust_name), it)
                    }
                }
            }

            item {
                ResolutionSection(resolution = evidence.resolution)
            }

            item {
                AccreditationSection(role = evidence.role, accreditation = evidence.accreditation)
            }

            items(items = trust.credentials) { credential ->
                CredentialEvidence(
                    credential = credential,
                    onOpenLink = onOpenLink,
                )
            }
        }
    }
}

@Composable
private fun IdentitySection(
    evidence: VeranaTrustEvidence,
    onOpenLink: (String) -> Unit,
) = DetailsSection(title = stringResource(R.string.verana_trust_identity_section)) {
    val network = evidence.resolution.network
    DetailRow(stringResource(R.string.verana_trust_did), evidence.did)
    network?.let {
        DetailRow(stringResource(R.string.verana_trust_network), it.name)
        DetailRow(stringResource(R.string.verana_trust_indexer), it.indexerUrl)
    }
    network?.explorerUrl?.let { explorerUrl ->
        val link = "$explorerUrl/did/${URLEncoder.encode(evidence.did, Charsets.UTF_8.name())}"
        Buttons.TextLink(
            text = stringResource(R.string.verana_trust_explorer),
            onClick = { onOpenLink(link) },
            endIcon = painterResource(R.drawable.wallet_ic_external_link),
        )
    }
}

@Composable
private fun ResolutionSection(
    resolution: VeranaTrustResolution,
) = DetailsSection(title = stringResource(R.string.verana_trust_q1_section)) {
    DetailRow(stringResource(R.string.verana_trust_status), resolution.status.name)
    resolution.reason?.let {
        DetailRow(stringResource(R.string.verana_trust_reason), it.name)
    }
    resolution.network?.let {
        DetailRow(
            stringResource(R.string.verana_trust_production),
            stringResource(if (it.production) R.string.verana_trust_yes else R.string.verana_trust_no),
        )
    }
    resolution.evaluatedAt?.let {
        DetailRow(stringResource(R.string.verana_trust_evaluated_at), it)
    }
    resolution.expiresAt?.let {
        DetailRow(stringResource(R.string.verana_trust_expires_at), it)
    }
    if (resolution.unresolvableCredentialIds.isNotEmpty()) {
        DetailRow(
            stringResource(R.string.verana_trust_unresolvable_credentials),
            resolution.unresolvableCredentialIds.joinToString("\n"),
        )
    }
}

@Composable
private fun AccreditationSection(
    role: VeranaTrustRole,
    accreditation: VeranaAccreditation?,
) = DetailsSection(
    title = stringResource(
        when (role) {
            VeranaTrustRole.ISSUER -> R.string.verana_trust_authorization_section_issuer
            VeranaTrustRole.VERIFIER -> R.string.verana_trust_authorization_section_verifier
        }
    )
) {
    DetailRow(
        stringResource(R.string.verana_trust_authorized),
        when (accreditation?.status) {
            VeranaAccreditationStatus.GRANTED -> stringResource(R.string.verana_trust_yes)
            VeranaAccreditationStatus.REFUSED -> stringResource(R.string.verana_trust_no)
            VeranaAccreditationStatus.UNDETERMINED, null -> NotAvailable
        },
    )
    accreditation?.let {
        DetailRow(stringResource(R.string.verana_trust_reason), it.reason.name)
    }
    accreditation?.ecosystemName?.let {
        DetailRow(stringResource(R.string.verana_trust_ecosystem), it)
    }
    accreditation?.schemaId?.let { schemaId ->
        DetailRow(stringResource(R.string.verana_trust_schema), "${accreditation.networkId.orEmpty()} · $schemaId")
    }
}

@Composable
private fun CredentialEvidence(
    credential: VeranaTrustCredentialUiState,
    onOpenLink: (String) -> Unit,
) {
    DetailsSection(title = stringResource(R.string.verana_trust_credential_evidence)) {
        DetailRow(stringResource(R.string.verana_trust_ecosystem_type), credential.ecsSchema)
        credential.id?.let {
            DetailRow(stringResource(R.string.verana_trust_credential_id), it)
        }

        if (credential.claims.isNotEmpty()) {
            SubsectionTitle(stringResource(R.string.verana_trust_claims))
            credential.claims.forEach { claim ->
                DetailRow(claim.name, claim.values.joinToString(", "))
                claim.safeHttpUrl?.let { link ->
                    Buttons.TextLink(
                        text = link,
                        onClick = { onOpenLink(link) },
                        endIcon = painterResource(R.drawable.wallet_ic_external_link),
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = WalletTheme.colorScheme.surfaceContainerHighest,
                shape = WalletTheme.shapes.medium,
            )
            .padding(Sizes.s04),
        verticalArrangement = Arrangement.spacedBy(Sizes.s02),
    ) {
        WalletTexts.TitleMediumEmphasized(
            text = title,
            modifier = Modifier.semantics { heading() },
        )
        HorizontalDivider()
        content()
    }
}

@Composable
private fun SubsectionTitle(title: String) {
    Spacer(Modifier.height(Sizes.s02))
    WalletTexts.LabelLargeEmphasized(text = title)
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(Sizes.s01)) {
        WalletTexts.LabelLargeEmphasized(text = label)
        WalletTexts.BodyMedium(text = value)
    }
}

private const val NotAvailable = "—"
