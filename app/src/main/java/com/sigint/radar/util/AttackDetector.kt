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

    /**
     * Detecta posibles ataques Karma/Jasager
     * Estos ataques responden a TODOS los probe requests con SSIDs falsos
     */
    fun detectKarmaJasagerAttacks(devices: List<DetectedDevice>): List<KarmaAttackCandidate> {
        val wifiDevices = devices.filter { it.type == DetectedDevice.DeviceType.WIFI }
        val candidates = mutableListOf<KarmaAttackCandidate>()

        // Agrupar por MAC para detectar APs que responden a múltiples SSIDs
        val devicesByMac = wifiDevices.groupBy { it.address }

        devicesByMac.forEach { (mac, aps) ->
            if (aps.size > 1) {
                // Mismo MAC, diferentes SSIDs = muy sospechoso
                val suspicionFactors = mutableListOf<String>()

                // Factor 1: Múltiples SSIDs desde la misma MAC
                suspicionFactors.add("Same MAC responding to ${aps.size} different SSIDs")

                // Factor 2: SSIDs genéricos comunes
                val genericSsids = aps.filter { ap ->
                    ap.name.lowercase() in listOf(
                        "home", "guest", "wifi", "free wifi", "public",
                        "linksys", "default", "netgear", "dlink"
                    )
                }
                if (genericSsids.isNotEmpty()) {
                    suspicionFactors.add("${genericSsids.size} generic/common SSIDs detected")
                }

                // Factor 3: Canales muy diferentes
                val channels = aps.mapNotNull { it.channel }.distinct()
                if (channels.size > 2) {
                    suspicionFactors.add("AP jumping between ${channels.size} different channels")
                }

                // Factor 4: Sin seguridad (open networks)
                val openNetworks = aps.filter { ap ->
                    ap.capabilities?.let { !it.contains("WPA") && !it.contains("WEP") } ?: false
                }
                if (openNetworks.size == aps.size) {
                    suspicionFactors.add("All networks are OPEN (no encryption)")
                }

                if (suspicionFactors.size >= 2) {
                    candidates.add(
                        KarmaAttackCandidate(
                            macAddress = mac,
                            ssids = aps.map { it.name },
                            suspicionFactors = suspicionFactors,
                            suspicionLevel = (suspicionFactors.size.toFloat() / 4f).coerceIn(0f, 1f)
                        )
                    )
                }
            }
        }

        return candidates.sortedByDescending { it.suspicionLevel }
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

data class KarmaAttackCandidate(
    val macAddress: String,
    val ssids: List<String>,
    val suspicionFactors: List<String>,
    val suspicionLevel: Float
)

