package ch.admin.foitt.wallet.platform.veranaTrust.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EcsClaimReaderTest {

    @Test
    fun `service claims read the v4 shape with digests`() {
        val service = EcsClaimReader.readEcsService(
            credential(
                ecsType = "ECS-SERVICE",
                claims = listOf(
                    claim("name", "Acme Service"),
                    claim("description", "A service"),
                    claim("descriptionFormat", "text/markdown"),
                    claim("minimumAgeRequired", "18"),
                    claim("logoUri", "https://acme.example/logo.png"),
                    claim("logoDigestSri", "sha256-logo"),
                    claim("termsAndConditionsUri", "https://acme.example/terms"),
                    claim("termsAndConditionsDigestSri", "sha256-terms"),
                    claim("privacyPolicyUri", "https://acme.example/privacy"),
                ),
            )
        )

        requireNotNull(service)
        assertEquals("Acme Service", service.name)
        assertEquals("text/markdown", service.descriptionFormat)
        assertEquals(18, service.minimumAgeRequired)
        assertEquals(EcsAssetRef("https://acme.example/logo.png", "sha256-logo"), service.logo)
        assertEquals(EcsAssetRef("https://acme.example/terms", "sha256-terms"), service.terms)
        assertEquals(EcsAssetRef("https://acme.example/privacy"), service.privacy)
    }

    @Test
    fun `service claims fall back to the v3 shape with hashes`() {
        val service = EcsClaimReader.readEcsService(
            credential(
                ecsType = "ECS-SERVICE",
                claims = listOf(
                    claim("termsAndConditions", "https://acme.example/terms"),
                    claim("termsAndConditionsHash", "hash-terms"),
                    claim("logo", "https://acme.example/logo.png"),
                ),
            )
        )

        requireNotNull(service)
        assertEquals(EcsAssetRef("https://acme.example/terms", "hash-terms"), service.terms)
        assertEquals(EcsAssetRef("https://acme.example/logo.png"), service.logo)
    }

    @Test
    fun `a credential that did not verify reads no claims`() {
        assertNull(EcsClaimReader.readEcsService(credential(ecsType = "ECS-SERVICE", result = "FAILED")))
        assertNull(EcsClaimReader.readEcsOrganization(credential(ecsType = "ECS-ORG", result = "IGNORED")))
    }

    @Test
    fun `verdict follows the two mandatory identity credentials`() {
        val service = credential(ecsType = "ECS-SERVICE")
        val organization = credential(ecsType = "ECS-ORG")

        assertEquals(EcsVerdict.TRUSTED, EcsClaimReader.deriveVerdict(listOf(service, organization)))
        assertEquals(EcsVerdict.PARTIAL, EcsClaimReader.deriveVerdict(listOf(service)))
        assertEquals(EcsVerdict.UNTRUSTED, EcsClaimReader.deriveVerdict(emptyList()))
        assertEquals(
            EcsVerdict.UNTRUSTED,
            EcsClaimReader.deriveVerdict(listOf(service.copy(result = "FAILED"), organization.copy(result = "FAILED"))),
        )
    }

    @Test
    fun `verdict wording is the versioned card wording`() {
        val service = credential(ecsType = "ECS-SERVICE")
        val organization = credential(ecsType = "ECS-ORG")

        assertEquals(
            "Both identity credentials verified against the Verana public registry",
            EcsClaimReader.describeVerdict(EcsVerdict.TRUSTED, listOf(service, organization)),
        )
        assertEquals(
            "The Verana public registry does not vouch for this service.",
            EcsClaimReader.describeVerdict(EcsVerdict.UNTRUSTED, listOf(service)),
        )
        assertEquals(
            "Neither identity credential verified. This counterparty cannot present verifiable trust credentials.",
            EcsClaimReader.describeVerdict(EcsVerdict.UNTRUSTED, emptyList()),
        )
        assertEquals(
            "The service credential verified. Nothing verifies who operates it.",
            EcsClaimReader.describeVerdict(EcsVerdict.PARTIAL, listOf(service)),
        )
        assertEquals(
            "The operator credential verified. Nothing verifies the service itself.",
            EcsClaimReader.describeVerdict(EcsVerdict.PARTIAL, listOf(organization)),
        )
    }

    @Test
    fun `self issuance compares the issuer DID without the key fragment`() {
        val credential = credential(ecsType = "ECS-SERVICE", issuedBy = "$DID#key-1")

        assertTrue(EcsClaimReader.isSelfIssued(credential, DID))
        assertFalse(EcsClaimReader.isSelfIssued(credential, "did:web:other.example"))
    }

    @Test
    fun `links are stripped from descriptions and counted`() {
        val stripped = EcsClaimReader.stripLinks(
            "See [our site](https://evil.example) and https://also-evil.example for details"
        )

        assertEquals("See our site and for details", stripped.text)
        assertEquals(2, stripped.removed)

        assertEquals(StrippedDescription("", 0), EcsClaimReader.stripLinks(null))
    }

    private fun credential(
        ecsType: String?,
        result: String = "VALID",
        issuedBy: String = "did:web:ecosystem.example",
        claims: List<VeranaTrustClaim> = emptyList(),
    ) = VeranaTrustCredential(
        result = result,
        ecsType = ecsType,
        presentedBy = DID,
        issuedBy = issuedBy,
        id = "urn:uuid:credential",
        type = "VerifiableTrustCredential",
        format = "W3C_VTC",
        claims = claims,
        permissionChain = emptyList(),
    )

    private fun claim(name: String, value: String) = VeranaTrustClaim(
        name = name,
        values = listOf(value),
    )

    private companion object {
        const val DID = "did:web:acme.example"
    }
}
