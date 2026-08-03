package ch.admin.foitt.wallet.platform.veranaTrust.data

import ch.admin.foitt.openid4vc.di.OpenId4VcModule.Companion.NAMED_DEFAULT_HTTP_CLIENT
import ch.admin.foitt.wallet.platform.utils.SafeJson
import ch.admin.foitt.wallet.platform.veranaTrust.domain.repository.VctTypeMetadataRepository
import com.github.michaelbull.result.get
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import javax.inject.Inject
import javax.inject.Named

class VctTypeMetadataRepositoryImpl @Inject constructor(
    @param:Named(NAMED_DEFAULT_HTTP_CLIENT) private val httpClient: HttpClient,
    private val safeJson: SafeJson,
) : VctTypeMetadataRepository {
    override suspend fun fetchRelatedJsonSchemaCredentialId(vctUrl: String): String? = try {
        withTimeout(FETCH_TIMEOUT_MILLIS) {
            val body = httpClient.get(vctUrl).body<String>()
            (safeJson.safeParseToJsonElement(body).get()?.jsonObject?.get(RELATED_JSON_SCHEMA_CREDENTIAL_ID) as? JsonPrimitive)
                ?.takeIf { it.isString }
                ?.contentOrNull
                ?.takeIf { it.isNotBlank() }
        }
    } catch (_: TimeoutCancellationException) {
        null
    } catch (error: CancellationException) {
        throw error
    } catch (_: Throwable) {
        null
    }

    private companion object {
        const val RELATED_JSON_SCHEMA_CREDENTIAL_ID = "relatedJsonSchemaCredentialId"
        const val FETCH_TIMEOUT_MILLIS = 10_000L
    }
}
