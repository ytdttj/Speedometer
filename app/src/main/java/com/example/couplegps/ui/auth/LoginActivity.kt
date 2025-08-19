package com.example.couplegps.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.couplegps.databinding.ActivityLoginBinding
import com.example.couplegps.ui.pairing.PairingActivity
import com.google.firebase.auth.FirebaseAuth

class LoginActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // 初始化Firebase Auth
        auth = FirebaseAuth.getInstance()
        
        // 设置登录按钮点击事件
        binding.btnLogin.setOnClickListener {
            loginUser()
        }
        
        // 设置注册按钮点击事件
        binding.btnRegister.setOnClickListener {
            registerUser()
        }
    }
    
    private fun loginUser() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()
        
        // 简单验证
        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "请填写邮箱和密码", Toast.LENGTH_SHORT).show()
            return
        }
        
        // 显示进度条
        binding.progressBar.visibility = View.VISIBLE
        
        // Firebase登录
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                // 隐藏进度条
                binding.progressBar.visibility = View.GONE
                
                if (task.isSuccessful) {
                    // 登录成功，跳转到配对界面
                    startActivity(Intent(this, PairingActivity::class.java))
                    finish()
                } else {
                    // 登录失败
                    Toast.makeText(this, "登录失败: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }
    
    private fun registerUser() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()
        
        // 简单验证
        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "请填写邮箱和密码", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (password.length < 6) {
            Toast.makeText(this, "密码长度至少为6位", Toast.LENGTH_SHORT).show()
            return
        }
        
        // 显示进度条
        binding.progressBar.visibility = View.VISIBLE
        
        // Firebase注册
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                // 隐藏进度条
                binding.progressBar.visibility = View.GONE
                
                if (task.isSuccessful) {
                    // 注册成功
                    Toast.makeText(this, "注册成功，请登录", Toast.LENGTH_SHORT).show()
                } else {
                    // 注册失败
                    Toast.makeText(this, "注册失败: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }
} 