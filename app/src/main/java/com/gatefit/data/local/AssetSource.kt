package com.gatefit.data.local

import android.content.Context

fun interface TextAssetSource {
    fun read(path: String): String
}

class AndroidAssetSource(context: Context) : TextAssetSource {
    private val assets = context.applicationContext.assets

    override fun read(path: String): String =
        assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
}

object AssetPaths {
    const val BASELINE_SNAPSHOT = "baseline/seoul_baseline.json"
    const val DEMO_PLACES = "demo/demo_places.json"
    const val HOLIDAYS = "calendar/kr_public_holidays.json"
    const val VEHICLE_SPECS = "catalog/vehicle_model_specs.json"
}
