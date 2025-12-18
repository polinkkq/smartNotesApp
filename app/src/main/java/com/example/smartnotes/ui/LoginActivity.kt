package com.example.smartnotes.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.example.smartnotes.R
import com.example.smartnotes.repository.AuthRepository
import com.example.smartnotes.repository.SessionManager
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var emailAuth: TextInputEditText
    private lateinit var passwordAuth: EditText

    private lateinit var buttonAuth: Button
    private lateinit var buttonGuest: Button
    private lateinit var buttonGoogle: Button

    private val authRepository = AuthRepository()

    private lateinit var googleSignInClient: GoogleSignInClient

    // Получаем результат выбора аккаунта Google
    private val googleLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            try {
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                val account = task.getResult(Exception::class.java)

                val idToken = account.idToken
                if (idToken.isNullOrBlank()) {
                    showLoading(false)
                    Toast.makeText(
                        this@LoginActivity,
                        "Не удалось получить токен Google. Проверь настройки Firebase (default_web_client_id).",
                        Toast.LENGTH_LONG
                    ).show()
                    return@registerForActivityResult
                }

                // Google-вход = не гость
                SessionManager.setGuestMode(this@LoginActivity, false)

                CoroutineScope(Dispatchers.IO).launch {
                    val res = authRepository.signInWithGoogleIdToken(idToken)
                    runOnUiThread {
                        showLoading(false)
                        if (res.isSuccess) {
                            Toast.makeText(
                                this@LoginActivity,
                                "Вход через Google выполнен!",
                                Toast.LENGTH_SHORT
                            ).show()
                            navigateToMainActivity()
                        } else {
                            Toast.makeText(
                                this@LoginActivity,
                                "Ошибка Google-входа: ${res.exceptionOrNull()?.message ?: "Неизвестная ошибка"}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            } catch (e: Exception) {
                showLoading(false)
                Toast.makeText(
                    this@LoginActivity,
                    "Google-вход отменён или произошла ошибка: ${e.message ?: "неизвестно"}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_login)

        // Настройка Google Sign-In
        // В strings.xml должна быть строка default_web_client_id (её обычно генерит google-services)
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // Сначала инициализируем view, потом проверяем пользователя
        initViews()
        setupClickListeners()
        checkCurrentUser()
    }

    private fun initViews() {
        emailAuth = findViewById(R.id.emailAuth)
        passwordAuth = findViewById(R.id.passwordAuth)
        buttonAuth = findViewById(R.id.buttonAuth)
        buttonGuest = findViewById(R.id.buttonGuest)

        // ⚠️ Должна быть в layout
        buttonGoogle = findViewById(R.id.buttonGoogle)
    }

    private fun setupClickListeners() {
        buttonAuth.setOnClickListener { loginUser() }

        buttonGuest.setOnClickListener {
            SessionManager.setGuestMode(this, true)
            navigateToMainActivity()
        }

        buttonGoogle.setOnClickListener {
            // на всякий случай чистим прошлый аккаунт, чтобы всегда показывался выбор
            googleSignInClient.signOut().addOnCompleteListener {
                showLoading(true)
                googleLauncher.launch(googleSignInClient.signInIntent)
            }
        }

        findViewById<TextView>(R.id.textRegister)?.setOnClickListener {
            navigateToRegistration()
        }
    }

    private fun checkCurrentUser() {
        // Если включён гость — НЕ автопереходить в Main при новом запуске.
        // (Ты уже хотела, чтобы гостя возвращало на авторизацию после перезапуска)
        if (SessionManager.isGuestMode(this)) {
            // остаёмся на логине
            return
        }

        val currentUser = authRepository.getCurrentUser()
        if (currentUser != null) {
            navigateToMainActivity()
        }
    }

    private fun loginUser() {
        SessionManager.setGuestMode(this, false)

        val email = emailAuth.text?.toString()?.trim().orEmpty()
        val password = passwordAuth.text?.toString()?.trim().orEmpty()

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
                        "Ошибка входа: ${result.exceptionOrNull()?.message ?: "Неизвестная ошибка"}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun validateInput(email: String, password: String): Boolean {
        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this@LoginActivity, "Заполните все поля", Toast.LENGTH_LONG).show()
            return false
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this@LoginActivity, "Введите корректный email", Toast.LENGTH_LONG).show()
            return false
        }
        if (password.length < 6) {
            Toast.makeText(this@LoginActivity, "Пароль должен содержать минимум 6 символов", Toast.LENGTH_LONG).show()
            return false
        }
        return true
    }

    private fun showLoading(show: Boolean) {
        buttonAuth.isEnabled = !show
        buttonGuest.isEnabled = !show
        buttonGoogle.isEnabled = !show
        buttonAuth.text = if (show) "Вход..." else "Войти"
    }

    private fun navigateToMainActivity() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun navigateToRegistration() {
        startActivity(Intent(this, RegistrationActivity::class.java))
    }
}
