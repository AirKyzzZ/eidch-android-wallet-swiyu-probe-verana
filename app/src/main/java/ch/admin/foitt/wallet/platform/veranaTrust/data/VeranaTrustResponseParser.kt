package ch.admin.foitt.wallet.platform.veranaTrust.data

import ch.admin.foitt.wallet.platform.utils.SafeJson
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaAuthorizationEvidence
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaPermissionChainEntry
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustClaim
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustCredential
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustDetails
import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustSummary
import com.github.michaelbull.result.get
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import java.net.URI
import java.time.Instant
import javax.inject.Inject

class VeranaTrustResponseParser @Inject constructor(
    private val safeJson: SafeJson,
) {
    fun parseSummary(
        body: String,
        evaluatedAtBlockHeader: String?,
        expectedDid: String,
    ): VeranaTrustSummary? = parseJsonObject(body)?.toSummary(
        evaluatedAtBlockHeader = evaluatedAtBlockHeader,
        expectedDid = expectedDid,
    )

    fun parseAuthorization(
        body: String,
        evaluatedAtBlockHeader: String?,
        expectedDid: String,
        expectedSchemaId: String,
    ): VeranaAuthorizationEvidence? {
        val json = parseJsonObject(body) ?: return null
        val block = evaluatedAtBlockHeader.toPositiveLongOrNull() ?: return null
        val did = json.strictString("did")?.takeIf { it == expectedDid } ?: return null
        val schemaId = json.strictString("vtjscId")?.takeIf { it == expectedSchemaId } ?: return null
        val authorized = json.strictBoolean("authorized") ?: return null
        val evaluatedAt = json.strictInstantString("evaluatedAt") ?: return null

        return VeranaAuthorizationEvidence(
            did = did,
            vcSchemaId = schemaId,
            authorized = authorized,
            evaluatedAt = evaluatedAt,
            evaluatedAtBlock = block,
        )
    }

    fun parseDetails(
        body: String,
        evaluatedAtBlockHeader: String?,
        expectedSummary: VeranaTrustSummary,
    ): VeranaTrustDetails? {
        val json = parseJsonObject(body) ?: return null
        val summary = json.toSummary(
            evaluatedAtBlockHeader = evaluatedAtBlockHeader,
            expectedDid = expectedSummary.did,
        ) ?: return null
        if (
            summary.did != expectedSummary.did ||
            summary.trustStatus != expectedSummary.trustStatus ||
            summary.production != expectedSummary.production
        ) {
            return null
        }

        val credentialsJson = json["credentials"] as? JsonArray ?: return null
        return VeranaTrustDetails(
            summary = summary,
            credentials = credentialsJson.mapNotNull { element ->
                (element as? JsonObject)?.toTrustCredential()
            },
        )
    }

    private fun parseJsonObject(body: String): JsonObject? = runCatching {
        safeJson.safeParseToJsonElement(body).get()?.jsonObject
    }.getOrNull()

    private fun JsonObject.toSummary(
        evaluatedAtBlockHeader: String?,
        expectedDid: String,
    ): VeranaTrustSummary? {
        val did = strictString("did")?.takeIf { it == expectedDid } ?: return null
        val trustStatus = strictString("trustStatus")?.takeIf { it in TRUST_STATUSES } ?: return null
        val production = strictBoolean("production") ?: return null
        val evaluatedAt = strictInstantString("evaluatedAt") ?: return null
        val expiresAt = strictInstantString("expiresAt") ?: return null
        val bodyBlock = strictPositiveLong("evaluatedAtBlock") ?: return null
        val headerBlock = evaluatedAtBlockHeader.toPositiveLongOrNull() ?: return null
        if (bodyBlock != headerBlock) return null

        return VeranaTrustSummary(
            did = did,
            trustStatus = trustStatus,
            production = production,
            evaluatedAt = evaluatedAt,
            evaluatedAtBlock = bodyBlock,
            expiresAt = expiresAt,
        )
    }

    private fun JsonObject.toTrustCredential(): VeranaTrustCredential? {
        val result = strictString("result")?.takeIf { it in CREDENTIAL_RESULTS } ?: return null
        val ecsType = when (val element = get("ecsType")) {
            null -> null
            is JsonPrimitive -> element.strictNullableString()?.takeIf { it in ECS_TYPES } ?: return null
            else -> return null
        }
        val presentedBy = strictString("presentedBy") ?: return null
        val issuedBy = strictString("issuedBy") ?: return null
        val id = strictString("id") ?: return null
        val type = strictString("type") ?: return null
        val format = strictString("format") ?: return null
        val claimsObject = get("claims") as? JsonObject ?: return null
        val permissionChain = (get("permissionChain") as? JsonArray)
            ?.mapNotNull { (it as? JsonObject)?.toPermissionChainEntry() }
            .orEmpty()

        return VeranaTrustCredential(
            result = result,
            ecsType = ecsType,
            presentedBy = presentedBy,
            issuedBy = issuedBy,
            id = id,
            type = type,
            format = format,
            claims = claimsObject.toAllowlistedClaims(),
            permissionChain = permissionChain,
        )
    }

    private fun JsonObject.toAllowlistedClaims(): List<VeranaTrustClaim> = entries.mapNotNull { (name, element) ->
        if (name !in ALLOWED_CLAIMS) return@mapNotNull null

        val values = when (element) {
            is JsonPrimitive -> element.takeIf { it.isString }?.contentOrNull?.let(::listOf)
            is JsonArray -> element.map { item ->
                (item as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull ?: return@mapNotNull null
            }
            else -> null
        }?.filter { it.isNotBlank() }.orEmpty()
        if (values.isEmpty()) return@mapNotNull null

        val safeHttpUrl = if (name in HTTP_URL_CLAIMS) {
            values.singleOrNull()?.toSafeHttpUrl() ?: return@mapNotNull null
        } else {
            null
        }
        VeranaTrustClaim(
            name = name,
            values = values,
            safeHttpUrl = safeHttpUrl,
        )
    }.sortedBy { it.name }

    private fun JsonObject.toPermissionChainEntry(): VeranaPermissionChainEntry? {
        val permissionId = strictPositiveLong("permissionId") ?: return null
        val type = strictString("type")?.takeIf { it in PERMISSION_TYPES } ?: return null
        val did = strictString("did")?.takeIf { it.startsWith("did:") } ?: return null
        val didIsTrusted = strictBoolean("didIsTrustedVS") ?: return null
        val deposit = strictString("deposit") ?: return null
        val permissionState = strictString("permState") ?: return null

        return VeranaPermissionChainEntry(
            permissionId = permissionId,
            type = type,
            did = did,
            didIsTrustedVerifiableService = didIsTrusted,
            deposit = deposit,
            permissionState = permissionState,
            serviceName = optionalString("serviceName"),
            organizationName = optionalString("organizationName"),
            countryCode = optionalString("countryCode"),
            legalJurisdiction = optionalString("legalJurisdiction"),
            effectiveFrom = optionalInstantString("effectiveFrom"),
            effectiveUntil = optionalInstantString("effectiveUntil"),
        )
    }

    private fun JsonObject.strictString(name: String): String? =
        (get(name) as? JsonPrimitive)
            ?.takeIf { it.isString }
            ?.contentOrNull
            ?.takeIf { it.isNotBlank() }

    private fun JsonObject.optionalString(name: String): String? = when (val element = get(name)) {
        null -> null
        is JsonPrimitive -> element.takeIf { it.isString }?.contentOrNull?.takeIf { it.isNotBlank() }
        else -> null
    }

    private fun JsonPrimitive.strictNullableString(): String? =
        takeIf { it.isString }?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonObject.strictBoolean(name: String): Boolean? =
        (get(name) as? JsonPrimitive)
            ?.takeUnless { it.isString }
            ?.booleanOrNull

    private fun JsonObject.strictPositiveLong(name: String): Long? =
        (get(name) as? JsonPrimitive)
            ?.takeUnless { it.isString }
            ?.longOrNull
            ?.takeIf { it > 0 }

    private fun JsonObject.strictInstantString(name: String): String? =
        strictString(name)?.takeIf { it.isInstant() }

    private fun JsonObject.optionalInstantString(name: String): String? =
        optionalString(name)?.takeIf { it.isInstant() }

    private fun String?.toPositiveLongOrNull(): Long? = this?.toLongOrNull()?.takeIf { it > 0 }

    private fun String.isInstant(): Boolean = runCatching { Instant.parse(this) }.isSuccess

    private fun String.toSafeHttpUrl(): String? = runCatching {
        val uri = URI(this)
        takeIf {
            uri.scheme?.lowercase() in HTTP_SCHEMES && !uri.host.isNullOrBlank()
        }
    }.getOrNull()

    private companion object {
        val TRUST_STATUSES = setOf("TRUSTED", "PARTIAL", "UNTRUSTED")
        val CREDENTIAL_RESULTS = setOf("VALID", "IGNORED", "FAILED")
        val ECS_TYPES = setOf("ECS-SERVICE", "ECS-ORG", "ECS-PERSONA", "ECS-UA")
        val PERMISSION_TYPES = setOf("ISSUER", "ISSUER_GRANTOR", "ECOSYSTEM", "VERIFIER", "VERIFIER_GRANTOR")
        val ALLOWED_CLAIMS = setOf(
            "name",
            "type",
            "description",
            "privacyPolicy",
            "termsAndConditions",
            "address",
            "registryId",
            "countryCode",
            "legalJurisdiction",
        )
        val HTTP_URL_CLAIMS = setOf("privacyPolicy", "termsAndConditions")
        val HTTP_SCHEMES = setOf("http", "https")
    }
}
