package com.example.smartnotes.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.drawerlayout.widget.DrawerLayout
import com.example.smartnotes.R
import com.example.smartnotes.models.Folder
import com.example.smartnotes.models.Summary
import com.example.smartnotes.repository.AuthRepository
import com.example.smartnotes.repository.FirebaseRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

    private val authRepository = AuthRepository()
    private val firebaseRepository = FirebaseRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)

        initViews()
        setupClickListeners()
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
    }

    private fun setupClickListeners() {
        menuButton.setOnClickListener {
            drawerLayout.open()
        }

        logoutButton.setOnClickListener {
            logout()
        }

        photoButton.setOnClickListener {
            selectPhotoFromGallery()
        }

        scanButton.setOnClickListener {
            startScanning()
        }
    }

    private fun loadUserData() {
        // Временное решение без Firebase
        userName.text = "Иванов Иван"
    }

    private fun loadFolders() {
        // Тестовые данные папок
        val testFolders = listOf(
            Folder(
                id = "1",
                title = "История",
                userId = "1",
                createdAt = System.currentTimeMillis(),
                summaryCount = 2
            ),
            Folder(
                id = "2",
                title = "Математика",
                userId = "1",
                createdAt = System.currentTimeMillis() - 24 * 60 * 60 * 1000,
                summaryCount = 1
            ),
            Folder(
                id = "3",
                title = "Физика",
                userId = "1",
                createdAt = System.currentTimeMillis() - 2 * 24 * 60 * 60 * 1000,
                summaryCount = 0
            )
        )

        displayFolders(testFolders)
    }

    private fun displayFolders(folders: List<Folder>) {
        foldersContainer.removeAllViews()

        // Показываем только первые 2 папки (или все если меньше)
        val foldersToShow = folders.take(2)

        foldersToShow.forEach { folder ->
            val folderView = createFolderView(folder)
            foldersContainer.addView(folderView)
        }
    }

    private fun createFolderView(folder: Folder): CardView {
        return CardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, dipToPx(12))
            }
            radius = dipToPx(12).toFloat()
            cardElevation = dipToPx(4).toFloat()

            addView(LinearLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                orientation = LinearLayout.VERTICAL
                setPadding(dipToPx(16), dipToPx(16), dipToPx(16), dipToPx(16))

                // Название папки
                addView(TextView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    text = folder.title
                    setTextColor(Color.parseColor("#2C2C2C"))
                    textSize = 16f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                })

                // Дата создания
                addView(TextView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(0, dipToPx(4), 0, 0)
                    }
                    text = getRelativeTime(folder.createdAt)
                    setTextColor(Color.parseColor("#666666"))
                    textSize = 14f
                })

                // Количество конспектов
                addView(TextView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(0, dipToPx(8), 0, 0)
                    }
                    text = "${folder.summaryCount} ${getSummaryCountText(folder.summaryCount)}"
                    setTextColor(Color.parseColor("#666666"))
                    textSize = 14f
                })
            })

            setOnClickListener {
                openFolder(folder)
            }
        }
    }

    private fun getSummaryCountText(count: Int): String {
        return when {
            count % 10 == 1 && count % 100 != 11 -> "конспект"
            count % 10 in 2..4 && count % 100 !in 12..14 -> "конспекта"
            else -> "конспектов"
        }
    }

    private fun loadSummaries() {
        // Тестовые данные конспектов
        val testSummaries = listOf(
            Summary(
                id = "1",
                title = "История России 01.01.2025",
                createdAt = System.currentTimeMillis(),
                folderId = "1" // принадлежит папке "История"
            ),
            Summary(
                id = "2",
                title = "Мобильная разработка 01.01.2025",
                createdAt = System.currentTimeMillis() - 24 * 60 * 60 * 1000,
                folderId = null // несортированный
            ),
            Summary(
                id = "3",
                title = "Алгебра 01.01.2025",
                createdAt = System.currentTimeMillis() - 2 * 24 * 60 * 60 * 1000,
                folderId = "2" // принадлежит папке "Математика"
            ),
            Summary(
                id = "4",
                title = "Программирование 01.01.2025",
                createdAt = System.currentTimeMillis() - 3 * 24 * 60 * 60 * 1000,
                folderId = null // несортированный
            )
        )

        // Фильтруем только несортированные конспекты (без папки)
        val unsortedSummaries = testSummaries.filter { it.folderId == null }
        displaySummaries(unsortedSummaries)
    }

    private fun displaySummaries(summaries: List<Summary>) {
        unsortedContainer.removeAllViews()

        summaries.forEach { summary ->
            val summaryView = createSummaryView(summary)
            unsortedContainer.addView(summaryView)
        }
    }

    private fun createSummaryView(summary: Summary): CardView {
        return CardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, dipToPx(12))
            }
            radius = dipToPx(8).toFloat()
            cardElevation = dipToPx(2).toFloat()

            addView(LinearLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                orientation = LinearLayout.VERTICAL
                setPadding(dipToPx(16), dipToPx(16), dipToPx(16), dipToPx(16))

                // Заголовок конспекта
                addView(TextView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    text = summary.title
                    setTextColor(Color.parseColor("#2C2C2C"))
                    textSize = 16f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                })

                // Строка с датой и количеством страниц
                addView(LinearLayout(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(0, dipToPx(4), 0, 0)
                    }
                    orientation = LinearLayout.HORIZONTAL

                    // Дата
                    addView(TextView(context).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        text = getRelativeTime(summary.createdAt)
                        setTextColor(Color.parseColor("#666666"))
                        textSize = 14f
                    })

                    // Пространство между
                    addView(TextView(context).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            0,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            weight = 1f
                        }
                    })

                    // Количество страниц
                    addView(TextView(context).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        text = "1 страница"
                        setTextColor(Color.parseColor("#666666"))
                        textSize = 14f
                    })
                })
            })

            setOnClickListener {
                openSummary(summary)
            }
        }
    }

    private fun dipToPx(dip: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dip.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    private fun getRelativeTime(timestamp: Long): String {
        val currentTime = System.currentTimeMillis()
        val diff = currentTime - timestamp

        return when {
            diff < 24 * 60 * 60 * 1000 -> "Сегодня"
            diff < 2 * 24 * 60 * 60 * 1000 -> "Вчера"
            diff < 7 * 24 * 60 * 60 * 1000 -> "Неделю назад"
            else -> {
                val date = Date(timestamp)
                val format = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                format.format(date)
            }
        }
    }

    private fun openFolder(folder: Folder) {
        Toast.makeText(this, "Открыта папка: ${folder.title}", Toast.LENGTH_SHORT).show()
        // Здесь будет переход к папке
    }

    private fun openSummary(summary: Summary) {
        Toast.makeText(this, "Открыт конспект: ${summary.title}", Toast.LENGTH_SHORT).show()
    }

    private fun selectPhotoFromGallery() {
        Toast.makeText(this, "Выбор фото из галереи", Toast.LENGTH_SHORT).show()
    }

    private fun startScanning() {
        Toast.makeText(this, "Запуск сканирования", Toast.LENGTH_SHORT).show()
    }

    private fun logout() {
        authRepository.logoutUser()
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
        finish()
    }

    fun refreshData() {
        loadFolders()
        loadSummaries()
    }
}