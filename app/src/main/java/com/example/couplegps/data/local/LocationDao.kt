package com.example.couplegps.data.local

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.couplegps.data.model.LocationData

/**
 * 位置数据访问对象
 * 处理位置数据的数据库操作
 */
@Dao
interface LocationDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocation(location: LocationData): Long
    
    @Query("SELECT * FROM locations WHERE isPartner = :isPartner AND pairId = :pairId ORDER BY timestamp DESC LIMIT 1")
    fun getLatestLocation(isPartner: Boolean, pairId: String): LiveData<LocationData?>
    
    @Query("SELECT * FROM locations WHERE pairId = :pairId ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentLocations(pairId: String, limit: Int): LiveData<List<LocationData>>
    
    @Query("DELETE FROM locations WHERE timestamp < :timestamp")
    suspend fun deleteOldLocations(timestamp: Long): Int
    
    @Query("DELETE FROM locations WHERE pairId = :pairId")
    suspend fun deleteAllLocationsByPairId(pairId: String): Int
} 