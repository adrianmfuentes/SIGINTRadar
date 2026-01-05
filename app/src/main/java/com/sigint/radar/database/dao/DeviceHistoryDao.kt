package com.sigint.radar.database.dao

import androidx.room.*
import com.sigint.radar.database.entities.DeviceHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceHistoryDao {

    @Insert
    suspend fun insert(device: DeviceHistoryEntity): Long

    @Insert
    suspend fun insertAll(devices: List<DeviceHistoryEntity>)

    @Query("SELECT * FROM device_history WHERE scanId = :scanId ORDER BY riskLevel DESC, distanceMeters ASC")
    fun getDevicesByScan(scanId: Long): Flow<List<DeviceHistoryEntity>>

    @Query("SELECT * FROM device_history WHERE macAddress = :macAddress ORDER BY timestamp DESC")
    fun getDeviceHistory(macAddress: String): Flow<List<DeviceHistoryEntity>>

    @Query("SELECT DISTINCT macAddress FROM device_history")
    suspend fun getAllUniqueMacAddresses(): List<String>

    @Query("SELECT * FROM device_history WHERE macAddress = :macAddress ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestDeviceRecord(macAddress: String): DeviceHistoryEntity?

    @Query("""
        SELECT * FROM device_history 
        WHERE timestamp BETWEEN :startTime AND :endTime 
        ORDER BY timestamp DESC
    """)
    fun getDevicesBetween(startTime: Long, endTime: Long): Flow<List<DeviceHistoryEntity>>

    @Query("DELETE FROM device_history WHERE scanId = :scanId")
    suspend fun deleteDevicesByScan(scanId: Long)

    @Query("""
        SELECT COUNT(DISTINCT macAddress) 
        FROM device_history 
        WHERE timestamp > :since
    """)
    suspend fun getUniqueDeviceCountSince(since: Long): Int
}

