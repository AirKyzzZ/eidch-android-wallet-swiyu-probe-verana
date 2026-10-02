package ch.admin.foitt.wallet.platform.veranaTrust.domain.usecase.implementation

import ch.admin.foitt.openid4vc.domain.model.jwk.Jwk
import ch.admin.foitt.openid4vc.domain.model.jwt.Jwt
import ch.admin.foitt.openid4vc.domain.model.vcSdJwt.VcSdJwtCredential
import ch.admin.foitt.openid4vc.domain.model.x509.didSubjectAlternativeName
import ch.admin.foitt.openid4vc.domain.model.x509.toP256Jwk
import ch.admin.foitt.openid4vc.domain.model.x509.x5cLeafCertificate
import ch.admin.foitt.wallet.platform.veranaTrust.VeranaDevnetFixtures
import ch.admin.foitt.wallet.platform.veranaTrust.VeranaDevnetFixtures.ISSUER_DID
import ch.admin.foitt.wallet.platform.veranaTrust.VeranaDevnetFixtures.VERIFIER_DID
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaDidKeyBinding
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustRole
import ch.admin.foitt.wallet.platform.veranaTrust.domain.repository.VeranaDocumentRepository
import com.nimbusds.jose.crypto.ECDSAVerifier
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jwt.SignedJWT
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.impl.annotations.MockK
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class VerifyVeranaDidKeyBindingImplTest {

    @MockK
    private lateinit var documents: VeranaDocumentRepository

    private lateinit var useCase: VerifyVeranaDidKeyBindingImpl

    private val issuerCredential = VcSdJwtCredential(payload = VeranaDevnetFixtures.issuerCredentialSdJwt)
    private val issuerKey = requireNotNull(issuerCredential.x5cPublicKey)
    private val verifierRequest = Jwt(VeranaDevnetFixtures.verifierRequestObjectJwt)
    private val verifierKey = verifierRequest.x5cLeafCertificate().toP256Jwk()

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        useCase = VerifyVeranaDidKeyBindingImpl(documents)

        coEvery { documents.fetchDidDocument(ISSUER_DID) } returns VeranaDevnetFixtures.issuerDidDocument
        coEvery { documents.fetchDidDocument(VERIFIER_DID) } returns VeranaDevnetFixtures.verifierDidDocument
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `the devnet credential and request object are signed by their x5c leaf and name their DID`() {
        assertTrue(issuerCredential.isX5cIssuerKey)
        assertEquals(ISSUER_DID, issuerCredential.issuer)
        assertTrue(signedBy(VeranaDevnetFixtures.issuerCredentialSdJwt.substringBefore('~'), issuerKey))
        assertTrue(signedBy(VeranaDevnetFixtures.issuerMetadataJwt, issuerKey))

        assertEquals(VERIFIER_DID, verifierRequest.x5cLeafCertificate().didSubjectAlternativeName())
        assertTrue(signedBy(VeranaDevnetFixtures.verifierRequestObjectJwt, verifierKey))
    }

    @Test
    fun `the devnet issuer DID document lists the credential signing key as an assertion method`() = runTest {
        assertEquals(VeranaDidKeyBinding.PROVEN, useCase(ISSUER_DID, VeranaTrustRole.ISSUER, issuerKey))
    }

    @Test
    fun `the devnet verifier DID document lists the request signing key for authentication`() = runTest {
        assertEquals(VeranaDidKeyBinding.PROVEN, useCase(VERIFIER_DID, VeranaTrustRole.VERIFIER, verifierKey))
    }

    @Test
    fun `an authentication key does not prove an issuer`() = runTest {
        assertEquals(VeranaDidKeyBinding.NOT_PROVEN, useCase(VERIFIER_DID, VeranaTrustRole.ISSUER, verifierKey))
    }

    @Test
    fun `a certificate key the DID document does not list is not proven`() = runTest {
        assertEquals(VeranaDidKeyBinding.NOT_PROVEN, useCase(VERIFIER_DID, VeranaTrustRole.VERIFIER, issuerKey))

        val withoutIssuerKey = VeranaDevnetFixtures.issuerDidDocument.let { document ->
            document.copy(assertionMethods = document.assertionMethods.filterNot { it.id.endsWith("#openid4vc-development-issuer") })
        }
        coEvery { documents.fetchDidDocument(ISSUER_DID) } returns withoutIssuerKey
        assertEquals(VeranaDidKeyBinding.NOT_PROVEN, useCase(ISSUER_DID, VeranaTrustRole.ISSUER, issuerKey))
    }

    @Test
    fun `an unreachable DID document leaves the binding unavailable`() = runTest {
        coEvery { documents.fetchDidDocument(ISSUER_DID) } returns null

        assertEquals(VeranaDidKeyBinding.UNAVAILABLE, useCase(ISSUER_DID, VeranaTrustRole.ISSUER, issuerKey))
    }

    private fun signedBy(compactJws: String, key: Jwk): Boolean {
        val ecKey = ECKey.parse("""{"kty":"${key.kty}","crv":"${key.crv}","x":"${key.x}","y":"${key.y}"}""")
        return SignedJWT.parse(compactJws).verify(ECDSAVerifier(ecKey))
    }
}
