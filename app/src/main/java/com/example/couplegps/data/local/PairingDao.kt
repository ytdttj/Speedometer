package com.example.couplegps.data.local

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.couplegps.data.model.PairingInfo

/**
 * 配对信息数据访问对象
 * 处理配对信息的数据库操作
 */
@Dao
interface PairingDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPairingInfo(pairingInfo: PairingInfo)
    
    @Query("SELECT * FROM pairing_info WHERE id = 1")
    fun getPairingInfo(): LiveData<PairingInfo?>
    
    @Query("SELECT * FROM pairing_info WHERE id = 1")
    suspend fun getPairingInfoSync(): PairingInfo?
    
    @Query("UPDATE pairing_info SET isPaired = :isPaired, pairId = :pairId, pairingTime = :pairingTime WHERE id = 1")
    suspend fun updatePairingInfo(isPaired: Boolean, pairId: String, pairingTime: Long)
    
    @Query("DELETE FROM pairing_info")
    suspend fun clearPairingInfo()
} 