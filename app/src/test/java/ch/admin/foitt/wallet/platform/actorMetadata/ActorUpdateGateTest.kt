package ch.admin.foitt.wallet.platform.actorMetadata

import ch.admin.foitt.wallet.platform.actorMetadata.domain.usecase.ActorUpdateGate
import ch.admin.foitt.wallet.platform.navigation.domain.model.ComponentScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ActorUpdateGateTest {

    @Test
    fun `a late actor result cannot overwrite a newer context`() = runTest {
        val gate = ActorUpdateGate()
        val staleUpdate = gate.begin(ComponentScope.Verifier)
        val currentUpdate = gate.begin(ComponentScope.Verifier)
        var staleWasPublished = false
        var currentWasPublished = false

        val currentAccepted = gate.publishIfCurrent(ComponentScope.Verifier, currentUpdate) {
            currentWasPublished = true
        }
        val staleAccepted = gate.publishIfCurrent(ComponentScope.Verifier, staleUpdate) {
            staleWasPublished = true
        }

        assertTrue(currentAccepted)
        assertTrue(currentWasPublished)
        assertFalse(staleAccepted)
        assertFalse(staleWasPublished)
    }

    @Test
    fun `issuer and verifier updates are isolated`() = runTest {
        val gate = ActorUpdateGate()
        val issuerUpdate = gate.begin(ComponentScope.CredentialIssuer)
        gate.begin(ComponentScope.Verifier)
        var issuerWasPublished = false

        val accepted = gate.publishIfCurrent(ComponentScope.CredentialIssuer, issuerUpdate) {
            issuerWasPublished = true
        }

        assertTrue(accepted)
        assertTrue(issuerWasPublished)
    }
}
