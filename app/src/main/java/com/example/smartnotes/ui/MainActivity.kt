package com.example.smartnotes.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.view.GravityCompat
import androidx.core.view.WindowCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import com.example.smartnotes.R
import com.example.smartnotes.models.Folder
import com.example.smartnotes.models.Summary
import com.example.smartnotes.repository.AuthRepository
import com.example.smartnotes.repository.NotesRepository
import com.example.smartnotes.repository.RepositoryProvider
import com.example.smartnotes.repository.SessionManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var menuButton: ImageButton
    private lateinit var logoutButton: ImageButton
    private lateinit var userName: TextView

    private lateinit var unsortedContainer: LinearLayout
    private lateinit var foldersContainer: LinearLayout

    private lateinit var photoButton: ImageButton
    private lateinit var scanButton: ImageButton
    private lateinit var createFolderButton: ImageButton

    private lateinit var buttonAuthOrChangePass: Button
    private lateinit var deleteAccountButton: Button

    private val authRepository = AuthRepository()
    private lateinit var notesRepository: NotesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.main_activity)

        notesRepository = RepositoryProvider.notes(this)

        initViews()
        setupClickListeners()
        applyDrawerAuthUi()

        loadUserData()
        loadFolders()
        loadSummaries()
    }

    override fun onResume() {
        super.onResume()
        notesRepository = RepositoryProvider.notes(this)
        applyDrawerAuthUi()
        loadUserData()
        loadFolders()
        loadSummaries()
    }

    private fun initViews() {
        drawerLayout = findViewById(R.id.drawerLayout)
        menuButton = findViewById(R.id.menuButton)
        logoutButton = findViewById(R.id.logoutButton)
        userName = findViewById(R.id.userName)

        unsortedContainer = findViewById(R.id.unsortedContainer)
        foldersContainer = findViewById(R.id.foldersContainer)

        photoButton = findViewById(R.id.photo_button)
        scanButton = findViewById(R.id.scan_button)
        createFolderButton = findViewById(R.id.create_folder_button)

        buttonAuthOrChangePass = findViewById(R.id.buttonAuthOrChangePass)
        deleteAccountButton = findViewById(R.id.deleteAccountButton)
    }

    private fun setupClickListeners() {
        menuButton.setOnClickListener { drawerLayout.openDrawer(GravityCompat.START) }

        logoutButton.setOnClickListener { logout() }

        photoButton.setOnClickListener { startActivity(Intent(this, GalleryPickerActivity::class.java)) }

        scanButton.setOnClickListener { startActivity(Intent(this, ScannerActivity::class.java)) }

        createFolderButton.setOnClickListener {
            startActivity(Intent(this, CreateFolderActivity::class.java))
        }

        buttonAuthOrChangePass.setOnClickListener {
            if (SessionManager.isGuestMode(this)) {
                SessionManager.setGuestMode(this, false)
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            } else {
                showChangePasswordDialog()
            }
        }

        deleteAccountButton.setOnClickListener { showDeleteAccountDialog() }
    }

    private fun applyDrawerAuthUi() {
        val isGuest = SessionManager.isGuestMode(this)
        if (isGuest) {
            buttonAuthOrChangePass.text = "Войти / Авторизация"
            userName.text = "Гость"
            deleteAccountButton.visibility = View.GONE
        } else {
            buttonAuthOrChangePass.text = "Сменить пароль"
            deleteAccountButton.visibility = View.VISIBLE
        }
    }

    private fun resolveUserId(): String? {
        return if (SessionManager.isGuestMode(this)) "guest" else authRepository.getCurrentUser()?.uid
    }

    private fun loadUserData() {
        if (SessionManager.isGuestMode(this)) {
            userName.text = "Гость"
            return
        }
        val currentUser = authRepository.getCurrentUser()
        userName.text = currentUser?.displayName ?: "Пользователь"
    }

    private fun loadFolders() {
        val uid = resolveUserId() ?: return
        lifecycleScope.launch {
            val res = notesRepository.getUserFolders(uid)
            displayFolders(res.getOrNull().orEmpty())
        }
    }

    private fun loadSummaries() {
        val uid = resolveUserId() ?: return
        lifecycleScope.launch {
            val res = notesRepository.getUnsortedSummaries(uid)
            displaySummaries(res.getOrNull().orEmpty())
        }
    }

    private fun displayFolders(folders: List<Folder>) {
        foldersContainer.removeAllViews()

        if (folders.isEmpty()) {
            foldersContainer.addView(TextView(this).apply {
                text = "Папок пока нет"
                setTextColor(Color.parseColor("#666666"))
                textSize = 14f
                setPadding(dipToPx(16), dipToPx(16), dipToPx(16), dipToPx(16))
            })
            return
        }

        folders.forEach { folder ->
            foldersContainer.addView(createFolderView(folder))
        }
    }

    private fun createFolderView(folder: Folder): CardView {
        return CardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, dipToPx(12)) }

            radius = dipToPx(12).toFloat()
            cardElevation = dipToPx(4).toFloat()

            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dipToPx(16), dipToPx(16), dipToPx(16), dipToPx(16))

                addView(TextView(context).apply {
                    text = folder.title
                    setTextColor(Color.parseColor("#2C2C2C"))
                    textSize = 16f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                })

                addView(TextView(context).apply {
                    setPadding(0, dipToPx(4), 0, 0)
                    text = getRelativeTime(folder.createdAt)
                    setTextColor(Color.parseColor("#666666"))
                    textSize = 14f
                })

                addView(TextView(context).apply {
                    setPadding(0, dipToPx(8), 0, 0)
                    text = "${folder.summaryCount} ${getSummaryCountText(folder.summaryCount)}"
                    setTextColor(Color.parseColor("#666666"))
                    textSize = 14f
                })
            })

            setOnClickListener { openFolder(folder) }
            setOnLongClickListener {
                showDeleteFolderDialog(folder)
                true
            }
        }
    }

    private fun displaySummaries(summaries: List<Summary>) {
        unsortedContainer.removeAllViews()

        if (summaries.isEmpty()) {
            unsortedContainer.addView(TextView(this).apply {
                text = "Несортированных конспектов пока нет"
                setTextColor(Color.parseColor("#666666"))
                textSize = 14f
                setPadding(dipToPx(16), dipToPx(16), dipToPx(16), dipToPx(16))
            })
            return
        }

        summaries.forEach { summary ->
            unsortedContainer.addView(createSummaryView(summary))
        }
    }

    private fun createSummaryView(summary: Summary): CardView {
        val contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dipToPx(16), dipToPx(16), dipToPx(16), dipToPx(16))

            addView(LinearLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    weight = 1f
                }
                orientation = LinearLayout.VERTICAL

                addView(TextView(context).apply {
                    text = summary.title
                    setTextColor(Color.parseColor("#2C2C2C"))
                    textSize = 16f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                })

                addView(LinearLayout(context).apply {
                    setPadding(0, dipToPx(4), 0, 0)
                    orientation = LinearLayout.HORIZONTAL

                    addView(TextView(context).apply {
                        text = getRelativeTime(summary.createdAt)
                        setTextColor(Color.parseColor("#666666"))
                        textSize = 14f
                    })

                    addView(View(context).apply {
                        layoutParams = LinearLayout.LayoutParams(0, 0, 1f)
                    })

                    addView(TextView(context).apply {
                        text = "${summary.pageCount} ${getPageCountText(summary.pageCount)}"
                        setTextColor(Color.parseColor("#666666"))
                        textSize = 14f
                    })
                })
            })
        }

        return CardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, dipToPx(12)) }

            radius = dipToPx(8).toFloat()
            cardElevation = dipToPx(2).toFloat()

            addView(contentLayout)

            setOnClickListener { openSummary(summary) }
            setOnLongClickListener {
                showDeleteSummaryDialog(summary)
                true
            }
        }
    }


    private fun openFolder(folder: Folder) {
        startActivity(Intent(this, FolderContentActivity::class.java).apply {
            putExtra("FOLDER_ID", folder.id)
            putExtra("FOLDER_TITLE", folder.title)
        })
    }

    private fun openSummary(summary: Summary) {
        startActivity(Intent(this, SummaryViewerActivity::class.java).apply {
            putExtra("SUMMARY_ID", summary.id)
        })
    }

    private fun showDeleteFolderDialog(folder: Folder) {
        val uid = resolveUserId() ?: return

        AlertDialog.Builder(this)
            .setTitle("Удалить папку?")
            .setMessage("Выбери вариант удаления папки «${folder.title}».")
            .setNegativeButton("Отмена", null)
            .setNeutralButton("Удалить папку, конспекты оставить") { _, _ ->
                lifecycleScope.launch {
                    val res = notesRepository.deleteFolderMoveSummariesToUnsorted(uid, folder.id)
                    if (res.isSuccess) {
                        Toast.makeText(this@MainActivity, "Папка удалена", Toast.LENGTH_SHORT).show()
                        loadFolders()
                        loadSummaries()
                    } else {
                        Toast.makeText(this@MainActivity, "Ошибка: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setPositiveButton("Удалить папку и конспекты") { _, _ ->
                lifecycleScope.launch {
                    val res = notesRepository.deleteFolderWithSummaries(uid, folder.id)
                    if (res.isSuccess) {
                        Toast.makeText(this@MainActivity, "Папка и конспекты удалены", Toast.LENGTH_SHORT).show()
                        loadFolders()
                        loadSummaries()
                    } else {
                        Toast.makeText(this@MainActivity, "Ошибка: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .show()
    }

    private fun showDeleteSummaryDialog(summary: Summary) {
        val uid = resolveUserId() ?: return

        AlertDialog.Builder(this)
            .setTitle("Удалить конспект?")
            .setMessage("Конспект «${summary.title}» и его страницы будут удалены без восстановления.")
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Удалить") { _, _ ->
                lifecycleScope.launch {
                    val res = notesRepository.deleteSummary(uid, summary.id)
                    if (res.isSuccess) {
                        Toast.makeText(this@MainActivity, "Конспект удалён", Toast.LENGTH_SHORT).show()
                        loadFolders()
                        loadSummaries()
                    } else {
                        Toast.makeText(this@MainActivity, "Ошибка: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .show()
    }

    private fun showChangePasswordDialog() {
        if (SessionManager.isGuestMode(this)) {
            Toast.makeText(this, "Гость не может менять пароль", Toast.LENGTH_SHORT).show()
            return
        }

        if (!authRepository.hasPasswordProvider()) {
            Toast.makeText(this, "Смена пароля доступна только для email/пароля", Toast.LENGTH_LONG).show()
            return
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dipToPx(16), dipToPx(8), dipToPx(16), dipToPx(8))
        }

        val currentPassInput = EditText(this).apply {
            hint = "Текущий пароль"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        val newPassInput = EditText(this).apply {
            hint = "Новый пароль"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        layout.addView(currentPassInput)
        layout.addView(newPassInput)

        AlertDialog.Builder(this)
            .setTitle("Сменить пароль")
            .setView(layout)
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Сменить") { _, _ ->
                val currentPass = currentPassInput.text.toString().trim()
                val newPass = newPassInput.text.toString().trim()

                if (currentPass.isBlank() || newPass.isBlank()) {
                    Toast.makeText(this, "Заполни оба поля", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                if (newPass.length < 6) {
                    Toast.makeText(this, "Новый пароль минимум 6 символов", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }

                lifecycleScope.launch {
                    val res = authRepository.changePassword(currentPass, newPass)
                    if (res.isSuccess) {
                        Toast.makeText(this@MainActivity, "Пароль изменён", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@MainActivity, "Ошибка: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .show()
    }

    private fun showDeleteAccountDialog() {
        if (SessionManager.isGuestMode(this)) {
            Toast.makeText(this, "Гостевой аккаунт удалять нельзя", Toast.LENGTH_SHORT).show()
            return
        }

        // Для email/password попросим пароль -> реавторизация -> удаление
        if (authRepository.hasPasswordProvider()) {
            val passInput = EditText(this).apply {
                hint = "Пароль"
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }

            AlertDialog.Builder(this)
                .setTitle("Удалить аккаунт?")
                .setMessage("Это удалит аккаунт и все данные (папки, конспекты, страницы). Действие необратимо.")
                .setView(passInput)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Удалить") { _, _ ->
                    val pass = passInput.text.toString().trim()
                    if (pass.isBlank()) {
                        Toast.makeText(this, "Пароль обязателен", Toast.LENGTH_LONG).show()
                        return@setPositiveButton
                    }

                    lifecycleScope.launch {
                        val res = authRepository.reauthAndDeleteAccount(pass)
                        if (res.isSuccess) {
                            Toast.makeText(this@MainActivity, "Аккаунт удалён", Toast.LENGTH_SHORT).show()
                            goToLoginAfterAccountDelete()
                        } else {
                            Toast.makeText(this@MainActivity, "Ошибка: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
                .show()

            return
        }

        // Для Google/других провайдеров просто пробуем удалить
        AlertDialog.Builder(this)
            .setTitle("Удалить аккаунт?")
            .setMessage("Это удалит аккаунт и все данные. Если появится ошибка «нужен повторный вход», выйди и войди заново, затем повтори удаление.")
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Удалить") { _, _ ->
                lifecycleScope.launch {
                    val res = authRepository.deleteAccount()
                    if (res.isSuccess) {
                        Toast.makeText(this@MainActivity, "Аккаунт удалён", Toast.LENGTH_SHORT).show()
                        goToLoginAfterAccountDelete()
                    } else {
                        Toast.makeText(this@MainActivity, "Ошибка: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .show()
    }

    private fun goToLoginAfterAccountDelete() {
        SessionManager.clear(this)
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    private fun logout() {
        SessionManager.clear(this)
        if (!SessionManager.isGuestMode(this)) {
            authRepository.logoutUser()
        }
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    private fun getSummaryCountText(count: Int): String {
        return when {
            count % 10 == 1 && count % 100 != 11 -> "конспект"
            count % 10 in 2..4 && count % 100 !in 12..14 -> "конспекта"
            else -> "конспектов"
        }
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
            else -> SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(timestampMillis))
        }
    }
}
