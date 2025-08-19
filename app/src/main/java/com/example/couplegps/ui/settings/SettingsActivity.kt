package com.example.couplegps.ui.settings

import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.couplegps.databinding.ActivitySettingsBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class SettingsActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    
    private var userId: String = ""
    
    // 默认位置更新间隔（毫秒）
    private val DEFAULT_UPDATE_INTERVAL = 10000L // 10秒
    private val MIN_UPDATE_INTERVAL = 5000L // 5秒
    private val MAX_UPDATE_INTERVAL = 60000L // 60秒
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // 设置返回按钮
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "设置"
        
        // 初始化Firebase
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()
        userId = auth.currentUser?.uid ?: ""
        
        if (userId.isEmpty()) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        // 加载当前设置
        loadSettings()
        
        // 保存按钮点击事件
        binding.btnSave.setOnClickListener {
            saveSettings()
        }
        
        // 解除配对按钮点击事件
        binding.btnUnpair.setOnClickListener {
            unpairUser()
        }
    }
    
    private fun loadSettings() {
        // 从SharedPreferences加载设置
        val sharedPrefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        val updateInterval = sharedPrefs.getLong("location_update_interval", DEFAULT_UPDATE_INTERVAL)
        
        // 设置滑块值
        val progress = ((updateInterval - MIN_UPDATE_INTERVAL) / 
                      (MAX_UPDATE_INTERVAL - MIN_UPDATE_INTERVAL) * 100).toInt()
        binding.sliderUpdateInterval.progress = progress
        
        // 更新显示的间隔值
        updateIntervalText(updateInterval)
        
        // 显示用户信息
        binding.tvUserEmail.text = "账号: ${auth.currentUser?.email ?: "未知"}"
    }
    
    private fun saveSettings() {
        // 计算实际的更新间隔
        val progress = binding.sliderUpdateInterval.progress
        val updateInterval = MIN_UPDATE_INTERVAL + 
                          (progress / 100.0 * (MAX_UPDATE_INTERVAL - MIN_UPDATE_INTERVAL)).toLong()
        
        // 保存到SharedPreferences
        val sharedPrefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        sharedPrefs.edit()
            .putLong("location_update_interval", updateInterval)
            .apply()
        
        Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show()
        finish()
    }
    
    private fun updateIntervalText(interval: Long) {
        val seconds = interval / 1000
        binding.tvUpdateInterval.text = "位置更新间隔: ${seconds}秒"
    }
    
    private fun unpairUser() {
        val userRef = database.getReference("users").child(userId)
        
        // 获取伴侣ID
        userRef.child("partnerUserId").get().addOnSuccessListener { snapshot ->
            val partnerUserId = snapshot.getValue(String::class.java)
            if (!partnerUserId.isNullOrEmpty()) {
                // 删除自己的伴侣ID
                userRef.child("partnerUserId").removeValue()
                
                // 删除伴侣的配对关系
                database.getReference("users")
                    .child(partnerUserId)
                    .child("partnerUserId")
                    .removeValue()
                    .addOnSuccessListener {
                        Toast.makeText(this, "已解除配对", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "解除配对失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            } else {
                Toast.makeText(this, "您尚未配对", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener { e ->
            Toast.makeText(this, "获取伴侣信息失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
