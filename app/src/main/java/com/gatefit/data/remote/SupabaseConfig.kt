package com.gatefit.data.remote

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.Base64

class SupabaseConfig private constructor(val baseUrl: HttpUrl, val anonKey: String) {

    companion object {
        // 설정이 없거나 안전하지 않으면 null, 이 경우 앱은 내장 데이터만으로 동작
        fun from(url: String, anonKey: String): SupabaseConfig? {
            val key = anonKey.trim()
            val parsed = url.trim().toHttpUrlOrNull() ?: return null
            if (!parsed.isHttps || key.isEmpty() || isPrivilegedKey(key)) return null
            val root = parsed.newBuilder().encodedPath("/").query(null).fragment(null).build()
            return SupabaseConfig(root, key)
        }

        // RLS를 우회하는 서버용 키가 실수로 앱에 주입되는 사고 방지
        fun isPrivilegedKey(key: String): Boolean {
            if (key.startsWith("sb_secret_")) return true
            val payload = key.split(".").getOrNull(1) ?: return false
            val decoded = try {
                String(Base64.getUrlDecoder().decode(payload), Charsets.UTF_8)
            } catch (_: IllegalArgumentException) {
                return false
            }
            return ROLE_PATTERN.find(decoded)?.groupValues?.get(1)?.let { it != "anon" } ?: false
        }

        private val ROLE_PATTERN = Regex("\"role\"\\s*:\\s*\"([^\"]*)\"")
    }
}
