package com.example.motoday.ui.utils

import android.content.Context
import com.example.motoday.data.local.AppDatabase
import kotlinx.coroutines.flow.first

class MaintenanceChecker(val context: Context) {
    private val db = AppDatabase.getDatabase(context)
    private val notifier = NotificationHelper(context)

    suspend fun checkStatus() {
        val user = db.userDao().getUserProfile().first() ?: return
        val logs = db.maintenanceDao().getAllLogs().first()
        
        val currentKm = user.totalKilometers
        
        // 1. Revisar Aceite (Intervalo 3000km)
        val lastOilKm = logs.filter { it.type == "Aceite" }.maxByOrNull { it.date }?.mileage ?: 0
        val oilRemaining = (lastOilKm + 3000) - currentKm
        
        if (oilRemaining <= 0) {
            notifier.showMaintenanceAlert(
                "¡Cambio de Aceite Vencido!", 
                "Tu moto tiene $currentKm km. Es urgente realizar el mantenimiento."
            )
        } else if (oilRemaining < 300) {
            notifier.showMaintenanceAlert(
                "Aceite Próximo a Vencer", 
                "Faltan solo $oilRemaining km para tu próximo cambio."
            )
        }

        // 2. Revisar Llantas (Intervalo 12000km)
        val lastTiresKm = logs.filter { it.type == "Llantas" }.maxByOrNull { it.date }?.mileage ?: 0
        val tiresRemaining = (lastTiresKm + 12000) - currentKm
        
        if (tiresRemaining <= 0) {
            notifier.showMaintenanceAlert(
                "Revisión de Llantas", 
                "Has superado el límite de 12,000 km. ¡Revisa el grabado de tus neumáticos!"
            )
        }
    }
}
