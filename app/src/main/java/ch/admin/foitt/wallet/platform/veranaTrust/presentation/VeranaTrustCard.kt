package ch.admin.foitt.wallet.platform.veranaTrust.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.admin.foitt.wallet.R
import ch.admin.foitt.wallet.platform.composables.presentation.spaceBarKeyClickable
import ch.admin.foitt.wallet.platform.utils.openLink
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.EcsAssetRef
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.EcsClaimReader
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.EcsOrganization
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.EcsService
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.EcsVerdict
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustCredential
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustEvidence
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustRole
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustVerdict
import ch.admin.foitt.wallet.theme.Sizes
import ch.admin.foitt.wallet.theme.WalletTheme

// The proof-of-trust card as versioned at playground/public/trust-card/index.html. Wording and
// palette are fixed cross-wallet, so the card renders the same evaluation in every wallet.
@Composable
fun VeranaTrustCard(
    evidence: VeranaTrustEvidence?,
    onOpenDetails: (() -> Unit)?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    partyName: String? = null,
    credentialName: String? = null,
) {
    if (evidence == null && !isLoading) return

    val action = onOpenDetails.takeIf { evidence != null }
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
            ),
        color = CardPalette.surface,
        contentColor = CardPalette.grey900,
        shape = RoundedCornerShape(Sizes.s04),
        border = BorderStroke(Sizes.line01, CardPalette.grey200),
    ) {
        Column(
            modifier = Modifier.padding(Sizes.s04),
            verticalArrangement = Arrangement.spacedBy(Sizes.s04),
        ) {
            if (isLoading || evidence == null) {
                LoadingRow(did = evidence?.did)
            } else {
                CardContent(
                    evidence = evidence,
                    partyName = partyName,
                    credentialName = credentialName,
                    onRetry = onRetry,
                )
            }
        }
    }
}

@Composable
private fun CardContent(
    evidence: VeranaTrustEvidence,
    partyName: String?,
    credentialName: String?,
    onRetry: () -> Unit,
) {
    val verdict = evidence.cardVerdict()
    val credentials = evidence.credentials
    val serviceCredential = EcsClaimReader.findServiceCredential(credentials)
    val organizationCredential = EcsClaimReader.findOrganizationCredential(credentials)
    val serviceTone = rowTone(verdict, serviceCredential)
    val organizationTone = rowTone(verdict, organizationCredential)
    val serviceWithheld = EcsClaimReader.isSelfIssued(serviceCredential, evidence.did)
    val organizationWithheld = EcsClaimReader.isSelfIssued(organizationCredential, evidence.did)
    val service = if (serviceTone == StepTone.OK && !serviceWithheld) {
        EcsClaimReader.readEcsService(serviceCredential)
    } else {
        null
    }
    val organization = if (organizationTone == StepTone.OK && !organizationWithheld) {
        EcsClaimReader.readEcsOrganization(organizationCredential)
    } else {
        null
    }

    DidRow(
        did = evidence.did,
        verdict = verdict,
        isTestnet = evidence.summary?.production == false,
    )

    if (credentials.isNotEmpty()) {
        Column {
            ChainStep(tone = serviceTone, label = "SERVICE") {
                if (service != null) {
                    ServiceIdentity(service)
                } else {
                    WithheldIdentity(
                        headline = if (serviceCredential != null) {
                            "Service claims not verified"
                        } else {
                            "No ECS-Service credential presented"
                        },
                        tone = serviceTone,
                        copy = withheldCopy(serviceCredential, serviceTone, evidence.did),
                    )
                }
            }
            ChainStep(tone = organizationTone, label = "OPERATED BY", isLast = true) {
                if (organization != null) {
                    OrganizationIdentity(organization)
                } else {
                    WithheldIdentity(
                        headline = if (organizationCredential != null) {
                            "Operator claims not verified"
                        } else {
                            "No ECS-Organization credential presented"
                        },
                        tone = organizationTone,
                        copy = withheldCopy(organizationCredential, organizationTone, evidence.did)
                            ?: "Nothing verifies who operates this service",
                    )
                }
            }
        }
    }

    VerdictPill(
        verdict = verdict,
        note = verdictNote(evidence, verdict, credentials),
    )

    AskBlock(
        evidence = evidence,
        party = service?.name ?: partyName ?: evidence.did.middleTruncated(),
        credentialLabel = credentialName ?: "this credential",
    )

    if (service?.terms != null || service?.privacy != null || service?.minimumAgeRequired != null) {
        ConditionsBlock(service)
    }

    if (evidence.verdict == VeranaTrustVerdict.RESOLVER_UNAVAILABLE) {
        Text(
            text = stringResource(R.string.verana_trust_retry),
            style = WalletTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = CardPalette.link,
            modifier = Modifier
                .clickable(role = Role.Button, onClick = onRetry)
                .spaceBarKeyClickable(onRetry)
                .padding(vertical = Sizes.s01),
        )
    }
}

