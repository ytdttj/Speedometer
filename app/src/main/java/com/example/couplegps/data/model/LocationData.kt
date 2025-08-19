package com.example.couplegps.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 位置数据模型类
 * 用于本地存储和Socket.IO传输
 */
@Entity(tableName = "locations")
data class LocationData(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val pairId: String = "", // 配对ID
    val isPartner: Boolean = false, // 是否为伴侣的位置
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val accuracy: Float = 0f,
    val timestamp: Long = 0
) 