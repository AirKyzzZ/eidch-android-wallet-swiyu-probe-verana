package ch.admin.foitt.wallet.platform.veranaTrust.domain.model

data class VeranaSchemaRef(
    val network: VeranaNetwork,
    val schemaId: String,
) {
    companion object {
        private val SCHEMA_REF = Regex("""^vpr:verana:([^:]+):cs:(\d+)$""")

        fun parse(ref: String?, networks: List<VeranaNetwork>): VeranaSchemaRef? {
            val match = ref?.let(SCHEMA_REF::matchEntire) ?: return null
            return networks.firstOrNull { it.id == match.groupValues[1] }
                ?.let { network -> VeranaSchemaRef(network = network, schemaId = match.groupValues[2]) }
        }
    }
}
