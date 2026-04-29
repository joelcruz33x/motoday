package com.example.motoday.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.motoday.data.local.AppDatabase
import com.example.motoday.data.remote.AppwriteManager
import com.example.motoday.data.remote.AuthManager

class CleanupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val appwrite = AppwriteManager.getInstance(applicationContext)
            val authManager = AuthManager(applicationContext)
            val db = AppDatabase.getDatabase(applicationContext)
            
            val userId = authManager.getCurrentUserId()
            
            Log.d("CleanupWorker", "Iniciando limpieza de datos...")

            // 1. Limpieza de Historias (Remoto)
            if (userId != null) {
                val deletedStories = appwrite.cleanupOldStories(userId)
                Log.d("CleanupWorker", "Historias eliminadas: $deletedStories")
            }

            // 2. Limpieza de Rutas finalizadas (Local y Remoto)
            val oneHourAgo = System.currentTimeMillis() - (60 * 60 * 1000)
            
            // Local
            db.rideDao().cleanupOldRides(oneHourAgo)
            
            // Remoto
            val deletedRemoteRides = appwrite.cleanupOldRemoteRides()
            Log.d("CleanupWorker", "Limpieza de rutas completada (Local y $deletedRemoteRides remotas)")

            Result.success()
        } catch (e: Exception) {
            Log.e("CleanupWorker", "Error en limpieza: ${e.message}")
            Result.retry()
        }
    }
}
