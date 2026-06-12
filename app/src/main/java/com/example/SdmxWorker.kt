package com.example

import android.content.Context
import androidx.work.*
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

class SdmxWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val prefs = PreferencesManager(applicationContext)
        val notifications = NotificationHelper(applicationContext)
        val apiService = SdmxApiService()

        val userSdmx = prefs.userSdmx ?: return Result.failure()
        val passSdmx = prefs.passSdmx ?: return Result.failure()

        LogManager.addLog("Iniciando ciclo de renovación automatizado...", prefs)

        try {
            // 2. Login
            val loginOk = apiService.login(userSdmx, passSdmx)
            if (!loginOk) {
                LogManager.addLog("❌ Login fallido — verifica credenciales.", prefs)
                notifications.showError("SDMX Error", "Login fallido — verifica credenciales")
                return Result.failure()
            }
            LogManager.addLog("✅ Sesión SDMX iniciada.", prefs)

            // 3. Read Google Sheets
            val (vigentes, noVigentes) = apiService.fetchSheets()
            LogManager.addLog("📋 Total leídos: ${vigentes.size + noVigentes.size} | Vigentes: ${vigentes.size} | No vigentes: ${noVigentes.size}", prefs)

            if (vigentes.isEmpty()) {
                LogManager.addLog("🎉 Ciclo completado. Procesados: 0 usuarios vigentes.", prefs)
                scheduleNextRun(prefs)
                return Result.success()
            }

            // 4 & 5. Delete and Create
            vigentes.forEach { user ->
                try {
                    apiService.deleteUser(user.id)
                    LogManager.addLog("🗑️ Eliminado: ${user.usuario} (id: ${user.id})", prefs)
                    delay(500) // Politeness delay
                } catch (e: Exception) {
                    LogManager.addLog("❌ Error al eliminar ${user.usuario}: ${e.message}", prefs)
                }

                try {
                    apiService.createUser(user)
                    LogManager.addLog("✅ Creado: ${user.usuario}", prefs)
                    delay(500)
                } catch (e: Exception) {
                    LogManager.addLog("❌ Error al crear ${user.usuario}: ${e.message}", prefs)
                }
            }

            // 6. Get updated table for new IDs
            delay(1000) // Wait for panel to reflect
            val newIdsMap = try {
                apiService.getUpdatedTable(vigentes)
            } catch (e: Exception) {
                LogManager.addLog("⚠️ No se pudo obtener la tabla de IDs: ${e.message}", prefs)
                emptyMap<String, String>()
            }

            // 7. Update Sheets with new IDs
            val updates = mutableListOf<SheetsUser>()
            vigentes.forEach { user ->
                val newId = newIdsMap[user.usuario]
                if (newId != null) {
                    updates.add(user.copy(id = newId))
                }
            }

            if (updates.isNotEmpty()) {
                try {
                    apiService.updateSheetsIds(updates)
                    updates.forEach {
                        LogManager.addLog("📝 Sheets actualizado: ${it.usuario} → nuevo id: ${it.id}", prefs)
                    }
                } catch (e: Exception) {
                    LogManager.addLog("❌ Error al actualizar Sheets: ${e.message}", prefs)
                }
            }

            // 8. Completed
            LogManager.addLog("🎉 Ciclo completado. Procesados: ${vigentes.size} usuarios vigentes.", prefs)
            
            scheduleNextRun(prefs)
            notifications.showSuccess("SDMX Auto-Renew", "Ciclo completado. ${vigentes.size} usuarios renovados.")

            return Result.success()

        } catch (e: Exception) {
            val msg = e.message ?: "Error desconocido"
            LogManager.addLog("❌ Error del sistema: $msg", prefs)
            notifications.showError("SDMX Error", "Proceso detenido por error: $msg")
            
            if (e is java.net.UnknownHostException || e is java.net.SocketException) {
                return Result.retry()
            }
            return Result.failure()
        }
    }

    private fun scheduleNextRun(prefs: PreferencesManager) {
        val intervalHours = prefs.intervalHours
        val nextTimeMs = System.currentTimeMillis() + (intervalHours * 3600 * 1000L)
        prefs.nextExecutionTime = nextTimeMs
        LogManager.addLog("⏳ Próxima ejecución en: $intervalHours horas.", prefs)
    }

    companion object {
        private const val WORK_NAME = "SDMX_AUTO_RENEW_WORK"

        fun enqueuePeriodic(context: Context, intervalHours: Int) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(false)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<SdmxWorker>(
                intervalHours.toLong(), TimeUnit.HOURS
            ).setConstraints(constraints).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )
            
            // Set first next Execution time visually
            val prefs = PreferencesManager(context)
            prefs.nextExecutionTime = System.currentTimeMillis() + (intervalHours * 3600 * 1000L)
        }

        fun executeNow(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<SdmxWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueue(workRequest)
        }
    }
}
