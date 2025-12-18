package com.example.smartnotes.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.example.smartnotes.R
import com.example.smartnotes.repository.AuthRepository
import com.example.smartnotes.repository.SessionManager
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var emailAuth: TextInputEditText
    private lateinit var passwordAuth: EditText
    private lateinit var buttonAuth: Button
    private lateinit var buttonGuest: Button

    private val authRepository = AuthRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_login)

        checkCurrentUser()
        initViews()
        setupClickListeners()
    }

    private fun initViews() {
        emailAuth = findViewById(R.id.emailAuth)
        passwordAuth = findViewById(R.id.passwordAuth)
        buttonAuth = findViewById(R.id.buttonAuth)
        buttonGuest = findViewById(R.id.buttonGuest)
    }

    private fun setupClickListeners() {
        buttonAuth.setOnClickListener { loginUser() }

        buttonGuest.setOnClickListener {
            // включаем гостевой режим
            SessionManager.setGuestMode(this, true)
            navigateToMainActivity()
        }

        findViewById<TextView>(R.id.textRegister)?.setOnClickListener {
            navigateToRegistration()
        }
    }

    private fun checkCurrentUser() {
        // Если включён гость — сразу в Main
        if (SessionManager.isGuestMode(this)) {
            navigateToMainActivity()
            return
        }

        val currentUser = authRepository.getCurrentUser()
        if (currentUser != null) {
            navigateToMainActivity()
        }
    }

    private fun loginUser() {
        // При обычном логине — выключаем гостевой режим
        SessionManager.setGuestMode(this, false)

        val email = emailAuth.text.toString().trim()
        val password = passwordAuth.text.toString().trim()

        if (!validateInput(email, password)) return

        showLoading(true)

        CoroutineScope(Dispatchers.IO).launch {
            val result = authRepository.loginUser(email, password)
            runOnUiThread {
                showLoading(false)
                if (result.isSuccess) {
                    Toast.makeText(this@LoginActivity, "Вход выполнен успешно!", Toast.LENGTH_SHORT).show()
                    navigateToMainActivity()
                } else {
                    Toast.makeText(
                        this@LoginActivity,
                        "Ошибка входа: ${result.exceptionOrNull()?.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun validateInput(email: String, password: String): Boolean {
        if (email.isEmpty() || password.isEmpty()) {
            showError("Заполните все поля")
            return false
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showError("Введите корректный email")
            return false
        }
        if (password.length < 6) {
            showError("Пароль должен содержать минимум 6 символов")
            return false
        }
        return true
    }

    private fun showLoading(show: Boolean) {
        buttonAuth.isEnabled = !show
        buttonGuest.isEnabled = !show
        buttonAuth.text = if (show) "Вход..." else "Войти"
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun navigateToMainActivity() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun navigateToRegistration() {
        startActivity(Intent(this, RegistrationActivity::class.java))
    }
}
