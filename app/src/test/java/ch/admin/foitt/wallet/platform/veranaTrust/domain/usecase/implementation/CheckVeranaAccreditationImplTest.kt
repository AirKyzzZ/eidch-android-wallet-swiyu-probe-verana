package ch.admin.foitt.wallet.platform.veranaTrust.domain.usecase.implementation

import ch.admin.foitt.wallet.platform.veranaTrust.VeranaDevnetFixtures
import ch.admin.foitt.wallet.platform.veranaTrust.VeranaDevnetFixtures.ECOSYSTEM_DID
import ch.admin.foitt.wallet.platform.veranaTrust.VeranaDevnetFixtures.ISSUER_DID
import ch.admin.foitt.wallet.platform.veranaTrust.VeranaDevnetFixtures.UNACCREDITED_ISSUER_DID
import ch.admin.foitt.wallet.platform.veranaTrust.VeranaDevnetFixtures.VCT
import ch.admin.foitt.wallet.platform.veranaTrust.VeranaDevnetFixtures.VTJSC_ID
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VERANA_NETWORKS
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaAccreditationReason
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaAccreditationStatus
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustClaim
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustCredential
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustResolution
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustRole
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustStatus
import ch.admin.foitt.wallet.platform.veranaTrust.domain.repository.VeranaDocumentRepository
import ch.admin.foitt.wallet.platform.veranaTrust.domain.repository.VeranaIndexerRepository
import ch.admin.foitt.wallet.platform.veranaTrust.domain.usecase.ResolveVeranaTrust
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CheckVeranaAccreditationImplTest {

    @MockK
    private lateinit var documents: VeranaDocumentRepository

    @MockK
    private lateinit var indexer: VeranaIndexerRepository

    private val devnet = VERANA_NETWORKS.first()

    private val resolveVeranaTrust = ResolveVeranaTrust { did ->
        VeranaTrustResolution(
            did = did,
            status = VeranaTrustStatus.TRUSTED,
            credentials = listOf(
                VeranaTrustCredential(
                    ecsSchema = "ServiceCredential",
                    claims = listOf(VeranaTrustClaim("name", listOf("Playground Demo"))),
                )
            ),
        )
    }

    private lateinit var useCase: CheckVeranaAccreditationImpl

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        useCase = CheckVeranaAccreditationImpl(documents, indexer, resolveVeranaTrust, VERANA_NETWORKS)

        coEvery { documents.fetchJson(VCT) } returns VeranaDevnetFixtures.typeMetadata
        coEvery { documents.fetchJson(VTJSC_ID) } returns VeranaDevnetFixtures.vtjsc
        coEvery { documents.fetchDidDocument(ECOSYSTEM_DID) } returns VeranaDevnetFixtures.ecosystemDidDocument
        coEvery { indexer.fetchSchemaEcosystemId(devnet, "8") } returns 6L
        coEvery { indexer.fetchEcosystemDid(devnet, 6L) } returns ECOSYSTEM_DID
        coEvery { indexer.hasActiveParticipant(devnet, any(), any(), "8") } returns false
        coEvery { indexer.hasActiveParticipant(devnet, ISSUER_DID, VeranaTrustRole.ISSUER, "8") } returns true
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `grants the accredited devnet issuer through the VTJSC chain`() = runTest {
        val accreditation = useCase(ISSUER_DID, VeranaTrustRole.ISSUER, VCT)

        assertEquals(VeranaAccreditationStatus.GRANTED, accreditation.status)
        assertEquals(VeranaAccreditationReason.ACTIVE_PARTICIPANT, accreditation.reason)
        assertEquals("DemoCredential", accreditation.credentialName)
        assertEquals("Playground Demo", accreditation.ecosystemName)
        assertEquals("8", accreditation.schemaId)
        assertEquals("vna-devnet-1", accreditation.networkId)
    }

    @Test
    fun `refuses the unaccredited devnet issuer`() = runTest {
        val accreditation = useCase(UNACCREDITED_ISSUER_DID, VeranaTrustRole.ISSUER, VCT)

        assertEquals(VeranaAccreditationStatus.REFUSED, accreditation.status)
        assertEquals(VeranaAccreditationReason.NO_ACTIVE_PARTICIPANT, accreditation.reason)
    }

    @Test
    fun `an issuer participant does not satisfy a verifier check`() = runTest {
        val accreditation = useCase(ISSUER_DID, VeranaTrustRole.VERIFIER, VCT)

        assertEquals(VeranaAccreditationStatus.REFUSED, accreditation.status)
        coVerify { indexer.hasActiveParticipant(devnet, ISSUER_DID, VeranaTrustRole.VERIFIER, "8") }
    }

    @Test
    fun `refuses a VTJSC whose proof does not verify`() = runTest {
        val tampered = JsonObject(VeranaDevnetFixtures.vtjsc + ("validUntil" to JsonPrimitive("2099-01-01T00:00:00.000Z")))
        coEvery { documents.fetchJson(VTJSC_ID) } returns tampered

        val accreditation = useCase(ISSUER_DID, VeranaTrustRole.ISSUER, VCT)

        assertEquals(VeranaAccreditationStatus.REFUSED, accreditation.status)
        assertEquals(VeranaAccreditationReason.SCHEMA_CREDENTIAL_INVALID, accreditation.reason)
        coVerify(exactly = 0) { indexer.hasActiveParticipant(any(), any(), any(), any()) }
    }

    @Test
    fun `refuses a VTJSC not issued by the ecosystem that owns the schema`() = runTest {
        coEvery { indexer.fetchEcosystemDid(devnet, 6L) } returns "did:webvh:QmOther:other.example"

        val accreditation = useCase(ISSUER_DID, VeranaTrustRole.ISSUER, VCT)

        assertEquals(VeranaAccreditationStatus.REFUSED, accreditation.status)
        assertEquals(VeranaAccreditationReason.ECOSYSTEM_MISMATCH, accreditation.reason)
    }

    @Test
    fun `refuses a credential type that names no schema credential`() = runTest {
        coEvery { documents.fetchJson(VCT) } returns JsonObject(mapOf("vct" to JsonPrimitive(VCT), "name" to JsonPrimitive("DemoCredential")))

        val withoutSchema = useCase(ISSUER_DID, VeranaTrustRole.ISSUER, VCT)
        val withoutVct = useCase(ISSUER_DID, VeranaTrustRole.ISSUER, null)

        assertEquals(VeranaAccreditationStatus.REFUSED, withoutSchema.status)
        assertEquals(VeranaAccreditationReason.NO_VERANA_SCHEMA, withoutSchema.reason)
        assertEquals("DemoCredential", withoutSchema.credentialName)
        assertEquals(VeranaAccreditationReason.NO_VERANA_SCHEMA, withoutVct.reason)
    }

    @Test
    fun `cannot decide when the indexer does not answer`() = runTest {
        coEvery { indexer.hasActiveParticipant(devnet, ISSUER_DID, VeranaTrustRole.ISSUER, "8") } returns null

        val accreditation = useCase(ISSUER_DID, VeranaTrustRole.ISSUER, VCT)

        assertEquals(VeranaAccreditationStatus.UNDETERMINED, accreditation.status)
    }

    @Test
    fun `cannot decide when the ecosystem DID does not resolve`() = runTest {
        coEvery { documents.fetchDidDocument(ECOSYSTEM_DID) } returns null

        val accreditation = useCase(ISSUER_DID, VeranaTrustRole.ISSUER, VCT)

        assertEquals(VeranaAccreditationStatus.UNDETERMINED, accreditation.status)
    }

    @Test
    fun `cannot decide when the type metadata does not load`() = runTest {
        coEvery { documents.fetchJson(VCT) } returns null

        assertEquals(VeranaAccreditationStatus.UNDETERMINED, useCase(ISSUER_DID, VeranaTrustRole.ISSUER, VCT).status)
    }

    @Test
    fun `maps a schema reference onto a configured network only`() {
        val schemaRef = requireNotNull(useCase.parseSchemaRef("vpr:verana:vna-devnet-1:cs:8"))

        assertEquals(devnet, schemaRef.network)
        assertEquals("8", schemaRef.schemaId)
        assertNull(useCase.parseSchemaRef("vpr:verana:vna-mainnet-9:cs:8"))
        assertNull(useCase.parseSchemaRef("vpr:verana:vna-testnet-1/cs/v1/js/253"))
    }
}
