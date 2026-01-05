package com.sigint.radar.util

import com.sigint.radar.model.DetectedDevice

/**
 * Detector de ataques específicos de red
 */
class AttackDetector {

    /**
     * Detecta posibles Evil Twin APs (APs con mismo SSID)
     */
    fun detectEvilTwins(devices: List<DetectedDevice>): List<EvilTwinGroup> {
        val wifiDevices = devices.filter { it.type == DetectedDevice.DeviceType.WIFI }

        // Agrupar por nombre (SSID)
        val groupedByName = wifiDevices.groupBy { it.name }

        return groupedByName
            .filter { it.value.size > 1 } // Múltiples APs con mismo nombre
            .map { (ssid, aps) ->
                EvilTwinGroup(
                    ssid = ssid,
                    accessPoints = aps,
                    suspicionLevel = calculateEvilTwinSuspicion(aps)
                )
            }
            .filter { it.suspicionLevel > 0.3f } // Filtrar solo sospechosos
    }

    private fun calculateEvilTwinSuspicion(aps: List<DetectedDevice>): Float {
        var suspicion = 0f

        // Diferentes fabricantes = más sospechoso
        val manufacturers = aps.map { it.manufacturer }.distinct()
        if (manufacturers.size > 1) {
            suspicion += 0.4f
        }

        // Diferentes canales = más sospechoso
        val channels = aps.mapNotNull { it.channel }.distinct()
        if (channels.size > 1) {
            suspicion += 0.3f
        }

        // Señales muy diferentes = sospechoso
        val rssiRange = aps.maxOf { it.rssi } - aps.minOf { it.rssi }
        if (rssiRange > 30) {
            suspicion += 0.3f
        }

        return suspicion.coerceIn(0f, 1f)
    }

    /**
     * Detecta posibles Rogue Access Points
     */
    fun detectRogueAPs(devices: List<DetectedDevice>, trustedMacs: Set<String>): List<DetectedDevice> {
        return devices.filter { device ->
            device.type == DetectedDevice.DeviceType.WIFI &&
            device.address !in trustedMacs &&
            isRogueAPCandidate(device)
        }
    }

    private fun isRogueAPCandidate(device: DetectedDevice): Boolean {
        // Características sospechosas de un rogue AP
        val suspiciousFactors = mutableListOf<Boolean>()

        // Nombre genérico o vacío
        suspiciousFactors.add(
            device.name.isBlank() ||
            device.name.lowercase() in listOf("free wifi", "public", "guest", "test")
        )

        // Sin encriptación o encriptación débil
        device.capabilities?.let { cap ->
            suspiciousFactors.add(
                !cap.contains("WPA") && !cap.contains("WPA2") && !cap.contains("WPA3")
            )
        }

        // Señal muy fuerte (muy cerca, sospechoso en algunos contextos)
        suspiciousFactors.add(device.rssi > -30)

        return suspiciousFactors.count { it } >= 2
    }

    /**
     * Detecta patrones de deauthentication attack
     * (difícil de detectar sin monitoreo pasivo de paquetes, pero podemos detectar indicios)
     */
    fun detectDeauthPatterns(
        currentDevices: List<DetectedDevice>,
        previousDevices: List<DetectedDevice>
    ): DeauthSuspicion? {
        // Detectar caída súbita de dispositivos WiFi
        val currentWifi = currentDevices.filter { it.type == DetectedDevice.DeviceType.WIFI }
        val previousWifi = previousDevices.filter { it.type == DetectedDevice.DeviceType.WIFI }

        if (previousWifi.isEmpty()) return null

        val dropRate = 1f - (currentWifi.size.toFloat() / previousWifi.size)

        // Si más del 50% de dispositivos desaparecen súbitamente
        if (dropRate > 0.5f) {
            return DeauthSuspicion(
                droppedDeviceCount = previousWifi.size - currentWifi.size,
                dropRate = dropRate,
                suspicionLevel = (dropRate * 2).coerceIn(0f, 1f)
            )
        }

        return null
    }

    /**
     * Detecta posibles Bluetooth sniffers o ataques
     */
    fun detectBluetoothThreats(devices: List<DetectedDevice>): List<BluetoothThreat> {
        val threats = mutableListOf<BluetoothThreat>()

        devices.filter { it.type == DetectedDevice.DeviceType.BLUETOOTH }.forEach { device ->
            val threatFactors = mutableListOf<String>()

            // Nombre sospechoso
            if (device.name.lowercase() in listOf("btlejack", "ubertooth", "blue hydra", "hcitool")) {
                threatFactors.add("Suspicious name (known BT attack tool)")
            }

            // Múltiples servicios inusuales
            device.serviceUuids?.let { uuids ->
                if (uuids.size > 10) {
                    threatFactors.add("Unusual number of services (${uuids.size})")
                }
            }

            // Señal muy fuerte
            if (device.rssi > -20) {
                threatFactors.add("Unusually strong signal (device very close)")
            }

            if (threatFactors.isNotEmpty()) {
                threats.add(
                    BluetoothThreat(
                        device = device,
                        threatFactors = threatFactors,
                        severityLevel = calculateBluetoothThreatSeverity(threatFactors)
                    )
                )
            }
        }

        return threats
    }

    private fun calculateBluetoothThreatSeverity(factors: List<String>): Float {
        return (factors.size.toFloat() / 5f).coerceIn(0f, 1f)
    }

    /**
     * Detecta dispositivos con capacidades de monitor mode (pueden capturar tráfico)
     */
    fun detectMonitorModeDevices(devices: List<DetectedDevice>): List<DetectedDevice> {
        return devices.filter { device ->
            device.manufacturer.lowercase().let { mfr ->
                mfr.contains("alfa") || // Alfa cards conocidas por monitor mode
                mfr.contains("panda") || // Panda wireless
                mfr.contains("realtek") && device.name.contains("RTL", ignoreCase = true)
            }
        }
    }
}

data class EvilTwinGroup(
    val ssid: String,
    val accessPoints: List<DetectedDevice>,
    val suspicionLevel: Float
)

data class DeauthSuspicion(
    val droppedDeviceCount: Int,
    val dropRate: Float,
    val suspicionLevel: Float
)

data class BluetoothThreat(
    val device: DetectedDevice,
    val threatFactors: List<String>,
    val severityLevel: Float
)

