package ch.admin.foitt.wallet.platform.actorMetadata

import ch.admin.foitt.wallet.platform.actorMetadata.domain.model.ActorDisplayData
import ch.admin.foitt.wallet.platform.actorMetadata.presentation.adapter.implementation.GetActorUiStateImpl
import ch.admin.foitt.wallet.platform.composables.presentation.adapter.GetDrawableFromUri
import ch.admin.foitt.wallet.platform.locale.domain.usecase.GetLocalizedDisplay
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustEvidence
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustRole
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustVerdict
import io.mockk.MockKAnnotations
import io.mockk.impl.annotations.MockK
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GetActorUiStateImplTest {

    @MockK
    private lateinit var getLocalizedDisplay: GetLocalizedDisplay

    @MockK
    private lateinit var getDrawableFromUri: GetDrawableFromUri

    private lateinit var useCase: GetActorUiStateImpl

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        useCase = GetActorUiStateImpl(getLocalizedDisplay, getDrawableFromUri)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `Verana evidence reaches UI state unchanged`() = runTest {
        val evidence = VeranaTrustEvidence(
            role = VeranaTrustRole.ISSUER,
            did = "did:web:issuer.example",
            vcSchemaIds = listOf("https://schemas.example/credential"),
            verdict = VeranaTrustVerdict.TRUSTED_AUTHORIZED,
            summary = null,
            authorizations = emptyList(),
        )
        val actor = ActorDisplayData.EMPTY.copy(
            name = null,
            image = null,
            nonComplianceReason = null,
            veranaTrustEvidence = evidence,
        )

        val result = useCase(actor)

        assertEquals(evidence, result.veranaTrustEvidence)
    }
}
