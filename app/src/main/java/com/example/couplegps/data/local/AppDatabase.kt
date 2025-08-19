package com.example.couplegps.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.couplegps.data.model.LocationData
import com.example.couplegps.data.model.PairingInfo

/**
 * 应用数据库
 * 用于本地存储配对信息和位置数据
 */
@Database(entities = [LocationData::class, PairingInfo::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun locationDao(): LocationDao
    abstract fun pairingDao(): PairingDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "couple_gps_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
} 