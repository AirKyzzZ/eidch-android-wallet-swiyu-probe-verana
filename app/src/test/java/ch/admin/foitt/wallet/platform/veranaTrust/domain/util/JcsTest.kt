package ch.admin.foitt.wallet.platform.veranaTrust.domain.util

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class JcsTest {

    @Test
    fun `sorts keys at every depth`() {
        val canonical = Jcs.canonicalize(Json.parseToJsonElement("""{"b":1,"a":{"d":[true,null],"c":"x"}}"""))

        assertEquals("""{"a":{"c":"x","d":[true,null]},"b":1}""", canonical)
    }

    @Test
    fun `serializes numbers the way RFC 8785 does`() {
        val canonical = Jcs.canonicalize(Json.parseToJsonElement("[1e21, 0.000001, -0, 10.5, 100, 1e-7, 123456789012345680000]"))

        assertEquals("[1e+21,0.000001,0,10.5,100,1e-7,123456789012345680000]", canonical)
    }

    @Test
    fun `escapes only what JSON requires`() {
        val canonical = Jcs.canonicalize(Json.parseToJsonElement(""""\u0001\n\"\\é€/""""))

        assertEquals(""""\u0001\n\"\\é€/"""", canonical)
    }

    @Test
    fun `sorts keys by UTF-16 code units`() {
        val canonical = Jcs.canonicalize(Json.parseToJsonElement("""{"é":1,"z":2,"A":3}"""))

        assertEquals("""{"A":3,"z":2,"é":1}""", canonical)
    }

    @Test
    fun `base58btc keeps leading zero bytes`() {
        assertArrayEquals(byteArrayOf(0, 0, 1), Base58Btc.decode("112"))
    }

    @Test
    fun `base58btc rejects characters outside the alphabet`() {
        assertThrows<IllegalArgumentException> { Base58Btc.decode("0OIl") }
    }
}
