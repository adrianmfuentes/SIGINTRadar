package com.sigint.radar.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scan_history")
data class ScanHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val totalDevices: Int,
    val wifiCount: Int,
    val bluetoothCount: Int,
    val criticalCount: Int,
    val highCount: Int,
    val mediumCount: Int,
    val lowCount: Int,
    val location: String? = null, // Opcional: ubicación del escaneo
    val notes: String? = null
)


