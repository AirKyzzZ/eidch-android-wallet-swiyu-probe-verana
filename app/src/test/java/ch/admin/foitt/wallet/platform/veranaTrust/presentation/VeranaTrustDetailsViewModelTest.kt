package ch.admin.foitt.wallet.platform.veranaTrust.presentation

import ch.admin.foitt.wallet.platform.navigation.NavigationManager
import ch.admin.foitt.wallet.platform.scaffold.domain.usecase.SetTopBarState
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaResolverResult
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustDetails
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustEvidence
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustRole
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustSummary
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustVerdict
import ch.admin.foitt.wallet.platform.veranaTrust.domain.usecase.FetchVeranaTrustDetails
import ch.admin.foitt.wallet.platform.veranaTrust.presentation.model.VeranaTrustDetailsLoadState
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VeranaTrustDetailsViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    @MockK
    private lateinit var fetchVeranaTrustDetails: FetchVeranaTrustDetails

    @MockK(relaxed = true)
    private lateinit var navigationManager: NavigationManager

    @MockK(relaxed = true)
    private lateinit var setTopBarState: SetTopBarState

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `details load lazily once when the screen view model is created`() = runTest(testDispatcher) {
        coEvery { fetchVeranaTrustDetails(EVIDENCE) } returns VeranaResolverResult.Success(DETAILS)

        val viewModel = createViewModel()
        assertTrue(viewModel.uiState.value is VeranaTrustDetailsLoadState.Loading)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is VeranaTrustDetailsLoadState.Loaded)
        coVerify(exactly = 1) { fetchVeranaTrustDetails(EVIDENCE) }
    }

    @Test
    fun `successful full evidence is mapped into the loaded state`() = runTest(testDispatcher) {
        coEvery { fetchVeranaTrustDetails(EVIDENCE) } returns VeranaResolverResult.Success(DETAILS)

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value as VeranaTrustDetailsLoadState.Loaded
        assertEquals(DID, state.trust.did)
        assertEquals(SUMMARY, state.trust.summary)
    }

    @Test
    fun `summary mismatch rejects the full response but retains validated evidence`() = runTest(testDispatcher) {
        val mismatched = DETAILS.copy(summary = SUMMARY.copy(evaluatedAtBlock = 99))
        coEvery { fetchVeranaTrustDetails(EVIDENCE) } returns VeranaResolverResult.Success(mismatched)

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value as VeranaTrustDetailsLoadState.Unavailable
        assertEquals(SUMMARY, state.trust.summary)
    }

    @Test
    fun `unavailable full evidence retains the validated summary verdict`() = runTest(testDispatcher) {
        coEvery { fetchVeranaTrustDetails(EVIDENCE) } returns VeranaResolverResult.Unavailable

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value as VeranaTrustDetailsLoadState.Unavailable
        assertEquals(VeranaTrustVerdict.TRUSTED_AUTHORIZED, state.trust.verdict)
        assertEquals(SUMMARY, state.trust.summary)
    }

    @Test
    fun `retry requests full evidence again and replaces unavailable state`() = runTest(testDispatcher) {
        coEvery { fetchVeranaTrustDetails(EVIDENCE) } returnsMany listOf(
            VeranaResolverResult.Unavailable,
            VeranaResolverResult.Success(DETAILS),
        )

        val viewModel = createViewModel()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is VeranaTrustDetailsLoadState.Unavailable)

        viewModel.onRetry()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is VeranaTrustDetailsLoadState.Loaded)
        coVerify(exactly = 2) { fetchVeranaTrustDetails(EVIDENCE) }
    }

    private fun createViewModel() = VeranaTrustDetailsViewModel(
        fetchVeranaTrustDetails = fetchVeranaTrustDetails,
        navigationManager = navigationManager,
        setTopBarState = setTopBarState,
        evidence = EVIDENCE,
    )

    private companion object {
        const val DID = "did:web:example.org"
        const val SCHEMA_ID = "https://schemas.example/credential"

        val SUMMARY = VeranaTrustSummary(
            did = DID,
            trustStatus = "TRUSTED",
            production = true,
            evaluatedAt = "2026-07-18T12:00:00Z",
            evaluatedAtBlock = 42,
            expiresAt = "2027-07-18T12:00:00Z",
        )

        val EVIDENCE = VeranaTrustEvidence(
            role = VeranaTrustRole.ISSUER,
            did = DID,
            vcSchemaIds = listOf(SCHEMA_ID),
            verdict = VeranaTrustVerdict.TRUSTED_AUTHORIZED,
            summary = SUMMARY,
            authorizations = emptyList(),
        )

        val DETAILS = VeranaTrustDetails(
            summary = SUMMARY,
            credentials = emptyList(),
        )
    }
}
