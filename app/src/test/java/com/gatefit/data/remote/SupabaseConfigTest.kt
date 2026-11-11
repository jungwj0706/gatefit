package com.gatefit.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class SupabaseConfigTest {

    private fun jwt(role: String): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val header = encoder.encodeToString("""{"alg":"HS256","typ":"JWT"}""".toByteArray())
        val payload = encoder.encodeToString("""{"iss":"supabase","ref":"example","role":"$role"}""".toByteArray())
        return "$header.$payload.signature"
    }

    @Test
    fun `blank configuration means remote sync is disabled`() {
        assertNull(SupabaseConfig.from("", ""))
        assertNull(SupabaseConfig.from("https://example.supabase.co", "  "))
        assertNull(SupabaseConfig.from("not a url", jwt("anon")))
    }

    @Test
    fun `plain http endpoint is refused`() {
        assertNull(SupabaseConfig.from("http://example.supabase.co", jwt("anon")))
    }

    @Test
    fun `anon and publishable keys are accepted and the url is normalized to the root`() {
        val config = SupabaseConfig.from(" https://example.supabase.co/rest/v1?x=1 ", " ${jwt("anon")} ")!!

        assertEquals("https://example.supabase.co/", config.baseUrl.toString())
        assertEquals(jwt("anon"), config.anonKey)
        assertTrue(SupabaseConfig.from("https://example.supabase.co", "sb_publishable_abc123") != null)
    }

    @Test
    fun `service role and secret keys are never accepted`() {
        assertTrue(SupabaseConfig.isPrivilegedKey(jwt("service_role")))
        assertTrue(SupabaseConfig.isPrivilegedKey("sb_secret_abc123"))
        assertFalse(SupabaseConfig.isPrivilegedKey(jwt("anon")))

        assertNull(SupabaseConfig.from("https://example.supabase.co", jwt("service_role")))
        assertNull(SupabaseConfig.from("https://example.supabase.co", "sb_secret_abc123"))
    }
}
