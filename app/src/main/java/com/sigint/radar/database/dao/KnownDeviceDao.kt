package com.sigint.radar.database.dao

import androidx.room.*
import com.sigint.radar.database.entities.KnownDeviceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KnownDeviceDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(device: KnownDeviceEntity): Long

    @Update
    suspend fun update(device: KnownDeviceEntity)

    @Delete
    suspend fun delete(device: KnownDeviceEntity)

    @Query("SELECT * FROM known_devices ORDER BY lastSeen DESC")
    fun getAllKnownDevices(): Flow<List<KnownDeviceEntity>>

    @Query("SELECT * FROM known_devices WHERE macAddress = :macAddress LIMIT 1")
    suspend fun getKnownDevice(macAddress: String): KnownDeviceEntity?

    @Query("SELECT * FROM known_devices WHERE macAddress = :macAddress LIMIT 1")
    fun getKnownDeviceFlow(macAddress: String): Flow<KnownDeviceEntity?>

    @Query("SELECT * FROM known_devices WHERE trustLevel = :trustLevel ORDER BY lastSeen DESC")
    fun getDevicesByTrustLevel(trustLevel: String): Flow<List<KnownDeviceEntity>>

    @Query("SELECT * FROM known_devices WHERE alertEnabled = 1")
    fun getDevicesWithAlerts(): Flow<List<KnownDeviceEntity>>

    @Query("UPDATE known_devices SET lastSeen = :timestamp, seenCount = seenCount + 1 WHERE macAddress = :macAddress")
    suspend fun updateLastSeen(macAddress: String, timestamp: Long)

    @Query("SELECT COUNT(*) FROM known_devices WHERE trustLevel = :trustLevel")
    suspend fun getCountByTrustLevel(trustLevel: String): Int

    @Query("DELETE FROM known_devices WHERE trustLevel = 'UNKNOWN' AND lastSeen < :timestamp")
    suspend fun cleanupOldUnknownDevices(timestamp: Long): Int
}

