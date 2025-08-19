package com.example.couplegps

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.couplegps.data.local.AppDatabase
import com.example.couplegps.databinding.ActivityMainBinding
import com.example.couplegps.ui.map.MapActivity
import com.example.couplegps.ui.pairing.PairingActivity
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var database: AppDatabase
    
    private val LOCATION_PERMISSION_REQUEST_CODE = 1001
    private val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // 初始化数据库
        database = AppDatabase.getInstance(this)
        
        // 检查权限
        if (allPermissionsGranted()) {
            checkPairingStatus()
        } else {
            requestLocationPermissions()
        }
    }
    
    private fun checkPairingStatus() {
        // 检查用户是否已配对
        lifecycleScope.launch {
            val pairingInfo = database.pairingDao().getPairingInfoSync()
            if (pairingInfo != null && pairingInfo.isPaired) {
                // 用户已配对，跳转到地图界面
                startActivity(Intent(this@MainActivity, MapActivity::class.java))
            } else {
                // 用户未配对，跳转到配对界面
                startActivity(Intent(this@MainActivity, PairingActivity::class.java))
            }
            finish()
        }
    }
    
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }
    
    private fun requestLocationPermissions() {
        ActivityCompat.requestPermissions(
            this, REQUIRED_PERMISSIONS, LOCATION_PERMISSION_REQUEST_CODE
        )
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (allPermissionsGranted()) {
                checkPairingStatus()
            } else {
                Toast.makeText(this, "需要定位权限才能使用此应用", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }
} 