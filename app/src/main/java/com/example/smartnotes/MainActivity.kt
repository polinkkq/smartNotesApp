package com.example.smartnotes

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import com.example.smartnotes.network.RetrofitClient
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var btnTestRegistration: Button
    private lateinit var btnGoToRegistration: Button
    private lateinit var tvStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupClickListeners()
        testApiConnection()
    }

    private fun initViews() {
        btnTestRegistration = findViewById(R.id.btnTestRegistration)
        btnGoToRegistration = findViewById(R.id.btnGoToRegistration)
        tvStatus = findViewById(R.id.tvStatus)
    }

    private fun setupClickListeners() {
        // Кнопка для тестирования API
        btnTestRegistration.setOnClickListener {
            testApiConnection()
        }

        // Кнопка для перехода к регистрации
        btnGoToRegistration.setOnClickListener {
            val intent = Intent(this, RegistrationActivity::class.java)
            startActivity(intent)
        }
    }

    private fun testApiConnection() {
        tvStatus.text = "Тестируем подключение..."

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = RetrofitClient.apiService.checkServer()
                runOnUiThread {
                    if (response.isSuccessful) {
                        tvStatus.text = "✅ API работает: ${response.body()?.message}"
                    } else {
                        tvStatus.text = "❌ Ошибка API: ${response.message()}"
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    tvStatus.text = "❌ Ошибка сети: ${e.message}"
                }
            }
        }
    }
}