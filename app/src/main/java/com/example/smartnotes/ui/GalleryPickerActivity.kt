package com.example.smartnotes.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.smartnotes.ai.YandexOcrService
import com.example.smartnotes.models.Page
import com.example.smartnotes.models.Summary
import com.example.smartnotes.repository.AuthRepository
import com.example.smartnotes.repository.NotesRepository
import com.example.smartnotes.repository.RepositoryProvider
import com.example.smartnotes.repository.SessionManager
import com.example.smartnotes.utils.TextPaginator
import kotlinx.coroutines.launch
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GalleryPickerActivity : AppCompatActivity() {

    private val authRepository = AuthRepository()
    private lateinit var notesRepository: NotesRepository

    companion object {
        private const val PICK_IMAGE_REQUEST = 1
        private const val MAX_CHARS_PER_PAGE = 1800
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        notesRepository = RepositoryProvider.notes(this)

        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        startActivityForResult(Intent.createChooser(intent, "Выберите изображения"), PICK_IMAGE_REQUEST)
    }

    private fun resolveUserId(): String? {
        return if (SessionManager.isGuestMode(this)) "guest" else authRepository.getCurrentUser()?.uid
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK) {
            val uris = mutableListOf<Uri>()

            val clipData = data?.clipData
            if (clipData != null && clipData.itemCount > 0) {
                for (i in 0 until clipData.itemCount) {
                    clipData.getItemAt(i)?.uri?.let { uris.add(it) }
                }
            } else {
                data?.data?.let { uris.add(it) }
            }

            if (uris.isEmpty()) {
                Toast.makeText(this, "Изображения не выбраны", Toast.LENGTH_SHORT).show()
                finish()
                return
            }

            // спрашиваем название конспекта
            showTitleInputDialog { title ->
                lifecycleScope.launch { processImagesBatch(uris, title) }
            }

        } else {
            finish()
        }
    }

    private suspend fun processImagesBatch(imageUris: List<Uri>, summaryTitle: String) {
        Timber.d("processImagesBatch called, count = ${imageUris.size}")

        val userId = resolveUserId()
        if (userId.isNullOrBlank()) {
            Toast.makeText(this, "Пользователь не авторизован", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        try {
            // создаём конспект с названием пользователя
            val summaryId = createNewSummary(userId, summaryTitle)

            // OCR всех картинок → один общий текст
            val sb = StringBuilder()
            var recognizedAny = false

            for (uri in imageUris) {
                try {
                    val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
                    val text = YandexOcrService.recognizeText(bitmap).trim()
                    if (text.isNotBlank()) {
                        recognizedAny = true
                        sb.append(text).append("\n\n")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error OCR for uri=$uri")
                }
            }

            if (!recognizedAny) {
                Toast.makeText(this, "Не удалось распознать текст ни на одном изображении", Toast.LENGTH_LONG).show()
                finish()
                return
            }

            val fullText = sb.toString().trim()

            // делим на страницы
            val pagesText = TextPaginator.splitIntoPages(fullText, MAX_CHARS_PER_PAGE)

            // сохраняем страницы
            val saveOk = savePages(summaryId, pagesText)
            if (!saveOk) {
                Toast.makeText(this, "Ошибка сохранения страниц", Toast.LENGTH_LONG).show()
                finish()
                return
            }

            notesRepository.updateSummaryPageCount(summaryId, pagesText.size)
            Toast.makeText(this, "Конспект сохранён, страниц: ${pagesText.size}", Toast.LENGTH_LONG).show()

        } catch (e: Exception) {
            Timber.e(e, "Error in processImagesBatch")
            Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
        } finally {
            finish()
        }
    }

    private suspend fun createNewSummary(userId: String, title: String): String {
        val newSummary = Summary(
            id = "",
            title = title,
            pageCount = 0,
            createdAt = System.currentTimeMillis(),
            userId = userId,
            folderId = ""
        )
        val result = notesRepository.createSummary(newSummary)
        return result.getOrThrow()
    }

    private suspend fun savePages(summaryId: String, pagesText: List<String>): Boolean {
        if (pagesText.isEmpty()) return false

        for ((index, text) in pagesText.withIndex()) {
            val page = Page(
                id = "",
                summaryId = summaryId,
                pageNumber = index + 1,
                imageUrl = "",
                recognizedText = text,
                createdAt = System.currentTimeMillis()
            )

            val res = notesRepository.createPage(page)
            if (!res.isSuccess) {
                Timber.e(res.exceptionOrNull(), "Failed to create page ${index + 1} for summaryId=$summaryId")
                return false
            }
        }
        return true
    }

    private fun showTitleInputDialog(onResult: (String) -> Unit) {
        val editText = EditText(this).apply {
            hint = "Название конспекта"
        }

        AlertDialog.Builder(this)
            .setTitle("Новый конспект")
            .setMessage("Введите название конспекта")
            .setView(editText)
            .setCancelable(false)
            .setPositiveButton("Продолжить") { _, _ ->
                val title = editText.text.toString().trim()
                val finalTitle =
                    if (title.isBlank()) {
                        "Конспект от ${SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())}"
                    } else title
                onResult(finalTitle)
            }
            .setNegativeButton("Отмена") { _, _ ->
                finish()
            }
            .show()
    }
}
