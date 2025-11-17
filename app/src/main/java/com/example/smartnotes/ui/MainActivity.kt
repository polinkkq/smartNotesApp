package com.example.smartnotes.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.drawerlayout.widget.DrawerLayout
import androidx.core.view.GravityCompat
import com.example.smartnotes.R
import com.example.smartnotes.models.Folder
import com.example.smartnotes.models.Summary
import com.example.smartnotes.repository.AuthRepository
import com.example.smartnotes.repository.FirebaseRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.core.view.WindowCompat

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
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.main_activity)

        initViews()
        setupClickListeners()
        loadUserData()
        loadFolders()
        loadSummaries()
    }

    override fun onResume() {
        super.onResume()
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
            drawerLayout.openDrawer(GravityCompat.START)
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
        val currentUser = authRepository.getCurrentUser()
        currentUser?.let { firebaseUser ->
            CoroutineScope(Dispatchers.Main).launch {
                val result = firebaseRepository.getUser(firebaseUser.uid)
                if (result.isSuccess) {
                    val user = result.getOrNull()
                    user?.let {
                        userName.text = "${it.firstName} ${it.lastName}"
                    } ?: run {
                        userName.text = firebaseUser.displayName ?: "Пользователь"
                    }
                } else {
                    userName.text = firebaseUser.displayName ?: "Пользователь"
                }
            }
        } ?: run {
            userName.text = "Пользователь"
        }
    }

    private fun loadFolders() {
        val currentUserId = authRepository.getCurrentUser()?.uid ?: return
        println("DEBUG: Loading folders for user: $currentUserId")

        CoroutineScope(Dispatchers.Main).launch {
            val result = firebaseRepository.getUserFolders(currentUserId)
            println("DEBUG: Folders result: $result")
            if (result.isSuccess) {
                val folders = result.getOrNull() ?: emptyList()
                println("DEBUG: Number of folders: ${folders.size}")
                displayFolders(folders)
            } else {
                println("DEBUG: Error loading folders: ${result.exceptionOrNull()}")
                displayFolders(emptyList())
            }
        }
    }

    private fun loadSummaries() {
        val currentUserId = authRepository.getCurrentUser()?.uid ?: return
        println("DEBUG: Loading summaries for user: $currentUserId")

        CoroutineScope(Dispatchers.Main).launch {
            val result = firebaseRepository.getUnsortedSummaries(currentUserId)
            println("DEBUG: Summaries result: $result")
            if (result.isSuccess) {
                val summaries = result.getOrNull() ?: emptyList()
                println("DEBUG: Number of summaries: ${summaries.size}")
                displaySummaries(summaries)
            } else {
                println("DEBUG: Error loading summaries: ${result.exceptionOrNull()}")
                displaySummaries(emptyList())
            }
        }
    }

    private fun displayFolders(folders: List<Folder>) {
        foldersContainer.removeAllViews()

        if (folders.isEmpty()) {
            val emptyText = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                text = "Папок пока нет"
                setTextColor(Color.parseColor("#666666"))
                textSize = 14f
                setPadding(dipToPx(16), dipToPx(16), dipToPx(16), dipToPx(16))
            }
            foldersContainer.addView(emptyText)
            return
        }

        folders.take(2).forEach { folder ->
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

    private fun displaySummaries(summaries: List<Summary>) {
        unsortedContainer.removeAllViews()

        if (summaries.isEmpty()) {
            val emptyText = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                text = "Несортированных конспектов пока нет"
                setTextColor(Color.parseColor("#666666"))
                textSize = 14f
                setPadding(dipToPx(16), dipToPx(16), dipToPx(16), dipToPx(16))
            }
            unsortedContainer.addView(emptyText)
            return
        }

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

                addView(LinearLayout(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(0, dipToPx(4), 0, 0)
                    }
                    orientation = LinearLayout.HORIZONTAL

                    addView(TextView(context).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        text = getRelativeTime(summary.createdAt)
                        setTextColor(Color.parseColor("#666666"))
                        textSize = 14f
                    })

                    addView(TextView(context).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            0,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            weight = 1f
                        }
                    })

                    addView(TextView(context).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        text = "${summary.pageCount} ${getPageCountText(summary.pageCount)}"
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
            diff < 24 * 60 * 60 * 1000 -> "Сегодня"
            diff < 2 * 24 * 60 * 60 * 1000 -> "Вчера"
            diff < 7 * 24 * 60 * 60 * 1000 -> "Неделю назад"
            else -> {
                val date = java.util.Date(timestampMillis)
                val format = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault())
                format.format(date)
            }
        }
    }

    private fun openFolder(folder: Folder) {
        println("DEBUG: Opening folder: ${folder.id}, title: ${folder.title}")
        val intent = Intent(this, FolderContentActivity::class.java).apply {
            putExtra("FOLDER_ID", folder.id)
            putExtra("FOLDER_TITLE", folder.title)
        }
        startActivity(intent)
    }

    private fun openSummary(summary: Summary) {
        val intent = Intent(this, SummaryViewerActivity::class.java).apply {
            putExtra("SUMMARY_ID", summary.id)
        }
        startActivity(intent)
    }

    private fun selectPhotoFromGallery() {
        val intent = Intent(this, GalleryPickerActivity::class.java)
        startActivity(intent)
    }

    private fun startScanning() {
        val intent = Intent(this, ScannerActivity::class.java)
        startActivity(intent)
    }

    private fun logout() {
        authRepository.logoutUser()
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
        finish()
    }

    fun refreshData() {
        loadUserData()
        loadFolders()
        loadSummaries()
    }
}
