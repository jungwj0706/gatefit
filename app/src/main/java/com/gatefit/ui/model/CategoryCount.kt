package com.gatefit.ui.model

import com.gatefit.domain.model.FacilityCategory
import com.gatefit.domain.repository.FacilityRepository

data class CategoryCount(val category: FacilityCategory, val count: Int)

// 등록된 시설이 있는 카테고리만 노출, 데이터가 없는 분류를 검색 가능한 것처럼 보이지 않게 함
suspend fun FacilityRepository.registeredCategories(): List<CategoryCount> {
    val counts = categoryCounts()
    return FacilityCategory.entries.mapNotNull { category ->
        counts[category]?.takeIf { it > 0 }?.let { CategoryCount(category, it) }
    }
}
