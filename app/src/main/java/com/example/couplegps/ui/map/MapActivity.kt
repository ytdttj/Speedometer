package com.example.couplegps.ui.map

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.model.BitmapDescriptorFactory
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.LatLngBounds
import com.amap.api.maps.model.Marker
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.location.AMapLocationListener
import com.example.couplegps.R
import com.example.couplegps.data.local.AppDatabase
import com.example.couplegps.data.model.LocationData
import com.example.couplegps.databinding.ActivityMapBinding
import com.example.couplegps.network.SocketClient
import com.example.couplegps.ui.settings.SettingsActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MapActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMapBinding
    private lateinit var aMap: AMap
    private lateinit var locationClient: AMapLocationClient
    private lateinit var locationOption: AMapLocationClientOption
    private lateinit var socketClient: SocketClient
    private lateinit var database: AppDatabase
    
    private var pairId: String = ""
    private var myMarker: Marker? = null
    private var partnerMarker: Marker? = null
    
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMapBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // 初始化数据库
        database = AppDatabase.getInstance(this)
        
        // 初始化Socket客户端
        socketClient = SocketClient(this)
        
        // 初始化高德地图
        binding.map.onCreate(savedInstanceState)
        aMap = binding.map.map
        
        // 初始化定位
        initLocation()
        
        // 配置地图
        setupMap()
        
        // 加载配对信息
        loadPairingInfo()
        
        // 监听伴侣位置更新
        observePartnerLocation()
        
        // 监听配对状态变化
        observePairingEvents()
        
        // 设置连接状态显示
        observeConnectionStatus()
        
        // 退出按钮事件
        binding.btnUnpair.setOnClickListener {
            unpair()
        }
    }
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.map_menu, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                // 打开设置页面
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun loadPairingInfo() {
        binding.progressBar.visibility = View.VISIBLE
        
        database.pairingDao().getPairingInfo().observe(this) { pairingInfo ->
            if (pairingInfo != null && pairingInfo.isPaired) {
                pairId = pairingInfo.pairId
                // 连接到Socket服务器
                socketClient.connect()
            } else {
                // 未配对，返回配对界面
                Toast.makeText(this, "您还没有配对", Toast.LENGTH_SHORT).show()
                finish()
            }
            binding.progressBar.visibility = View.GONE
        }
    }
    
    private fun setupMap() {
        // 设置地图类型（普通地图）
        aMap.mapType = AMap.MAP_TYPE_NORMAL
        
        // 启用室内地图
        aMap.showIndoorMap(true)
        
        // 开启我的位置图层
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            aMap.isMyLocationEnabled = true
        }
    }
    
    private fun initLocation() {
        try {
            // 初始化定位
            AMapLocationClient.updatePrivacyShow(this, true, true)
            AMapLocationClient.updatePrivacyAgree(this, true)
            locationClient = AMapLocationClient(applicationContext)
            
            // 设置定位回调
            locationClient.setLocationListener { location ->
                if (location != null) {
                    if (location.errorCode == 0) {
                        // 定位成功
                        val latLng = LatLng(location.latitude, location.longitude)
                        
                        // 更新我的位置标记
                        updateMyLocation(latLng, location.accuracy)
                        
                        // 保存并发送位置更新
                        if (pairId.isNotEmpty() && socketClient.connectionStatus.value == SocketClient.ConnectionStatus.CONNECTED) {
                            val locationData = LocationData(
                                pairId = pairId,
                                isPartner = false,
                                latitude = location.latitude,
                                longitude = location.longitude,
                                accuracy = location.accuracy,
                                timestamp = System.currentTimeMillis()
                            )
                            
                            // 保存到本地数据库并发送到服务器
                            saveAndSendLocation(locationData)
                        }
                    } else {
                        // 定位失败
                        Toast.makeText(this, "定位失败: ${location.errorInfo}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            
            // 配置定位参数
            locationOption = AMapLocationClientOption().apply {
                // 从设置读取位置更新间隔
                val sharedPrefs = getSharedPreferences("app_settings", MODE_PRIVATE)
                val updateInterval = sharedPrefs.getLong("location_update_interval", 10000L) // 默认10秒
                
                // 设置定位模式为高精度模式
                locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
                // 设置定位间隔
                interval = updateInterval
                // 设置是否返回地址信息
                isNeedAddress = true
            }
            
            // 设置定位参数
            locationClient.setLocationOption(locationOption)
        } catch (e: Exception) {
            Toast.makeText(this, "定位初始化失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun saveAndSendLocation(locationData: LocationData) {
        lifecycleScope.launch {
            // 保存到本地数据库
            withContext(Dispatchers.IO) {
                database.locationDao().insertLocation(locationData)
            }
            
            // 发送到服务器
            socketClient.sendLocationUpdate(locationData)
        }
    }
    
    private fun updateMyLocation(latLng: LatLng, accuracy: Float) {
        if (myMarker == null) {
            // 创建标记
            val markerOptions = MarkerOptions()
                .position(latLng)
                .title("我的位置")
                .snippet("精度：${accuracy}米")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
            
            myMarker = aMap.addMarker(markerOptions)
            aMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
        } else {
            // 更新标记位置
            myMarker?.position = latLng
            myMarker?.snippet = "精度：${accuracy}米"
        }
        
        // 如果两个标记都存在，调整地图以显示两者
        adjustCameraForBothMarkers()
    }
    
    private fun updatePartnerMarker(locationData: LocationData) {
        val latLng = LatLng(locationData.latitude, locationData.longitude)
        
        if (partnerMarker == null) {
            // 创建标记
            val markerOptions = MarkerOptions()
                .position(latLng)
                .title("伴侣位置")
                .snippet("精度：${locationData.accuracy}米")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
            
            partnerMarker = aMap.addMarker(markerOptions)
        } else {
            // 更新标记位置
            partnerMarker?.position = latLng
            partnerMarker?.snippet = "精度：${locationData.accuracy}米"
        }
        
        // 更新最后一次位置的时间
        val lastUpdateTime = dateFormat.format(Date(locationData.timestamp))
        binding.tvLastUpdate.text = "最后更新: $lastUpdateTime"
        binding.tvLastUpdate.visibility = View.VISIBLE
        
        // 如果两个标记都存在，调整地图以显示两者
        adjustCameraForBothMarkers()
    }
    
    private fun adjustCameraForBothMarkers() {
        if (myMarker != null && partnerMarker != null) {
            // 创建包含两个标记的边界
            val bounds = LatLngBounds.Builder()
                .include(myMarker!!.position)
                .include(partnerMarker!!.position)
                .build()
            
            // 调整相机以显示两个标记
            aMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100))
        }
    }
    
    private fun observePartnerLocation() {
        // 从Socket客户端监听伴侣位置更新
        socketClient.partnerLocation.observe(this) { locationData ->
            // 更新UI上的伴侣位置标记
            updatePartnerMarker(locationData)
            
            // 保存伴侣位置到本地数据库（设置isPartner为true）
            val partnerLocationData = locationData.copy(isPartner = true)
            lifecycleScope.launch(Dispatchers.IO) {
                database.locationDao().insertLocation(partnerLocationData)
            }
        }
        
        // 从本地数据库加载最新的伴侣位置
        database.locationDao().getLatestLocation(true, pairId).observe(this) { locationData ->
            if (locationData != null) {
                updatePartnerMarker(locationData)
            }
        }
    }
    
    private fun observePairingEvents() {
        socketClient.pairingEvent.observe(this) { event ->
            when (event) {
                SocketClient.PairingEvent.PARTNER_UNPAIRED, SocketClient.PairingEvent.UNPAIRED -> {
                    // 伴侣解除了配对或自己解除了配对
                    Toast.makeText(this, "配对已解除", Toast.LENGTH_SHORT).show()
                    
                    // 清除配对信息并返回配对界面
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) {
                            database.pairingDao().clearPairingInfo()
                            database.locationDao().deleteAllLocationsByPairId(pairId)
                        }
                        finish()
                    }
                }
                SocketClient.PairingEvent.PARTNER_DISCONNECTED -> {
                    // 伴侣断开连接，显示提示但不退出
                    Toast.makeText(this, "伴侣已断开连接", Toast.LENGTH_SHORT).show()
                }
                else -> {}
            }
        }
    }
    
    private fun observeConnectionStatus() {
        socketClient.connectionStatus.observe(this) { status ->
            when (status) {
                SocketClient.ConnectionStatus.CONNECTED -> {
                    binding.tvConnectionStatus.text = "已连接"
                    binding.tvConnectionStatus.setTextColor(getColor(android.R.color.holo_green_dark))
                }
                SocketClient.ConnectionStatus.DISCONNECTED -> {
                    binding.tvConnectionStatus.text = "未连接"
                    binding.tvConnectionStatus.setTextColor(getColor(android.R.color.holo_red_dark))
                }
                SocketClient.ConnectionStatus.ERROR -> {
                    binding.tvConnectionStatus.text = "连接错误"
                    binding.tvConnectionStatus.setTextColor(getColor(android.R.color.holo_red_dark))
                }
            }
        }
    }
    
    private fun unpair() {
        if (pairId.isNotEmpty()) {
            // 通过Socket服务器解除配对
            socketClient.unpair()
            
            // 清除本地配对信息和位置数据
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    database.pairingDao().clearPairingInfo()
                    database.locationDao().deleteAllLocationsByPairId(pairId)
                }
                finish()
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        binding.map.onResume()
        
        // 重新配置位置更新间隔
        if (::locationClient.isInitialized && ::locationOption.isInitialized) {
            val sharedPrefs = getSharedPreferences("app_settings", MODE_PRIVATE)
            val updateInterval = sharedPrefs.getLong("location_update_interval", 10000L)
            locationOption.interval = updateInterval
            locationClient.setLocationOption(locationOption)
        }
        
        // 启动定位
        startLocationUpdates()
    }
    
    override fun onPause() {
        super.onPause()
        binding.map.onPause()
        stopLocationUpdates()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        binding.map.onDestroy()
        stopLocationUpdates()
        socketClient.disconnect()
    }
    
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding.map.onSaveInstanceState(outState)
    }
    
    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        
        if (::locationClient.isInitialized) {
            try {
                locationClient.startLocation()
            } catch (e: Exception) {
                Toast.makeText(this, "启动定位失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun stopLocationUpdates() {
        if (::locationClient.isInitialized) {
            locationClient.stopLocation()
        }
    }
}