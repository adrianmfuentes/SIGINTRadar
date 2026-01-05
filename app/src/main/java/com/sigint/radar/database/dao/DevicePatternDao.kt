package com.sigint.radar.database.dao

import androidx.room.*
import com.sigint.radar.database.entities.DevicePatternEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DevicePatternDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(pattern: DevicePatternEntity): Long

    @Update
    suspend fun update(pattern: DevicePatternEntity)

    @Query("""
        SELECT * FROM device_patterns 
        WHERE (device1Mac = :mac1 AND device2Mac = :mac2) 
           OR (device1Mac = :mac2 AND device2Mac = :mac1)
        LIMIT 1
    """)
    suspend fun getPattern(mac1: String, mac2: String): DevicePatternEntity?

    @Query("""
        SELECT * FROM device_patterns 
        WHERE device1Mac = :macAddress OR device2Mac = :macAddress
        ORDER BY coOccurrenceCount DESC, confidence DESC
    """)
    fun getPatternsForDevice(macAddress: String): Flow<List<DevicePatternEntity>>

    @Query("""
        SELECT * FROM device_patterns 
        WHERE coOccurrenceCount >= :minOccurrences AND confidence >= :minConfidence
        ORDER BY confidence DESC, coOccurrenceCount DESC
    """)
    fun getStrongPatterns(minOccurrences: Int = 3, minConfidence: Float = 0.5f): Flow<List<DevicePatternEntity>>

    @Query("DELETE FROM device_patterns WHERE lastSeen < :timestamp AND coOccurrenceCount < 3")
    suspend fun cleanupWeakOldPatterns(timestamp: Long): Int
}