@Composable
private fun LoadingRow(did: String?) = Column(
    verticalArrangement = Arrangement.spacedBy(Sizes.s04),
) {
    did?.let {
        DidRow(did = it, verdict = CardVerdict.RESOLVING, isTestnet = false)
    } ?: Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Sizes.s03),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(Sizes.s05),
            color = CardPalette.grey500,
            strokeWidth = Sizes.line02,
        )
        Spacer(Modifier.weight(1f))
        VeranaMark(size = 19.dp)
    }
    VerdictPill(
        verdict = CardVerdict.RESOLVING,
        note = "Checking the Verana public registry…",
    )
}

@Composable
private fun DidRow(
    did: String,
    verdict: CardVerdict,
    isTestnet: Boolean,
) = Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(Sizes.s02),
) {
    Box(
        modifier = Modifier
            .size(Sizes.s02)
            .background(verdict.color, CircleShape),
    )
    Text(
        text = did.middleTruncated(),
        style = WalletTheme.typography.bodySmall,
        color = CardPalette.grey500,
        maxLines = 1,
        modifier = Modifier.weight(1f),
    )
    if (isTestnet) {
        Text(
            text = "TESTNET",
            style = WalletTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
            color = CardPalette.warning,
            modifier = Modifier
                .background(CardPalette.warningContainer, RoundedCornerShape(Sizes.s01))
                .padding(horizontal = Sizes.s02, vertical = 2.dp),
        )
    }
    VeranaMark(size = 19.dp)
}

@Composable
private fun ChainStep(
    tone: StepTone,
    label: String,
    isLast: Boolean = false,
    content: @Composable () -> Unit,
) = Row(
    modifier = Modifier.height(IntrinsicSize.Min),
    horizontalArrangement = Arrangement.spacedBy(Sizes.s03),
) {
    Column(
        modifier = Modifier
            .width(Sizes.s07)
            .fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StepTick(tone)
        if (!isLast) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .width(Sizes.line02)
                    .background(tone.railColor),
            )
        }
    }
    Column(
        modifier = Modifier
            .weight(1f)
            .padding(bottom = if (isLast) Sizes.s00 else Sizes.s04),
        verticalArrangement = Arrangement.spacedBy(Sizes.s01),
    ) {
        SectionLabel(label)
        content()
    }
}

@Composable
private fun StepTick(tone: StepTone) = Box(
    modifier = Modifier
        .size(Sizes.s07)
        .background(tone.badgeColor, CircleShape),
    contentAlignment = Alignment.Center,
) {
    when (tone) {
        StepTone.OK -> Icon(
            painter = painterResource(R.drawable.wallet_ic_checkmark),
            contentDescription = null,
            modifier = Modifier.size(15.dp),
            tint = Color.White,
        )

        StepTone.BAD -> Icon(
            painter = painterResource(R.drawable.wallet_ic_cross),
            contentDescription = null,
            modifier = Modifier.size(15.dp),
            tint = Color.White,
        )

        StepTone.NONE -> Text(
            text = "?",
            style = WalletTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
        )
    }
}

