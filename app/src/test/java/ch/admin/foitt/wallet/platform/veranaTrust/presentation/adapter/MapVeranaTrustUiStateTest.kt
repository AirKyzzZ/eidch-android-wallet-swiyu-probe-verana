package ch.admin.foitt.wallet.platform.veranaTrust.presentation.adapter

import ch.admin.foitt.wallet.R
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaPermissionChainEntry
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustClaim
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustCredential
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustDetails
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustEvidence
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustRole
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustSummary
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustVerdict
import ch.admin.foitt.wallet.platform.veranaTrust.presentation.model.VeranaTrustAction
import ch.admin.foitt.wallet.platform.veranaTrust.presentation.model.VeranaTrustTone
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class MapVeranaTrustUiStateTest {
    @Test
    fun `trusted authorized issuer is the only positive state and opens details`() {
        val state = mapVeranaTrustUiState(evidence())

        assertEquals(R.string.verana_trust_issuer, state.roleResId)
        assertEquals(R.string.verana_trust_trusted_authorized_title, state.titleResId)
        assertEquals(VeranaTrustTone.POSITIVE, state.tone)
        assertEquals(VeranaTrustAction.OPEN_DETAILS, state.action)
    }

    @Test
    fun `trusted but unauthorized verifier is warning and still opens evidence`() {
        val state = mapVeranaTrustUiState(
            evidence(
                role = VeranaTrustRole.VERIFIER,
                verdict = VeranaTrustVerdict.TRUSTED_NOT_AUTHORIZED,
            )
        )

        assertEquals(R.string.verana_trust_verifier, state.roleResId)
        assertEquals(R.string.verana_trust_not_authorized_title, state.titleResId)
        assertEquals(VeranaTrustTone.WARNING, state.tone)
        assertEquals(VeranaTrustAction.OPEN_DETAILS, state.action)
        assertNotEquals(VeranaTrustTone.POSITIVE, state.tone)
    }

    @Test
    fun `untrusted is non-positive and has no action`() {
        val state = mapVeranaTrustUiState(evidence(verdict = VeranaTrustVerdict.UNTRUSTED))

        assertEquals(VeranaTrustTone.NEGATIVE, state.tone)
        assertEquals(VeranaTrustAction.NONE, state.action)
        assertNotEquals(VeranaTrustTone.POSITIVE, state.tone)
    }

    @Test
    fun `unverified is neutral and has no action`() {
        val state = mapVeranaTrustUiState(evidence(verdict = VeranaTrustVerdict.UNVERIFIED))

        assertEquals(R.string.verana_trust_unverified_title, state.titleResId)
        assertEquals(VeranaTrustTone.NEUTRAL, state.tone)
        assertEquals(VeranaTrustAction.NONE, state.action)
    }

    @Test
    fun `resolver unavailable is neutral and retries instead of opening details`() {
        val state = mapVeranaTrustUiState(evidence(verdict = VeranaTrustVerdict.RESOLVER_UNAVAILABLE))

        assertEquals(R.string.verana_trust_unavailable_title, state.titleResId)
        assertEquals(VeranaTrustTone.NEUTRAL, state.tone)
        assertEquals(VeranaTrustAction.RETRY, state.action)
        assertNotEquals(VeranaTrustTone.POSITIVE, state.tone)
    }

    @Test
    fun `every exact credential schema is retained as a separate row`() {
        val state = mapVeranaTrustUiState(
            evidence(schemaIds = listOf(SCHEMA_ID, SCHEMA_ID_2))
        )

        assertEquals(listOf(SCHEMA_ID, SCHEMA_ID_2), state.schemaIds)
    }

    @Test
    fun `full evidence exposes only safe http links`() {
        val details = details(
            claims = listOf(
                VeranaTrustClaim("privacyPolicy", listOf("safe"), "https://example.org/privacy"),
                VeranaTrustClaim("terms", listOf("unsafe"), "javascript:alert(1)"),
                VeranaTrustClaim("homepage", listOf("relative"), "/about"),
            )
        )

        val state = mapVeranaTrustUiState(evidence(), details)

        assertEquals("https://example.org/privacy", state.credentials[0].claims[0].safeHttpUrl)
        assertNull(state.credentials[0].claims[1].safeHttpUrl)
        assertNull(state.credentials[0].claims[2].safeHttpUrl)
    }

    private fun evidence(
        role: VeranaTrustRole = VeranaTrustRole.ISSUER,
        verdict: VeranaTrustVerdict = VeranaTrustVerdict.TRUSTED_AUTHORIZED,
        schemaIds: List<String> = listOf(SCHEMA_ID),
    ) = VeranaTrustEvidence(
        role = role,
        did = DID,
        vcSchemaIds = schemaIds,
        verdict = verdict,
        summary = summary(),
        authorizations = emptyList(),
    )

    private fun details(
        claims: List<VeranaTrustClaim> = emptyList(),
        summary: VeranaTrustSummary = summary(),
    ) = VeranaTrustDetails(
        summary = summary,
        credentials = listOf(
            VeranaTrustCredential(
                result = "TRUSTED",
                ecsType = "VerifiableService",
                presentedBy = DID,
                issuedBy = DID,
                id = "credential-1",
                type = "VerifiableCredential",
                format = "jwt_vc_json",
                claims = claims,
                permissionChain = listOf(
                    VeranaPermissionChainEntry(
                        permissionId = 1,
                        type = "ECOSYSTEM",
                        did = DID,
                        didIsTrustedVerifiableService = true,
                        deposit = "0",
                        permissionState = "VALIDATED",
                    )
                ),
            )
        ),
    )

    private fun summary() = VeranaTrustSummary(
        did = DID,
        trustStatus = "TRUSTED",
        production = true,
        evaluatedAt = "2026-07-18T12:00:00Z",
        evaluatedAtBlock = 42,
        expiresAt = "2027-07-18T12:00:00Z",
    )

    private companion object {
        const val DID = "did:web:example.org"
        const val SCHEMA_ID = "https://schemas.example/one"
        const val SCHEMA_ID_2 = "https://schemas.example/two"
    }
}
