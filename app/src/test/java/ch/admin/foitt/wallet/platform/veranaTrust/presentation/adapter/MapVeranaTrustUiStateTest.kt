package ch.admin.foitt.wallet.platform.veranaTrust.presentation.adapter

import ch.admin.foitt.wallet.R
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaAccreditation
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaAccreditationReason
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaAccreditationStatus
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustClaim
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustCredential
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustEvidence
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustResolution
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustRole
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustStatus
import ch.admin.foitt.wallet.platform.veranaTrust.presentation.model.VeranaTrustTone
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class MapVeranaTrustUiStateTest {

    @Test
    fun `trusted and granted is positive`() {
        val state = mapVeranaTrustUiState(evidence(VeranaTrustStatus.TRUSTED, VeranaAccreditationStatus.GRANTED))

        assertEquals(R.string.verana_trust_trusted_authorized_title, state.titleResId)
        assertEquals(VeranaTrustTone.POSITIVE, state.tone)
        assertEquals(R.string.verana_trust_issuer, state.roleResId)
    }

    @Test
    fun `trusted but refused is a warning`() {
        val state = mapVeranaTrustUiState(evidence(VeranaTrustStatus.TRUSTED, VeranaAccreditationStatus.REFUSED))

        assertEquals(R.string.verana_trust_not_authorized_title, state.titleResId)
        assertEquals(VeranaTrustTone.WARNING, state.tone)
    }

    @Test
    fun `trusted with an undetermined accreditation is unavailable`() {
        val state = mapVeranaTrustUiState(evidence(VeranaTrustStatus.TRUSTED, VeranaAccreditationStatus.UNDETERMINED))

        assertEquals(R.string.verana_trust_unavailable_title, state.titleResId)
        assertEquals(VeranaTrustTone.NEUTRAL, state.tone)
    }

    @Test
    fun `untrusted is negative whatever the accreditation says`() {
        val state = mapVeranaTrustUiState(evidence(VeranaTrustStatus.UNTRUSTED, VeranaAccreditationStatus.GRANTED))

        assertEquals(R.string.verana_trust_untrusted_title, state.titleResId)
        assertEquals(VeranaTrustTone.NEGATIVE, state.tone)
    }

    @Test
    fun `unverified is neutral`() {
        val state = mapVeranaTrustUiState(evidence(VeranaTrustStatus.UNVERIFIED, null))

        assertEquals(R.string.verana_trust_unverified_title, state.titleResId)
        assertEquals(VeranaTrustTone.NEUTRAL, state.tone)
    }

    @Test
    fun `credential claims keep only safe http links`() {
        val credential = VeranaTrustCredential(
            ecsSchema = "ServiceCredential",
            claims = listOf(
                VeranaTrustClaim("privacyPolicyUri", listOf("https://svc.example/p"), "https://svc.example/p"),
                VeranaTrustClaim("termsAndConditionsUri", listOf("ftp://svc.example/t"), "ftp://svc.example/t"),
            ),
        )
        val evidence = evidence(VeranaTrustStatus.TRUSTED, VeranaAccreditationStatus.GRANTED).let {
            it.copy(resolution = it.resolution.copy(credentials = listOf(credential)))
        }

        val claims = mapVeranaTrustUiState(evidence).credentials.single().claims

        assertEquals("https://svc.example/p", claims[0].safeHttpUrl)
        assertNull(claims[1].safeHttpUrl)
    }

    private fun evidence(status: VeranaTrustStatus, accreditation: VeranaAccreditationStatus?) = VeranaTrustEvidence(
        role = VeranaTrustRole.ISSUER,
        did = DID,
        vct = "https://ecosystem.example/vt/vct/8",
        resolution = VeranaTrustResolution(did = DID, status = status),
        accreditation = accreditation?.let {
            VeranaAccreditation(status = it, reason = VeranaAccreditationReason.ACTIVE_PARTICIPANT)
        },
    )

    private companion object {
        const val DID = "did:webvh:QmService:service.example"
    }
}
