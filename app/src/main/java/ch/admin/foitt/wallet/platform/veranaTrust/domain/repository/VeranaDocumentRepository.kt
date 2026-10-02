package ch.admin.foitt.wallet.platform.veranaTrust.domain.repository

import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaDidDocument
import kotlinx.serialization.json.JsonElement

interface VeranaDocumentRepository {
    suspend fun fetchJson(url: String): JsonElement?

    suspend fun fetchDidDocument(did: String): VeranaDidDocument?
}
