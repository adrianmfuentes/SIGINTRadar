package com.sigint.radar.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "device_history",
    foreignKeys = [
        ForeignKey(
            entity = ScanHistoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["scanId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("scanId"), Index("macAddress")]
)
data class DeviceHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val scanId: Long,
    val macAddress: String,
    val name: String,
    val deviceType: String, // WIFI, BLUETOOTH, BEACON
    val manufacturer: String,
    val rssi: Int,
    val distanceMeters: Float,
    val riskLevel: String, // CRITICAL, HIGH, MEDIUM, LOW
    val riskScore: Int,
    val signalQuality: Int,
    val timestamp: Long,
    // Datos WiFi específicos
    val channel: Int? = null,
    val frequency: Int? = null,
    val channelWidth: String? = null,
    val capabilities: String? = null,
    val is6GHz: Boolean = false,
    // Datos Bluetooth específicos
    val txPower: Int? = null,
    val serviceUuids: String? = null, // JSON array como string
    val beaconType: String? = null
)

