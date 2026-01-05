package com.sigint.radar

import com.sigint.radar.model.DetectedDevice

object MockDataProvider {

    fun getMockDevices(): List<DetectedDevice> {
        return listOf(
            // WiFi - Router sospechoso cercano
            DetectedDevice(
                address = "00:11:22:33:44:55",
                name = "RedSospechosa_01",
                type = DetectedDevice.DeviceType.WIFI,
                rssi = -45,
                frequency = 2437,
                timestamp = System.currentTimeMillis(),
                manufacturer = "Unknown Manufacturer",
                distanceMeters = 3.5,
                signalQuality = 85,
                riskLevel = DetectedDevice.RiskLevel.HIGH,
                capabilities = "[WPA2-PSK-CCMP][ESS]",
                channelWidth = DetectedDevice.ChannelWidth.MHZ_40,
                channel = 6,
                signalStrength = -45
            ),

            // WiFi - Red normal lejana
            DetectedDevice(
                address = "AA:BB:CC:DD:EE:FF",
                name = "WiFi_Vecino_2G",
                type = DetectedDevice.DeviceType.WIFI,
                rssi = -68,
                frequency = 2462,
                timestamp = System.currentTimeMillis(),
                manufacturer = "TP-Link",
                distanceMeters = 12.0,
                signalQuality = 45,
                riskLevel = DetectedDevice.RiskLevel.LOW,
                capabilities = "[WPA2-PSK-CCMP][ESS]",
                channelWidth = DetectedDevice.ChannelWidth.MHZ_20,
                channel = 11,
                signalStrength = -68
            ),

            // WiFi - Red 5GHz cercana
            DetectedDevice(
                address = "11:22:33:44:55:66",
                name = "Router_5G_Test",
                type = DetectedDevice.DeviceType.WIFI,
                rssi = -52,
                frequency = 5180,
                timestamp = System.currentTimeMillis(),
                manufacturer = "Cisco Systems",
                distanceMeters = 5.2,
                signalQuality = 70,
                riskLevel = DetectedDevice.RiskLevel.MEDIUM,
                capabilities = "[WPA2-PSK-CCMP][ESS]",
                channelWidth = DetectedDevice.ChannelWidth.MHZ_80,
                channel = 36,
                signalStrength = -52
            ),

            // Bluetooth - Dispositivo de tracking
            DetectedDevice(
                address = "DE:AD:BE:EF:00:01",
                name = "BLE_Tracker",
                type = DetectedDevice.DeviceType.BEACON,
                rssi = -55,
                frequency = 2402,
                timestamp = System.currentTimeMillis(),
                manufacturer = "Unknown",
                distanceMeters = 4.8,
                signalQuality = 65,
                riskLevel = DetectedDevice.RiskLevel.CRITICAL,
                txPower = -59,
                isBeacon = true,
                beaconType = DetectedDevice.BeaconType.IBEACON,
                serviceUuids = listOf("0000180F-0000-1000-8000-00805F9B34FB"),
                signalStrength = -55
            ),

            // Bluetooth - Auriculares normales
            DetectedDevice(
                address = "12:34:56:78:9A:BC",
                name = "Sony WH-1000XM4",
                type = DetectedDevice.DeviceType.BLUETOOTH,
                rssi = -62,
                frequency = 2402,
                timestamp = System.currentTimeMillis(),
                manufacturer = "Sony",
                distanceMeters = 8.5,
                signalQuality = 55,
                riskLevel = DetectedDevice.RiskLevel.LOW,
                txPower = -4,
                serviceUuids = listOf("0000110B-0000-1000-8000-00805F9B34FB"),
                signalStrength = -62
            ),

            // WiFi - Red oculta sospechosa
            DetectedDevice(
                address = "FF:EE:DD:CC:BB:AA",
                name = "<hidden>",
                type = DetectedDevice.DeviceType.WIFI,
                rssi = -40,
                frequency = 5240,
                timestamp = System.currentTimeMillis(),
                manufacturer = "Unknown",
                distanceMeters = 2.1,
                signalQuality = 92,
                riskLevel = DetectedDevice.RiskLevel.CRITICAL,
                capabilities = "[WPA2-PSK-CCMP][ESS]",
                channelWidth = DetectedDevice.ChannelWidth.MHZ_160,
                channel = 48,
                signalStrength = -40
            ),

            // Bluetooth - Beacon de tienda
            DetectedDevice(
                address = "BA:BE:CA:FE:00:02",
                name = "Eddystone",
                type = DetectedDevice.DeviceType.BEACON,
                rssi = -75,
                frequency = 2402,
                timestamp = System.currentTimeMillis(),
                manufacturer = "Google",
                distanceMeters = 18.3,
                signalQuality = 28,
                riskLevel = DetectedDevice.RiskLevel.MEDIUM,
                txPower = -8,
                isBeacon = true,
                beaconType = DetectedDevice.BeaconType.EDDYSTONE_URL,
                serviceUuids = listOf("0000FEAA-0000-1000-8000-00805F9B34FB"),
                signalStrength = -75
            ),

            // WiFi - Punto de acceso móvil
            DetectedDevice(
                address = "AB:CD:EF:12:34:56",
                name = "iPhone de Juan",
                type = DetectedDevice.DeviceType.WIFI,
                rssi = -58,
                frequency = 2412,
                timestamp = System.currentTimeMillis(),
                manufacturer = "Apple",
                distanceMeters = 7.2,
                signalQuality = 60,
                riskLevel = DetectedDevice.RiskLevel.LOW,
                capabilities = "[WPA2-PSK-CCMP][ESS]",
                channelWidth = DetectedDevice.ChannelWidth.MHZ_20,
                channel = 1,
                signalStrength = -58
            )
        )
    }
}
