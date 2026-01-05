package com.sigint.radar.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "known_devices",
    indices = [Index("macAddress", unique = true)]
)
data class KnownDeviceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val macAddress: String,
    val customName: String? = null, // Nombre personalizado
    val deviceType: String, // WIFI, BLUETOOTH, BEACON
    val trustLevel: String, // TRUSTED, SUSPICIOUS, BLOCKED, UNKNOWN
    val notes: String? = null,
    val firstSeen: Long,
    val lastSeen: Long,
    val seenCount: Int = 1,
    val manufacturer: String? = null,
    val alertEnabled: Boolean = false // Si debe alertar cuando se detecte
)

