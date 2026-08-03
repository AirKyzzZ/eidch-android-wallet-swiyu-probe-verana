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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ch.admin.foitt.wallet.R
import ch.admin.foitt.wallet.platform.composables.Buttons
import ch.admin.foitt.wallet.platform.scaffold.presentation.LocalScaffoldPaddings
import ch.admin.foitt.wallet.platform.utils.openLink
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustEvidence
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustRole
import ch.admin.foitt.wallet.platform.veranaTrust.presentation.model.VeranaTrustCredentialUiState
import ch.admin.foitt.wallet.platform.veranaTrust.presentation.model.VeranaTrustDetailsLoadState
import ch.admin.foitt.wallet.theme.Sizes
import ch.admin.foitt.wallet.theme.WalletTexts
import ch.admin.foitt.wallet.theme.WalletTheme

@Composable
fun VeranaTrustDetailsScreen(
    viewModel: VeranaTrustDetailsViewModel,
) {
    val context = LocalContext.current
    VeranaTrustDetailsContent(
        state = viewModel.uiState.collectAsStateWithLifecycle().value,
        cardEvidence = viewModel.cardEvidence.collectAsStateWithLifecycle().value,
        onRetry = viewModel::onRetry,
        onOpenLink = context::openLink,
    )
}

@Composable
private fun VeranaTrustDetailsContent(
    state: VeranaTrustDetailsLoadState,
    cardEvidence: VeranaTrustEvidence,
    onRetry: () -> Unit,
    onOpenLink: (String) -> Unit,
) {
    val trust = state.trust
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
                    evidence = cardEvidence,
                    onOpenDetails = null,
                    onRetry = onRetry,
                )
            }

            item {
                DetailsSection(title = stringResource(R.string.verana_trust_identity_section)) {
                    DetailRow(stringResource(R.string.verana_trust_did), trust.did)
                    DetailRow(stringResource(R.string.verana_trust_resolver), trust.resolverUrl)
                }
            }

            item {
                DetailsSection(title = stringResource(R.string.verana_trust_credential_types)) {
                    trust.schemaIds.forEach { schemaId ->
                        WalletTexts.BodyMedium(text = schemaId)
                    }
                }
            }

            trust.summary?.let { summary ->
                item {
                    DetailsSection(title = stringResource(R.string.verana_trust_q1_section)) {
                        DetailRow(stringResource(R.string.verana_trust_status), summary.trustStatus)
                        DetailRow(
                            stringResource(R.string.verana_trust_production),
                            stringResource(if (summary.production) R.string.verana_trust_yes else R.string.verana_trust_no),
                        )
                        DetailRow(stringResource(R.string.verana_trust_evaluated_at), summary.evaluatedAt)
                        DetailRow(stringResource(R.string.verana_trust_block), summary.evaluatedAtBlock.toString())
                        DetailRow(stringResource(R.string.verana_trust_expires_at), summary.expiresAt)
                    }
                }
            }

            item {
                DetailsSection(
                    title = stringResource(
                        if (trust.role == VeranaTrustRole.ISSUER) {
                            R.string.verana_trust_authorization_section_issuer
                        } else {
                            R.string.verana_trust_authorization_section_verifier
                        }
                    )
                ) {
                    trust.authorizations.forEach { authorization ->
                        DetailRow(stringResource(R.string.verana_trust_did), authorization.did)
                        DetailRow(stringResource(R.string.verana_trust_credential_types), authorization.vcSchemaId)
                        DetailRow(
                            stringResource(R.string.verana_trust_authorized),
                            stringResource(if (authorization.authorized) R.string.verana_trust_yes else R.string.verana_trust_no),
                        )
                        DetailRow(stringResource(R.string.verana_trust_evaluated_at), authorization.evaluatedAt)
                        DetailRow(stringResource(R.string.verana_trust_block), authorization.evaluatedAtBlock.toString())
                    }
                }
            }

            when (state) {
                is VeranaTrustDetailsLoadState.Loading -> item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Sizes.s08),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                is VeranaTrustDetailsLoadState.Unavailable -> item {
                    DetailsSection(title = stringResource(R.string.verana_trust_full_evidence)) {
                        WalletTexts.BodyMedium(
                            text = stringResource(R.string.verana_trust_full_evidence_unavailable)
                        )
                        Spacer(Modifier.height(Sizes.s02))
                        Buttons.FilledSecondary(
                            text = stringResource(R.string.verana_trust_retry),
                            onClick = onRetry,
                        )
                    }
                }

                is VeranaTrustDetailsLoadState.Loaded -> items(
                    items = trust.credentials,
                ) { credential ->
                    CredentialEvidence(
                        credential = credential,
                        onOpenLink = onOpenLink,
                    )
                }
            }
        }
    }
}

@Composable
private fun CredentialEvidence(
    credential: VeranaTrustCredentialUiState,
    onOpenLink: (String) -> Unit,
) {
    DetailsSection(title = stringResource(R.string.verana_trust_credential_evidence)) {
        DetailRow(stringResource(R.string.verana_trust_result), credential.result)
        credential.ecsType?.let {
            DetailRow(stringResource(R.string.verana_trust_ecosystem_type), it)
        }
        DetailRow(stringResource(R.string.verana_trust_presented_by), credential.presentedBy)
        DetailRow(stringResource(R.string.verana_trust_issued_by), credential.issuedBy)
        DetailRow(stringResource(R.string.verana_trust_credential_id), credential.id)
        DetailRow(stringResource(R.string.verana_trust_type), credential.type)
        DetailRow(stringResource(R.string.verana_trust_format), credential.format)

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

        if (credential.permissionChain.isNotEmpty()) {
            SubsectionTitle(stringResource(R.string.verana_trust_permission_chain))
            credential.permissionChain.forEach { permission ->
                WalletTexts.LabelLargeEmphasized(
                    text = stringResource(R.string.verana_trust_permission, permission.permissionId)
                )
                DetailRow(stringResource(R.string.verana_trust_type), permission.type)
                DetailRow(stringResource(R.string.verana_trust_did), permission.did)
                DetailRow(
                    stringResource(R.string.verana_trust_trusted_service),
                    stringResource(
                        if (permission.didIsTrustedVerifiableService) {
                            R.string.verana_trust_yes
                        } else {
                            R.string.verana_trust_no
                        }
                    ),
                )
                DetailRow(stringResource(R.string.verana_trust_permission_state), permission.permissionState)
                DetailRow(stringResource(R.string.verana_trust_deposit), permission.deposit)
                permission.serviceName?.let {
                    DetailRow(stringResource(R.string.verana_trust_service_name), it)
                }
                permission.organizationName?.let {
                    DetailRow(stringResource(R.string.verana_trust_organization_name), it)
                }
                permission.countryCode?.let {
                    DetailRow(stringResource(R.string.verana_trust_country_code), it)
                }
                permission.legalJurisdiction?.let {
                    DetailRow(stringResource(R.string.verana_trust_legal_jurisdiction), it)
                }
                permission.effectiveFrom?.let {
                    DetailRow(stringResource(R.string.verana_trust_effective_from), it)
                }
                permission.effectiveUntil?.let {
                    DetailRow(stringResource(R.string.verana_trust_effective_until), it)
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