@Composable
private fun ServiceIdentity(service: EcsService) {
    val description = EcsClaimReader.stripLinks(service.description)
    Row(horizontalArrangement = Arrangement.spacedBy(Sizes.s03)) {
        MonogramTile(name = service.name, verified = service.logo?.digest != null)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Sizes.s01),
        ) {
            IdentityHeading(name = service.name, countryCode = null)
            if (description.text.isNotEmpty()) {
                Text(
                    text = description.text,
                    style = WalletTheme.typography.bodyMedium,
                    color = CardPalette.grey700,
                )
            }
            if (description.removed > 0) {
                Text(
                    text = "${description.removed} link${if (description.removed > 1) "s" else ""} " +
                        "removed from this description before display",
                    style = WalletTheme.typography.bodySmall,
                    color = CardPalette.grey500,
                )
            }
        }
    }
}

@Composable
private fun OrganizationIdentity(
    organization: EcsOrganization,
) = Row(horizontalArrangement = Arrangement.spacedBy(Sizes.s03)) {
    MonogramTile(name = organization.name, verified = organization.logo?.digest != null)
    Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(Sizes.s01),
    ) {
        IdentityHeading(name = organization.name, countryCode = organization.countryCode)
        organization.address?.let { address ->
            Text(
                text = address,
                style = WalletTheme.typography.bodyMedium,
                color = CardPalette.grey700,
            )
        }
        organization.registryId?.let { registryId ->
            RegistryChip(label = "REG", value = registryId)
        }
    }
}

@Composable
private fun WithheldIdentity(
    headline: String,
    tone: StepTone,
    copy: String?,
) = Column(verticalArrangement = Arrangement.spacedBy(Sizes.s01)) {
    Text(
        text = headline,
        style = WalletTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
        color = if (tone == StepTone.NONE) CardPalette.grey600 else CardPalette.danger,
    )
    copy?.let {
        Text(
            text = it,
            style = WalletTheme.typography.bodySmall,
            color = CardPalette.grey500,
        )
    }
}

@Composable
private fun IdentityHeading(
    name: String?,
    countryCode: String?,
) = Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(Sizes.s02),
) {
    Text(
        text = name ?: "Not presented",
        style = WalletTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        color = CardPalette.grey900,
        modifier = Modifier.weight(1f, fill = false),
    )
    countryCode?.let { CountryFlag(code = it) }
}

@Composable
private fun MonogramTile(
    name: String?,
    verified: Boolean,
) {
    val initials = initialsOf(name)
    val tint = LOGO_TINTS[initials.first().code % LOGO_TINTS.size]
    Box(modifier = Modifier.size(Sizes.s10)) {
        Box(
            modifier = Modifier
                .size(Sizes.s10)
                .background(tint, RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = initials,
                style = WalletTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
            )
        }
        if (verified) {
            Box(
                modifier = Modifier
                    .size(Sizes.s04)
                    .align(Alignment.BottomEnd)
                    .offset(x = Sizes.s01, y = Sizes.s01)
                    .background(Color.White, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.wallet_ic_checkmark),
                    contentDescription = null,
                    modifier = Modifier.size(11.dp),
                    tint = CardPalette.positive,
                )
            }
        }
    }
}

// Drawn rather than an emoji: emoji flags resolve through the system font and fall back
// inconsistently across Android versions. Undrawn countries degrade to the ISO code.
@Composable
private fun CountryFlag(code: String, size: Dp = Sizes.s04) {
    if (code == "CH") {
        Canvas(modifier = Modifier.size(size)) {
            val unit = this.size.width / 32f
            drawRoundRect(
                color = SwissFlagRed,
                cornerRadius = CornerRadius(3f * unit),
            )
            drawRect(
                color = Color.White,
                topLeft = Offset(13f * unit, 6f * unit),
                size = Size(6f * unit, 20f * unit),
            )
            drawRect(
                color = Color.White,
                topLeft = Offset(6f * unit, 13f * unit),
                size = Size(20f * unit, 6f * unit),
            )
        }
    } else {
        Text(
            text = code,
            style = WalletTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = CardPalette.grey600,
            modifier = Modifier
                .background(CardPalette.grey100, RoundedCornerShape(Sizes.s01))
                .padding(horizontal = Sizes.s01, vertical = 2.dp),
        )
    }
}

