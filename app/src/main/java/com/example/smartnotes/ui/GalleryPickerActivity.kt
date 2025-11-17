package com.example.smartnotes.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.smartnotes.ai.YandexOcrService
import com.example.smartnotes.models.Page
import com.example.smartnotes.models.Summary
import com.example.smartnotes.repository.AuthRepository
import com.example.smartnotes.repository.FirebaseRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber

class GalleryPickerActivity : AppCompatActivity() {

    private lateinit var firebaseRepository: FirebaseRepository
    private val authRepository = AuthRepository()

    companion object {
        private const val PICK_IMAGE_REQUEST = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        firebaseRepository = FirebaseRepository()

        // 🔹 Разрешаем выбор нескольких изображений
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        startActivityForResult(Intent.createChooser(intent, "Выберите изображения"), PICK_IMAGE_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK) {

            val uris = mutableListOf<Uri>()

            // Если выбрано несколько файлов
            val clipData = data?.clipData
            if (clipData != null && clipData.itemCount > 0) {
                for (i in 0 until clipData.itemCount) {
                    val uri = clipData.getItemAt(i).uri
                    if (uri != null) uris.add(uri)
                }
            } else {
                // Если выбрано одно изображение
                data?.data?.let { uri ->
                    uris.add(uri)
                }
            }

            if (uris.isEmpty()) {
                Toast.makeText(this, "Изображения не выбраны", Toast.LENGTH_SHORT).show()
                finish()
                return
            }

            // 🔹 Обрабатываем сразу все изображения
            lifecycleScope.launch {
                processImagesBatch(uris)
            }
        } else {
            // Пользователь отменил выбор
            finish()
        }
    }

    /**
     * Обработка пачки изображений:
     *  - создаём ОДИН summary
     *  - для каждого изображения создаём Page с новым номером
     */
    private suspend fun processImagesBatch(imageUris: List<Uri>) {
        Timber.d("processImagesBatch called, count = ${imageUris.size}")

        val userId = authRepository.getCurrentUser()?.uid
        if (userId == null) {
            Timber.e("User not authenticated")
            Toast.makeText(this, "Пользователь не авторизован", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        try {
            // 🔹 1. Создаём один конспект
            val summaryId = createNewSummary(userId, "Новый конспект из галереи")

            var successfulPages = 0
            var pageNumber = 1

            // 🔹 2. Для каждого изображения делаем OCR и сохраняем как страницу
            for (uri in imageUris) {
                try {
                    val text = recognizeTextFromImage(uri)
                    if (text.isNotBlank()) {
                        savePage(summaryId, pageNumber, text, uri.toString())
                        successfulPages++
                        pageNumber++
                    } else {
                        Timber.d("Empty OCR result for uri: $uri")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error processing image: $uri")
                }
            }

            // 🔹 3. Обновляем количество страниц в summary
            if (successfulPages > 0) {
                firebaseRepository.updateSummaryPageCount(summaryId, successfulPages)
                Toast.makeText(
                    this,
                    "Конспект сохранён, страниц: $successfulPages",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Toast.makeText(
                    this,
                    "Не удалось распознать текст ни на одном изображении",
                    Toast.LENGTH_LONG
                ).show()
            }

        } catch (e: Exception) {
            Timber.e(e, "Error in processImagesBatch")
            Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
        } finally {
            // Возвращаемся на главный экран после обработки
            finish()
        }
    }

    /**
     * Отдельная функция для OCR одного изображения
     */
    private suspend fun recognizeTextFromImage(imageUri: Uri): String {
        Timber.d("recognizeTextFromImage called with URI: $imageUri")
        return try {
            val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, imageUri)

            // ✅ вызывaем YandexOcrService только с bitmap
            val recognizedText = YandexOcrService.recognizeText(bitmap)

            Timber.d("Recognized text from Yandex OCR: $recognizedText")
            recognizedText
        } catch (e: Exception) {
            Timber.e(e, "Error in recognizeTextFromImage")
            ""
        }
    }

    /**
     * Сохраняем страницу в Firestore
     */
    private suspend fun savePage(summaryId: String, pageNumber: Int, text: String, imageUrl: String) {
        val newPage = Page(
            id = "",
            summaryId = summaryId,
            pageNumber = pageNumber,
            imageUrl = imageUrl,
            recognizedText = text,
            createdAt = System.currentTimeMillis()
        )

        val result = firebaseRepository.createPage(newPage)
        if (result.isSuccess) {
            Timber.d("Page $pageNumber saved for summary $summaryId")
        } else {
            Timber.e(result.exceptionOrNull(), "Failed to save page $pageNumber for summary $summaryId")
        }
    }

    /**
     * Создаём новый summary c пустым folderId (несортированные)
     */
    private suspend fun createNewSummary(userId: String, title: String): String {
        val newSummary = Summary(
            id = "",
            title = title,
            pageCount = 0,
            createdAt = System.currentTimeMillis(),
            userId = userId,
            folderId = ""
        )
        val result = firebaseRepository.createSummary(newSummary)
        return result.getOrThrow()
    }
}
