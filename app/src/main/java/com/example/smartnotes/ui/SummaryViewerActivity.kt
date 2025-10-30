package com.example.smartnotes.ui

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.smartnotes.R
import com.example.smartnotes.repository.FirebaseRepository
import com.example.smartnotes.models.Page
import kotlinx.coroutines.launch
import timber.log.Timber

class SummaryViewerActivity : AppCompatActivity() {

    private lateinit var firebaseRepository: FirebaseRepository

    private lateinit var textContent: TextView
    private lateinit var pageNumberText: TextView
    private lateinit var buttonPrev: Button
    private lateinit var buttonNext: Button

    private var pages: List<Page> = emptyList()
    private var currentPageIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_summary_viewer)

        firebaseRepository = FirebaseRepository()

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

        loadSummaryPages(summaryId)

        buttonPrev.setOnClickListener {
            if (currentPageIndex > 0) {
                currentPageIndex--
                displayCurrentPage()
            }
        }

        buttonNext.setOnClickListener {
            if (currentPageIndex < pages.size - 1) {
                currentPageIndex++
                displayCurrentPage()
            }
        }
    }

    private fun loadSummaryPages(summaryId: String) {
        lifecycleScope.launch {
            try {

                val result = firebaseRepository.getPagesBySummaryId(summaryId)
                if (result.isSuccess) {
                    pages = result.getOrNull()?.sortedBy { it.pageNumber } ?: emptyList()
                    if (pages.isNotEmpty()) {
                        displayCurrentPage()
                    } else {
                        textContent.text = "Конспект не содержит страниц."
                        pageNumberText.text = "0 / 0"
                        buttonPrev.isEnabled = false
                        buttonNext.isEnabled = false
                    }
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Неизвестная ошибка"
                    Toast.makeText(this@SummaryViewerActivity, "Ошибка загрузки: $error", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Timber.e(e, "Error loading pages for summary: $summaryId")
            }
        }
    }

    private fun displayCurrentPage() {
        if (pages.isEmpty()) return

        val currentPage = pages[currentPageIndex]
        textContent.text = currentPage.recognizedText


        pageNumberText.text = "${currentPageIndex + 1} из ${pages.size}"


        buttonPrev.isEnabled = currentPageIndex > 0
        buttonNext.isEnabled = currentPageIndex < pages.size - 1
    }
}