@Composable
private fun RegistryChip(
    label: String,
    value: String,
) = Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(Sizes.s01),
    modifier = Modifier
        .background(CardPalette.grey100, RoundedCornerShape(Sizes.s01))
        .padding(horizontal = Sizes.s02, vertical = Sizes.s01),
) {
    Text(
        text = label,
        style = WalletTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
        color = CardPalette.grey500,
    )
    Text(
        text = value,
        style = WalletTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
        color = CardPalette.grey700,
    )
}

@Composable
private fun VerdictPill(
    verdict: CardVerdict,
    note: String?,
) = Column(verticalArrangement = Arrangement.spacedBy(Sizes.s02)) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Sizes.s02),
        modifier = Modifier
            .background(Color.White, RoundedCornerShape(Sizes.s03))
            .border(width = Sizes.line02, color = verdict.color, shape = RoundedCornerShape(Sizes.s03))
            .padding(horizontal = Sizes.s03, vertical = Sizes.s02),
    ) {
        VeranaMark(size = Sizes.s04)
        Text(
            text = verdict.label,
            style = WalletTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp),
            color = verdict.color,
        )
    }
    note?.let {
        Text(
            text = it,
            style = WalletTheme.typography.bodySmall,
            color = when (verdict) {
                CardVerdict.RESOLVING, CardVerdict.TRUSTED, CardVerdict.UNVERIFIED -> CardPalette.grey600
                CardVerdict.PARTIAL, CardVerdict.UNTRUSTED -> CardPalette.dangerDark
            },
        )
    }
}

@Composable
private fun AskBlock(
    evidence: VeranaTrustEvidence,
    party: String,
    credentialLabel: String,
) {
    val granted = evidence.askGranted()
    val borderColor = when (granted) {
        true -> CardPalette.positive
        false -> CardPalette.danger
        null -> CardPalette.grey200
    }
    val containerColor = when (granted) {
        true -> CardPalette.positiveContainer
        false -> CardPalette.dangerContainer
        null -> CardPalette.grey50
    }
    val verb = when (evidence.role) {
        VeranaTrustRole.ISSUER -> "issuer"
        VeranaTrustRole.VERIFIER -> "verifier"
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(Sizes.s02),
        modifier = Modifier
            .fillMaxWidth()
            .background(containerColor, RoundedCornerShape(Sizes.s03))
            .border(width = Sizes.line02, color = borderColor, shape = RoundedCornerShape(Sizes.s03))
            .padding(Sizes.s03),
    ) {
        SectionLabel(if (evidence.role == VeranaTrustRole.ISSUER) "OFFERS YOU" else "ASKS YOU FOR")
        Text(
            text = credentialLabel,
            style = WalletTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = CardPalette.grey900,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Sizes.s02)) {
            when (granted) {
                true -> Icon(
                    painter = painterResource(R.drawable.wallet_ic_checkmark),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = CardPalette.positive,
                )

                false -> Icon(
                    painter = painterResource(R.drawable.wallet_ic_cross),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = CardPalette.danger,
                )

                null -> Icon(
                    painter = painterResource(R.drawable.wallet_ic_info),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = CardPalette.grey500,
                )
            }
            Text(
                text = when (granted) {
                    true -> "$party is an authorized $verb of $credentialLabel"
                    false -> "$party is not an authorized $verb of $credentialLabel"
                    null -> "This could not be checked against the registry."
                },
                style = WalletTheme.typography.bodyMedium,
                color = CardPalette.grey800,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ConditionsBlock(service: EcsService) = Column(
    verticalArrangement = Arrangement.spacedBy(Sizes.s02),
    modifier = Modifier
        .fillMaxWidth()
        .background(CardPalette.grey100, RoundedCornerShape(Sizes.s03))
        .padding(Sizes.s03),
) {
    SectionLabel("CONDITIONS OF CONNECTING")
    val minimumAge = service.minimumAgeRequired
    if (minimumAge != null) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Sizes.s02),
        ) {
            Text(
                text = "$minimumAge+",
                style = WalletTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = CardPalette.warning,
            )
            Text(
                text = "This service requires you to be at least $minimumAge to connect",
                style = WalletTheme.typography.bodyMedium,
                color = CardPalette.grey700,
                modifier = Modifier.weight(1f),
            )
        }
    } else {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Sizes.s02),
        ) {
            Icon(
                painter = painterResource(R.drawable.wallet_ic_info),
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = CardPalette.grey500,
            )
            Text(
                text = "No age restriction",
                style = WalletTheme.typography.bodyMedium,
                color = CardPalette.grey700,
            )
        }
    }
    ConditionRow(asset = service.terms, label = "Terms & conditions")
    ConditionRow(asset = service.privacy, label = "Privacy policy")
}

