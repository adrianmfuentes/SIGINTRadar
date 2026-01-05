package com.sigint.radar.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sigint.radar.MainActivity
import com.sigint.radar.R
import com.sigint.radar.database.RadarDatabase
import com.sigint.radar.model.DetectedDevice
import com.sigint.radar.repository.KnownDeviceRepository
import com.sigint.radar.repository.ScanHistoryRepository
import com.sigint.radar.scanner.BluetoothScanner
import com.sigint.radar.scanner.WifiScanner
import kotlinx.coroutines.flow.first

/**
 * Worker para escaneos periódicos en segundo plano
 */
class PeriodicScanWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val database = RadarDatabase.getDatabase(context)
    private val scanHistoryRepo = ScanHistoryRepository(database)
    private val knownDeviceRepo = KnownDeviceRepository(database)

    override suspend fun doWork(): Result {
        try {
            // Realizar escaneo
            val devices = performScan()

            // Guardar en historial
            scanHistoryRepo.saveScan(devices, notes = "Automatic periodic scan")

            // Verificar dispositivos con alertas
            checkAlerts(devices)

            return Result.success()
        } catch (e: Exception) {
            return Result.failure()
        }
    }

    private suspend fun performScan(): List<DetectedDevice> {
        val allDevices = mutableListOf<DetectedDevice>()

        // TODO: Implement background scanning
        // Background scanning requires different approach than foreground scanning
        // For now, return empty list

        // Escanear WiFi
        try {
            // val wifiScanner = WifiScanner(applicationContext)
            // Background WiFi scanning has limitations on Android 9+
        } catch (e: Exception) {
            // Ignorar errores de permisos en background
        }

        return allDevices
    }

    private suspend fun checkAlerts(devices: List<DetectedDevice>) {
        val alertDevices = knownDeviceRepo.getDevicesWithAlerts().first()

        devices.forEach { device ->
            val knownDevice = alertDevices.find { it.macAddress == device.address }
            if (knownDevice != null) {
                // Enviar notificación
                sendAlert(device, knownDevice.customName ?: device.name)
            }
        }

        // También alertar sobre dispositivos de alto riesgo
        val criticalDevices = devices.filter {
            it.riskLevel == DetectedDevice.RiskLevel.CRITICAL ||
            it.riskLevel == DetectedDevice.RiskLevel.HIGH
        }

        if (criticalDevices.isNotEmpty()) {
            sendHighRiskAlert(criticalDevices.size)
        }
    }

    private fun sendAlert(device: DetectedDevice, displayName: String) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        createNotificationChannel(notificationManager)

        val intent = Intent(applicationContext, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_radar)
            .setContentTitle("Device Detected: $displayName")
            .setContentText("MAC: ${device.address} - Risk: ${device.riskLevel.displayName}")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(device.address.hashCode(), notification)
    }

    private fun sendHighRiskAlert(count: Int) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        createNotificationChannel(notificationManager)

        val intent = Intent(applicationContext, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_radar)
            .setContentTitle("High Risk Devices Detected")
            .setContentText("$count high-risk device(s) detected nearby")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID_HIGH_RISK, notification)
    }

    private fun createNotificationChannel(notificationManager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SIGINT Radar Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts for detected devices"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "sigint_alerts"
        private const val NOTIFICATION_ID_HIGH_RISK = 1000
    }
}

