package com.example

import android.content.Context

class PreferencesManager(context: Context) {
    private val prefs = context.getSharedPreferences("sdmx_prefs", Context.MODE_PRIVATE)

    var userSdmx: String?
        get() = prefs.getString("userSdmx", null)
        set(value) = prefs.edit().putString("userSdmx", value).apply()

    var passSdmx: String?
        get() = prefs.getString("passSdmx", null)
        set(value) = prefs.edit().putString("passSdmx", value).apply()

    var intervalHours: Int
        get() = prefs.getInt("intervalHours", 24)
        set(value) = prefs.edit().putInt("intervalHours", value).apply()
        
    var nextExecutionTime: Long
        get() = prefs.getLong("nextExecutionTime", 0L)
        set(value) = prefs.edit().putLong("nextExecutionTime", value).apply()
        
    fun getSavedLogs(): String {
        return prefs.getString("savedLogs", "") ?: ""
    }
    
    fun saveLogs(logs: List<String>) {
        prefs.edit().putString("savedLogs", logs.joinToString("||")).apply()
    }
}
