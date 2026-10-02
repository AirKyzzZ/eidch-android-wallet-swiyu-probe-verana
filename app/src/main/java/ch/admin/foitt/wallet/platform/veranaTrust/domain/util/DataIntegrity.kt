package ch.admin.foitt.wallet.platform.veranaTrust.domain.util

import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaDidDocument
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaVerificationMethod
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.Ed25519Verifier
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.OctetKeyPair
import com.nimbusds.jose.util.Base64URL
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.security.MessageDigest
import java.time.Instant
import java.time.OffsetDateTime

object DataIntegrity {
    private const val PROOF = "proof"
    private const val PROOF_VALUE = "proofValue"
    private const val CONTEXT = "@context"
    private const val MULTIBASE_BASE58BTC = 'z'
    private const val ED25519_KEY_LENGTH = 32
    private val ED25519_MULTICODEC = byteArrayOf(0xed.toByte(), 0x01)

    fun issuerOf(credential: JsonObject): String? = when (val issuer = credential["issuer"]) {
        is JsonObject -> issuer.string("id")
        else -> credential.string("issuer")
    }

    fun withinValidity(credential: JsonObject, now: Instant): Boolean =
        credential.boundHolds("validFrom") { from -> !from.isAfter(now) } &&
            credential.boundHolds("validUntil") { until -> until.isAfter(now) }

    @Suppress("ReturnCount")
    fun verifyEddsaJcs2022(document: JsonObject, issuerDocument: VeranaDidDocument): Boolean {
        val proof = document[PROOF] as? JsonObject ?: return false
        val unsecured = JsonObject(document - PROOF)
        val proofValue = proof.string(PROOF_VALUE)
        if (
            proof.string("type") != "DataIntegrityProof" ||
            proof.string("cryptosuite") != "eddsa-jcs-2022" ||
            proof.string("proofPurpose") != "assertionMethod" ||
            proofValue == null ||
            proofValue.firstOrNull() != MULTIBASE_BASE58BTC ||
            issuerOf(unsecured) != issuerDocument.id
        ) {
            return false
        }

        val verificationMethod = proof.string("verificationMethod")
        val publicKey = issuerDocument.assertionMethods
            .firstOrNull { it.id == verificationMethod }
            ?.ed25519PublicKey()
            ?: return false

        val proofConfig = (proof - PROOF_VALUE).toMutableMap<String, JsonElement>()
        unsecured[CONTEXT]?.let { proofConfig[CONTEXT] = it }
        val hashData = sha256(Jcs.canonicalize(JsonObject(proofConfig))) + sha256(Jcs.canonicalize(unsecured))

        return runCatching {
            verifyEd25519(
                publicKey = publicKey,
                data = hashData,
                signature = Base58Btc.decode(proofValue.substring(1)),
            )
        }.getOrDefault(false)
    }

    private fun VeranaVerificationMethod.ed25519PublicKey(): ByteArray? {
        val multibase = publicKeyMultibase
        if (multibase != null && multibase.firstOrNull() == MULTIBASE_BASE58BTC) {
            val key = runCatching { Base58Btc.decode(multibase.substring(1)) }.getOrNull() ?: return null
            val hasEd25519Prefix = key.size == ED25519_MULTICODEC.size + ED25519_KEY_LENGTH &&
                key.copyOfRange(0, ED25519_MULTICODEC.size).contentEquals(ED25519_MULTICODEC)
            return if (hasEd25519Prefix) key.copyOfRange(ED25519_MULTICODEC.size, key.size) else null
        }
        val jwk = publicKeyJwk ?: return null
        val x = jwk.string("x")
        return if (jwk.string("kty") == "OKP" && jwk.string("crv") == "Ed25519" && x != null) {
            runCatching { Base64URL(x).decode() }.getOrNull()
        } else {
            null
        }
    }

    // Through nimbus, whose Ed25519 runs on Tink: java.security has no Ed25519 below API 33.
    private fun verifyEd25519(publicKey: ByteArray, data: ByteArray, signature: ByteArray): Boolean {
        val key = OctetKeyPair.Builder(Curve.Ed25519, Base64URL.encode(publicKey)).build()
        return Ed25519Verifier(key).verify(JWSHeader(JWSAlgorithm.EdDSA), data, Base64URL.encode(signature))
    }

    private fun sha256(value: String): ByteArray = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())

    private fun JsonObject.boundHolds(name: String, holds: (Instant) -> Boolean): Boolean {
        val bound = get(name) ?: return true
        val time = (bound as? JsonPrimitive)
            ?.takeIf { it.isString }
            ?.content
            ?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() }
        return time != null && holds(time)
    }

    private fun JsonObject.string(name: String): String? =
        (get(name) as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
}
