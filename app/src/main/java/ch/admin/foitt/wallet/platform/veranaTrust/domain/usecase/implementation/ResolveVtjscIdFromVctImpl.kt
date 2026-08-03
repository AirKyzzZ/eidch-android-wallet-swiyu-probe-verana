package ch.admin.foitt.wallet.platform.veranaTrust.domain.usecase.implementation

import ch.admin.foitt.wallet.platform.veranaTrust.domain.repository.VctTypeMetadataRepository
import ch.admin.foitt.wallet.platform.veranaTrust.domain.usecase.ResolveVtjscIdFromVct
import java.net.URI
import javax.inject.Inject

class ResolveVtjscIdFromVctImpl @Inject constructor(
    private val repository: VctTypeMetadataRepository,
) : ResolveVtjscIdFromVct {
    override suspend fun invoke(credentialSchemaId: String?, vct: String): String? {
        credentialSchemaId?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        val vctUrl = vct.trim().takeIf(::isHttpsUrl) ?: return null
        return repository.fetchRelatedJsonSchemaCredentialId(vctUrl)?.takeIf(::isHttpsUrl)
    }

    private fun isHttpsUrl(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme?.lowercase() == "https" && !uri.host.isNullOrBlank()
    }.getOrDefault(false)
}
