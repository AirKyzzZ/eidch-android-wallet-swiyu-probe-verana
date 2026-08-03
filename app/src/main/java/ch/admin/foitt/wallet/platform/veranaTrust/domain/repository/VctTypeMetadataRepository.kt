package ch.admin.foitt.wallet.platform.veranaTrust.domain.repository

fun interface VctTypeMetadataRepository {
    suspend fun fetchRelatedJsonSchemaCredentialId(vctUrl: String): String?
}
