package com.gatefit.ui.navigation

import android.net.Uri

object Routes {
    const val ARG_ONBOARDING = "onboarding"
    const val ARG_FACILITY_ID = "facilityId"
    const val ARG_ENTRANCE_ID = "entranceId"
    const val ARG_CATEGORY = "category"
    const val ARG_KIND = "kind"

    const val SPLASH = "splash"
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val DATA_POLICY = "data_policy"
    const val VEHICLE_SETUP = "vehicle_setup?$ARG_ONBOARDING={$ARG_ONBOARDING}"
    const val SEARCH = "search?$ARG_CATEGORY={$ARG_CATEGORY}"
    const val ANALYSIS = "analysis/{$ARG_FACILITY_ID}"
    const val ENTRANCE_DETAIL = "entrance/{$ARG_ENTRANCE_ID}"
    const val NAVIGATION_HANDOFF = "handoff/{$ARG_ENTRANCE_ID}"
    const val REPORT = "report/{$ARG_ENTRANCE_ID}"
    const val SAVED_PLACES = "saved/{$ARG_KIND}"

    const val KIND_RECENT = "recent"
    const val KIND_FAVORITE = "favorite"

    fun vehicleSetup(onboarding: Boolean) = "vehicle_setup?$ARG_ONBOARDING=$onboarding"

    fun search(category: String? = null) =
        if (category == null) "search" else "search?$ARG_CATEGORY=${Uri.encode(category)}"

    fun analysis(facilityId: String) = "analysis/${Uri.encode(facilityId)}"

    fun entranceDetail(entranceId: String) = "entrance/${Uri.encode(entranceId)}"

    fun navigationHandoff(entranceId: String) = "handoff/${Uri.encode(entranceId)}"

    fun report(entranceId: String) = "report/${Uri.encode(entranceId)}"

    fun savedPlaces(kind: String) = "saved/$kind"
}
