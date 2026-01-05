package com.sigint.radar.util

object OuiDatabase {

    private val database = mapOf(
        // Cámaras de vigilancia (CRITICAL)
        "B09575" to "Hikvision",
        "001212" to "Dahua Technology",
        "00408C" to "Axis Communications",
        "001D42" to "Hanwha Techwin",
        "C008E9" to "Foscam",

        // Fabricantes principales
        "3C15C2" to "Apple",
        "001A11" to "Google",
        "0050F2" to "Microsoft",
        "44650D" to "Amazon",
        "001788" to "Philips",
        "7854D6" to "Xiaomi",
        "2CF0A2" to "Xiaomi",
        "009EC8" to "Samsung",
        "0018AF" to "Samsung",
        "E84E06" to "Samsung",

        // Networking equipment
        "000E58" to "Cisco",
        "001D45" to "Cisco",
        "00259C" to "Cisco",
        "24DE6C" to "Ubiquiti",
        "68D79A" to "Ubiquiti",
        "F09FC2" to "Ubiquiti",
        "D85D4C" to "TP-Link",
        "0C806C" to "D-Link",
        "001B11" to "D-Link",
        "14DAE9" to "Aruba",
        "000B86" to "Aruba",
        "9047AD" to "Ruckus Wireless",

        // IoT vulnerable
        "ACCF23" to "Espressif (ESP32)",
        "807D3A" to "Espressif (ESP8266)",
        "24:0A:C4" to "Espressif",
        "A020A6" to "Espressif",
        "3C71BF" to "Espressif",

        // Dispositivos comunes
        "001DC9" to "Sonos",
        "5CF938" to "Sonos",
        "B827EB" to "Raspberry Pi",
        "DC:A6:32" to "Raspberry Pi",
        "E45F01" to "Raspberry Pi",
        "54D1A7" to "Amazon Echo",
        "4CEFC0" to "Amazon Echo",
        "F0D2F1" to "Nest",
        "18B430" to "Nest",

        // Wearables & trackers
        "404E36" to "Apple Watch",
        "A45046" to "Apple AirTag",
        "E8B2AC" to "Tile",
        "788B2A" to "Fitbit",
        "D03972" to "Fitbit",

        // Teléfonos
        "C89CDC" to "iPhone",
        "D48564" to "iPhone",
        "8C5877" to "iPhone",
        "98D6F7" to "OnePlus",
        "AC37C3" to "OnePlus",
        "1C91C0" to "Huawei",
        "20F170" to "Huawei"
    )

    /**
     * Busca el fabricante a partir de la dirección MAC
     * @param mac Dirección MAC completa (XX:XX:XX:XX:XX:XX) o solo OUI (XX:XX:XX)
     * @return Nombre del fabricante o "Unknown"
     */
    fun lookup(mac: String): String {
        val oui = mac
            .replace(":", "")
            .replace("-", "")
            .replace(".", "")
            .take(6)
            .uppercase()

        return database[oui] ?: "Unknown"
    }

    /**
     * Verifica si un dispositivo es una cámara de vigilancia
     */
    fun isSurveillanceCamera(mac: String): Boolean {
        val manufacturer = lookup(mac).lowercase()
        return manufacturer in listOf(
            "hikvision", "dahua", "axis", "hanwha", "foscam"
        )
    }

    /**
     * Verifica si un dispositivo es IoT vulnerable
     */
    fun isVulnerableIoT(mac: String): Boolean {
        val manufacturer = lookup(mac).lowercase()
        return manufacturer.contains("espressif") ||
                manufacturer.contains("esp8266") ||
                manufacturer.contains("esp32")
    }

    /**
     * Obtiene categoría del dispositivo
     */
    fun getCategory(mac: String): DeviceCategory {
        val manufacturer = lookup(mac).lowercase()

        return when {
            manufacturer in listOf("hikvision", "dahua", "axis", "hanwha", "foscam")
                -> DeviceCategory.SURVEILLANCE

            manufacturer.contains("espressif") || manufacturer.contains("esp")
                -> DeviceCategory.IOT

            manufacturer in listOf("cisco", "aruba", "ubiquiti", "ruckus")
                -> DeviceCategory.NETWORKING

            manufacturer in listOf("apple", "samsung", "huawei", "oneplus", "xiaomi")
                -> DeviceCategory.MOBILE

            manufacturer.contains("raspberry") || manufacturer.contains("arduino")
                -> DeviceCategory.DEVELOPMENT

            manufacturer in listOf("tile", "airtag") || manufacturer.contains("tracker")
                -> DeviceCategory.TRACKER

            else -> DeviceCategory.OTHER
        }
    }

    enum class DeviceCategory {
        SURVEILLANCE,
        IOT,
        NETWORKING,
        MOBILE,
        DEVELOPMENT,
        TRACKER,
        OTHER
    }
}