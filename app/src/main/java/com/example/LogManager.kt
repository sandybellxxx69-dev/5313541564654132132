package com.example

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogManager {
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs = _logs.asStateFlow()
    
    fun init(prefs: PreferencesManager) {
        val saved = prefs.getSavedLogs()
        if (saved.isNotEmpty()) {
            _logs.value = saved.split("||")
        }
    }

    fun addLog(msg: String, prefs: PreferencesManager? = null) {
        val dateStr = SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault()).format(Date())
        val newLog = "[$dateStr] $msg"
        
        _logs.update { current ->
            val updated = current.toMutableList()
            updated.add(0, newLog)
            if (updated.size > 100) {
                updated.removeLast()
            }
            prefs?.saveLogs(updated)
            updated
        }
    }
}
