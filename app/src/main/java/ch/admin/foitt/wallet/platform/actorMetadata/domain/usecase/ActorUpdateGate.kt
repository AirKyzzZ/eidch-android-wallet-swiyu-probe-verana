package ch.admin.foitt.wallet.platform.actorMetadata.domain.usecase

import ch.admin.foitt.wallet.platform.navigation.domain.model.ComponentScope
import dagger.hilt.android.scopes.ActivityRetainedScoped
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

@ActivityRetainedScoped
class ActorUpdateGate @Inject constructor() {
    private val mutex = Mutex()
    private val generations = mutableMapOf<ComponentScope, Long>()

    suspend fun begin(componentScope: ComponentScope): Long = mutex.withLock {
        generations.getOrDefault(componentScope, 0L)
            .inc()
            .also { generations[componentScope] = it }
    }

    suspend fun publishIfCurrent(
        componentScope: ComponentScope,
        generation: Long,
        publish: suspend () -> Unit,
    ): Boolean {
        mutex.lock()
        return try {
            if (generations[componentScope] != generation) {
                false
            } else {
                publish()
                true
            }
        } finally {
            mutex.unlock()
        }
    }
}
