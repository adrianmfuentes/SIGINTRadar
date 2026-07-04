package com.sigint.radar.repository

import com.sigint.radar.database.RadarDatabase
import com.sigint.radar.database.entities.KnownDeviceEntity
import com.sigint.radar.model.DetectedDevice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

enum class TrustLevel {
    TRUSTED,    // Dispositivo confiable (ej: tu router, tu teléfono)
    SUSPICIOUS, // Sospechoso, requiere atención
    BLOCKED,    // Bloqueado/Peligroso conocido
    UNKNOWN     // Desconocido, neutral
}

class KnownDeviceRepository(private val database: RadarDatabase) {

    private val knownDeviceDao = database.knownDeviceDao()

    fun getAllKnownDevices(): Flow<List<KnownDeviceEntity>> =
        knownDeviceDao.getAllKnownDevices()

    fun getDevicesByTrustLevel(trustLevel: TrustLevel): Flow<List<KnownDeviceEntity>> =
        knownDeviceDao.getDevicesByTrustLevel(trustLevel.name)

    suspend fun getKnownDevice(macAddress: String): KnownDeviceEntity? =
        knownDeviceDao.getKnownDevice(macAddress)

    fun getKnownDeviceFlow(macAddress: String): Flow<KnownDeviceEntity?> =
        knownDeviceDao.getKnownDeviceFlow(macAddress)

    suspend fun addOrUpdateDevice(
        device: DetectedDevice,
        trustLevel: TrustLevel = TrustLevel.UNKNOWN,
        customName: String? = null,
        notes: String? = null,
        alertEnabled: Boolean = false
    ): Long {
        val timestamp = System.currentTimeMillis()
        val existing = knownDeviceDao.getKnownDevice(device.address)

        return if (existing != null) {
            // Actualizar dispositivo existente
            val updated = existing.copy(
                customName = customName ?: existing.customName,
                trustLevel = trustLevel.name,
                notes = notes ?: existing.notes,
                lastSeen = timestamp,
                seenCount = existing.seenCount + 1,
                alertEnabled = alertEnabled
            )
            knownDeviceDao.update(updated)
            existing.id
        } else {
            // Insertar nuevo dispositivo
            val newDevice = KnownDeviceEntity(
                macAddress = device.address,
                customName = customName,
                deviceType = device.type.name,
                trustLevel = trustLevel.name,
                notes = notes,
                firstSeen = timestamp,
                lastSeen = timestamp,
                seenCount = 1,
                manufacturer = device.manufacturer,
                alertEnabled = alertEnabled
            )
            knownDeviceDao.insert(newDevice)
        }
    }

    suspend fun updateTrustLevel(macAddress: String, trustLevel: TrustLevel) {
        val device = knownDeviceDao.getKnownDevice(macAddress)
        device?.let {
            knownDeviceDao.update(it.copy(trustLevel = trustLevel.name))
        }
    }

    suspend fun updateCustomName(macAddress: String, customName: String) {
        val device = knownDeviceDao.getKnownDevice(macAddress)
        device?.let {
            knownDeviceDao.update(it.copy(customName = customName))
        }
    }

    suspend fun updateNotes(macAddress: String, notes: String) {
        val device = knownDeviceDao.getKnownDevice(macAddress)
        device?.let {
            knownDeviceDao.update(it.copy(notes = notes))
        }
    }

    suspend fun setAlertEnabled(macAddress: String, enabled: Boolean) {
        val device = knownDeviceDao.getKnownDevice(macAddress)
        device?.let {
            knownDeviceDao.update(it.copy(alertEnabled = enabled))
        }
    }

    suspend fun deleteDevice(device: KnownDeviceEntity) {
        knownDeviceDao.delete(device)
    }

    suspend fun updateLastSeen(macAddress: String) {
        knownDeviceDao.updateLastSeen(macAddress, System.currentTimeMillis())
    }

    fun getDevicesWithAlerts(): Flow<List<KnownDeviceEntity>> =
        knownDeviceDao.getDevicesWithAlerts()

