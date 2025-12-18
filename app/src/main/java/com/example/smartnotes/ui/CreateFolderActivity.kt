package com.example.smartnotes.ui

import android.os.Bundle
import android.util.TypedValue
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.smartnotes.R
import com.example.smartnotes.models.Summary
import com.example.smartnotes.repository.AuthRepository
import com.example.smartnotes.repository.NotesRepository
import com.example.smartnotes.repository.RepositoryProvider
import com.example.smartnotes.repository.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.core.view.WindowCompat

class CreateFolderActivity : AppCompatActivity() {

    private lateinit var editFolderName: EditText
    private lateinit var summariesContainer: LinearLayout
    private lateinit var buttonCancel: ImageButton
    private lateinit var buttonCreate: ImageButton

    private val authRepository = AuthRepository()
    private lateinit var notesRepository: NotesRepository

    private var userId: String? = null
    private var loadedSummaries: List<Summary> = emptyList()

    private var isCreating = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_create_folder)

        notesRepository = RepositoryProvider.notes(this)

        editFolderName = findViewById(R.id.editFolderName)
        summariesContainer = findViewById(R.id.summariesContainer)
        buttonCancel = findViewById(R.id.buttonCancel)
        buttonCreate = findViewById(R.id.buttonCreate)

        userId = resolveUserId()
        if (userId.isNullOrBlank()) {
            Toast.makeText(this, "Ошибка: пользователь не авторизован", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        buttonCancel.setOnClickListener { finish() }
        buttonCreate.setOnClickListener { onCreateFolderClicked() }

        loadUnsortedSummaries()
    }

    private fun resolveUserId(): String? {
        return if (SessionManager.isGuestMode(this)) "guest" else authRepository.getCurrentUser()?.uid
    }

    private fun loadUnsortedSummaries() {
        val uid = userId ?: return

        CoroutineScope(Dispatchers.Main).launch {
            val result = notesRepository.getUnsortedSummaries(uid)
            if (result.isSuccess) {
                loadedSummaries = result.getOrNull().orEmpty()
                displaySummariesForSelection(loadedSummaries)
            } else {
                Toast.makeText(this@CreateFolderActivity, "Ошибка загрузки конспектов", Toast.LENGTH_SHORT).show()
                displaySummariesForSelection(emptyList())
            }
        }
    }

    private fun displaySummariesForSelection(summaries: List<Summary>) {
        summariesContainer.removeAllViews()

        if (summaries.isEmpty()) {
            val emptyText = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                text = "Несортированных конспектов пока нет"
                setTextColor(android.graphics.Color.parseColor("#666666"))
                textSize = 14f
                setPadding(dipToPx(8), dipToPx(8), dipToPx(8), dipToPx(8))
            }
            summariesContainer.addView(emptyText)
            return
        }

        summaries.forEach { summary ->
            val checkBox = CheckBox(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, dipToPx(4), 0, dipToPx(4)) }

                val dateStr = formatDate(summary.createdAt)
                text = "${summary.title} — $dateStr"
                textSize = 14f
                setPadding(dipToPx(8), dipToPx(4), dipToPx(8), dipToPx(4))
                tag = summary.id
            }
            summariesContainer.addView(checkBox)
        }
    }

    private fun onCreateFolderClicked() {
        val uid = userId ?: return
        if (isCreating) return

        val folderName = editFolderName.text.toString().trim().ifBlank { "Новая папка" }

        val selectedSummaryIds = mutableListOf<String>()
        for (i in 0 until summariesContainer.childCount) {
            val view = summariesContainer.getChildAt(i)
            if (view is CheckBox && view.isChecked) {
                val id = view.tag as? String
                if (!id.isNullOrBlank()) selectedSummaryIds.add(id)
            }
        }

        isCreating = true
        buttonCreate.isEnabled = false
        buttonCancel.isEnabled = false

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val folderRes = notesRepository.createFolder(uid, folderName)
                if (!folderRes.isSuccess) {
                    val msg = folderRes.exceptionOrNull()?.message ?: "Неизвестная ошибка"
                    Toast.makeText(this@CreateFolderActivity, "Ошибка создания папки: $msg", Toast.LENGTH_LONG).show()
                    return@launch
                }

                val folder = folderRes.getOrNull()
                if (folder == null) {
                    Toast.makeText(this@CreateFolderActivity, "Ошибка: папка не создана", Toast.LENGTH_LONG).show()
                    return@launch
                }

                if (selectedSummaryIds.isNotEmpty()) {
                    val addRes = notesRepository.addSummariesToFolder(
                        userId = uid,
                        folderId = folder.id,
                        summaryIds = selectedSummaryIds
                    )

                    if (!addRes.isSuccess) {
                        Toast.makeText(
                            this@CreateFolderActivity,
                            "Папка создана, но не удалось добавить конспекты",
                            Toast.LENGTH_LONG
                        ).show()
                        finish()
                        return@launch
                    }

                    Toast.makeText(this@CreateFolderActivity, "Папка создана и конспекты добавлены", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@CreateFolderActivity, "Папка создана", Toast.LENGTH_SHORT).show()
                }

                finish()
            } finally {
                isCreating = false
                buttonCreate.isEnabled = true
                buttonCancel.isEnabled = true
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

    private fun formatDate(timestampMillis: Long): String {
        val date = java.util.Date(timestampMillis)
        val format = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault())
        return format.format(date)
    }
}
