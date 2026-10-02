package ch.admin.foitt.wallet.platform.veranaTrust.domain.usecase.implementation

import ch.admin.foitt.openid4vc.domain.model.jwk.Jwk
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaAccreditation
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaAccreditationReason
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaAccreditationStatus
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaDidKeyBinding
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustEvidence
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustResolution
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustRole
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustStatus
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaUntrustedReason
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.blocksAccept
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.canRetry
import ch.admin.foitt.wallet.platform.veranaTrust.domain.usecase.CheckVeranaAccreditation
import ch.admin.foitt.wallet.platform.veranaTrust.domain.usecase.ResolveVeranaTrust
import ch.admin.foitt.wallet.platform.veranaTrust.domain.usecase.VerifyVeranaDidKeyBinding
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class EvaluateVeranaTrustImplTest {

    @MockK
    private lateinit var verifyBinding: VerifyVeranaDidKeyBinding

    @MockK
    private lateinit var resolveVeranaTrust: ResolveVeranaTrust

    @MockK
    private lateinit var checkAccreditation: CheckVeranaAccreditation

    private lateinit var useCase: EvaluateVeranaTrustImpl

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        useCase = EvaluateVeranaTrustImpl(verifyBinding, resolveVeranaTrust, checkAccreditation)

        coEvery { verifyBinding(DID, any(), KEY) } returns VeranaDidKeyBinding.PROVEN
        coEvery { resolveVeranaTrust(DID) } returns resolution(VeranaTrustStatus.TRUSTED)
        coEvery { checkAccreditation(DID, any(), VCT) } returns accreditation(VeranaAccreditationStatus.GRANTED)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `trusted and granted opens the gate`() = runTest {
        val evidence = useCase(VeranaTrustRole.ISSUER, DID, VCT, KEY)

        assertEquals(VeranaTrustStatus.TRUSTED, evidence.resolution.status)
        assertEquals(VeranaAccreditationStatus.GRANTED, evidence.accreditation?.status)
        assertEquals(VCT, evidence.vct)
        assertFalse(evidence.blocksAccept)
        assertFalse(evidence.canRetry)
    }

    @Test
    fun `trusted but refused keeps the gate closed without a retry`() = runTest {
        coEvery { checkAccreditation(DID, any(), VCT) } returns accreditation(VeranaAccreditationStatus.REFUSED)

        val evidence = useCase(VeranaTrustRole.VERIFIER, DID, VCT, KEY)

        assertTrue(evidence.blocksAccept)
        assertFalse(evidence.canRetry)
    }

    @Test
    fun `undetermined accreditation keeps the gate closed with a retry`() = runTest {
        coEvery { checkAccreditation(DID, any(), VCT) } returns accreditation(VeranaAccreditationStatus.UNDETERMINED)

        val evidence = useCase(VeranaTrustRole.ISSUER, DID, VCT, KEY)

        assertTrue(evidence.blocksAccept)
        assertTrue(evidence.canRetry)
    }

    @Test
    fun `unverified and untrusted counterparties keep the gate closed even when granted`() = runTest {
        coEvery { resolveVeranaTrust(DID) } returns resolution(VeranaTrustStatus.UNVERIFIED)
        val unverified = useCase(VeranaTrustRole.ISSUER, DID, VCT, KEY)

        coEvery { resolveVeranaTrust(DID) } returns resolution(VeranaTrustStatus.UNTRUSTED)
        val untrusted = useCase(VeranaTrustRole.ISSUER, DID, VCT, KEY)

        assertTrue(unverified.blocksAccept)
        assertTrue(unverified.canRetry)
        assertTrue(untrusted.blocksAccept)
        assertFalse(untrusted.canRetry)
    }

    @Test
    fun `a certificate key the DID does not list makes the counterparty untrusted`() = runTest {
        coEvery { verifyBinding(DID, VeranaTrustRole.VERIFIER, KEY) } returns VeranaDidKeyBinding.NOT_PROVEN

        val evidence = useCase(VeranaTrustRole.VERIFIER, DID, VCT, KEY)

        assertEquals(VeranaTrustStatus.UNTRUSTED, evidence.resolution.status)
        assertEquals(VeranaUntrustedReason.DID_NOT_PROVEN, evidence.resolution.reason)
        assertNull(evidence.accreditation)
        assertTrue(evidence.blocksAccept)
        coVerify(exactly = 0) { resolveVeranaTrust(any()) }
        coVerify(exactly = 0) { checkAccreditation(any(), any(), any()) }
    }

    @Test
    fun `an unreachable DID document leaves the counterparty unverified`() = runTest {
        coEvery { verifyBinding(DID, VeranaTrustRole.ISSUER, KEY) } returns VeranaDidKeyBinding.UNAVAILABLE

        val evidence = useCase(VeranaTrustRole.ISSUER, DID, VCT, KEY)

        assertEquals(VeranaTrustStatus.UNVERIFIED, evidence.resolution.status)
        assertTrue(evidence.blocksAccept)
        assertTrue(evidence.canRetry)
    }

    @Test
    fun `a DID authenticated without a certificate skips the key binding`() = runTest {
        useCase(VeranaTrustRole.VERIFIER, DID, VCT, certificateKey = null)

        coVerify(exactly = 0) { verifyBinding(any(), any(), any()) }
        coVerify { resolveVeranaTrust(DID) }
    }

    @Test
    fun `an unexpected failure is unverified, never trusted`() = runTest {
        coEvery { resolveVeranaTrust(DID) } throws IllegalStateException("boom")

        val evidence = useCase(VeranaTrustRole.ISSUER, DID, VCT, KEY)

        assertEquals(VeranaTrustStatus.UNVERIFIED, evidence.resolution.status)
        assertTrue(evidence.blocksAccept)
    }

    @Test
    fun `no evidence means a counterparty without a DID, which keeps the normal flow`() {
        val missing: VeranaTrustEvidence? = null

        assertFalse(missing.blocksAccept)
    }

    private fun resolution(status: VeranaTrustStatus) = VeranaTrustResolution(did = DID, status = status)

    private fun accreditation(status: VeranaAccreditationStatus) = VeranaAccreditation(
        status = status,
        reason = VeranaAccreditationReason.ACTIVE_PARTICIPANT,
    )

    private companion object {
        const val DID = "did:webvh:QmService:service.example"
        const val VCT = "https://ecosystem.example/vt/vct/8"
        val KEY = Jwk(x = "x", y = "y", crv = "P-256", kty = "EC")
    }
}
