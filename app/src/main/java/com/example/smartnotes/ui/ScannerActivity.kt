package com.example.smartnotes.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.example.smartnotes.R
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
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ScannerActivity : AppCompatActivity() {

    private lateinit var viewFinder: PreviewView
    private lateinit var captureButton: ImageButton

    private val authRepository = AuthRepository()
    private lateinit var notesRepository: NotesRepository

    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)

        // сколько символов примерно влезает на 1 страницу
        private const val MAX_CHARS_PER_PAGE = 1800
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_scanner)

        notesRepository = RepositoryProvider.notes(this)

        viewFinder = findViewById(R.id.viewFinder)
        captureButton = findViewById(R.id.captureButton)

        cameraExecutor = Executors.newSingleThreadExecutor()

        captureButton.setOnClickListener { takePhoto() }

        if (allPermissionsGranted()) startCamera()
        else ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
    }

    private fun resolveUserId(): String? {
        return if (SessionManager.isGuestMode(this)) "guest" else authRepository.getCurrentUser()?.uid
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US).format(java.util.Date())
        val file = File(externalCacheDir, "$name.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(this@ScannerActivity, "Ошибка при захвате фото", Toast.LENGTH_SHORT).show()
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val savedUri = output.savedUri ?: Uri.fromFile(file)
                    lifecycleScope.launch { processImage(savedUri) }
                }
            }
        )
    }

    private suspend fun processImage(imageUri: Uri) {
        Timber.d("processImage called with URI: $imageUri")

        val userId = resolveUserId()
        if (userId.isNullOrBlank()) {
            Toast.makeText(this, "Пользователь не авторизован", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, imageUri)
            val recognizedText = YandexOcrService.recognizeText(bitmap).trim()

            if (recognizedText.isBlank()) {
                Toast.makeText(this, "Не удалось распознать текст", Toast.LENGTH_SHORT).show()
                return
            }

            // 1) создаём новый конспект
            val summaryId = createNewSummary(userId, "Новый конспект из камеры")

            // 2) режем текст на страницы
            val pagesText = TextPaginator.splitIntoPages(recognizedText, MAX_CHARS_PER_PAGE)

            // 3) сохраняем страницы в БД
            val saveOk = savePages(summaryId, pagesText)

            if (!saveOk) {
                Toast.makeText(this, "Ошибка сохранения страниц", Toast.LENGTH_LONG).show()
                return
            }

            // 4) обновляем pageCount у конспекта
            notesRepository.updateSummaryPageCount(summaryId, pagesText.size)

            Toast.makeText(this, "Конспект сохранён, страниц: ${pagesText.size}", Toast.LENGTH_LONG).show()
            finish()

        } catch (e: Exception) {
            Timber.e(e, "Error in processImage")
            Toast.makeText(this, "Ошибка распознавания: ${e.message}", Toast.LENGTH_SHORT).show()
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
                imageUrl = "", // в этом режиме мы страницы показываем текстом, без картинок
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

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(viewFinder.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder().build()
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
            } catch (exc: Exception) {
                Toast.makeText(this, "Ошибка камеры: ${exc.message}", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) startCamera()
            else {
                Toast.makeText(this, "Разрешения камеры не предоставлены", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
