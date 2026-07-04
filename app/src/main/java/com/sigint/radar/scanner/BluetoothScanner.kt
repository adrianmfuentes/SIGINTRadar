package com.sigint.radar.scanner

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import com.sigint.radar.model.DetectedDevice
import com.sigint.radar.util.OuiDatabase
import kotlin.math.pow
import androidx.core.util.isNotEmpty
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class BluetoothScanner(private val context: Context) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE)
            as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val bleScanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner

    private val devices = mutableMapOf<String, DetectedDevice>()
    private val scannedDevices = mutableListOf<DetectedDevice>()

    @SuppressLint("MissingPermission")
    suspend fun scan(): List<DetectedDevice> {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            return emptyList()
        }

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            return emptyList()
        }

        scannedDevices.clear()

        return suspendCancellableCoroutine { continuation ->
            val scanCallback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    val device = enhanceDevice(result)
                    scannedDevices.add(device)
                }

                override fun onScanFailed(errorCode: Int) {
                    continuation.resume(emptyList())
                }
            }

            bleScanner?.startScan(scanCallback)

            // Detener escaneo después de 1.5 segundos
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                bleScanner?.stopScan(scanCallback)
                continuation.resume(scannedDevices.toList())
            }, 1500)

            continuation.invokeOnCancellation {
                bleScanner?.stopScan(scanCallback)
            }
        }
    }


    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun enhanceDevice(result: ScanResult): DetectedDevice {
        val manufacturer = getManufacturer(result)
        val distance = calculateDistance(result)
        val riskLevel = assessRisk(result)
        val isBeacon = isBeacon(result)
        val beaconType = if (isBeacon) detectBeaconType(result) else null

        return DetectedDevice(
            address = result.device.address,
            name = result.scanRecord?.deviceName ?: result.device.name ?: "Unknown",
            type = if (isBeacon) DetectedDevice.DeviceType.BEACON
            else DetectedDevice.DeviceType.BLUETOOTH,
            rssi = result.rssi,
            frequency = 2400, // BLE usa ~2.4GHz
            timestamp = System.currentTimeMillis(),
            manufacturer = manufacturer,
            distanceMeters = distance,
            signalQuality = calculateSignalQuality(result.rssi),
            riskLevel = riskLevel,
            txPower = result.scanRecord?.txPowerLevel,
            isBeacon = isBeacon,
            beaconType = beaconType,
            serviceUuids = result.scanRecord?.serviceUuids?.map { it.toString() },
            signalStrength = result.rssi
        )
    }

    private fun calculateDistance(result: ScanResult): Double {
        val txPower = result.scanRecord?.txPowerLevel ?: -59
        val rssi = result.rssi.toDouble()

        if (rssi == 0.0) return 999.0

        val ratio = rssi / txPower

        return if (ratio < 1.0) {
            ratio.pow(10.0)
        } else {
            0.89976 * ratio.pow(7.7095) + 0.111
        }.coerceIn(0.1, 100.0)
    }

    private fun getManufacturer(result: ScanResult): String {
        val manufacturerData = result.scanRecord?.manufacturerSpecificData
        if (manufacturerData != null && manufacturerData.isNotEmpty()) {
            val companyId = manufacturerData.keyAt(0)
            return when (companyId) {
                0x004C -> "Apple"
                0x0006 -> "Microsoft"
                0x00E0 -> "Google"
                0x0075 -> "Samsung"
                0x0157 -> "Xiaomi"
                else -> OuiDatabase.lookup(result.device.address)
            }
        }
        return OuiDatabase.lookup(result.device.address)
    }

    private fun assessRisk(result: ScanResult): DetectedDevice.RiskLevel {
        val name = result.scanRecord?.deviceName?.lowercase() ?: ""

        // CRITICAL: Cámaras ocultas o dispositivos de espionaje
        if (name.contains("spy") || name.contains("hidden") || name.contains("cam")) {
            return DetectedDevice.RiskLevel.CRITICAL
        }

        // HIGH: Dispositivos de rastreo (AirTags, Tiles)
        if (name.contains("tile") || name.contains("tag") ||
            name.contains("tracker") || isBeacon(result)) {
            return DetectedDevice.RiskLevel.HIGH
        }

        // MEDIUM: Beacons genéricos
        if (isBeacon(result)) {
            return DetectedDevice.RiskLevel.MEDIUM
        }

        return DetectedDevice.RiskLevel.LOW
    }

    private fun isBeacon(result: ScanResult): Boolean {
        val data = result.scanRecord?.bytes ?: return false

        // iBeacon signature
        if (data.size > 25 && data[0] == 0x02.toByte() && data[1] == 0x15.toByte()) {
            return true
        }

        // Eddystone
        val serviceUuids = result.scanRecord?.serviceUuids ?: emptyList()
        return serviceUuids.any {
            it.toString().contains("0000FEAA", ignoreCase = true)
        }
    }

    private fun detectBeaconType(result: ScanResult): DetectedDevice.BeaconType? {
        val data = result.scanRecord?.bytes ?: return null

        // iBeacon
        if (data.size > 25 && data[0] == 0x02.toByte() && data[1] == 0x15.toByte()) {
            return DetectedDevice.BeaconType.IBEACON
        }

        // Eddystone
        val serviceUuids = result.scanRecord?.serviceUuids ?: emptyList()
        if (serviceUuids.any { it.toString().contains("0000FEAA", ignoreCase = true) }) {
            val serviceData = result.scanRecord?.serviceData
            val eddystoneData = serviceData?.values?.firstOrNull()

            if (eddystoneData != null && eddystoneData.isNotEmpty()) {
                return when (eddystoneData[0]) {
                    0x00.toByte() -> DetectedDevice.BeaconType.EDDYSTONE_UID
                    0x10.toByte() -> DetectedDevice.BeaconType.EDDYSTONE_URL
                    else -> null
                }
            }
        }

        return null
    }

    private fun calculateSignalQuality(rssi: Int): Int {
        return ((rssi + 100) * 100 / 70).coerceIn(0, 100)
    }

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    fun stop() {
        // Cleanup si es necesario
    }
}