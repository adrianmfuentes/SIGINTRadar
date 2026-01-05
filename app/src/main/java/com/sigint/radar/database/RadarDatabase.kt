package com.sigint.radar.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.sigint.radar.database.dao.*
import com.sigint.radar.database.entities.*

@Database(
    entities = [
        ScanHistoryEntity::class,
        DeviceHistoryEntity::class,
        KnownDeviceEntity::class,
        DevicePatternEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class RadarDatabase : RoomDatabase() {

    abstract fun scanHistoryDao(): ScanHistoryDao
    abstract fun deviceHistoryDao(): DeviceHistoryDao
    abstract fun knownDeviceDao(): KnownDeviceDao
    abstract fun devicePatternDao(): DevicePatternDao

    companion object {
        @Volatile
        private var INSTANCE: RadarDatabase? = null

        fun getDatabase(context: Context): RadarDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RadarDatabase::class.java,
                    "sigint_radar_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

