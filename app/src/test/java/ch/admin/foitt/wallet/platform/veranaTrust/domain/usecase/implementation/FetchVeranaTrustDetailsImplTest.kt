package ch.admin.foitt.wallet.platform.veranaTrust.domain.usecase.implementation

import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaResolverResult
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustDetails
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustEvidence
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustRole
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustSummary
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustVerdict
import ch.admin.foitt.wallet.platform.veranaTrust.domain.repository.VeranaTrustResolverRepository
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.impl.annotations.MockK
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class FetchVeranaTrustDetailsImplTest {

    @MockK
    private lateinit var repository: VeranaTrustResolverRepository

    private lateinit var useCase: FetchVeranaTrustDetailsImpl

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        useCase = FetchVeranaTrustDetailsImpl(repository)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `trusted evidence loads full details against the accepted summary`() = runTest {
        val expected = VeranaTrustDetails(summary = SUMMARY, credentials = emptyList())
        coEvery { repository.fetchDetails(DID, SUMMARY) } returns VeranaResolverResult.Success(expected)

        val result = useCase(EVIDENCE)

        assertEquals(VeranaResolverResult.Success(expected), result)
        coVerify(exactly = 1) { repository.fetchDetails(DID, SUMMARY) }
    }

    @Test
    fun `missing accepted summary cannot request full details`() = runTest {
        val result = useCase(EVIDENCE.copy(summary = null))

        assertEquals(VeranaResolverResult.Unavailable, result)
        confirmVerified(repository)
    }

    @Test
    fun `untrusted evidence with an accepted summary still loads full details`() = runTest {
        val expected = VeranaTrustDetails(summary = SUMMARY, credentials = emptyList())
        coEvery { repository.fetchDetails(DID, SUMMARY) } returns VeranaResolverResult.Success(expected)

        val result = useCase(EVIDENCE.copy(verdict = VeranaTrustVerdict.UNTRUSTED))

        assertEquals(VeranaResolverResult.Success(expected), result)
    }

    @Test
    fun `summary DID mismatch cannot request full details`() = runTest {
        val result = useCase(EVIDENCE.copy(did = "did:web:other.example"))

        assertEquals(VeranaResolverResult.Unavailable, result)
        confirmVerified(repository)
    }

    @Test
    fun `repository full-details failure remains unavailable`() = runTest {
        coEvery { repository.fetchDetails(DID, SUMMARY) } returns VeranaResolverResult.Unavailable

        val result = useCase(EVIDENCE)

        assertEquals(VeranaResolverResult.Unavailable, result)
    }

    private companion object {
        const val DID = "did:web:acme.example"
        const val SCHEMA = "https://schemas.example/credential"

        val SUMMARY = VeranaTrustSummary(
            did = DID,
            trustStatus = "TRUSTED",
            production = true,
            evaluatedAt = "2026-07-18T12:00:00.000Z",
            evaluatedAtBlock = 1_500_000,
            expiresAt = "2026-07-19T12:00:00.000Z",
        )

        val EVIDENCE = VeranaTrustEvidence(
            role = VeranaTrustRole.ISSUER,
            did = DID,
            vcSchemaIds = listOf(SCHEMA),
            verdict = VeranaTrustVerdict.TRUSTED_AUTHORIZED,
            summary = SUMMARY,
            authorizations = emptyList(),
        )
    }
}
