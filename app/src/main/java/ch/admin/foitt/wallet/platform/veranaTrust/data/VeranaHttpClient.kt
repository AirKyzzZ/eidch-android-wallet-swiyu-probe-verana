package ch.admin.foitt.wallet.platform.veranaTrust.data

import ch.admin.foitt.openid4vc.di.OpenId4VcModule.Companion.NAMED_DEFAULT_HTTP_CLIENT
import io.ktor.client.HttpClient
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named

data class VeranaHttpResponse(
    val status: Int,
    val body: String,
)

class VeranaHttpClient @Inject constructor(
    @param:Named(NAMED_DEFAULT_HTTP_CLIENT) private val httpClient: HttpClient,
) {
    suspend fun get(url: String): VeranaHttpResponse? = request(url) {
        httpClient.get(url) {
            expectSuccess = false
            accept(ContentType.Application.Json)
        }
    }

    suspend fun postJson(url: String, body: String): VeranaHttpResponse? = request(url) {
        httpClient.post(url) {
            expectSuccess = false
            accept(ContentType.Application.Json)
            setBody(TextContent(body, ContentType.Application.Json))
        }
    }

    private suspend fun request(url: String, block: suspend () -> HttpResponse): VeranaHttpResponse? = try {
        withTimeoutOrNull(REQUEST_TIMEOUT_MILLIS) {
            val response = block()
            VeranaHttpResponse(status = response.status.value, body = response.bodyAsText())
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        Timber.w(error, "Verana request to $url failed")
        null
    }

    private companion object {
        const val REQUEST_TIMEOUT_MILLIS = 15_000L
    }
}
