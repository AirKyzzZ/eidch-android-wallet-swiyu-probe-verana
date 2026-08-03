package ch.admin.foitt.wallet.platform.veranaTrust.domain.usecase.implementation

import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaAuthorizationEvidence
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaResolverResult
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustRole
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustSummary
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustVerdict
import ch.admin.foitt.wallet.platform.veranaTrust.domain.repository.VeranaTrustResolverRepository
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifySequence
import io.mockk.confirmVerified
import io.mockk.impl.annotations.MockK
import io.mockk.unmockkAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class EvaluateVeranaTrustImplTest {

    @MockK
    private lateinit var repository: VeranaTrustResolverRepository

    private lateinit var useCase: EvaluateVeranaTrustImpl

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        useCase = EvaluateVeranaTrustImpl(repository)

        coEvery { repository.fetchSummary(DID) } returns VeranaResolverResult.Success(trustedSummary())
        coEvery {
            repository.fetchAuthorization(any(), DID, any())
        } answers {
            VeranaResolverResult.Success(
                authorization(
                    schemaId = thirdArg(),
                    authorized = true,
                )
            )
        }
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `trusted DID with every issuer schema authorized is positive`() = runTest {
        val result = useCase(
            role = VeranaTrustRole.ISSUER,
            did = DID,
            vcSchemaIds = setOf(SCHEMA_B, SCHEMA_A),
            vtjscIds = setOf(SCHEMA_B, SCHEMA_A),
        )

        assertEquals(VeranaTrustVerdict.TRUSTED_AUTHORIZED, result.verdict)
        assertEquals(listOf(SCHEMA_A, SCHEMA_B), result.vcSchemaIds)
        assertEquals(listOf(SCHEMA_A, SCHEMA_B), result.authorizations.map { it.vcSchemaId })
        assertEquals(DID, result.did)
        assertEquals(VeranaTrustRole.ISSUER, result.role)
        assertEquals(RESOLVER_URL, result.resolverUrl)
        coVerifySequence {
            repository.fetchSummary(DID)
            repository.fetchAuthorization(VeranaTrustRole.ISSUER, DID, SCHEMA_A)
            repository.fetchAuthorization(VeranaTrustRole.ISSUER, DID, SCHEMA_B)
        }
    }

    @Test
    fun `trusted DID with every verifier schema authorized is positive`() = runTest {
        val result = useCase(
            role = VeranaTrustRole.VERIFIER,
            did = DID,
            vcSchemaIds = setOf(SCHEMA_A),
            vtjscIds = setOf(SCHEMA_A),
        )

        assertEquals(VeranaTrustVerdict.TRUSTED_AUTHORIZED, result.verdict)
        coVerify { repository.fetchAuthorization(VeranaTrustRole.VERIFIER, DID, SCHEMA_A) }
    }

    @Test
    fun `one unauthorized schema makes the result trusted but not authorized`() = runTest {
        coEvery {
            repository.fetchAuthorization(VeranaTrustRole.VERIFIER, DID, SCHEMA_B)
        } returns VeranaResolverResult.Success(authorization(SCHEMA_B, authorized = false))

        val result = useCase(
            role = VeranaTrustRole.VERIFIER,
            did = DID,
            vcSchemaIds = setOf(SCHEMA_A, SCHEMA_B),
            vtjscIds = setOf(SCHEMA_A, SCHEMA_B),
        )

        assertEquals(VeranaTrustVerdict.TRUSTED_NOT_AUTHORIZED, result.verdict)
        assertEquals(listOf(true, false), result.authorizations.map { it.authorized })
    }

    @Test
    fun `Q1 not found is unverified and skips authorization`() = runTest {
        coEvery { repository.fetchSummary(DID) } returns VeranaResolverResult.NotFound

        val result = useCase(VeranaTrustRole.ISSUER, DID, setOf(SCHEMA_A), setOf(SCHEMA_A))

        assertEquals(VeranaTrustVerdict.UNVERIFIED, result.verdict)
        assertTrue(result.authorizations.isEmpty())
        coVerify(exactly = 0) { repository.fetchAuthorization(any(), any(), any()) }
    }

    @Test
    fun `Q1 unavailable is unavailable and skips authorization`() = runTest {
        coEvery { repository.fetchSummary(DID) } returns VeranaResolverResult.Unavailable

        val result = useCase(VeranaTrustRole.ISSUER, DID, setOf(SCHEMA_A), setOf(SCHEMA_A))

        assertEquals(VeranaTrustVerdict.RESOLVER_UNAVAILABLE, result.verdict)
        coVerify(exactly = 0) { repository.fetchAuthorization(any(), any(), any()) }
    }

    @Test
    fun `Q1 partial or untrusted is untrusted`() = runTest {
        val summaries = listOf(
            trustedSummary().copy(trustStatus = "PARTIAL"),
            trustedSummary().copy(trustStatus = "UNTRUSTED"),
        )

        summaries.forEach { summary ->
            coEvery { repository.fetchSummary(DID) } returns VeranaResolverResult.Success(summary)

            val result = useCase(VeranaTrustRole.ISSUER, DID, setOf(SCHEMA_A), setOf(SCHEMA_A))

            assertEquals(VeranaTrustVerdict.UNTRUSTED, result.verdict)
        }
        coVerify(exactly = 0) { repository.fetchAuthorization(any(), any(), any()) }
    }

    @Test
    fun `resolver production flag does not gate trust`() = runTest {
        coEvery {
            repository.fetchSummary(DID)
        } returns VeranaResolverResult.Success(trustedSummary().copy(production = false))

        val result = useCase(VeranaTrustRole.ISSUER, DID, setOf(SCHEMA_A), setOf(SCHEMA_A))

        assertEquals(VeranaTrustVerdict.TRUSTED_AUTHORIZED, result.verdict)
        coVerify { repository.fetchAuthorization(VeranaTrustRole.ISSUER, DID, SCHEMA_A) }
    }

    @Test
    fun `Q1 exact DID mismatch is untrusted`() = runTest {
        coEvery {
            repository.fetchSummary(DID)
        } returns VeranaResolverResult.Success(trustedSummary().copy(did = OTHER_DID))

        val result = useCase(VeranaTrustRole.ISSUER, DID, setOf(SCHEMA_A), setOf(SCHEMA_A))

        assertEquals(VeranaTrustVerdict.UNTRUSTED, result.verdict)
        coVerify(exactly = 0) { repository.fetchAuthorization(any(), any(), any()) }
    }

    @Test
    fun `authorization unavailable makes the whole result unavailable`() = runTest {
        coEvery {
            repository.fetchAuthorization(VeranaTrustRole.ISSUER, DID, SCHEMA_B)
        } returns VeranaResolverResult.Unavailable

        val result = useCase(VeranaTrustRole.ISSUER, DID, setOf(SCHEMA_A, SCHEMA_B), setOf(SCHEMA_A, SCHEMA_B))

        assertEquals(VeranaTrustVerdict.RESOLVER_UNAVAILABLE, result.verdict)
    }

    @Test
    fun `authorization not found makes the whole result unavailable`() = runTest {
        coEvery {
            repository.fetchAuthorization(VeranaTrustRole.ISSUER, DID, SCHEMA_A)
        } returns VeranaResolverResult.NotFound

        val result = useCase(VeranaTrustRole.ISSUER, DID, setOf(SCHEMA_A), setOf(SCHEMA_A))

        assertEquals(VeranaTrustVerdict.RESOLVER_UNAVAILABLE, result.verdict)
    }

    @Test
    fun `authorization exact DID mismatch makes the whole result unavailable`() = runTest {
        coEvery {
            repository.fetchAuthorization(VeranaTrustRole.ISSUER, DID, SCHEMA_A)
        } returns VeranaResolverResult.Success(authorization(SCHEMA_A, did = OTHER_DID))

        val result = useCase(VeranaTrustRole.ISSUER, DID, setOf(SCHEMA_A), setOf(SCHEMA_A))

        assertEquals(VeranaTrustVerdict.RESOLVER_UNAVAILABLE, result.verdict)
    }

    @Test
    fun `authorization exact schema mismatch makes the whole result unavailable`() = runTest {
        coEvery {
            repository.fetchAuthorization(VeranaTrustRole.ISSUER, DID, SCHEMA_A)
        } returns VeranaResolverResult.Success(authorization(SCHEMA_B))

        val result = useCase(VeranaTrustRole.ISSUER, DID, setOf(SCHEMA_A), setOf(SCHEMA_A))

        assertEquals(VeranaTrustVerdict.RESOLVER_UNAVAILABLE, result.verdict)
    }

    @Test
    fun `blank DID or schema is unverified without resolver access`() = runTest {
        val blankDid = useCase(VeranaTrustRole.ISSUER, " ", setOf(SCHEMA_A), setOf(SCHEMA_A))
        val blankSchema = useCase(VeranaTrustRole.ISSUER, DID, setOf(" "), setOf(" "))
        val missingSchema = useCase(VeranaTrustRole.ISSUER, DID, emptySet(), emptySet())

        assertEquals(VeranaTrustVerdict.UNVERIFIED, blankDid.verdict)
        assertEquals(VeranaTrustVerdict.UNVERIFIED, blankSchema.verdict)
        assertEquals(VeranaTrustVerdict.UNVERIFIED, missingSchema.verdict)
        confirmVerified(repository)
    }

    @Test
    fun `non DID issuer is unverified without resolver access`() = runTest {
        val result = useCase(
            role = VeranaTrustRole.ISSUER,
            did = "https://issuer.example",
            vcSchemaIds = setOf(SCHEMA_A),
            vtjscIds = setOf(SCHEMA_A),
        )

        assertEquals(VeranaTrustVerdict.UNVERIFIED, result.verdict)
        confirmVerified(repository)
    }

    @Test
    fun `malformed DIDs are unverified without resolver access`() = runTest {
        val malformedDids = listOf(
            "did:",
            "did:method",
            "did:METHOD:identifier",
            "did:method:",
            "did:method:identifier with whitespace",
            "did:method:identifier%2",
        )

        malformedDids.forEach { malformedDid ->
            val result = useCase(
                role = VeranaTrustRole.ISSUER,
                did = malformedDid,
                vcSchemaIds = setOf(SCHEMA_A),
                vtjscIds = setOf(SCHEMA_A),
            )

            assertEquals(VeranaTrustVerdict.UNVERIFIED, result.verdict)
        }
        confirmVerified(repository)
    }

    @Test
    fun `trusted DID without a resolvable vtjscId is unavailable, never refused`() = runTest {
        val result = useCase(VeranaTrustRole.ISSUER, DID, setOf(SCHEMA_A), emptySet())

        assertEquals(VeranaTrustVerdict.RESOLVER_UNAVAILABLE, result.verdict)
        assertTrue(result.authorizations.isEmpty())
        coVerify(exactly = 0) { repository.fetchAuthorization(any(), any(), any()) }
    }

    @Test
    fun `the complete evaluation is bounded by ten seconds`() = runTest {
        coEvery { repository.fetchSummary(DID) } coAnswers {
            delay(Long.MAX_VALUE)
            VeranaResolverResult.Success(trustedSummary())
        }

        val result = useCase(VeranaTrustRole.ISSUER, DID, setOf(SCHEMA_A), setOf(SCHEMA_A))

        assertEquals(VeranaTrustVerdict.RESOLVER_UNAVAILABLE, result.verdict)
    }

    private fun trustedSummary() = VeranaTrustSummary(
        did = DID,
        trustStatus = "TRUSTED",
        production = true,
        evaluatedAt = "2026-07-18T12:00:00.000Z",
        evaluatedAtBlock = 1_500_000,
        expiresAt = "2026-07-19T12:00:00.000Z",
    )

    private fun authorization(
        schemaId: String,
        authorized: Boolean = true,
        did: String = DID,
    ) = VeranaAuthorizationEvidence(
        did = did,
        vcSchemaId = schemaId,
        authorized = authorized,
        evaluatedAt = "2026-07-18T12:00:01.000Z",
        evaluatedAtBlock = 1_500_001,
    )

    private companion object {
        const val DID = "did:web:trusted.example"
        const val OTHER_DID = "did:web:other.example"
        const val SCHEMA_A = "https://schemas.example/a"
        const val SCHEMA_B = "https://schemas.example/b"
        const val RESOLVER_URL = "https://resolver.testnet.verana.network"
    }
}
