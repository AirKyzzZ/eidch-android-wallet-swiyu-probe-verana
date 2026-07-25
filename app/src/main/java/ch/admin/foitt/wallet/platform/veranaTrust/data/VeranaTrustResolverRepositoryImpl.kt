package ch.admin.foitt.wallet.platform.veranaTrust.data

import ch.admin.foitt.openid4vc.di.OpenId4VcModule.Companion.NAMED_DEFAULT_HTTP_CLIENT
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaAuthorizationEvidence
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaResolverResult
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustDetails
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustResolverUrl
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustRole
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustSummary
import ch.admin.foitt.wallet.platform.veranaTrust.domain.repository.VeranaTrustResolverRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Named

class VeranaTrustResolverRepositoryImpl @Inject constructor(
    @param:Named(NAMED_DEFAULT_HTTP_CLIENT) private val httpClient: HttpClient,
    private val parser: VeranaTrustResponseParser,
) : VeranaTrustResolverRepository {
    override suspend fun fetchSummary(did: String): VeranaResolverResult<VeranaTrustSummary> = request {
        httpClient.get("$VeranaTrustResolverUrl/v1/trust/resolve") {
            parameter("did", did)
            parameter("detail", "summary")
        }.parse { response, body ->
            parser.parseSummary(
                body = body,
                evaluatedAtBlockHeader = response.headers[EVALUATED_AT_BLOCK_HEADER],
                expectedDid = did,
            )
        }
    }

    override suspend fun fetchAuthorization(
        role: VeranaTrustRole,
        did: String,
        vcSchemaId: String,
    ): VeranaResolverResult<VeranaAuthorizationEvidence> = request {
        val path = when (role) {
            VeranaTrustRole.ISSUER -> "issuer-authorization"
            VeranaTrustRole.VERIFIER -> "verifier-authorization"
        }
        httpClient.get("$VeranaTrustResolverUrl/v1/trust/$path") {
            parameter("did", did)
            parameter("vtjscId", vcSchemaId)
        }.parse { response, body ->
            parser.parseAuthorization(
                body = body,
                evaluatedAtBlockHeader = response.headers[EVALUATED_AT_BLOCK_HEADER],
                expectedDid = did,
                expectedSchemaId = vcSchemaId,
            )
        }
    }

    override suspend fun fetchDetails(
        did: String,
        expectedSummary: VeranaTrustSummary,
    ): VeranaResolverResult<VeranaTrustDetails> = request {
        httpClient.get("$VeranaTrustResolverUrl/v1/trust/resolve") {
            parameter("did", did)
            parameter("detail", "full")
        }.parse { response, body ->
            parser.parseDetails(
                body = body,
                evaluatedAtBlockHeader = response.headers[EVALUATED_AT_BLOCK_HEADER],
                expectedSummary = expectedSummary,
            )
        }
    }

    private suspend fun <T> request(block: suspend () -> VeranaResolverResult<T>): VeranaResolverResult<T> = try {
        block()
    } catch (error: ClientRequestException) {
        if (error.response.status == HttpStatusCode.NotFound) {
            VeranaResolverResult.NotFound
        } else {
            VeranaResolverResult.Unavailable
        }
    } catch (error: CancellationException) {
        throw error
    } catch (_: Throwable) {
        VeranaResolverResult.Unavailable
    }

    private suspend fun <T> HttpResponse.parse(parser: (HttpResponse, String) -> T?): VeranaResolverResult<T> =
        parser(this, body<String>())
            ?.let(VeranaResolverResult<T>::Success)
            ?: VeranaResolverResult.Unavailable

    private companion object {
        const val EVALUATED_AT_BLOCK_HEADER = "X-Evaluated-At-Block"
    }
}
