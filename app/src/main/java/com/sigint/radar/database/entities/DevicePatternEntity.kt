package com.sigint.radar.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Registra patrones de dispositivos que aparecen juntos
 */
@Entity(
    tableName = "device_patterns",
    indices = [Index("device1Mac"), Index("device2Mac")]
)
data class DevicePatternEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val device1Mac: String,
    val device2Mac: String,
    val coOccurrenceCount: Int = 1, // Veces que aparecen juntos
    val firstSeen: Long,
    val lastSeen: Long,
    val confidence: Float = 0f // 0-1, confianza del patrón
)

