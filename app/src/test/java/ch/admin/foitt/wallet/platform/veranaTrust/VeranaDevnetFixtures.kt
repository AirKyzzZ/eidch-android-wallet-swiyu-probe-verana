package ch.admin.foitt.wallet.platform.veranaTrust

import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaDidDocument
import ch.admin.foitt.wallet.platform.veranaTrust.domain.util.VeranaDids
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

object VeranaDevnetFixtures {
    const val ISSUER_DID =
        "did:webvh:Qmaup6XHn4UMTEUu9PWMEwcRAYmZ74SpPpUGYpnXj9dTPf:demo-issuer-accredited.playground.devnet.verana.network"
    const val UNACCREDITED_ISSUER_DID =
        "did:webvh:Qmbep95zc1AokqrHBvFVPZWWz13SewXd2dcKS2RjHyKNH7:demo-issuer-unaccredited.playground.devnet.verana.network"
    const val VERIFIER_DID =
        "did:webvh:QmUXVK59dTbKcUiSsbLGABrjaqhBinm2tu9eVYjAX5wbEA:demo-verifier-accredited.playground.devnet.verana.network"
    const val ECOSYSTEM_DID =
        "did:webvh:QmdLKxG6ZdBRJbUevm1mQdPr3arKSE3bg5RfA8cg8uUszF:playground-demo.playground.devnet.verana.network"
    const val VCT = "https://playground-demo.playground.devnet.verana.network/vt/vct/8"
    const val VTJSC_ID = "https://playground-demo.playground.devnet.verana.network/vt/schemas-8-jsc.json"
    const val INDEXER = "https://idx.devnet.verana.network"

    val typeMetadata: JsonObject get() = json("type-metadata.json")
    val vtjsc: JsonObject get() = json("vtjsc.json")
    val issuerDidDocumentJson: JsonObject get() = json("issuer-did-document.json")
    val verifierDidDocumentJson: JsonObject get() = json("verifier-did-document.json")
    val ecosystemDidDocumentJson: JsonObject get() = json("ecosystem-did-document.json")
    val resolveVerifierResponse: String get() = text("resolve-verifier-accredited.json")
    val issuerMetadataJwt: String get() = text("issuer-metadata.jwt").trim()
    val verifierRequestObjectJwt: String get() = text("verifier-request-object.jwt").trim()
    val issuerCredentialSdJwt: String get() = text("issuer-credential.sdjwt").trim()

    val issuerDidDocument: VeranaDidDocument get() = didDocument(ISSUER_DID, issuerDidDocumentJson)
    val verifierDidDocument: VeranaDidDocument get() = didDocument(VERIFIER_DID, verifierDidDocumentJson)
    val ecosystemDidDocument: VeranaDidDocument get() = didDocument(ECOSYSTEM_DID, ecosystemDidDocumentJson)

    fun didLog(document: JsonObject): String = """{"versionId":"1-Qm","state":$document}""" + "\n"

    private fun didDocument(did: String, json: JsonObject) = requireNotNull(VeranaDids.parseDocument(did, json))

    private fun json(name: String): JsonObject = Json.parseToJsonElement(text(name)).jsonObject

    private fun text(name: String): String =
        requireNotNull(javaClass.getResource("/veranaTrust/$name")) { "missing fixture $name" }.readText()
}
