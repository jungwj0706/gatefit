package com.gatefit.data.local

import android.content.Context
import androidx.core.content.edit

class SharedPreferencesStore(context: Context, name: String) : KeyValueStore {
    private val preferences = context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)

    override fun getString(key: String): String? = preferences.getString(key, null)

    override fun putString(key: String, value: String?) {
        preferences.edit {
            if (value == null) remove(key) else putString(key, value)
        }
    }
}
