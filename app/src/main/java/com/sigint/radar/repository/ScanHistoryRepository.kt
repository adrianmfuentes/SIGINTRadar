package com.sigint.radar.repository

import com.sigint.radar.database.RadarDatabase
import com.sigint.radar.database.entities.ScanHistoryEntity
import com.sigint.radar.database.entities.DeviceHistoryEntity
import com.sigint.radar.model.DetectedDevice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull

class ScanHistoryRepository(private val database: RadarDatabase) {

    private val scanDao = database.scanHistoryDao()
    private val deviceDao = database.deviceHistoryDao()

    fun getAllScans(): Flow<List<ScanHistoryEntity>> = scanDao.getAllScans()

    fun getRecentScans(limit: Int = 10): Flow<List<ScanHistoryEntity>> =
        scanDao.getRecentScans(limit)

    suspend fun saveScan(
        devices: List<DetectedDevice>,
        location: String? = null,
        notes: String? = null
    ): Long {
        val timestamp = System.currentTimeMillis()

        val scan = ScanHistoryEntity(
            timestamp = timestamp,
            totalDevices = devices.size,
            wifiCount = devices.count { it.type == DetectedDevice.DeviceType.WIFI },
            bluetoothCount = devices.count {
                it.type == DetectedDevice.DeviceType.BLUETOOTH ||
                it.type == DetectedDevice.DeviceType.BEACON
            },
            criticalCount = devices.count { it.riskLevel == DetectedDevice.RiskLevel.CRITICAL },
            highCount = devices.count { it.riskLevel == DetectedDevice.RiskLevel.HIGH },
            mediumCount = devices.count { it.riskLevel == DetectedDevice.RiskLevel.MEDIUM },
            lowCount = devices.count { it.riskLevel == DetectedDevice.RiskLevel.LOW },
            location = location,
            notes = notes
        )

        val scanId = scanDao.insert(scan)

        // Guardar dispositivos
        val deviceEntities = devices.map { device ->
            DeviceHistoryEntity(
                scanId = scanId,
                macAddress = device.address,
                name = device.name,
                deviceType = device.type.name,
                manufacturer = device.manufacturer,
                rssi = device.rssi,
                distanceMeters = device.distanceMeters.toFloat(),
                riskLevel = device.riskLevel.name,
                riskScore = device.getRiskScore(),
                signalQuality = device.signalQuality,
                timestamp = timestamp,
                channel = device.channel,
                frequency = device.frequency,
                channelWidth = device.channelWidth?.name,
                capabilities = device.capabilities,
                is6GHz = device.is6GHz,
                txPower = device.txPower,
                serviceUuids = device.serviceUuids?.joinToString(","),
                beaconType = device.beaconType?.name
            )
        }

        deviceDao.insertAll(deviceEntities)

        return scanId
    }

    suspend fun getScanById(scanId: Long): ScanHistoryEntity? = scanDao.getScanById(scanId)

    fun getDevicesByScan(scanId: Long): Flow<List<DeviceHistoryEntity>> =
        deviceDao.getDevicesByScan(scanId)

    suspend fun deleteScan(scan: ScanHistoryEntity) {
        deviceDao.deleteDevicesByScan(scan.id)
        scanDao.delete(scan)
    }

    suspend fun deleteOldScans(daysOld: Int = 30): Int {
        val timestampBefore = System.currentTimeMillis() - (daysOld * 24 * 60 * 60 * 1000L)
        return scanDao.deleteOlderThan(timestampBefore)
    }

    /**
     * Compara dos escaneos y devuelve dispositivos nuevos, desaparecidos y comunes
     */
    suspend fun compareScans(scan1Id: Long, scan2Id: Long): ScanComparison {
        val devices1 = deviceDao.getDevicesByScan(scan1Id).firstOrNull() ?: emptyList()
        val devices2 = deviceDao.getDevicesByScan(scan2Id).firstOrNull() ?: emptyList()

        val macs1 = devices1.map { it.macAddress }.toSet()
        val macs2 = devices2.map { it.macAddress }.toSet()

        val newDevices = devices2.filter { it.macAddress !in macs1 }
        val disappearedDevices = devices1.filter { it.macAddress !in macs2 }
        val commonDevices = devices2.filter { it.macAddress in macs1 }

        return ScanComparison(
            newDevices = newDevices,
            disappearedDevices = disappearedDevices,
            commonDevices = commonDevices
        )
    }
}

data class ScanComparison(
    val newDevices: List<DeviceHistoryEntity>,
    val disappearedDevices: List<DeviceHistoryEntity>,
    val commonDevices: List<DeviceHistoryEntity>
)


