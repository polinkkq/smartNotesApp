package com.example.smartnotes.ui

import android.os.Bundle
import android.widget.ImageButton
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.smartnotes.R
import com.example.smartnotes.models.Page
import com.example.smartnotes.repository.NotesRepository
import com.example.smartnotes.repository.RepositoryProvider
import kotlinx.coroutines.launch
import timber.log.Timber

class SummaryViewerActivity : AppCompatActivity() {

    private lateinit var notesRepository: NotesRepository

    private lateinit var pageScroll: ScrollView
    private lateinit var textContent: TextView
    private lateinit var pageNumberText: TextView
    private lateinit var buttonPrev: ImageButton
    private lateinit var buttonNext: ImageButton

    private var pages: List<Page> = emptyList()
    private var currentPageIndex: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_summary_viewer)

        notesRepository = RepositoryProvider.notes(this)

        pageScroll = findViewById(R.id.pageScroll)
        textContent = findViewById(R.id.textContent)
        pageNumberText = findViewById(R.id.pageNumberText)
        buttonPrev = findViewById(R.id.buttonPrev)
        buttonNext = findViewById(R.id.buttonNext)

        val summaryId = intent.getStringExtra("SUMMARY_ID")
        if (summaryId.isNullOrBlank()) {
            Toast.makeText(this, "ID конспекта не передан", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        buttonPrev.setOnClickListener {
            if (currentPageIndex > 0) {
                currentPageIndex--
                showPage()
            }
        }

        buttonNext.setOnClickListener {
            if (currentPageIndex < pages.size - 1) {
                currentPageIndex++
                showPage()
            }
        }

        loadPages(summaryId)
    }

    private fun loadPages(summaryId: String) {
        lifecycleScope.launch {
            try {
                val result = notesRepository.getPagesBySummaryId(summaryId)
                if (!result.isSuccess) {
                    val error = result.exceptionOrNull()?.message ?: "Неизвестная ошибка"
                    Toast.makeText(this@SummaryViewerActivity, "Ошибка загрузки: $error", Toast.LENGTH_LONG).show()
                    return@launch
                }

                pages = result.getOrNull()
                    ?.sortedBy { it.pageNumber }
                    .orEmpty()

                if (pages.isEmpty()) {
                    textContent.text = "Конспект не содержит страниц."
                    pageNumberText.text = "0 из 0"
                    buttonPrev.isEnabled = false
                    buttonNext.isEnabled = false
                    return@launch
                }

                currentPageIndex = 0
                showPage()
            } catch (e: Exception) {
                Timber.e(e, "Error loading pages for summaryId=$summaryId")
                Toast.makeText(this@SummaryViewerActivity, "Ошибка загрузки страниц", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showPage() {
        if (pages.isEmpty()) return

        val page = pages[currentPageIndex]
        textContent.text = page.recognizedText.ifBlank { "Страница без текста" }

        pageScroll.post { pageScroll.scrollTo(0, 0) }

        pageNumberText.text = "${currentPageIndex + 1} из ${pages.size}"

        buttonPrev.isEnabled = currentPageIndex > 0
        buttonNext.isEnabled = currentPageIndex < pages.size - 1
    }
}
