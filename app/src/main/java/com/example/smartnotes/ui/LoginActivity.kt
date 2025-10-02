package com.example.smartnotes.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.smartnotes.R
import com.google.android.material.textfield.TextInputEditText
import com.example.smartnotes.repository.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var emailAuth: TextInputEditText
    private lateinit var passwordAuth: EditText
    private lateinit var buttonAuth: Button

    private val authRepository = AuthRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        initViews()
        setupClickListeners()

    }

    private fun initViews() {
        emailAuth = findViewById(R.id.emailAuth)
        passwordAuth = findViewById(R.id.passwordAuth)
        buttonAuth = findViewById(R.id.buttonAuth)
    }

    private fun setupClickListeners() {
        buttonAuth.setOnClickListener {
            loginUser()
        }

        findViewById<TextView>(R.id.textRegister)?.setOnClickListener {
            navigateToRegistration()
        }
    }

    private fun checkCurrentUser() {
        val currentUser = authRepository.getCurrentUser()
        if (currentUser != null) {
            // Пользователь уже авторизован, переходим на главный экран
            navigateToMainActivity()
        }
    }

    private fun loginUser() {
        val email = emailAuth.text.toString().trim()
        val password = passwordAuth.text.toString().trim()

        // Валидация
        if (!validateInput(email, password)) {
            return
        }

        showLoading(true)

        // Авторизация через Firebase
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = authRepository.loginUser(email, password)

                runOnUiThread {
                    if (result.isSuccess) {
                        showSuccess("Вход выполнен успешно!")
                        navigateToMainActivity()
                    } else {
                        showError("Ошибка входа: ${result.exceptionOrNull()?.message}")
                    }
                    showLoading(false)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    showError("Ошибка: ${e.message}")
                    showLoading(false)
                }
            }
        }
    }

    private fun validateInput(email: String, password: String): Boolean {
        if (email.isEmpty() || password.isEmpty()) {
            showError("Заполните все поля")
            return false
        }

        if (!isValidEmail(email)) {
            showError("Введите корректный email")
            return false
        }

        if (password.length < 6) {
            showError("Пароль должен содержать минимум 6 символов")
            return false
        }

        return true
    }

    private fun isValidEmail(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    private fun showLoading(show: Boolean) {
        buttonAuth.isEnabled = !show
        buttonAuth.text = if (show) "Вход..." else "Войти"
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun showSuccess(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }


    private fun navigateToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
    private fun navigateToRegistration() {
        val intent = Intent(this, RegistrationActivity::class.java)
        startActivity(intent)
    }
}