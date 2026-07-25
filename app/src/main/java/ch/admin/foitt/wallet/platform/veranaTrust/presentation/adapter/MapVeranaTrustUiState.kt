package ch.admin.foitt.wallet.platform.veranaTrust.presentation.adapter

import ch.admin.foitt.wallet.R
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustDetails
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustEvidence
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustRole
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustVerdict
import ch.admin.foitt.wallet.platform.veranaTrust.presentation.model.VeranaTrustAction
import ch.admin.foitt.wallet.platform.veranaTrust.presentation.model.VeranaTrustClaimUiState
import ch.admin.foitt.wallet.platform.veranaTrust.presentation.model.VeranaTrustCredentialUiState
import ch.admin.foitt.wallet.platform.veranaTrust.presentation.model.VeranaTrustTone
import ch.admin.foitt.wallet.platform.veranaTrust.presentation.model.VeranaTrustUiState
import java.net.URI

fun mapVeranaTrustUiState(
    evidence: VeranaTrustEvidence,
    details: VeranaTrustDetails? = null,
): VeranaTrustUiState {
    val presentation = when (evidence.verdict) {
        VeranaTrustVerdict.TRUSTED_AUTHORIZED -> VerdictPresentation(
            titleResId = R.string.verana_trust_trusted_authorized_title,
            descriptionResId = R.string.verana_trust_trusted_authorized_description,
            tone = VeranaTrustTone.POSITIVE,
            action = VeranaTrustAction.OPEN_DETAILS,
        )

        VeranaTrustVerdict.TRUSTED_NOT_AUTHORIZED -> VerdictPresentation(
            titleResId = R.string.verana_trust_not_authorized_title,
            descriptionResId = R.string.verana_trust_not_authorized_description,
            tone = VeranaTrustTone.WARNING,
            action = VeranaTrustAction.OPEN_DETAILS,
        )

        VeranaTrustVerdict.UNTRUSTED -> VerdictPresentation(
            titleResId = R.string.verana_trust_untrusted_title,
            descriptionResId = R.string.verana_trust_untrusted_description,
            tone = VeranaTrustTone.NEGATIVE,
            action = VeranaTrustAction.NONE,
        )

        VeranaTrustVerdict.RESOLVER_UNAVAILABLE -> VerdictPresentation(
            titleResId = R.string.verana_trust_unavailable_title,
            descriptionResId = R.string.verana_trust_unavailable_description,
            tone = VeranaTrustTone.NEUTRAL,
            action = VeranaTrustAction.RETRY,
        )
    }

    return VeranaTrustUiState(
        titleResId = presentation.titleResId,
        descriptionResId = presentation.descriptionResId,
        roleResId = when (evidence.role) {
            VeranaTrustRole.ISSUER -> R.string.verana_trust_issuer
            VeranaTrustRole.VERIFIER -> R.string.verana_trust_verifier
        },
        tone = presentation.tone,
        action = presentation.action,
        role = evidence.role,
        verdict = evidence.verdict,
        did = evidence.did,
        schemaIds = evidence.vcSchemaIds,
        summary = evidence.summary,
        authorizations = evidence.authorizations,
        resolverUrl = evidence.resolverUrl,
        credentials = details?.credentials.orEmpty().map { credential ->
            VeranaTrustCredentialUiState(
                result = credential.result,
                ecsType = credential.ecsType,
                presentedBy = credential.presentedBy,
                issuedBy = credential.issuedBy,
                id = credential.id,
                type = credential.type,
                format = credential.format,
                claims = credential.claims.map { claim ->
                    VeranaTrustClaimUiState(
                        name = claim.name,
                        values = claim.values,
                        safeHttpUrl = claim.safeHttpUrl?.takeIf(::isSafeHttpUrl),
                    )
                },
                permissionChain = credential.permissionChain,
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
    val action: VeranaTrustAction,
)
