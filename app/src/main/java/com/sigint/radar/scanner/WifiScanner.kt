package com.sigint.radar.scanner

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import androidx.core.content.ContextCompat
import com.sigint.radar.model.DetectedDevice
import com.sigint.radar.util.OuiDatabase
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.pow

class WifiScanner(private val context: Context) {

    private val wifiManager = context.applicationContext
        .getSystemService(Context.WIFI_SERVICE) as WifiManager

    private var scanReceiver: BroadcastReceiver? = null

    suspend fun scan(): List<DetectedDevice> = suspendCancellableCoroutine { continuation ->
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            continuation.resume(emptyList())
            return@suspendCancellableCoroutine
        }

        scanReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
                    if (ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        // Permission denied, nothing to do here
                        return
                    }
                    val results = wifiManager.scanResults ?: emptyList()
                    val devices = results.map { enhanceDevice(it) }

                    context.unregisterReceiver(this)
                    scanReceiver = null

                    continuation.resume(devices)
                }
            }
        }

        val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        context.registerReceiver(scanReceiver, filter)

        val success = wifiManager.startScan()
        if (!success) {
            // Si falla, usar resultados cacheados
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                continuation.resume(emptyList())
                context.unregisterReceiver(scanReceiver!!)
                scanReceiver = null
                return@suspendCancellableCoroutine
            }

            val results = wifiManager.scanResults ?: emptyList()
            val devices = results.map { enhanceDevice(it) }

            context.unregisterReceiver(scanReceiver!!)
            scanReceiver = null

            continuation.resume(devices)
        }

        continuation.invokeOnCancellation {
            scanReceiver?.let { context.unregisterReceiver(it) }
        }
    }

    private fun enhanceDevice(scan: ScanResult): DetectedDevice {
        val manufacturer = OuiDatabase.lookup(scan.BSSID)
        val distance = calculateDistance(scan.level, scan.frequency)
        val riskLevel = assessRisk(scan, manufacturer)
        val signalQuality = calculateSignalQuality(scan.level)
        val channelWidth = getChannelWidth(scan)
        val channel = frequencyToChannel(scan.frequency)
        val is6GHz = scan.frequency in 5925..7125

        return DetectedDevice(
            address = scan.BSSID,
            name = if (scan.SSID.isNullOrBlank()) "Hidden Network" else scan.SSID,
            type = DetectedDevice.DeviceType.WIFI,
            rssi = scan.level,
            frequency = scan.frequency,
            timestamp = System.currentTimeMillis(),
            manufacturer = manufacturer,
            distanceMeters = distance,
            signalQuality = signalQuality,
            riskLevel = riskLevel,
            capabilities = scan.capabilities,
            channelWidth = channelWidth,
            channel = channel,
            is6GHz = is6GHz,
            signalStrength = scan.level
        )
    }

    private fun calculateDistance(rssi: Int, frequency: Int): Double {
        // Tx Power ajustado por frecuencia
        val txPower = when (frequency) {
            in 5000..6000 -> -25.0
            in 5925..7125 -> -23.0
            else -> -30.0
        }

        // Path loss exponent (mayor en frecuencias altas)
        val pathLossExponent = when {
            frequency > 5000 -> 4.2
            else -> 3.5
        }

        if (rssi >= 0 || rssi < -100) return 999.0

        val distance = 10.0.pow((txPower - rssi) / (10.0 * pathLossExponent))
        return distance.coerceIn(0.1, 200.0)
    }

    private fun assessRisk(
        scan: ScanResult,
        manufacturer: String
    ): DetectedDevice.RiskLevel {
        val mfg = manufacturer.lowercase()
        val ssid = scan.SSID?.lowercase() ?: ""
        val capabilities = scan.capabilities.lowercase()

        // CRITICAL: Cámaras de vigilancia
        if (mfg in listOf("hikvision", "dahua", "axis", "hanwha", "foscam")) {
            return DetectedDevice.RiskLevel.CRITICAL
        }

        // HIGH: Redes sin seguridad o WEP obsoleto
        if (capabilities.contains("wep") ||
            (!capabilities.contains("wpa") && !capabilities.contains("wep"))) {
            return DetectedDevice.RiskLevel.HIGH
        }

        // MEDIUM: IoT vulnerable o redes sospechosas
        if (mfg in listOf("espressif", "esp8266", "esp32") ||
            ssid.contains("esp") ||
            ssid.contains("free") ||
            ssid.contains("guest") ||
            ssid.isEmpty()) {
            return DetectedDevice.RiskLevel.MEDIUM
        }

        // LOW: WPA3 o redes enterprise seguras
        if (capabilities.contains("sae") ||
            mfg in listOf("cisco", "aruba", "ubiquiti", "ruckus")) {
            return DetectedDevice.RiskLevel.LOW
        }

        return DetectedDevice.RiskLevel.MEDIUM
    }

    private fun calculateSignalQuality(rssi: Int): Int {
        return ((rssi + 100) * 100 / 70).coerceIn(0, 100)
    }

    private fun getChannelWidth(scan: ScanResult): DetectedDevice.ChannelWidth {
        return when (scan.channelWidth) {
            ScanResult.CHANNEL_WIDTH_20MHZ -> DetectedDevice.ChannelWidth.MHZ_20
            ScanResult.CHANNEL_WIDTH_40MHZ -> DetectedDevice.ChannelWidth.MHZ_40
            ScanResult.CHANNEL_WIDTH_80MHZ -> DetectedDevice.ChannelWidth.MHZ_80
            ScanResult.CHANNEL_WIDTH_160MHZ -> DetectedDevice.ChannelWidth.MHZ_160
            else -> DetectedDevice.ChannelWidth.UNKNOWN
        }
    }

    private fun frequencyToChannel(freq: Int): Int {
        return when (freq) {
            in 2412..2484 -> (freq - 2407) / 5
            in 5170..5825 -> (freq - 5000) / 5
            in 5925..7125 -> (freq - 5950) / 5 + 1
            else -> 0
        }
    }

    fun stop() {
        scanReceiver?.let { context.unregisterReceiver(it) }
    }
}