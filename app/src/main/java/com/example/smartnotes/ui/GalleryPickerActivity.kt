package com.example.smartnotes.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.smartnotes.models.Page
import com.example.smartnotes.models.Summary
import com.example.smartnotes.repository.AuthRepository
import com.example.smartnotes.repository.FirebaseRepository
import com.google.firebase.Timestamp
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber

class GalleryPickerActivity : AppCompatActivity() {

    private lateinit var firebaseRepository: FirebaseRepository

    companion object {
        private const val PICK_IMAGE_REQUEST = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        firebaseRepository = FirebaseRepository()


        val intent = Intent(Intent.ACTION_PICK).apply {
            type = "image/*"
        }
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK) {
            val imageUri = data?.data
            imageUri?.let {

                lifecycleScope.launch {
                    processImageFromGallery(it)
                }
            }
        }
    }

    private suspend fun processImageFromGallery(imageUri: Uri) {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val image = InputImage.fromFilePath(this, imageUri)

        try {
            val visionText = recognizer.process(image).await()
            val recognizedText = visionText.text
            savePageToFirebase(recognizedText, imageUri.toString())
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка распознавания: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun savePageToFirebase(text: String, imageUrl: String) {
        val authRepository = AuthRepository()
        val userId = authRepository.getCurrentUser()?.uid

        if (userId == null) {
            Toast.makeText(this, "Пользователь не авторизован", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val summaryId = createNewSummary(userId, "Новый конспект из галереи")
            Timber.d("Created Summary with ID: $summaryId") // <-- Добавь лог

            val pageCount = getSummaryPageCount(summaryId)
            Timber.d("Page count for summary $summaryId is $pageCount") // <-- Добавь лог

            val newPage = Page(
                id = "",
                summaryId = summaryId, // <-- Вот тут
                pageNumber = pageCount + 1,
                imageUrl = imageUrl,
                recognizedText = text,
                createdAt = System.currentTimeMillis()
            )

            val result = firebaseRepository.createPage(newPage)
            if (result.isSuccess) {
                Timber.d("Created Page with ID: ${result.getOrNull()}, for Summary: $summaryId") // <-- Добавь лог
                firebaseRepository.updateSummaryPageCount(summaryId, pageCount + 1)
                Toast.makeText(this, "Страница сохранена", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Ошибка сохранения страницы: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {

            Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private suspend fun createNewSummary(userId: String, title: String): String {
        val newSummary = Summary(
            id = "",
            title = title,
            pageCount = 0,
            createdAt = Timestamp.now(),
            userId = userId,
            folderId = ""
        )
        val result = firebaseRepository.createSummary(newSummary)
        return result.getOrThrow()
    }

    private suspend fun getSummaryPageCount(summaryId: String): Int {
        val snapshot = firebaseRepository.database.collection("summaries").document(summaryId).get().await()
        return snapshot.getLong("pageCount")?.toInt() ?: 0
    }
}