    suspend fun getStatistics(): DeviceStatistics {
        return DeviceStatistics(
            trusted = knownDeviceDao.getCountByTrustLevel(TrustLevel.TRUSTED.name),
            suspicious = knownDeviceDao.getCountByTrustLevel(TrustLevel.SUSPICIOUS.name),
            blocked = knownDeviceDao.getCountByTrustLevel(TrustLevel.BLOCKED.name),
            unknown = knownDeviceDao.getCountByTrustLevel(TrustLevel.UNKNOWN.name)
        )
    }

    suspend fun cleanupOldUnknownDevices(daysOld: Int = 30) {
        val timestamp = System.currentTimeMillis() - (daysOld * 24 * 60 * 60 * 1000L)
        knownDeviceDao.cleanupOldUnknownDevices(timestamp)
    }

    /**
     * Exportar lista de dispositivos a formato importable
     */
    suspend fun exportDeviceList(): String {
        val devices = knownDeviceDao.getAllKnownDevices()
        val deviceList = devices.first()

        val jsonArray = JSONArray()
        deviceList.forEach { device ->
            val jsonDevice = JSONObject().apply {
                put("macAddress", device.macAddress)
                put("customName", device.customName)
                put("deviceType", device.deviceType)
                put("trustLevel", device.trustLevel)
                put("notes", device.notes)
                put("firstSeen", device.firstSeen)
                put("lastSeen", device.lastSeen)
                put("seenCount", device.seenCount)
                put("manufacturer", device.manufacturer)
                put("alertEnabled", device.alertEnabled)
            }
            jsonArray.put(jsonDevice)
        }

        val result = JSONObject()
        result.put("version", 1)
        result.put("exportDate", System.currentTimeMillis())
        result.put("devices", jsonArray)

        return result.toString(2)
    }

    private val macAddressRegex = Regex("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$")

    /**
     * Importar lista de dispositivos. Devuelve cuántos dispositivos se importaron
     * y cuántas filas inválidas se descartaron, en vez de fallar toda la importación
     * por una sola entrada corrupta.
     */
    suspend fun importDeviceList(jsonData: String): ImportResult {
        val root = try {
            JSONObject(jsonData)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid JSON: ${e.message}", e)
        }

        val version = root.optInt("version", 1)
        if (version != 1) {
            throw IllegalArgumentException("Unsupported import version: $version")
        }

        val devices = root.optJSONArray("devices")
            ?: throw IllegalArgumentException("Missing 'devices' array")

        var imported = 0
        var skipped = 0

        for (i in 0 until devices.length()) {
            val deviceJson = devices.optJSONObject(i)
            if (deviceJson == null) {
                skipped++
                continue
            }

            val macAddress = deviceJson.optString("macAddress")
            val trustLevelRaw = deviceJson.optString("trustLevel")
            val deviceType = deviceJson.optString("deviceType")

            val isValidMac = macAddressRegex.matches(macAddress)
            val isValidTrustLevel = TrustLevel.entries.any { it.name == trustLevelRaw }
            val isValidDeviceType = DetectedDevice.DeviceType.entries.any { it.name == deviceType }

            if (!isValidMac || !isValidTrustLevel || !isValidDeviceType) {
                skipped++
                continue
            }

            val device = KnownDeviceEntity(
                macAddress = macAddress,
                customName = deviceJson.optString("customName").takeIf { it.isNotEmpty() },
                deviceType = deviceType,
                trustLevel = trustLevelRaw,
                notes = deviceJson.optString("notes").takeIf { it.isNotEmpty() },
                firstSeen = deviceJson.optLong("firstSeen", System.currentTimeMillis()),
                lastSeen = deviceJson.optLong("lastSeen", System.currentTimeMillis()),
                seenCount = deviceJson.optInt("seenCount", 1),
                manufacturer = deviceJson.optString("manufacturer").takeIf { it.isNotEmpty() },
                alertEnabled = deviceJson.optBoolean("alertEnabled", false)
            )

            knownDeviceDao.insert(device)
            imported++
        }

        return ImportResult(imported, skipped)
    }
}

data class ImportResult(val imported: Int, val skipped: Int)

data class DeviceStatistics(
    val trusted: Int,
    val suspicious: Int,
    val blocked: Int,
    val unknown: Int
) {
    val total: Int get() = trusted + suspicious + blocked + unknown
}

