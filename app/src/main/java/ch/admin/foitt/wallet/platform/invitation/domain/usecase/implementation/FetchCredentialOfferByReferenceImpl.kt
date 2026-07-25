package ch.admin.foitt.wallet.platform.invitation.domain.usecase.implementation

import ch.admin.foitt.openid4vc.di.OpenId4VcModule.Companion.NAMED_DEFAULT_HTTP_CLIENT
import ch.admin.foitt.wallet.platform.invitation.domain.model.GetCredentialOfferError
import ch.admin.foitt.wallet.platform.invitation.domain.model.InvitationError
import ch.admin.foitt.wallet.platform.invitation.domain.usecase.FetchCredentialOfferByReference
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.coroutines.runSuspendCatching
import com.github.michaelbull.result.mapError
import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.URLBuilder
import java.net.URI
import javax.inject.Inject
import javax.inject.Named

internal class FetchCredentialOfferByReferenceImpl @Inject constructor(
    @param:Named(NAMED_DEFAULT_HTTP_CLIENT) private val httpClient: HttpClient,
) : FetchCredentialOfferByReference {
    override suspend fun invoke(uri: URI): Result<String, GetCredentialOfferError> = coroutineBinding {
        val expectedUrl = URLBuilder(uri.toString()).build()
        val response = runSuspendCatching {
            httpClient.get(expectedUrl) {
                accept(ContentType.Application.Json)
            }
        }.mapError { InvitationError.NetworkError }.bind()

        if (response.call.request.url != expectedUrl) {
            Err(
                InvitationError.CredentialOfferDeserializationFailed(
                    IllegalArgumentException("credential_offer_uri redirects are not accepted")
                )
            ).bind<String>()
        }

        runSuspendCatching {
            response.bodyAsText()
        }.mapError { InvitationError.NetworkError }.bind()
    }
}
