package com.example.smartnotes.ui

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.smartnotes.R
import com.google.android.material.textfield.TextInputEditText
import com.example.smartnotes.repository.AuthRepository
import com.example.smartnotes.models.UserRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RegistrationActivity : AppCompatActivity() {

    private lateinit var nameRegistration: TextInputEditText
    private lateinit var lastNameRegistration: TextInputEditText
    private lateinit var emailRegistration: TextInputEditText
    private lateinit var passwordRegistration: EditText
    private lateinit var registerButton: Button
    private lateinit var roleRadioGroup: RadioGroup

    private val authRepository = AuthRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        initViews()
        setupClickListeners()

        val backButton = findViewById<ImageButton>(R.id.imageButton)
        backButton.setOnClickListener {
            finish()
        }
    }

    private fun initViews() {
        nameRegistration = findViewById(R.id.nameRegistration)
        lastNameRegistration = findViewById(R.id.lastnameRegistration)
        emailRegistration = findViewById(R.id.emailRegistration)
        passwordRegistration = findViewById(R.id.passwordRegistration)
        registerButton = findViewById(R.id.buttonRegistration)
        roleRadioGroup = findViewById(R.id.roleGroup)
    }

    private fun setupClickListeners() {
        registerButton.setOnClickListener {
            registerUser()
        }
    }

    private fun registerUser() {
        val firstName = nameRegistration.text.toString().trim()
        val lastName = lastNameRegistration.text.toString().trim()
        val email = emailRegistration.text.toString().trim()
        val password = passwordRegistration.text.toString().trim()

        val selectedRole = when (roleRadioGroup.checkedRadioButtonId) {
            R.id.roleTeacher -> UserRole.TEACHER
            else -> UserRole.STUDENT
        }

        if (!validateInput(firstName, lastName, email, password)) {
            return
        }

        showLoading(true)

        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    authRepository.registerUser(
                        email = email,
                        password = password,
                        firstName = firstName,
                        lastName = lastName,
                        role = selectedRole // Передаем UserRole, а не String
                    )
                }

                showLoading(false)

                if (result.isSuccess) {
                    showSuccess("Регистрация успешна!")
                    navigateToMainActivity()
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Неизвестная ошибка"
                    showError("Ошибка регистрации: $error")
                }
            } catch (e: Exception) {
                showLoading(false)
                showError("Ошибка: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    private fun validateInput(
        firstName: String,
        lastName: String,
        email: String,
        password: String
    ): Boolean {

        if (firstName.isEmpty() || lastName.isEmpty() || email.isEmpty() || password.isEmpty()) {
            showError("Заполните все обязательные поля")
            return false
        }

        if (password.length < 6) {
            showError("Пароль должен содержать минимум 6 символов")
            return false
        }

        if (!isValidEmail(email)) {
            showError("Введите корректный email")
            return false
        }

        return true
    }

    private fun isValidEmail(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    private fun showLoading(show: Boolean) {
        registerButton.isEnabled = !show
        registerButton.text = if (show) "Регистрация..." else "Зарегистрироваться"
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun showSuccess(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun navigateToMainActivity() {
        try {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        } catch (e: Exception) {
            e.printStackTrace()
            showError("Ошибка перехода: ${e.message}")
        }
    }
}