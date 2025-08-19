package com.example.couplegps.ui.pairing

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.couplegps.data.local.AppDatabase
import com.example.couplegps.data.model.PairingInfo
import com.example.couplegps.databinding.ActivityPairingBinding
import com.example.couplegps.network.SocketClient
import com.example.couplegps.ui.map.MapActivity
import kotlinx.coroutines.launch
import java.util.Date

class PairingActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityPairingBinding
    private lateinit var socketClient: SocketClient
    private lateinit var database: AppDatabase
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPairingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // 初始化数据库
        database = AppDatabase.getInstance(this)
        
        // 初始化Socket客户端
        socketClient = SocketClient(this)
        
        // 检查是否已配对
        checkPairingStatus()
        
        // 设置Socket连接状态监听
        socketClient.connectionStatus.observe(this) { status ->
            when (status) {
                SocketClient.ConnectionStatus.CONNECTED -> {
                    binding.tvConnectionStatus.text = "服务器连接成功"
                    binding.tvConnectionStatus.setTextColor(getColor(android.R.color.holo_green_dark))
                }
                SocketClient.ConnectionStatus.DISCONNECTED -> {
                    binding.tvConnectionStatus.text = "服务器连接断开"
                    binding.tvConnectionStatus.setTextColor(getColor(android.R.color.holo_red_dark))
                }
                SocketClient.ConnectionStatus.ERROR -> {
                    binding.tvConnectionStatus.text = "服务器连接错误"
                    binding.tvConnectionStatus.setTextColor(getColor(android.R.color.holo_red_dark))
                    Toast.makeText(this, "无法连接到服务器，请检查网络连接", Toast.LENGTH_SHORT).show()
                }
            }
        }
        
        // 设置配对码监听
        socketClient.pairingCode.observe(this) { code ->
            binding.progressBar.visibility = View.GONE
            binding.tvGeneratedCode.text = "您的配对码: $code"
            binding.tvGeneratedCode.visibility = View.VISIBLE
        }
        
        // 设置配对结果监听
        socketClient.pairingResult.observe(this) { result ->
            binding.progressBar.visibility = View.GONE
            
            if (result.success) {
                Toast.makeText(this, "配对成功！", Toast.LENGTH_SHORT).show()
                
                // 保存配对信息到本地数据库
                savePairingInfo(result.pairId)
            } else {
                Toast.makeText(this, "配对失败: ${result.message}", Toast.LENGTH_SHORT).show()
            }
        }
        
        // 连接到Socket服务器
        socketClient.connect()
        
        // 生成配对码按钮
        binding.btnGenerateCode.setOnClickListener {
            binding.progressBar.visibility = View.VISIBLE
            socketClient.requestPairingCode()
        }
        
        // 输入配对码按钮
        binding.btnEnterCode.setOnClickListener {
            val code = binding.etPairingCode.text.toString().trim()
            if (code.isNotEmpty() && code.length == 6) {
                binding.progressBar.visibility = View.VISIBLE
                socketClient.pairWithCode(code)
            } else {
                Toast.makeText(this, "请输入6位数字配对码", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun checkPairingStatus() {
        lifecycleScope.launch {
            val pairingInfo = database.pairingDao().getPairingInfoSync()
            
            if (pairingInfo != null && pairingInfo.isPaired) {
                // 用户已配对，直接跳转到地图界面
                startActivity(Intent(this@PairingActivity, MapActivity::class.java))
                finish()
            }
        }
    }
    
    private fun savePairingInfo(pairId: String) {
        lifecycleScope.launch {
            // 创建配对信息
            val pairingInfo = PairingInfo(
                id = 1,
                isPaired = true,
                pairId = pairId,
                pairingTime = Date().time
            )
            
            // 保存到数据库
            database.pairingDao().insertPairingInfo(pairingInfo)
            
            // 跳转到地图界面
            startActivity(Intent(this@PairingActivity, MapActivity::class.java))
            finish()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        socketClient.disconnect()
    }
} 