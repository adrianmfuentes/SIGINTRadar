package com.sigint.radar.model

data class DetectedDevice(
    val address: String,
    val name: String,
    val type: DeviceType,
    val rssi: Int,
    val frequency: Int,
    val timestamp: Long,

    // Información extendida
    val manufacturer: String,
    val distanceMeters: Double,
    val signalQuality: Int, // 0-100
    val riskLevel: RiskLevel,

    // WiFi específico
    val capabilities: String? = null,
    val channelWidth: ChannelWidth? = null,
    val channel: Int? = null,
    val is6GHz: Boolean = false,

    // Bluetooth específico
    val txPower: Int? = null,
    val isBeacon: Boolean = false,
    val beaconType: BeaconType? = null,
    val serviceUuids: List<String>? = null,
    val signalStrength: Int
) {
    enum class DeviceType {
        WIFI, BLUETOOTH, BEACON
    }

    enum class RiskLevel(val color: Int, val displayName: String) {
        CRITICAL(0xFFFF0032.toInt(), "CRITICAL"),
        HIGH(0xFFFF3264.toInt(), "HIGH"),
        MEDIUM(0xFFFFAA00.toInt(), "MEDIUM"),
        LOW(0xFF00FF41.toInt(), "LOW")
    }

    enum class ChannelWidth {
        MHZ_20, MHZ_40, MHZ_80, MHZ_160, UNKNOWN
    }

    enum class BeaconType {
        IBEACON, EDDYSTONE_UID, EDDYSTONE_URL, ALTBEACON
    }

    // Cálculo de ángulo para el radar (basado en hash del BSSID)
    fun getRadarAngle(): Float {
        val hash = address.hashCode()
        return (hash % 360).toFloat()
    }

    // Distancia normalizada para el radar (0-1)
    fun getNormalizedDistance(maxDistance: Float = 50f): Float {
        return (distanceMeters / maxDistance).toFloat().coerceIn(0f, 1f)
    }

    // Calcular puntuación de riesgo (0-100)
    fun getRiskScore(): Int {
        var score = 0

        // Base score por nivel de riesgo
        score += when (riskLevel) {
            RiskLevel.CRITICAL -> 70
            RiskLevel.HIGH -> 50
            RiskLevel.MEDIUM -> 30
            RiskLevel.LOW -> 10
        }

        // Bonus por señal fuerte (cerca)
        if (rssi > -50) score += 15
        else if (rssi > -65) score += 10
        else if (rssi > -75) score += 5

        // Bonus por beacons
        if (isBeacon) score += 10

        // Bonus por fabricante desconocido
        if (manufacturer.contains("Unknown", ignoreCase = true)) score += 5

        return score.coerceIn(0, 100)
    }

    // Obtener IDs de recursos para factores de riesgo (para internacionalización)
    fun getRiskFactorIds(): List<Int> {
        val factors = mutableListOf<Int>()

        if (isBeacon) {
            factors.add(com.sigint.radar.R.string.risk_beacon_detected)
        }

        if (rssi > -50) {
            factors.add(com.sigint.radar.R.string.risk_close_proximity)
        }

        if (manufacturer.contains("Unknown", ignoreCase = true)) {
            factors.add(com.sigint.radar.R.string.risk_unknown_manufacturer)
        }

        if (type == DeviceType.WIFI && name.contains("<hidden>", ignoreCase = true)) {
            factors.add(com.sigint.radar.R.string.risk_hidden_network)
        }

        if (distanceMeters < 2.0) {
            factors.add(com.sigint.radar.R.string.risk_close_proximity)
        }

        return factors.distinct() // Eliminar duplicados
    }

    // Mantener compatibilidad con código existente (deprecated)
    @Deprecated("Use getRiskFactorIds() for internationalization")
    fun getRiskFactors(): List<String> {
        val factors = mutableListOf<String>()

        if (isBeacon) {
            factors.add("Tracking beacon detected")
        }

        if (rssi > -50) {
            factors.add("Very close proximity")
        }

        if (manufacturer.contains("Unknown", ignoreCase = true)) {
            factors.add("Unknown manufacturer")
        }

        when (riskLevel) {
            RiskLevel.CRITICAL -> factors.add("Critical security risk")
            RiskLevel.HIGH -> factors.add("High security concern")
            RiskLevel.MEDIUM -> factors.add("Moderate risk level")
            RiskLevel.LOW -> {}
        }

        if (type == DeviceType.WIFI && channelWidth == ChannelWidth.MHZ_160) {
            factors.add("Advanced capabilities")
        }

        if (distanceMeters < 2.0) {
            factors.add("Extremely close range")
        }

        return factors
    }
}