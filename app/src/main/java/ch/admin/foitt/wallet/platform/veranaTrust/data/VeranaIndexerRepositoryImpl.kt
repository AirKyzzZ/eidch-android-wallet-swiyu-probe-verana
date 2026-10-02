package ch.admin.foitt.wallet.platform.veranaTrust.data

import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaIndexerAnswer
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaNetwork
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustRole
import ch.admin.foitt.wallet.platform.veranaTrust.domain.repository.VeranaIndexerRepository
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.net.URLEncoder
import javax.inject.Inject

class VeranaIndexerRepositoryImpl @Inject constructor(
    private val httpClient: VeranaHttpClient,
    private val parser: VeranaTrustResponseParser,
) : VeranaIndexerRepository {
    override suspend fun resolve(network: VeranaNetwork, did: String): VeranaIndexerAnswer {
        val response = httpClient.postJson(
            url = "${network.indexerUrl}/v4/verifiable-trust/resolve",
            body = resolveRequest(did),
        ) ?: return VeranaIndexerAnswer.Unanswered
        return parser.parseResolveAnswer(
            status = response.status,
            body = response.body,
            did = did,
            network = network,
        )
    }

    override suspend fun fetchEcosystemDid(network: VeranaNetwork, ecosystemId: Long): String? =
        getOk("${network.indexerUrl}/v4/ecosystem/get/$ecosystemId")?.let(parser::parseEcosystemDid)

    override suspend fun fetchSchemaEcosystemId(network: VeranaNetwork, schemaId: String): Long? =
        getOk("${network.indexerUrl}/v4/credential-schema/get/$schemaId")?.let(parser::parseSchemaEcosystemId)

    override suspend fun hasActiveParticipant(
        network: VeranaNetwork,
        did: String,
        role: VeranaTrustRole,
        schemaId: String,
    ): Boolean? {
        val query = "did=${URLEncoder.encode(did, Charsets.UTF_8.name())}&role=${role.name}" +
            "&schema_id=$schemaId&participant_state=ACTIVE"
        return getOk("${network.indexerUrl}/v4/participant/list?$query")?.let { body ->
            parser.parseParticipantGranted(body = body, did = did, role = role, schemaId = schemaId)
        }
    }

    private suspend fun getOk(url: String): String? =
        httpClient.get(url)?.takeIf { it.status == HTTP_OK }?.body

    private fun resolveRequest(did: String): String = buildJsonObject {
        put("did", did)
        putJsonObject("participations") {
            putJsonArray("states") {
                add("ACTIVE")
                add("EXPIRED")
                add("REVOKED")
            }
        }
        putJsonObject("presentations") {
            put("unresolvableCredentialIds", true)
        }
        put("ecsCredentials", true)
    }.toString()

    private companion object {
        const val HTTP_OK = 200
    }
}
