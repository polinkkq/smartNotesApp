package com.example.smartnotes.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
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
import com.example.smartnotes.repository.FirebaseRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ScannerActivity : AppCompatActivity() {

    private lateinit var viewFinder: PreviewView
    private lateinit var captureButton: ImageButton

    private lateinit var firebaseRepository: FirebaseRepository
    private val authRepository = AuthRepository()

    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContentView(R.layout.activity_scanner)

        viewFinder = findViewById(R.id.viewFinder)
        captureButton = findViewById(R.id.captureButton)

        firebaseRepository = FirebaseRepository()
        cameraExecutor = Executors.newSingleThreadExecutor()

        captureButton.setOnClickListener {
            takePhoto()
        }

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this,
                REQUIRED_PERMISSIONS,
                REQUEST_CODE_PERMISSIONS
            )
        }
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US)
            .format(java.util.Date())
        val file = File(externalCacheDir, "$name.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(
                        this@ScannerActivity,
                        "Ошибка при захвате фото",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val savedUri = output.savedUri ?: Uri.fromFile(file)
                    lifecycleScope.launch {
                        processImage(savedUri)
                    }
                }
            }
        )
    }

    private suspend fun processImage(imageUri: Uri) {
        Timber.d("processImage called with URI: $imageUri")

        try {
            val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, imageUri)

            val recognizedText = YandexOcrService.recognizeText(bitmap)

            if (recognizedText.isNotEmpty()) {
                Timber.d("Recognized text from Yandex OCR: $recognizedText")
                savePageToFirebase(recognizedText, imageUri.toString())
            } else {
                Timber.d("Recognized text is empty from Yandex OCR")
                Toast.makeText(this, "Не удалось распознать текст", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Timber.e(e, "Error in processImage")
            Toast.makeText(this, "Ошибка распознавания: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun savePageToFirebase(text: String, imageUrl: String) {
        val userId = authRepository.getCurrentUser()?.uid

        if (userId == null) {
            Timber.e("User not authenticated")
            Toast.makeText(this, "Пользователь не авторизован", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val summaryId = createNewSummary(userId, "Новый конспект из камеры")
            val pageCount = getSummaryPageCount(summaryId)

            val newPage = Page(
                id = "",
                summaryId = summaryId,
                pageNumber = pageCount + 1,
                imageUrl = imageUrl,
                recognizedText = text,
                createdAt = System.currentTimeMillis()
            )

            val result = firebaseRepository.createPage(newPage)

            if (result.isSuccess) {
                firebaseRepository.updateSummaryPageCount(summaryId, pageCount + 1)
                Toast.makeText(this@ScannerActivity, "Страница сохранена", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(
                    this,
                    "Ошибка сохранения страницы: ${result.exceptionOrNull()?.message}",
                    Toast.LENGTH_LONG
                ).show()
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
            createdAt = System.currentTimeMillis(),
            userId = userId,
            folderId = ""
        )
        val result = firebaseRepository.createSummary(newSummary)
        return result.getOrThrow()
    }

    private suspend fun getSummaryPageCount(summaryId: String): Int {
        val snapshot =
            firebaseRepository.database.collection("summaries").document(summaryId).get().await()
        return snapshot.getLong("pageCount")?.toInt() ?: 0
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
                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageCapture
                )
            } catch (exc: Exception) {
                Toast.makeText(
                    this,
                    "Ошибка камеры: ${exc.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext,
            it
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Разрешения камеры не предоставлены", Toast.LENGTH_SHORT)
                    .show()
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
