package ch.admin.foitt.wallet.platform.veranaTrust.domain.repository

import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaIndexerAnswer
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaNetwork
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustRole

interface VeranaIndexerRepository {
    suspend fun resolve(network: VeranaNetwork, did: String): VeranaIndexerAnswer

    suspend fun fetchEcosystemDid(network: VeranaNetwork, ecosystemId: Long): String?

    suspend fun fetchSchemaEcosystemId(network: VeranaNetwork, schemaId: String): Long?

    suspend fun hasActiveParticipant(
        network: VeranaNetwork,
        did: String,
        role: VeranaTrustRole,
        schemaId: String,
    ): Boolean?
}
