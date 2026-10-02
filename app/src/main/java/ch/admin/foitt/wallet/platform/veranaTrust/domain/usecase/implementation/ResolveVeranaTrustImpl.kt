package ch.admin.foitt.wallet.platform.veranaTrust.domain.usecase.implementation

import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VERANA_NETWORKS
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaIndexerAnswer
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaNetwork
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustCredential
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustResolution
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustResolution.Companion.unresolved
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustStatus
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaUntrustedReason
import ch.admin.foitt.wallet.platform.veranaTrust.domain.repository.VeranaIndexerRepository
import ch.admin.foitt.wallet.platform.veranaTrust.domain.usecase.ResolveVeranaTrust
import ch.admin.foitt.wallet.platform.veranaTrust.domain.util.VeranaDids
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

class ResolveVeranaTrustImpl(
    private val indexerRepository: VeranaIndexerRepository,
    private val networks: List<VeranaNetwork>,
) : ResolveVeranaTrust {

    @Inject
    constructor(indexerRepository: VeranaIndexerRepository) : this(indexerRepository, VERANA_NETWORKS)

    override suspend fun invoke(did: String): VeranaTrustResolution {
        if (!VeranaDids.hasDidDocument(did)) {
            return unresolved(did, VeranaTrustStatus.UNTRUSTED, VeranaUntrustedReason.NO_DID_DOCUMENT)
        }
        if (networks.isEmpty()) return unresolved(did, VeranaTrustStatus.UNVERIFIED)

        return coroutineScope {
            val answers = Channel<VeranaTrustResolution?>(capacity = networks.size)
            val queries = networks.map { network ->
                launch { answers.send(queryNetwork(network, did)) }
            }
            val settled = mutableListOf<VeranaTrustResolution?>()
            repeat(networks.size) {
                val answer = answers.receive()
                if (answer?.status == VeranaTrustStatus.TRUSTED) {
                    queries.forEach(Job::cancel)
                    return@coroutineScope answer
                }
                settled += answer
            }
            settledVerdict(did, settled)
        }
    }

    private suspend fun queryNetwork(network: VeranaNetwork, did: String): VeranaTrustResolution? =
        when (val answer = indexerRepository.resolve(network, did)) {
            VeranaIndexerAnswer.Unanswered -> null
            VeranaIndexerAnswer.NotRegistered ->
                unresolved(did, VeranaTrustStatus.UNTRUSTED, VeranaUntrustedReason.NOT_REGISTERED, network)

            is VeranaIndexerAnswer.Resolved -> answer.resolution.takeUnless { it.status == VeranaTrustStatus.TRUSTED }
                ?: withTrustedEcosystems(network, answer.resolution)
        }

    private suspend fun withTrustedEcosystems(
        network: VeranaNetwork,
        resolution: VeranaTrustResolution,
    ): VeranaTrustResolution? = when (fromTrustedEcosystems(network, resolution.credentials)) {
        null -> null
        true -> resolution
        false -> resolution.copy(
            status = VeranaTrustStatus.UNTRUSTED,
            reason = VeranaUntrustedReason.ECOSYSTEM_NOT_TRUSTED,
        )
    }

    private suspend fun fromTrustedEcosystems(
        network: VeranaNetwork,
        credentials: List<VeranaTrustCredential>,
    ): Boolean? {
        val trustedDids = network.trustedEcsEcosystemDids ?: return true
        val ecosystemDids = coroutineScope {
            credentials.map { credential ->
                async { credential.ecosystemId?.let { indexerRepository.fetchEcosystemDid(network, it) } }
            }.awaitAll()
        }
        if (ecosystemDids.any { it == null }) return null
        return ecosystemDids.all { it in trustedDids }
    }

    private fun settledVerdict(did: String, answers: List<VeranaTrustResolution?>): VeranaTrustResolution {
        if (answers.any { it == null }) {
            return unresolved(did, VeranaTrustStatus.UNVERIFIED)
        }
        val untrusted = answers.filterNotNull()
        return untrusted.firstOrNull { it.reason != VeranaUntrustedReason.NOT_REGISTERED }
            ?: untrusted.firstOrNull()
            ?: unresolved(did, VeranaTrustStatus.UNTRUSTED, VeranaUntrustedReason.NOT_REGISTERED)
    }
}
