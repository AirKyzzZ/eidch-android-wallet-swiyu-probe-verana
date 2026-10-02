package ch.admin.foitt.wallet.platform.veranaTrust.presentation.adapter

import ch.admin.foitt.wallet.R
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaAccreditationStatus
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustEvidence
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustRole
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustStatus
import ch.admin.foitt.wallet.platform.veranaTrust.presentation.model.VeranaTrustClaimUiState
import ch.admin.foitt.wallet.platform.veranaTrust.presentation.model.VeranaTrustCredentialUiState
import ch.admin.foitt.wallet.platform.veranaTrust.presentation.model.VeranaTrustTone
import ch.admin.foitt.wallet.platform.veranaTrust.presentation.model.VeranaTrustUiState
import java.net.URI

fun mapVeranaTrustUiState(evidence: VeranaTrustEvidence): VeranaTrustUiState {
    val presentation = when (evidence.resolution.status) {
        VeranaTrustStatus.UNVERIFIED -> VerdictPresentation(
            titleResId = R.string.verana_trust_unverified_title,
            descriptionResId = R.string.verana_trust_unverified_description,
            tone = VeranaTrustTone.NEUTRAL,
        )

        VeranaTrustStatus.UNTRUSTED -> VerdictPresentation(
            titleResId = R.string.verana_trust_untrusted_title,
            descriptionResId = R.string.verana_trust_untrusted_description,
            tone = VeranaTrustTone.NEGATIVE,
        )

        VeranaTrustStatus.TRUSTED -> when (evidence.accreditation?.status) {
            VeranaAccreditationStatus.GRANTED -> VerdictPresentation(
                titleResId = R.string.verana_trust_trusted_authorized_title,
                descriptionResId = R.string.verana_trust_trusted_authorized_description,
                tone = VeranaTrustTone.POSITIVE,
            )

            VeranaAccreditationStatus.REFUSED -> VerdictPresentation(
                titleResId = R.string.verana_trust_not_authorized_title,
                descriptionResId = R.string.verana_trust_not_authorized_description,
                tone = VeranaTrustTone.WARNING,
            )

            VeranaAccreditationStatus.UNDETERMINED, null -> VerdictPresentation(
                titleResId = R.string.verana_trust_unavailable_title,
                descriptionResId = R.string.verana_trust_unavailable_description,
                tone = VeranaTrustTone.NEUTRAL,
            )
        }
    }

    return VeranaTrustUiState(
        titleResId = presentation.titleResId,
        descriptionResId = presentation.descriptionResId,
        roleResId = when (evidence.role) {
            VeranaTrustRole.ISSUER -> R.string.verana_trust_issuer
            VeranaTrustRole.VERIFIER -> R.string.verana_trust_verifier
        },
        tone = presentation.tone,
        evidence = evidence,
        credentials = evidence.resolution.credentials.map { credential ->
            VeranaTrustCredentialUiState(
                ecsSchema = credential.ecsSchema,
                id = credential.id,
                claims = credential.claims.map { claim ->
                    VeranaTrustClaimUiState(
                        name = claim.name,
                        values = claim.values,
                        safeHttpUrl = claim.safeHttpUrl?.takeIf(::isSafeHttpUrl),
                    )
                },
            )
        },
    )
}

private fun isSafeHttpUrl(value: String): Boolean = runCatching {
    val uri = URI(value)
    uri.scheme in setOf("https", "http") && !uri.host.isNullOrBlank()
}.getOrDefault(false)

private data class VerdictPresentation(
    val titleResId: Int,
    val descriptionResId: Int,
    val tone: VeranaTrustTone,
)
