package com.sigint.radar.service

import android.app.*
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.sigint.radar.MainActivity
import com.sigint.radar.R
import com.sigint.radar.model.DetectedDevice
import com.sigint.radar.scanner.BluetoothScanner
import com.sigint.radar.scanner.WifiScanner
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ScannerStatus(
    val wifiThrottled: Boolean = false,
    val bluetoothEnabled: Boolean = true
)

class ScannerService : LifecycleService() {
    private val binder = LocalBinder()
    private lateinit var wifiScanner: WifiScanner
    private lateinit var bluetoothScanner: BluetoothScanner
    private val _devicesFlow = MutableStateFlow<List<DetectedDevice>>(emptyList())
    val devicesFlow: StateFlow<List<DetectedDevice>> = _devicesFlow.asStateFlow()
    private val _statusFlow = MutableStateFlow(ScannerStatus())
    val statusFlow: StateFlow<ScannerStatus> = _statusFlow.asStateFlow()
    private val allDevices = mutableMapOf<String, DetectedDevice>()

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "sigint_scanner"

        // UI/pruning tick - keeps the radar and stale-device cleanup responsive.
        private const val TICK_INTERVAL_MS = 2000L

        // Android throttles WifiManager.startScan() to ~4 calls per 2-minute window
        // (API 28+). Calling it every tick guarantees throttling within seconds, after
        // which every "scan" silently returns stale cached results. Scanning every 15
        // ticks (~30s) stays comfortably under the limit so results are actually fresh.
        private const val WIFI_SCAN_EVERY_TICKS = 15

        // BLE scanning isn't throttled the same way; keep it more frequent.
        private const val BLUETOOTH_SCAN_EVERY_TICKS = 3

        // Devices must survive a couple of scan cycles of either radio before being
        // dropped, otherwise they'd be pruned before the next WiFi scan even runs.
        private const val DEVICE_EXPIRY_MS = 75_000L
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
            var tick = 0
            while (true) {
                if (tick % WIFI_SCAN_EVERY_TICKS == 0) {
                    val wifiDevices = wifiScanner.scan()
                    wifiDevices.forEach { device -> allDevices[device.address] = device }
                }

                if (tick % BLUETOOTH_SCAN_EVERY_TICKS == 0) {
                    val bleDevices = bluetoothScanner.scan()
                    bleDevices.forEach { device -> allDevices[device.address] = device }
                }

                _statusFlow.value = ScannerStatus(
                    wifiThrottled = wifiScanner.lastScanThrottled,
                    bluetoothEnabled = bluetoothScanner.isBluetoothEnabled()
                )

                val now = System.currentTimeMillis()
                allDevices.entries.removeIf { (_, device) ->
                    now - device.timestamp > DEVICE_EXPIRY_MS
                }

                _devicesFlow.value = allDevices.values.toList()
                updateNotification(allDevices.size)

                tick++
                delay(TICK_INTERVAL_MS)
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
