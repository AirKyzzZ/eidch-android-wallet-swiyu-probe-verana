package ch.admin.foitt.wallet.platform.veranaTrust.data

import ch.admin.foitt.wallet.platform.utils.SafeJson
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaDidDocument
import ch.admin.foitt.wallet.platform.veranaTrust.domain.repository.VeranaDocumentRepository
import ch.admin.foitt.wallet.platform.veranaTrust.domain.util.VeranaDids
import com.github.michaelbull.result.get
import kotlinx.serialization.json.JsonElement
import javax.inject.Inject

class VeranaDocumentRepositoryImpl @Inject constructor(
    private val httpClient: VeranaHttpClient,
    private val safeJson: SafeJson,
) : VeranaDocumentRepository {
    override suspend fun fetchJson(url: String): JsonElement? =
        fetchOk(url)?.let { body -> safeJson.safeParseToJsonElement(body).get() }

    override suspend fun fetchDidDocument(did: String): VeranaDidDocument? {
        val url = VeranaDids.documentUrl(did) ?: return null
        val body = fetchOk(url) ?: return null
        val document = if (VeranaDids.isWebvh(did)) {
            VeranaDids.lastLogState(body)
        } else {
            safeJson.safeParseToJsonElement(body).get()
        }
        return VeranaDids.parseDocument(did, document)
    }

    private suspend fun fetchOk(url: String): String? =
        httpClient.get(url)?.takeIf { it.status == HTTP_OK }?.body

    private companion object {
        const val HTTP_OK = 200
    }
}
