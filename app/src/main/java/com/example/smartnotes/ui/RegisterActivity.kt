package com.example.smartnotes.ui

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.smartnotes.R
import com.google.android.material.textfield.TextInputEditText
import com.example.smartnotes.models.UserRole
import com.example.smartnotes.repository.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min
import java.util.Calendar

class RegistrationActivity : AppCompatActivity() {

    private lateinit var nameRegistration: TextInputEditText
    private lateinit var lastNameRegistration: TextInputEditText
    private lateinit var emailRegistration: TextInputEditText
    private lateinit var birthdayRegistration: EditText
    private lateinit var phoneRegistration: EditText
    private lateinit var passwordRegistration: EditText
    private lateinit var registerButton: Button
    private lateinit var roleRadioGroup: RadioGroup
    private lateinit var radioStudent: RadioButton
    private lateinit var radioTeacher: RadioButton

    private val authRepository = AuthRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        initViews()
        setupClickListeners()
        setupPhoneMask()
        setupDatePicker()
    }

    private fun initViews() {
        nameRegistration = findViewById(R.id.nameRegistration)
        lastNameRegistration = findViewById(R.id.lastnameRegistration)
        emailRegistration = findViewById(R.id.emailRegistration)
        birthdayRegistration = findViewById(R.id.birthdayRegistration)
        phoneRegistration = findViewById(R.id.phoneRegistration)
        passwordRegistration = findViewById(R.id.passwordRegistration)
        registerButton = findViewById(R.id.buttonRegistration)
        roleRadioGroup = findViewById(R.id.roleGroup)
        radioStudent = findViewById(R.id.roleStudent)
        radioTeacher = findViewById(R.id.roleTeacher)
    }

    private fun setupClickListeners() {
        registerButton.setOnClickListener {
            registerUser()
        }

        birthdayRegistration.setOnClickListener {
            showDatePickerDialog()
        }
    }
    private fun setupPhoneMask() {
        phoneRegistration.addTextChangedListener(object : TextWatcher {
            private var isFormatting = false

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (isFormatting) return
                isFormatting = true

                if (!s.isNullOrEmpty()) {
                    val cleanString = s.toString().replace(Regex("[^\\d]"), "")

                    when {
                        cleanString.isEmpty() -> {
                            // Оставляем поле пустым
                        }
                        cleanString.length == 1 -> {
                            if (cleanString != "7") {
                                s.replace(0, s.length, "+7")
                            } else {
                                s.replace(0, s.length, "+7")
                            }
                        }
                        cleanString.length in 2..11 -> {
                            val formatted = StringBuilder("+7")

                            if (cleanString.length > 1) {
                                formatted.append(" (")
                                formatted.append(cleanString.substring(1, min(4, cleanString.length)))
                            }

                            if (cleanString.length >= 4) {
                                formatted.append(") ")
                                formatted.append(cleanString.substring(4, min(7, cleanString.length)))
                            }

                            if (cleanString.length >= 7) {
                                formatted.append("-")
                                formatted.append(cleanString.substring(7, min(9, cleanString.length)))
                            }

                            if (cleanString.length >= 9) {
                                formatted.append("-")
                                formatted.append(cleanString.substring(9, min(11, cleanString.length)))
                            }

                            s.replace(0, s.length, formatted.toString())
                        }
                        cleanString.length > 11 -> {
                            val trimmed = cleanString.substring(0, 11)
                            val formatted = StringBuilder("+7")
                            formatted.append(" (")
                            formatted.append(trimmed.substring(1, 4))
                            formatted.append(") ")
                            formatted.append(trimmed.substring(4, 7))
                            formatted.append("-")
                            formatted.append(trimmed.substring(7, 9))
                            formatted.append("-")
                            formatted.append(trimmed.substring(9, 11))
                            s.replace(0, s.length, formatted.toString())
                        }
                    }

                    phoneRegistration.setSelection(phoneRegistration.text?.length ?: 0)
                }

                isFormatting = false
            }
        })
    }

    private fun setupDatePicker() {
        birthdayRegistration.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (s.isNullOrEmpty()) return

                val text = s.toString()
                if (text.length == 2 || text.length == 5) {
                    if (!text.endsWith(".")) {
                        s.append(".")
                    }
                }
            }
        })
    }

    private fun showDatePickerDialog() {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val datePickerDialog = DatePickerDialog(
            this,
            { _, selectedYear, selectedMonth, selectedDay ->
                val formattedDate = String.format("%02d.%02d.%d", selectedDay, selectedMonth + 1, selectedYear)
                birthdayRegistration.setText(formattedDate)
            },
            year, month, day
        )

        datePickerDialog.datePicker.maxDate = System.currentTimeMillis()
        datePickerDialog.show()
    }
    private fun registerUser() {
        val firstName = nameRegistration.text.toString().trim()
        val lastName = lastNameRegistration.text.toString().trim()
        val email = emailRegistration.text.toString().trim()
        val password = passwordRegistration.text.toString().trim()
        val birthDate = birthdayRegistration.text.toString().trim()
        val phone = phoneRegistration.text.toString().trim()

        // Определяем выбранную роль
        val selectedRole = when (roleRadioGroup.checkedRadioButtonId) {
            R.id.roleTeacher -> UserRole.TEACHER
            else -> UserRole.STUDENT
        }

        // Валидация
        if (!validateInput(firstName, lastName, email, password, birthDate, phone)) {
            return
        }

        showLoading(true)

        // Используем lifecycleScope вместо CoroutineScope
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    authRepository.registerUser(
                        email = email,
                        password = password,
                        firstName = firstName,
                        lastName = lastName,
                        role = selectedRole.name.lowercase(),
                        birthDate = if (birthDate.isNotEmpty()) birthDate else null,
                        phone = if (phone.isNotEmpty()) phone else null
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
        password: String,
        birthDate: String,
        phone: String
    ): Boolean {
        // твой существующий код валидации
        if (firstName.isEmpty() || lastName.isEmpty() || email.isEmpty() || password.isEmpty()) {
            showError("Заполните обязательные поля: Имя, Фамилия, Email и Пароль")
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

        if (birthDate.isNotEmpty() && !isValidDate(birthDate)) {
            showError("Введите корректную дату в формате дд.мм.гггг")
            return false
        }

        if (phone.isNotEmpty() && !isValidPhone(phone)) {
            showError("Введите корректный номер телефона")
            return false
        }

        return true
    }

    private fun isValidEmail(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    private fun isValidPhone(phone: String): Boolean {
        val cleanPhone = phone.replace(Regex("[^\\d]"), "")
        return cleanPhone.length == 11 && cleanPhone.startsWith("7")
    }

    private fun isValidDate(date: String): Boolean {
        return try {
            val sdf = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault())
            sdf.isLenient = false
            sdf.parse(date)
            true
        } catch (e: Exception) {
            false
        }
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