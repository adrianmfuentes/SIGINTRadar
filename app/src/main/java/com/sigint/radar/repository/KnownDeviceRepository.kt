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

    /**
     * Importar lista de dispositivos
     */
    suspend fun importDeviceList(jsonData: String) {
        try {
            val root = JSONObject(jsonData)
            val version = root.optInt("version", 1)

            if (version != 1) {
                throw IllegalArgumentException("Unsupported import version: $version")
            }

            val devices = root.getJSONArray("devices")

            for (i in 0 until devices.length()) {
                val deviceJson = devices.getJSONObject(i)

                val device = KnownDeviceEntity(
                    macAddress = deviceJson.getString("macAddress"),
                    customName = deviceJson.optString("customName").takeIf { it.isNotEmpty() },
                    deviceType = deviceJson.getString("deviceType"),
                    trustLevel = deviceJson.getString("trustLevel"),
                    notes = deviceJson.optString("notes").takeIf { it.isNotEmpty() },
                    firstSeen = deviceJson.getLong("firstSeen"),
                    lastSeen = deviceJson.getLong("lastSeen"),
                    seenCount = deviceJson.getInt("seenCount"),
                    manufacturer = deviceJson.optString("manufacturer").takeIf { it.isNotEmpty() },
                    alertEnabled = deviceJson.getBoolean("alertEnabled")
                )

                knownDeviceDao.insert(device)
            }
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to import device list: ${e.message}", e)
        }
    }
}

data class DeviceStatistics(
    val trusted: Int,
    val suspicious: Int,
    val blocked: Int,
    val unknown: Int
) {
    val total: Int get() = trusted + suspicious + blocked + unknown
}