@Composable
private fun ConditionRow(
    asset: EcsAssetRef?,
    label: String,
) {
    asset ?: return
    val context = LocalContext.current
    val open = { context.openLink(asset.uri) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Sizes.s02),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = open)
            .spaceBarKeyClickable(open),
    ) {
        Icon(
            painter = painterResource(R.drawable.wallet_ic_lock_small),
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = CardPalette.link,
        )
        Text(
            text = label,
            style = WalletTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = CardPalette.link,
            modifier = Modifier.weight(1f),
        )
        if (asset.digest != null) {
            Icon(
                painter = painterResource(R.drawable.wallet_ic_checkmark),
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = CardPalette.positive,
            )
            Text(
                text = "intact",
                style = WalletTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = CardPalette.positive,
            )
        } else {
            Text(
                text = "no digest",
                style = WalletTheme.typography.labelSmall,
                color = CardPalette.grey500,
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) = Text(
    text = text,
    style = WalletTheme.typography.labelSmall.copy(
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        letterSpacing = 0.8.sp,
    ),
    color = CardPalette.grey500,
)

@Composable
private fun VeranaMark(size: Dp) = Canvas(modifier = Modifier.size(size)) {
    val unit = this.size.width / 64f
    drawRoundRect(
        color = VeranaPurple,
        cornerRadius = CornerRadius(12f * unit),
    )
    val path = Path().apply {
        moveTo(46.3f * unit, 22.8f * unit)
        lineTo(32f * unit, 50.4f * unit)
        lineTo(17.7f * unit, 22.8f * unit)
        lineTo(19.6f * unit, 19.4f * unit)
        lineTo(21.6f * unit, 22.9f * unit)
        lineTo(32f * unit, 43.4f * unit)
        lineTo(42.4f * unit, 22.9f * unit)
        lineTo(44.4f * unit, 19.4f * unit)
        close()
        moveTo(22.4f * unit, 15.8f * unit)
        lineTo(32f * unit, 34.2f * unit)
        lineTo(41.3f * unit, 15.8f * unit)
        close()
    }
    drawPath(path, Color.White)
}

private enum class CardVerdict(
    val label: String,
    val color: Color,
) {
    RESOLVING("CHECKING…", CardPalette.grey500),
    TRUSTED("TRUSTED", CardPalette.positive),
    PARTIAL("PARTIAL", CardPalette.warning),
    UNTRUSTED("UNTRUSTED", CardPalette.danger),
    UNVERIFIED("COULD NOT VERIFY", CardPalette.grey500),
}

private enum class StepTone(
    val badgeColor: Color,
    val railColor: Color,
) {
    OK(CardPalette.positive, CardPalette.positiveRail),
    BAD(CardPalette.danger, CardPalette.dangerRail),
    NONE(CardPalette.grey400, CardPalette.grey300),
}

// [UW-POT-5] The pill follows the resolution's own trust status, never a wallet-side guess.
private fun VeranaTrustEvidence.cardVerdict(): CardVerdict = when (summary?.trustStatus) {
    "TRUSTED" -> CardVerdict.TRUSTED
    "PARTIAL" -> CardVerdict.PARTIAL
    "UNTRUSTED" -> CardVerdict.UNTRUSTED
    else -> CardVerdict.UNVERIFIED
}

private fun rowTone(verdict: CardVerdict, credential: VeranaTrustCredential?): StepTone = when {
    verdict == CardVerdict.UNVERIFIED -> StepTone.NONE
    verdict == CardVerdict.UNTRUSTED -> StepTone.BAD
    credential?.result == "VALID" -> StepTone.OK
    else -> StepTone.BAD
}

private fun withheldCopy(
    credential: VeranaTrustCredential?,
    tone: StepTone,
    did: String,
): String? = when {
    tone == StepTone.NONE -> "Not checked."
    credential == null -> null
    EcsClaimReader.isSelfIssued(credential, did) -> "Issued by this service to itself, so nothing independent verifies it."
    else -> "Nothing in the registry vouches for this credential, so its claims are not shown."
}

private fun verdictNote(
    evidence: VeranaTrustEvidence,
    verdict: CardVerdict,
    credentials: List<VeranaTrustCredential>,
): String? = when {
    verdict == CardVerdict.UNVERIFIED && evidence.verdict == VeranaTrustVerdict.RESOLVER_UNAVAILABLE ->
        "The Verana resolver could not be reached. This counterparty is neither trusted nor untrusted."

    verdict == CardVerdict.UNVERIFIED ->
        "This counterparty could not be verified against the Verana registry. It is neither trusted nor untrusted."

    credentials.isNotEmpty() -> EcsClaimReader.describeVerdict(verdict.toEcsVerdict(), credentials)

    else -> null
}

private fun CardVerdict.toEcsVerdict(): EcsVerdict = when (this) {
    CardVerdict.TRUSTED -> EcsVerdict.TRUSTED
    CardVerdict.PARTIAL -> EcsVerdict.PARTIAL
    CardVerdict.RESOLVING, CardVerdict.UNTRUSTED, CardVerdict.UNVERIFIED -> EcsVerdict.UNTRUSTED
}

private fun VeranaTrustEvidence.askGranted(): Boolean? = when {
    verdict == VeranaTrustVerdict.TRUSTED_AUTHORIZED && authorizations.isNotEmpty() -> true
    verdict == VeranaTrustVerdict.TRUSTED_NOT_AUTHORIZED -> false
    else -> null
}

private fun String.middleTruncated(maxLength: Int = 40): String =
    if (length <= maxLength) this else "${take(maxLength - 10)}…${takeLast(9)}"

private fun initialsOf(name: String?): String =
    (name ?: "?")
        .split(Regex("""\s+"""))
        .filter { it.isNotEmpty() }
        .take(2)
        .mapNotNull { word -> word.firstOrNull()?.uppercaseChar() }
        .joinToString("")
        .ifEmpty { "?" }

private val VeranaPurple = Color(0xFF763EF0)
private val SwissFlagRed = Color(0xFFD52B1E)
private val LOGO_TINTS = listOf(
    Color(0xFF0F9488),
    Color(0xFF1D4ED8),
    Color(0xFF9A3412),
    Color(0xFF3F3F46),
)

private object CardPalette {
    val surface = Color(0xFFFFFFFF)
    val positive = Color(0xFF059669)
    val positiveContainer = Color(0xFFECFDF5)
    val positiveRail = Color(0xFF6EE7B7)
    val danger = Color(0xFFDC2626)
    val dangerDark = Color(0xFFB91C1C)
    val dangerContainer = Color(0xFFFEF2F2)
    val dangerRail = Color(0xFFFCA5A5)
    val warning = Color(0xFFD97706)
    val warningContainer = Color(0xFFFFFBEB)
    val link = Color(0xFF1D4ED8)
    val grey50 = Color(0xFFF9FAFB)
    val grey100 = Color(0xFFF3F4F6)
    val grey200 = Color(0xFFE5E7EB)
    val grey300 = Color(0xFFD1D5DB)
    val grey400 = Color(0xFF9CA3AF)
    val grey500 = Color(0xFF6B7280)
    val grey600 = Color(0xFF4B5563)
    val grey700 = Color(0xFF374151)
    val grey800 = Color(0xFF1F2937)
    val grey900 = Color(0xFF111827)
}
