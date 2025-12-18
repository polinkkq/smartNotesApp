package com.example.smartnotes.ui

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.view.GravityCompat
import androidx.core.view.WindowCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.example.smartnotes.R
import com.example.smartnotes.models.Summary
import com.example.smartnotes.repository.AuthRepository
import com.example.smartnotes.repository.NotesRepository
import com.example.smartnotes.repository.RepositoryProvider
import com.example.smartnotes.repository.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FolderContentActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var menuButton: ImageButton
    private lateinit var logoutButton: ImageButton
    private lateinit var userName: TextView
    private lateinit var folderTitleText: TextView
    private lateinit var summariesContainer: LinearLayout
    private lateinit var buttonAuthOrChangePass: Button

    private lateinit var addSummaryBtn: ImageButton
    private lateinit var photoButton: ImageButton
    private lateinit var scanButton: ImageButton

    private val authRepository = AuthRepository()
    private lateinit var notesRepository: NotesRepository
    private lateinit var deleteAccountButton: Button

    private var folderId: String = ""
    private var folderTitle: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_folder_content)

        notesRepository = RepositoryProvider.notes(this)

        folderId = intent.getStringExtra("FOLDER_ID") ?: ""
        folderTitle = intent.getStringExtra("FOLDER_TITLE") ?: "Папка"

        initViews()
        setupClickListeners()

        loadUserData()
        setupDrawerActionButton()
        loadFolderSummaries()
    }

    override fun onResume() {
        super.onResume()
        loadUserData()
        setupDrawerActionButton()
        loadFolderSummaries()
    }

    private fun resolveUserId(): String? {
        return if (SessionManager.isGuestMode(this)) "guest" else authRepository.getCurrentUser()?.uid
    }

    private fun initViews() {
        drawerLayout = findViewById(R.id.drawerLayout)
        menuButton = findViewById(R.id.menuButton)
        logoutButton = findViewById(R.id.logoutButton)
        userName = findViewById(R.id.userName)
        folderTitleText = findViewById(R.id.folderTitleText)
        summariesContainer = findViewById(R.id.summariesContainer)
        buttonAuthOrChangePass = findViewById(R.id.buttonAuthOrChangePass)

        addSummaryBtn = findViewById(R.id.addSummaryBtn)
        photoButton = findViewById(R.id.photo_button)
        scanButton = findViewById(R.id.scan_button)
        deleteAccountButton = findViewById(R.id.deleteAccountButton)
        folderTitleText.text = folderTitle

    }

    private fun setupClickListeners() {
        menuButton.setOnClickListener { drawerLayout.openDrawer(GravityCompat.START) }

        logoutButton.setOnClickListener { logout() }

        // Кнопка в drawer: "Войти" (гость) / "Сменить пароль" (не гость)
        buttonAuthOrChangePass.setOnClickListener { onAuthOrChangePasswordClicked() }

        // "+" добавить несортированные в эту папку
        addSummaryBtn.setOnClickListener { showAddSummaryDialog() }

        photoButton.setOnClickListener {
            startActivity(Intent(this, GalleryPickerActivity::class.java))
        }

        scanButton.setOnClickListener {
            startActivity(Intent(this, ScannerActivity::class.java))
        }
        deleteAccountButton.setOnClickListener {
            showDeleteAccountDialog()
        }
    }

    private fun loadUserData() {
        if (SessionManager.isGuestMode(this)) {
            userName.text = "Гость"
            return
        }
        val currentUser = authRepository.getCurrentUser()
        userName.text = currentUser?.displayName ?: "Пользователь"
    }

    private fun setupDrawerActionButton() {
        val isGuest = SessionManager.isGuestMode(this)
        deleteAccountButton.visibility = if (isGuest) View.GONE else View.VISIBLE

        if (isGuest) {
            buttonAuthOrChangePass.text = "Войти"
            buttonAuthOrChangePass.isEnabled = true
            buttonAuthOrChangePass.isClickable = true
        } else {
            // Если пользователь НЕ гостевой, но парольного провайдера нет (Google),
            // можно оставить "Сменить пароль", но по клику покажем тост.
            buttonAuthOrChangePass.text = "Сменить пароль"
            buttonAuthOrChangePass.isEnabled = true
            buttonAuthOrChangePass.isClickable = true
        }
    }

    private fun onAuthOrChangePasswordClicked() {
        if (SessionManager.isGuestMode(this)) {
            // Переход к авторизации из гостевого режима
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        // Смена пароля
        showChangePasswordDialog()
    }

    private fun loadFolderSummaries() {
        val uid = resolveUserId() ?: return

        CoroutineScope(Dispatchers.Main).launch {
            val result = notesRepository.getSummariesByFolderId(uid, folderId)
            if (result.isSuccess) {
                displaySummaries(result.getOrNull().orEmpty())
            } else {
                displaySummaries(emptyList())
            }
        }
    }

    private fun displaySummaries(summaries: List<Summary>) {
        summariesContainer.removeAllViews()

        if (summaries.isEmpty()) {
            val emptyText = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                text = "В этой папке пока нет конспектов"
                setTextColor(getColor(R.color.text_secondary))
                textSize = 14f
                setPadding(dipToPx(16), dipToPx(16), dipToPx(16), dipToPx(16))
            }
            summariesContainer.addView(emptyText)
            return
        }

        summaries.forEach { summary ->
            summariesContainer.addView(createSummaryView(summary))
        }
    }

    private fun createSummaryView(summary: Summary): CardView {
        return CardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, dipToPx(12)) }

            radius = dipToPx(8).toFloat()
            cardElevation = dipToPx(2).toFloat()
            setCardBackgroundColor(getColor(R.color.background_light))

            addView(LinearLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                orientation = LinearLayout.VERTICAL
                setPadding(dipToPx(16), dipToPx(16), dipToPx(16), dipToPx(16))

                addView(TextView(context).apply {
                    text = summary.title
                    setTextColor(getColor(R.color.text_primary))
                    textSize = 16f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                })

                addView(LinearLayout(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { setMargins(0, dipToPx(4), 0, 0) }

                    orientation = LinearLayout.HORIZONTAL

                    addView(TextView(context).apply {
                        text = getRelativeTime(summary.createdAt)
                        setTextColor(getColor(R.color.text_secondary))
                        textSize = 14f
                    })

                    addView(TextView(context).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            0,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { weight = 1f }
                    })

                    addView(TextView(context).apply {
                        text = "${summary.pageCount} ${getPageCountText(summary.pageCount)}"
                        setTextColor(getColor(R.color.text_secondary))
                        textSize = 14f
                    })
                })
            })

            setOnClickListener { openSummary(summary) }

            // ✅ Удаление из папки по удержанию (перенос в несортированные)
            setOnLongClickListener {
                showRemoveFromFolderDialog(summary)
                true
            }
        }
    }

    private fun openSummary(summary: Summary) {
        startActivity(Intent(this, SummaryViewerActivity::class.java).apply {
            putExtra("SUMMARY_ID", summary.id)
        })
    }

    // ---------------------------
    // ДОБАВЛЕНИЕ КОНСПЕКТОВ В ПАПКУ (из несортированных)
    // ---------------------------
    private fun showAddSummaryDialog() {
        val uid = resolveUserId()
        if (uid.isNullOrBlank()) return

        CoroutineScope(Dispatchers.Main).launch {
            val res = notesRepository.getUnsortedSummaries(uid)
            if (!res.isSuccess) {
                Toast.makeText(
                    this@FolderContentActivity,
                    "Ошибка загрузки несортированных конспектов",
                    Toast.LENGTH_LONG
                ).show()
                return@launch
            }

            val unsorted = res.getOrNull().orEmpty()
            if (unsorted.isEmpty()) {
                Toast.makeText(
                    this@FolderContentActivity,
                    "Нет несортированных конспектов",
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }

            val listLayout = LinearLayout(this@FolderContentActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dipToPx(8), dipToPx(8), dipToPx(8), dipToPx(8))
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }

            unsorted.forEach { s ->
                val cb = CheckBox(this@FolderContentActivity).apply {
                    text = "${s.title} — ${formatDate(s.createdAt)}"
                    textSize = 14f
                    tag = s.id
                    setPadding(dipToPx(6), dipToPx(6), dipToPx(6), dipToPx(6))
                }
                listLayout.addView(cb)
            }

            val scroll = ScrollView(this@FolderContentActivity).apply {
                addView(listLayout)
            }

            AlertDialog.Builder(this@FolderContentActivity)
                .setTitle("Добавить конспект в папку")
                .setView(scroll)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Добавить") { _, _ ->
                    val selectedIds = mutableListOf<String>()
                    for (i in 0 until listLayout.childCount) {
                        val v = listLayout.getChildAt(i)
                        if (v is CheckBox && v.isChecked) {
                            val id = v.tag as? String
                            if (!id.isNullOrBlank()) selectedIds.add(id)
                        }
                    }

                    if (selectedIds.isEmpty()) {
                        Toast.makeText(
                            this@FolderContentActivity,
                            "Ничего не выбрано",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@setPositiveButton
                    }

                    CoroutineScope(Dispatchers.Main).launch {
                        val addRes = notesRepository.addSummariesToFolder(
                            userId = uid,
                            folderId = folderId,
                            summaryIds = selectedIds
                        )

                        if (addRes.isSuccess) {
                            Toast.makeText(
                                this@FolderContentActivity,
                                "Добавлено: ${selectedIds.size}",
                                Toast.LENGTH_SHORT
                            ).show()
                            loadFolderSummaries()
                        } else {
                            Toast.makeText(
                                this@FolderContentActivity,
                                "Ошибка добавления: ${addRes.exceptionOrNull()?.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
                .show()
        }
    }

    // ---------------------------
    // УДАЛИТЬ КОНСПЕКТ ИЗ ПАПКИ (folderId -> "")
    // ---------------------------
    private fun showRemoveFromFolderDialog(summary: Summary) {
        val uid = resolveUserId() ?: return

        AlertDialog.Builder(this@FolderContentActivity)
            .setTitle("Убрать из папки?")
            .setMessage("Конспект «${summary.title}» будет перемещён в «Несортированные».")
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Убрать") { _, _ ->
                CoroutineScope(Dispatchers.Main).launch {
                    val res = notesRepository.removeSummaryFromFolder(uid, summary.id)
                    if (res.isSuccess) {
                        Toast.makeText(this@FolderContentActivity, "Конспект перемещён", Toast.LENGTH_SHORT).show()
                        loadFolderSummaries()
                    } else {
                        Toast.makeText(this@FolderContentActivity, "Ошибка: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .show()
    }

    // ---------------------------
    // СМЕНА ПАРОЛЯ
    // ---------------------------
    private fun showChangePasswordDialog() {
        if (SessionManager.isGuestMode(this)) {
            Toast.makeText(
                this@FolderContentActivity,
                "В гостевом режиме пароль недоступен",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (!authRepository.hasPasswordProvider()) {
            Toast.makeText(
                this@FolderContentActivity,
                "Смена пароля доступна только для входа по email/паролю",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val layout = LinearLayout(this@FolderContentActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dipToPx(16), dipToPx(8), dipToPx(16), dipToPx(8))
        }

        val currentPassInput = EditText(this@FolderContentActivity).apply {
            hint = "Текущий пароль"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        val newPassInput = EditText(this@FolderContentActivity).apply {
            hint = "Новый пароль"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        layout.addView(currentPassInput)
        layout.addView(newPassInput)

        AlertDialog.Builder(this@FolderContentActivity)
            .setTitle("Сменить пароль")
            .setView(layout)
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Сменить") { _, _ ->
                val currentPass = currentPassInput.text.toString().trim()
                val newPass = newPassInput.text.toString().trim()

                if (currentPass.isBlank() || newPass.isBlank()) {
                    Toast.makeText(
                        this@FolderContentActivity,
                        "Заполните оба поля",
                        Toast.LENGTH_LONG
                    ).show()
                    return@setPositiveButton
                }

                if (newPass.length < 6) {
                    Toast.makeText(
                        this@FolderContentActivity,
                        "Новый пароль минимум 6 символов",
                        Toast.LENGTH_LONG
                    ).show()
                    return@setPositiveButton
                }

                CoroutineScope(Dispatchers.IO).launch {
                    val res = authRepository.changePassword(currentPass, newPass)
                    runOnUiThread {
                        if (res.isSuccess) {
                            Toast.makeText(
                                this@FolderContentActivity,
                                "Пароль изменён",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            Toast.makeText(
                                this@FolderContentActivity,
                                "Ошибка: ${res.exceptionOrNull()?.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }
            .show()
    }

    private fun logout() {
        if (SessionManager.isGuestMode(this)) {
            SessionManager.clear(this)
        } else {
            authRepository.logoutUser()
            SessionManager.clear(this)
        }
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    private fun getPageCountText(count: Int): String {
        return when {
            count % 10 == 1 && count % 100 != 11 -> "страница"
            count % 10 in 2..4 && count % 100 !in 12..14 -> "страницы"
            else -> "страниц"
        }
    }

    private fun dipToPx(dip: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dip.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    private fun getRelativeTime(timestampMillis: Long): String {
        val currentTime = System.currentTimeMillis()
        val diff = currentTime - timestampMillis

        return when {
            diff < 60 * 1000 -> "Только что"
            diff < 60 * 60 * 1000 -> "${(diff / (60 * 1000))} мин. назад"
            diff < 24 * 60 * 60 * 1000 -> "${(diff / (60 * 60 * 1000))} ч. назад"
            diff < 2 * 24 * 60 * 60 * 1000 -> "Вчера"
            diff < 7 * 24 * 60 * 60 * 1000 -> "Неделю назад"
            else -> {
                val date = Date(timestampMillis)
                val format = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                format.format(date)
            }
        }
    }

    private fun formatDate(timestampMillis: Long): String {
        val format = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        return format.format(Date(timestampMillis))
    }
    private fun showDeleteAccountDialog() {
        if (SessionManager.isGuestMode(this)) {
            Toast.makeText(this, "Гостевой аккаунт удалять нельзя", Toast.LENGTH_SHORT).show()
            return
        }

        // если вход по email/паролю — просим пароль для reauth
        if (authRepository.hasPasswordProvider()) {
            val passInput = EditText(this).apply {
                hint = "Пароль"
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }

            AlertDialog.Builder(this)
                .setTitle("Удалить аккаунт?")
                .setMessage("Аккаунт и все данные будут удалены без возможности восстановления.")
                .setView(passInput)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Удалить") { _, _ ->
                    val pass = passInput.text.toString().trim()
                    if (pass.isBlank()) {
                        Toast.makeText(this, "Пароль обязателен", Toast.LENGTH_LONG).show()
                        return@setPositiveButton
                    }

                    CoroutineScope(Dispatchers.Main).launch {
                        val res = authRepository.reauthAndDeleteAccount(pass)
                        if (res.isSuccess) {
                            Toast.makeText(this@FolderContentActivity, "Аккаунт удалён", Toast.LENGTH_SHORT).show()
                            SessionManager.clear(this@FolderContentActivity)
                            startActivity(Intent(this@FolderContentActivity, LoginActivity::class.java))
                            finish()
                        } else {
                            Toast.makeText(this@FolderContentActivity, "Ошибка: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
                .show()

            return
        }

        // Google и др. — пробуем просто удалить
        AlertDialog.Builder(this)
            .setTitle("Удалить аккаунт?")
            .setMessage("Аккаунт и все данные будут удалены. Если появится ошибка «нужен повторный вход», выйди и войди заново, затем повтори.")
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Удалить") { _, _ ->
                CoroutineScope(Dispatchers.Main).launch {
                    val res = authRepository.deleteAccount()
                    if (res.isSuccess) {
                        Toast.makeText(this@FolderContentActivity, "Аккаунт удалён", Toast.LENGTH_SHORT).show()
                        SessionManager.clear(this@FolderContentActivity)
                        startActivity(Intent(this@FolderContentActivity, LoginActivity::class.java))
                        finish()
                    } else {
                        Toast.makeText(this@FolderContentActivity, "Ошибка: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .show()
    }

}
