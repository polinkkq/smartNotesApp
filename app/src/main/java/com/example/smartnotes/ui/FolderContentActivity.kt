package com.example.smartnotes.ui

import android.os.Bundle
import android.util.TypedValue
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.drawerlayout.widget.DrawerLayout
import com.example.smartnotes.R
import com.example.smartnotes.models.Summary
import com.example.smartnotes.repository.AuthRepository
import com.example.smartnotes.repository.FirebaseRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class FolderContentActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var menuButton: ImageButton
    private lateinit var logoutButton: ImageButton
    private lateinit var userName: TextView
    private lateinit var folderTitleText: TextView
    private lateinit var summariesContainer: LinearLayout

    private val authRepository = AuthRepository()
    private val firebaseRepository = FirebaseRepository()

    private var folderId: String = ""
    private var folderTitle: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_folder_content)

        // Получаем данные из интента
        folderId = intent.getStringExtra("FOLDER_ID") ?: ""
        folderTitle = intent.getStringExtra("FOLDER_TITLE") ?: "Папка"

        initViews()
        setupClickListeners()
        loadUserData()
        loadFolderSummaries()
    }

    private fun initViews() {
        drawerLayout = findViewById(R.id.drawerLayout)
        menuButton = findViewById(R.id.menuButton)
        logoutButton = findViewById(R.id.logoutButton)
        userName = findViewById(R.id.userName)
        folderTitleText = findViewById(R.id.folderTitleText)
        summariesContainer = findViewById(R.id.summariesContainer)

        folderTitleText.text = folderTitle
    }

    private fun setupClickListeners() {
        menuButton.setOnClickListener {
            drawerLayout.open()
        }

        logoutButton.setOnClickListener {
            logout()
        }
    }

    private fun loadUserData() {
        val currentUser = authRepository.getCurrentUser()
        currentUser?.let { firebaseUser ->
            userName.text = firebaseUser.displayName ?: "Пользователь"
        } ?: run {
            userName.text = "Пользователь"
        }
    }

    private fun loadFolderSummaries() {
        val currentUserId = authRepository.getCurrentUser()?.uid ?: return

        println("DEBUG: Starting to load summaries for folder: $folderId")

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val result = firebaseRepository.getSummariesByFolderId(currentUserId, folderId)
                if (result.isSuccess) {
                    val summaries = result.getOrNull() ?: emptyList()
                    println("DEBUG: Successfully loaded ${summaries.size} summaries")
                    displaySummaries(summaries)
                } else {
                    println("DEBUG: Failed to load summaries: ${result.exceptionOrNull()}")
                    displaySummaries(emptyList())
                }
            } catch (e: Exception) {
                println("DEBUG: Exception in loadFolderSummaries: ${e.message}")
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
            val summaryView = createSummaryView(summary)
            summariesContainer.addView(summaryView)
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
            setCardBackgroundColor(getColor(R.color.background_light))

            addView(LinearLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                orientation = LinearLayout.VERTICAL
                setPadding(dipToPx(16), dipToPx(16), dipToPx(16), dipToPx(16))

                // Название конспекта
                addView(TextView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    text = summary.title
                    setTextColor(getColor(R.color.text_primary))
                    textSize = 16f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                })

                // Контейнер для даты и количества страниц
                addView(LinearLayout(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(0, dipToPx(4), 0, 0)
                    }
                    orientation = LinearLayout.HORIZONTAL

                    // Дата создания
                    addView(TextView(context).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        text = getRelativeTime(summary.createdAt)
                        setTextColor(getColor(R.color.text_secondary))
                        textSize = 14f
                    })

                    // Распорка
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
                        text = "${summary.pageCount} ${getPageCountText(summary.pageCount)}"
                        setTextColor(getColor(R.color.text_secondary))
                        textSize = 14f
                    })
                })
            })

            setOnClickListener {
                openSummary(summary)
            }
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

    private fun getRelativeTime(timestamp: com.google.firebase.Timestamp): String {
        val currentTime = System.currentTimeMillis()
        val timestampMillis = timestamp.toDate().time
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

    private fun openSummary(summary: Summary) {
        // Здесь можно добавить логику открытия конспекта
        println("DEBUG: Opening summary: ${summary.title}")
    }

    private fun logout() {
        authRepository.logoutUser()
        finish()
    }
}