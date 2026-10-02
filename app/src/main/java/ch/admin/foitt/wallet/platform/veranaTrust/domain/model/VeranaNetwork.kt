package ch.admin.foitt.wallet.platform.veranaTrust.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class VeranaNetwork(
    val id: String,
    val name: String,
    val indexerUrl: String,
    val explorerUrl: String? = null,
    val production: Boolean,
    val trustedEcsEcosystemDids: List<String>? = null,
)

val VERANA_NETWORKS = listOf(
    VeranaNetwork(
        id = "vna-devnet-1",
        name = "Devnet",
        indexerUrl = "https://idx.devnet.verana.network",
        explorerUrl = "https://explorer.devnet.verana.network",
        production = false,
    ),
    VeranaNetwork(
        id = "vna-testnet-1",
        name = "Testnet",
        indexerUrl = "https://idx.testnet.verana.network",
        explorerUrl = "https://explorer.testnet.verana.network",
        production = false,
    ),
)

fun List<VeranaNetwork>.networkLabel(): String? = filterNot { it.production }
    .joinToString(separator = " · ") { it.name.uppercase() }
    .ifEmpty { null }
