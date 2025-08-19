package com.example.couplegps.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 配对信息模型类
 * 存储当前用户的配对状态
 */
@Entity(tableName = "pairing_info")
data class PairingInfo(
    @PrimaryKey
    val id: Int = 1, // 固定为1，只存储一条记录
    val isPaired: Boolean = false, // 是否已配对
    val pairId: String = "", // 配对ID，由服务器生成的UUID
    val pairingTime: Long = 0 // 配对时间戳
) 