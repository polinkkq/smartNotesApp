package com.example.smartnotes.ui

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.smartnotes.R
import com.example.smartnotes.models.Page
import com.example.smartnotes.repository.FirebaseRepository
import kotlinx.coroutines.launch
import timber.log.Timber

class SummaryViewerActivity : AppCompatActivity() {

    private lateinit var firebaseRepository: FirebaseRepository

    private lateinit var scrollView: ScrollView
    private lateinit var pagesContainer: LinearLayout
    private lateinit var pageIndicator: TextView

    private var pages: List<Page> = emptyList()

    private val pageWidthDp = 480  // Увеличиваем ширину страницы до 480dp
    private val maxPageTextLength = 2500  // Максимальное количество символов на странице

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_summary_viewer)

        firebaseRepository = FirebaseRepository()

        scrollView = findViewById(R.id.scrollView)
        pagesContainer = findViewById(R.id.pagesContainer)
        pageIndicator = findViewById(R.id.pageIndicator)

        val summaryId = intent.getStringExtra("SUMMARY_ID")
        if (summaryId.isNullOrBlank()) {
            Toast.makeText(this, "ID конспекта не передан", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        loadSummaryPages(summaryId)

        // Отслеживание прокрутки
        scrollView.viewTreeObserver.addOnScrollChangedListener {
            updateCurrentPageIndicator()
        }
    }

    private fun loadSummaryPages(summaryId: String) {
        lifecycleScope.launch {
            try {
                val result = firebaseRepository.getPagesBySummaryId(summaryId)
                if (result.isSuccess) {
                    pages = result.getOrNull()
                        ?.sortedBy { it.pageNumber }
                        .orEmpty()

                    if (pages.isNotEmpty()) {
                        renderPages()
                    } else {
                        Toast.makeText(
                            this@SummaryViewerActivity,
                            "Конспект не содержит страниц.",
                            Toast.LENGTH_SHORT
                        ).show()
                        pageIndicator.text = "0 из 0"
                    }
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Неизвестная ошибка"
                    Toast.makeText(
                        this@SummaryViewerActivity,
                        "Ошибка загрузки: $error",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                Timber.e(e, "Error loading pages for summary: $summaryId")
                Toast.makeText(
                    this@SummaryViewerActivity,
                    "Ошибка загрузки страниц",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun renderPages() {
        pagesContainer.removeAllViews()

        val fullText = pages.joinToString("\n\n") { it.recognizedText }
        val pagesText = breakTextIntoPages(fullText)

        val total = pagesText.size
        pagesText.forEachIndexed { index, pageText ->
            val pageLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    dpToPx(pageWidthDp),
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = if (index == 0) 0 else dpToPx(16)
                }
                setBackgroundResource(R.drawable.bg_page_card)
                setPadding(
                    dpToPx(16),
                    dpToPx(16),
                    dpToPx(16),
                    dpToPx(16)
                )
            }

            val textView = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                text = pageText.ifBlank { "Страница без текста" }
                textSize = 16f
                setLineSpacing(dpToPx(4).toFloat(), 1f)
            }

            pageLayout.addView(textView)
            pagesContainer.addView(pageLayout)
        }

        pageIndicator.text = "1 из $total"
    }

    private fun breakTextIntoPages(fullText: String): List<String> {
        val pagesList = mutableListOf<String>()
        var currentText = StringBuilder()

        fullText.lines().forEach { line ->
            if (currentText.length + line.length < maxPageTextLength) {
                currentText.append(line).append("\n")
            } else {
                pagesList.add(currentText.toString())
                currentText = StringBuilder(line).append("\n")
            }
        }

        if (currentText.isNotEmpty()) {
            pagesList.add(currentText.toString())
        }

        return pagesList
    }

    private fun updateCurrentPageIndicator() {
        if (pagesContainer.childCount == 0) {
            pageIndicator.text = "0 из 0"
            return
        }

        val scrollY = scrollView.scrollY
        val scrollViewHeight = scrollView.height

        // Определим центральную точку ScrollView
        val centerY = scrollY + scrollViewHeight / 2

        var currentPageIndex = 0

        // Ищем страницу, которая находится в центре экрана
        for (i in 0 until pagesContainer.childCount) {
            val pageView = pagesContainer.getChildAt(i)
            val top = pageView.top
            val bottom = pageView.bottom

            // Проверка, попадает ли центральная точка в эту страницу
            if (centerY in top..bottom) {
                currentPageIndex = i
                break
            }
        }

        // Выводим индекс текущей страницы и общее количество страниц
        val humanIndex = currentPageIndex + 1
        val totalPages = pagesContainer.childCount
        pageIndicator.text = "$humanIndex из $totalPages"
    }


    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density + 0.5f).toInt()
    }
}
