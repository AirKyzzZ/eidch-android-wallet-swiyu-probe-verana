package ch.admin.foitt.wallet.platform.veranaTrust.domain.model

data class EcsAssetRef(
    val uri: String,
    val digest: String? = null,
)

data class EcsService(
    val id: String? = null,
    val name: String? = null,
    val type: String? = null,
    val description: String? = null,
    val descriptionFormat: String = "text/plain",
    val logo: EcsAssetRef? = null,
    val minimumAgeRequired: Int? = null,
    val terms: EcsAssetRef? = null,
    val privacy: EcsAssetRef? = null,
)

data class EcsOrganization(
    val id: String? = null,
    val name: String? = null,
    val logo: EcsAssetRef? = null,
    val registryId: String? = null,
    val address: String? = null,
    val countryCode: String? = null,
    val registryUri: String? = null,
    val legalJurisdiction: String? = null,
    val organizationKind: String? = null,
    val lei: String? = null,
)

enum class EcsVerdict {
    TRUSTED,
    PARTIAL,
    UNTRUSTED,
}

data class StrippedDescription(
    val text: String,
    val removed: Int,
)

object EcsClaimReader {

    fun findServiceCredential(credentials: List<VeranaTrustCredential>?): VeranaTrustCredential? =
        findEcsCredential(credentials, SERVICE_ECS_TYPES)

    fun findOrganizationCredential(credentials: List<VeranaTrustCredential>?): VeranaTrustCredential? =
        findEcsCredential(credentials, ORGANIZATION_ECS_TYPES)

    // Claims render as facts only from credentials the resolver verified.
    fun readEcsService(credential: VeranaTrustCredential?): EcsService? {
        credential?.takeIf { it.isValid() } ?: return null

        val format = credential.claim("descriptionFormat")
        return EcsService(
            id = credential.claim("id"),
            name = credential.claim("name"),
            type = credential.claim("type"),
            description = credential.claim("description"),
            descriptionFormat = if (format == "text/markdown") "text/markdown" else "text/plain",
            logo = credential.asset("logoUri", "logoDigestSri", "logo"),
            minimumAgeRequired = credential.claim("minimumAgeRequired")?.toIntOrNull(),
            terms = credential.asset(
                "termsAndConditionsUri",
                "termsAndConditionsDigestSri",
                "termsAndConditions",
                "termsAndConditionsHash",
            ),
            privacy = credential.asset("privacyPolicyUri", "privacyPolicyDigestSri", "privacyPolicy", "privacyPolicyHash"),
        )
    }

    fun readEcsOrganization(credential: VeranaTrustCredential?): EcsOrganization? {
        credential?.takeIf { it.isValid() } ?: return null

        return EcsOrganization(
            id = credential.claim("id"),
            name = credential.claim("name"),
            logo = credential.asset("logoUri", "logoDigestSri", "logo"),
            registryId = credential.claim("registryId"),
            address = credential.claim("address"),
            countryCode = credential.claim("countryCode")?.uppercase(),
            registryUri = credential.claim("registryUri"),
            legalJurisdiction = credential.claim("legalJurisdiction"),
            organizationKind = credential.claim("organizationKind"),
            lei = credential.claim("lei"),
        )
    }

    fun deriveVerdict(credentials: List<VeranaTrustCredential>?): EcsVerdict {
        val service = findServiceCredential(credentials).isValid()
        val organization = findOrganizationCredential(credentials).isValid()

        return when {
            service && organization -> EcsVerdict.TRUSTED
            service || organization -> EcsVerdict.PARTIAL
            else -> EcsVerdict.UNTRUSTED
        }
    }

    // Wording is fixed by the versioned card at playground/public/trust-card/index.html.
    fun describeVerdict(verdict: EcsVerdict, credentials: List<VeranaTrustCredential>?): String = when (verdict) {
        EcsVerdict.TRUSTED -> "Both identity credentials verified against the Verana public registry"
        EcsVerdict.UNTRUSTED ->
            if (findServiceCredential(credentials).isValid() || findOrganizationCredential(credentials).isValid()) {
                "The Verana public registry does not vouch for this service."
            } else {
                "Neither identity credential verified. This counterparty cannot present verifiable trust credentials."
            }

        EcsVerdict.PARTIAL ->
            if (findServiceCredential(credentials).isValid()) {
                "The service credential verified. Nothing verifies who operates it."
            } else {
                "The operator credential verified. Nothing verifies the service itself."
            }
    }

    fun isSelfIssued(credential: VeranaTrustCredential?, did: String): Boolean =
        credential?.issuedBy?.substringBefore('#') == did

    fun stripLinks(description: String?): StrippedDescription {
        if (description.isNullOrEmpty()) return StrippedDescription(text = "", removed = 0)

        var removed = 0
        val withoutMarkdown = MARKDOWN_LINK.replace(description) { match ->
            removed += 1
            match.groupValues[1]
        }
        val text = BARE_URL.replace(withoutMarkdown) {
            removed += 1
            ""
        }

        return StrippedDescription(
            text = text.replace(MULTI_WHITESPACE, " ").trim(),
            removed = removed,
        )
    }

    private fun findEcsCredential(
        credentials: List<VeranaTrustCredential>?,
        ecsTypes: Set<String>,
    ): VeranaTrustCredential? = credentials?.firstOrNull { it.ecsType in ecsTypes }

    private fun VeranaTrustCredential?.isValid(): Boolean = this?.result == "VALID"

    private fun VeranaTrustCredential.claim(name: String): String? =
        claims.firstOrNull { it.name == name }?.values?.singleOrNull()?.takeIf { it.isNotEmpty() }

    private fun VeranaTrustCredential.asset(
        v4Uri: String,
        v4Digest: String,
        v3Uri: String,
        v3Digest: String? = null,
    ): EcsAssetRef? {
        val uri = claim(v4Uri) ?: claim(v3Uri) ?: return null
        val digest = claim(v4Digest) ?: v3Digest?.let { claim(it) }
        return EcsAssetRef(uri = uri, digest = digest)
    }

    private val SERVICE_ECS_TYPES = setOf("ECS-SERVICE")
    private val ORGANIZATION_ECS_TYPES = setOf("ECS-ORG", "ECS-ORGANIZATION", "ECS-PERSONA")
    private val MARKDOWN_LINK = Regex("""\[([^\]]*)]\(([^)]*)\)""")
    private val BARE_URL = Regex("""\bhttps?://\S+""", RegexOption.IGNORE_CASE)
    private val MULTI_WHITESPACE = Regex("""\s{2,}""")
}
