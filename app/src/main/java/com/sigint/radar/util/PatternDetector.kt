package com.sigint.radar.util

import com.sigint.radar.database.RadarDatabase
import com.sigint.radar.database.entities.DevicePatternEntity
import com.sigint.radar.model.DetectedDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PatternDetector(private val database: RadarDatabase) {

    private val patternDao = database.devicePatternDao()

    /**
     * Detecta dispositivos que aparecen juntos frecuentemente
     */
    suspend fun detectCoOccurrences(devices: List<DetectedDevice>) = withContext(Dispatchers.IO) {
        val timestamp = System.currentTimeMillis()

        // Para cada par de dispositivos detectados simultáneamente
        for (i in devices.indices) {
            for (j in i + 1 until devices.size) {
                val device1 = devices[i]
                val device2 = devices[j]

                // Buscar patrón existente
                val existingPattern = patternDao.getPattern(device1.address, device2.address)

                if (existingPattern != null) {
                    // Actualizar patrón existente
                    val updatedPattern = existingPattern.copy(
                        coOccurrenceCount = existingPattern.coOccurrenceCount + 1,
                        lastSeen = timestamp,
                        confidence = calculateConfidence(existingPattern.coOccurrenceCount + 1, timestamp - existingPattern.firstSeen)
                    )
                    patternDao.update(updatedPattern)
                } else {
                    // Crear nuevo patrón
                    val newPattern = DevicePatternEntity(
                        device1Mac = device1.address,
                        device2Mac = device2.address,
                        coOccurrenceCount = 1,
                        firstSeen = timestamp,
                        lastSeen = timestamp,
                        confidence = 0.1f
                    )
                    patternDao.insert(newPattern)
                }
            }
        }
    }

    /**
     * Calcula la confianza de un patrón basado en frecuencia y tiempo
     */
    private fun calculateConfidence(occurrences: Int, timeSpanMillis: Long): Float {
        // Más ocurrencias = mayor confianza
        val occurrenceScore = (occurrences.toFloat() / 10f).coerceIn(0f, 1f)

        // Más tiempo observando = mayor confianza
        val daysObserved = timeSpanMillis / (24 * 60 * 60 * 1000f)
        val timeScore = (daysObserved / 30f).coerceIn(0f, 1f)

        return (occurrenceScore * 0.7f + timeScore * 0.3f).coerceIn(0f, 1f)
    }

    /**
     * Detecta posibles cambios de MAC address (dispositivos que cambian su dirección)
     */
    suspend fun detectMacRandomization(devices: List<DetectedDevice>): List<MacRandomizationCandidate> {
        val candidates = mutableListOf<MacRandomizationCandidate>()

        // Agrupar por fabricante y tipo
        val groupedDevices = devices.groupBy { "${it.manufacturer}_${it.type.name}" }

        groupedDevices.forEach { (_, deviceGroup) ->
            if (deviceGroup.size > 1) {
                // Múltiples MACs del mismo fabricante/tipo podrían indicar randomización
                val isRandomized = deviceGroup.any { device ->
                    // Verificar si la MAC tiene el bit de randomización activado
                    isMacRandomized(device.address)
                }

                if (isRandomized) {
                    candidates.add(
                        MacRandomizationCandidate(
                            manufacturer = deviceGroup.first().manufacturer,
                            deviceType = deviceGroup.first().type.name,
                            macAddresses = deviceGroup.map { it.address },
                            suspicionLevel = calculateRandomizationSuspicion(deviceGroup)
                        )
                    )
                }
            }
        }

        return candidates
    }

    private fun isMacRandomized(macAddress: String): Boolean {
        // El segundo bit del primer byte indica si es una MAC localmente administrada (randomizada)
        val firstByte = macAddress.split(":").firstOrNull()?.toIntOrNull(16) ?: return false
        return (firstByte and 0x02) != 0
    }

    private fun calculateRandomizationSuspicion(devices: List<DetectedDevice>): Float {
        val randomizedCount = devices.count { isMacRandomized(it.address) }
        return randomizedCount.toFloat() / devices.size
    }

    /**
     * Detecta beacons de tracking/publicidad sospechosos
     */
    suspend fun detectTrackingBeacons(devices: List<DetectedDevice>): List<DetectedDevice> {
        return devices.filter { device ->
            // Beacons con nombres genéricos o UUID de servicios conocidos de tracking
            device.type == DetectedDevice.DeviceType.BEACON ||
            (device.type == DetectedDevice.DeviceType.BLUETOOTH &&
             device.serviceUuids?.any { isTrackingService(it) } == true)
        }
    }

    private fun isTrackingService(uuid: String): Boolean {
        // UUIDs conocidos de servicios de tracking/publicidad
        val trackingUuids = listOf(
            "0000feaa", // Eddystone (Google)
            "0000fed8", // Tile tracking
            "0000180f", // Battery Service (común en beacons)
        )
        return trackingUuids.any { uuid.lowercase().contains(it) }
    }

    /**
     * Limpia patrones antiguos y débiles
     */
    suspend fun cleanupOldPatterns(daysOld: Int = 60) {
        val timestamp = System.currentTimeMillis() - (daysOld * 24 * 60 * 60 * 1000L)
        patternDao.cleanupWeakOldPatterns(timestamp)
    }
}

data class MacRandomizationCandidate(
    val manufacturer: String,
    val deviceType: String,
    val macAddresses: List<String>,
    val suspicionLevel: Float // 0-1
)

