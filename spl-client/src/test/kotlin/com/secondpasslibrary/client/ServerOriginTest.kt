package com.secondpasslibrary.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ServerOriginTest {
    @Test
    fun `normalizes an arbitrary path to its https origin`() {
        val origin = ServerOrigin.fromUserInput(" HTTPS://Example.COM:443/api/v1/foo?x=1#part ")

        assertEquals("https://example.com:443", origin.value)
        assertEquals(
            "https://example.com:443/.well-known/secondpass",
            origin.endpoint("/.well-known/secondpass")
        )
    }

    @Test
    fun `adds https when a user enters only a host`() {
        assertEquals(
            "https://library.example",
            ServerOrigin.fromUserInput("library.example/path").value
        )
    }

    @Test
    fun `preserves a non-default port`() {
        assertEquals(
            "http://localhost:8123",
            ServerOrigin.fromUserInput("http://localhost:8123/random").value
        )
    }

    @Test
    fun `rejects non-http and credential-bearing URLs`() {
        assertThrows(SplClientException.InvalidServerUrl::class.java) {
            ServerOrigin.fromUserInput("ftp://library.example/books")
        }
        assertThrows(SplClientException.InvalidServerUrl::class.java) {
            ServerOrigin.fromUserInput("https://user:password@library.example")
        }
    }
}
