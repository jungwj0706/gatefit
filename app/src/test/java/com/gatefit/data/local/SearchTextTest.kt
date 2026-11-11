package com.gatefit.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTextTest {

    @Test
    fun `spacing, case and punctuation do not affect matching`() {
        assertEquals("ifc몰여의도점", SearchText.normalize("IFC 몰 (여의도점)"))
        assertEquals(SearchText.normalize("서울대학교병원"), SearchText.normalize("서울 대학교 병원"))
    }

    @Test
    fun `query is split into distinct normalized tokens`() {
        assertEquals(listOf("강남", "병원"), SearchText.tokens("  강남   병원 강남 "))
        assertTrue(SearchText.tokens(" ( ) ").isEmpty())
    }

    @Test
    fun `like wildcards typed by the user are escaped`() {
        assertEquals("100\\%\\_할인\\\\", SearchText.escapeLike("100%_할인\\"))
    }

    @Test
    fun `searchable text covers name, aliases and both addresses`() {
        val text = SearchText.searchable("코엑스", listOf("COEX Mall"), "서울특별시 강남구 영동대로 513", "삼성동 159")

        assertTrue("coexmall" in text)
        assertTrue("영동대로513" in text)
        assertTrue("삼성동159" in text)
    }
}
