package com.gatefit.data.local

object SearchText {
    private val IGNORED = Regex("[\\s()\\[\\]·,.\\-_/&'\"]+")

    // 띄어쓰기·기호 차이로 검색이 실패하지 않도록 비교용 문자열로 정규화
    fun normalize(text: String): String = text.lowercase().replace(IGNORED, "")

    fun tokens(query: String): List<String> =
        query.trim().split(Regex("\\s+")).map(::normalize).filter { it.isNotEmpty() }.distinct()

    fun escapeLike(token: String): String =
        token.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    fun searchable(name: String, aliases: List<String>, roadAddress: String?, lotAddress: String?): String =
        (listOf(name) + aliases + listOfNotNull(roadAddress, lotAddress)).joinToString("|") { normalize(it) }
}
