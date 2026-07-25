package ch.admin.foitt.openid4vc.domain.model

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TokenTypeTest {

    @Test
    fun `token type decoding is case insensitive`() {
        val variants = mapOf(
            "Bearer" to TokenType.BEARER,
            "bearer" to TokenType.BEARER,
            "BEARER" to TokenType.BEARER,
            "DPoP" to TokenType.DPOP,
            "dpop" to TokenType.DPOP,
        )

        variants.forEach { (value, expected) ->
            assertEquals(expected, Json.decodeFromString<TokenType>("\"$value\""))
        }
    }
}
