package ch.admin.foitt.wallet.platform.veranaTrust.domain.usecase

fun interface ResolveVtjscIdFromVct {
    suspend operator fun invoke(credentialSchemaId: String?, vct: String): String?
}
