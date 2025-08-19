package com.example.couplegps.model

/**
 * 位置数据模型类
 * 用于Firebase实时数据库的位置数据存储
 */
data class LocationData(
    val userId: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val accuracy: Float = 0f,
    val timestamp: Long = 0
) 