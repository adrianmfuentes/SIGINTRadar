package com.sigint.radar.service

import android.app.*
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.sigint.radar.MainActivity
import com.sigint.radar.MockDataProvider
import com.sigint.radar.R
import com.sigint.radar.model.DetectedDevice
import com.sigint.radar.scanner.BluetoothScanner
import com.sigint.radar.scanner.WifiScanner
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.compareTo
import kotlin.text.set

class ScannerService : LifecycleService() {
    private val binder = LocalBinder()
    private lateinit var wifiScanner: WifiScanner
    private lateinit var bluetoothScanner: BluetoothScanner
    private var debugMode = false
    private val _devicesFlow = MutableStateFlow<List<DetectedDevice>>(emptyList())
    val devicesFlow: StateFlow<List<DetectedDevice>> = _devicesFlow.asStateFlow()
    private val allDevices = mutableMapOf<String, DetectedDevice>()

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "sigint_scanner"
        private const val SCAN_INTERVAL_MS = 2000L
    }

    inner class LocalBinder : Binder() {
        fun getService(): ScannerService = this@ScannerService
    }

    override fun onCreate() {
        super.onCreate()

        wifiScanner = WifiScanner(this)
        bluetoothScanner = BluetoothScanner(this)

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        // Recibir modo debug del intent
        debugMode = intent?.getBooleanExtra("DEBUG_MODE", false) ?: false

        startContinuousScanning()

        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "SIGINT Scanner",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Escaneo continuo de dispositivos WiFi/BLE"
            setShowBadge(false)
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SIGINT Radar")
            .setContentText("Escaneando dispositivos cercanos...")
            .setSmallIcon(R.drawable.ic_radar)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun startContinuousScanning() {
        lifecycleScope.launch {
            var scanCount = 0
            while (true) {
                if (debugMode) {
                    val mockDevices = MockDataProvider.getMockDevices()
                    mockDevices.forEach { device ->
                        allDevices[device.address] = device
                    }
                } else {
                    // Siempre escanear WiFi
                    val wifiDevices = wifiScanner.scan()
                    wifiDevices.forEach { device ->
                        allDevices[device.address] = device
                    }

                    // Escanear Bluetooth solo cada 3 ciclos (cada 6 segundos)
                    if (scanCount % 3 == 0) {
                        val bleDevices = bluetoothScanner.scan()
                        bleDevices.forEach { device ->
                            allDevices[device.address] = device
                        }
                    }
                }

                val now = System.currentTimeMillis()
                allDevices.entries.removeIf { (_, device) ->
                    now - device.timestamp > 30_000
                }

                _devicesFlow.value = allDevices.values.toList()
                updateNotification(allDevices.size)

                scanCount++
                delay(SCAN_INTERVAL_MS)
            }
        }
    }

    private fun updateNotification(deviceCount: Int) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SIGINT Radar")
            .setContentText("$deviceCount dispositivos detectados")
            .setSmallIcon(R.drawable.ic_radar)
            .setOngoing(true)
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        wifiScanner.stop()
        bluetoothScanner.stop()
    }
}