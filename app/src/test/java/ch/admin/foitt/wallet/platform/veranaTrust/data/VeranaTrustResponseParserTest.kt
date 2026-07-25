package ch.admin.foitt.wallet.platform.veranaTrust.data

import ch.admin.foitt.wallet.platform.veranaTrust.domain.model.VeranaTrustSummary
import ch.admin.foitt.wallet.util.SafeJsonTestInstance
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VeranaTrustResponseParserTest {

    private val parser = VeranaTrustResponseParser(SafeJsonTestInstance.safeJson)

    @Test
    fun `strict summary parser accepts a complete exact response`() {
        val result = parser.parseSummary(
            body = summaryJson(),
            evaluatedAtBlockHeader = BLOCK.toString(),
            expectedDid = DID,
        )

        assertEquals(summary(), result)
    }

    @Test
    fun `summary parser rejects malformed or missing load-bearing fields`() {
        val malformedBodies = listOf(
            summaryJson().replace("\"did\":\"$DID\"", "\"did\":7"),
            summaryJson().replace("\"trustStatus\":\"TRUSTED\"", "\"trustStatus\":true"),
            summaryJson().replace("\"production\":true", "\"production\":\"true\""),
            summaryJson().replace("\"evaluatedAtBlock\":$BLOCK,", ""),
            summaryJson().replace("\"evaluatedAt\":\"$EVALUATED_AT\"", "\"evaluatedAt\":\"not-a-time\""),
            summaryJson().replace("\"expiresAt\":\"$EXPIRES_AT\"", "\"expiresAt\":null"),
        )

        malformedBodies.forEach { body ->
            assertNull(parser.parseSummary(body, BLOCK.toString(), DID))
        }
    }

    @Test
    fun `summary parser rejects unknown status exact DID mismatch and block mismatch`() {
        assertNull(
            parser.parseSummary(
                summaryJson().replace("TRUSTED", "UNKNOWN"),
                BLOCK.toString(),
                DID,
            )
        )
        assertNull(parser.parseSummary(summaryJson(did = OTHER_DID), BLOCK.toString(), DID))
        assertNull(parser.parseSummary(summaryJson(), (BLOCK + 1).toString(), DID))
        assertNull(parser.parseSummary(summaryJson(), "not-a-block", DID))
    }

    @Test
    fun `strict authorization parser accepts exact typed fields and the header block`() {
        val result = parser.parseAuthorization(
            body = authorizationJson(),
            evaluatedAtBlockHeader = AUTH_BLOCK.toString(),
            expectedDid = DID,
            expectedSchemaId = SCHEMA,
        )

        requireNotNull(result)
        assertEquals(DID, result.did)
        assertEquals(SCHEMA, result.vcSchemaId)
        assertTrue(result.authorized)
        assertEquals(AUTH_BLOCK, result.evaluatedAtBlock)
    }

    @Test
    fun `authorization parser rejects non-boolean authorization and exact identity mismatches`() {
        assertNull(
            parser.parseAuthorization(
                authorizationJson().replace("\"authorized\":true", "\"authorized\":\"true\""),
                AUTH_BLOCK.toString(),
                DID,
                SCHEMA,
            )
        )
        assertNull(parser.parseAuthorization(authorizationJson(did = OTHER_DID), AUTH_BLOCK.toString(), DID, SCHEMA))
        assertNull(parser.parseAuthorization(authorizationJson(schema = OTHER_SCHEMA), AUTH_BLOCK.toString(), DID, SCHEMA))
        assertNull(parser.parseAuthorization(authorizationJson(), "0", DID, SCHEMA))
        assertNull(
            parser.parseAuthorization(
                authorizationJson().replace(EVALUATED_AT, "not-a-time"),
                AUTH_BLOCK.toString(),
                DID,
                SCHEMA,
            )
        )
    }

    @Test
    fun `full parser keeps allowlisted credential evidence and ignores placeholders or complex claims`() {
        val result = parser.parseDetails(
            body = fullJson(),
            evaluatedAtBlockHeader = BLOCK.toString(),
            expectedSummary = summary(),
        )

        requireNotNull(result)
        assertEquals(summary(), result.summary)
        assertEquals(1, result.credentials.size)
        val credential = result.credentials.single()
        assertEquals("ECS-SERVICE", credential.ecsType)
        assertEquals("VALID", credential.result)
        assertEquals(DID, credential.presentedBy)
        assertEquals("did:web:ecosystem.example", credential.issuedBy)
        assertEquals("Acme Service", credential.claims.single { it.name == "name" }.values.single())
        assertEquals(
            "https://acme.example/privacy",
            credential.claims.single { it.name == "privacyPolicy" }.safeHttpUrl,
        )
        assertTrue(credential.claims.none { it.name == "nested" })
        assertTrue(credential.claims.none { it.name == "termsAndConditions" })
        assertEquals(1, credential.permissionChain.size)
        assertEquals(42L, credential.permissionChain.single().permissionId)
        assertEquals("Acme Service", credential.permissionChain.single().serviceName)
    }

    @Test
    fun `full parser rejects a changed summary identity status or production flag`() {
        assertNull(parser.parseDetails(fullJson(did = OTHER_DID), BLOCK.toString(), summary()))
        assertNull(
            parser.parseDetails(
                fullJson().replace("\"trustStatus\":\"TRUSTED\"", "\"trustStatus\":\"PARTIAL\""),
                BLOCK.toString(),
                summary(),
            )
        )
        assertNull(
            parser.parseDetails(
                fullJson().replace("\"production\":true", "\"production\":false"),
                BLOCK.toString(),
                summary(),
            )
        )
    }

    private fun summary() = VeranaTrustSummary(
        did = DID,
        trustStatus = "TRUSTED",
        production = true,
        evaluatedAt = EVALUATED_AT,
        evaluatedAtBlock = BLOCK,
        expiresAt = EXPIRES_AT,
    )

    private fun summaryJson(did: String = DID) = """
        {
          "did":"$did",
          "trustStatus":"TRUSTED",
          "production":true,
          "evaluatedAt":"$EVALUATED_AT",
          "evaluatedAtBlock":$BLOCK,
          "expiresAt":"$EXPIRES_AT"
        }
    """.trimIndent()

    private fun authorizationJson(
        did: String = DID,
        schema: String = SCHEMA,
    ) = """
        {
          "did":"$did",
          "vtjscId":"$schema",
          "authorized":true,
          "evaluatedAt":"$EVALUATED_AT",
          "evaluatedAtBlock":{},
          "permission":{},
          "fees":{},
          "permissionChain":{}
        }
    """.trimIndent()

    private fun fullJson(did: String = DID) = """
        {
          "did":"$did",
          "trustStatus":"TRUSTED",
          "production":true,
          "evaluatedAt":"$EVALUATED_AT",
          "evaluatedAtBlock":$BLOCK,
          "expiresAt":"$EXPIRES_AT",
          "credentials":[
            {
              "result":"VALID",
              "ecsType":"ECS-SERVICE",
              "presentedBy":"$DID",
              "issuedBy":"did:web:ecosystem.example",
              "id":"urn:uuid:credential",
              "type":"VerifiableTrustCredential",
              "format":"W3C_VTC",
              "claims":{
                "name":"Acme Service",
                "type":"Insurance",
                "description":"A trusted service",
                "privacyPolicy":"https://acme.example/privacy",
                "termsAndConditions":"javascript:alert(1)",
                "nested":{"must":"not render"},
                "unknown":"must not render"
              },
              "permissionChain":[
                {
                  "permissionId":42,
                  "type":"ISSUER",
                  "did":"$DID",
                  "didIsTrustedVS":true,
                  "serviceName":"Acme Service",
                  "deposit":"500uvna",
                  "permState":"ACTIVE"
                }
              ]
            }
          ],
          "failedCredentials":[],
          "dereferenceErrors":[]
        }
    """.trimIndent()

    private companion object {
        const val DID = "did:web:acme.example"
        const val OTHER_DID = "did:web:other.example"
        const val SCHEMA = "https://schemas.example/credential"
        const val OTHER_SCHEMA = "https://schemas.example/other"
        const val EVALUATED_AT = "2026-07-18T12:00:00.000Z"
        const val EXPIRES_AT = "2026-07-19T12:00:00.000Z"
        const val BLOCK = 1_500_000L
        const val AUTH_BLOCK = 1_500_001L
    }
}
