package com.sigint.radar.database.dao

import androidx.room.*
import com.sigint.radar.database.entities.ScanHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanHistoryDao {

    @Insert
    suspend fun insert(scan: ScanHistoryEntity): Long

    @Update
    suspend fun update(scan: ScanHistoryEntity)

    @Delete
    suspend fun delete(scan: ScanHistoryEntity)

    @Query("SELECT * FROM scan_history ORDER BY timestamp DESC")
    fun getAllScans(): Flow<List<ScanHistoryEntity>>

    @Query("SELECT * FROM scan_history ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentScans(limit: Int = 10): Flow<List<ScanHistoryEntity>>

    @Query("SELECT * FROM scan_history WHERE id = :scanId")
    suspend fun getScanById(scanId: Long): ScanHistoryEntity?

    @Query("DELETE FROM scan_history WHERE timestamp < :timestampBefore")
    suspend fun deleteOlderThan(timestampBefore: Long): Int

    @Query("SELECT COUNT(*) FROM scan_history")
    suspend fun getScansCount(): Int

    @Query("SELECT * FROM scan_history WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    fun getScansBetween(startTime: Long, endTime: Long): Flow<List<ScanHistoryEntity>>
}


