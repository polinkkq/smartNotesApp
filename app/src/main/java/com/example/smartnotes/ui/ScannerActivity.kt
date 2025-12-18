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
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.exifinterface.media.ExifInterface
import android.graphics.Bitmap
import android.graphics.Matrix
import com.example.smartnotes.ui.ScanOverlayView

class ScannerActivity : AppCompatActivity() {

    private lateinit var viewFinder: PreviewView
    private lateinit var captureButton: ImageButton

    private val authRepository = AuthRepository()
    private lateinit var notesRepository: NotesRepository

    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var scanOverlay: ScanOverlayView
    private lateinit var flashView: View
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView


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
        scanOverlay = findViewById(R.id.scanOverlay)
        flashView = findViewById(R.id.flashView)
        progressBar = findViewById(R.id.progressBar)
        statusText = findViewById(R.id.statusText)


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
                    setProcessing(false)
                    Toast.makeText(this@ScannerActivity, "Ошибка при захвате фото", Toast.LENGTH_SHORT).show()
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val savedUri = output.savedUri ?: Uri.fromFile(file)
                    lifecycleScope.launch { processImage(savedUri) }
                }
            }
        )
        captureButton.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        shutterFlash()
        setProcessing(true)
    }

    private suspend fun processImage(imageUri: Uri) {
        val userId = resolveUserId()
        if (userId.isNullOrBlank()) {
            setProcessing(false)
            Toast.makeText(this, "Пользователь не авторизован", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            statusText.text = "Распознавание..."

            // берём файл (он у тебя в cache)
            val file = File(imageUri.path ?: run {
                setProcessing(false)
                Toast.makeText(this, "Не удалось получить файл изображения", Toast.LENGTH_SHORT).show()
                return
            })

            var bitmap = MediaStore.Images.Media.getBitmap(contentResolver, imageUri)
            bitmap = rotateIfNeeded(file, bitmap)

            // обрезаем по рамке
            bitmap = cropByOverlay(bitmap)

            val recognizedText = YandexOcrService.recognizeText(bitmap).trim()
            if (recognizedText.isBlank()) {
                setProcessing(false)
                Toast.makeText(this, "Не удалось распознать текст", Toast.LENGTH_SHORT).show()
                return
            }

            statusText.text = "Сохранение..."

            val summaryId = createNewSummary(userId, "Новый конспект из камеры")
            val pagesText = TextPaginator.splitIntoPages(recognizedText, MAX_CHARS_PER_PAGE)

            val saveOk = savePages(summaryId, pagesText)
            if (!saveOk) {
                setProcessing(false)
                Toast.makeText(this, "Ошибка сохранения страниц", Toast.LENGTH_LONG).show()
                return
            }

            notesRepository.updateSummaryPageCount(summaryId, pagesText.size)

            setProcessing(false)
            Toast.makeText(this, "Конспект сохранён, страниц: ${pagesText.size}", Toast.LENGTH_LONG).show()
            finish()

        } catch (e: Exception) {
            Timber.e(e, "Error in processImage")
            setProcessing(false)
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
    private fun setProcessing(isProcessing: Boolean) {
        captureButton.isEnabled = !isProcessing
        progressBar.visibility = if (isProcessing) View.VISIBLE else View.GONE
        statusText.visibility = if (isProcessing) View.VISIBLE else View.GONE
    }

    private fun shutterFlash() {
        flashView.visibility = View.VISIBLE
        flashView.alpha = 1f
        flashView.animate()
            .alpha(0f)
            .setDuration(120)
            .withEndAction { flashView.visibility = View.GONE }
            .start()
    }
    private fun rotateIfNeeded(file: File, bitmap: Bitmap): Bitmap {
        return try {
            val exif = ExifInterface(file.absolutePath)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            val angle = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (angle == 0f) bitmap
            else {
                val m = Matrix().apply { postRotate(angle) }
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
            }
        } catch (_: Exception) {
            bitmap
        }
    }

    private fun cropByOverlay(bitmap: Bitmap): Bitmap {
        // рамка в координатах viewFinder
        val rect = scanOverlay.getFrameRect()

        val vw = viewFinder.width.toFloat().coerceAtLeast(1f)
        val vh = viewFinder.height.toFloat().coerceAtLeast(1f)

        // нормализуем (0..1)
        val nx = (rect.left / vw).coerceIn(0f, 1f)
        val ny = (rect.top / vh).coerceIn(0f, 1f)
        val nw = (rect.width() / vw).coerceIn(0f, 1f)
        val nh = (rect.height() / vh).coerceIn(0f, 1f)

        // применяем к bitmap
        val bx = (nx * bitmap.width).toInt()
        val by = (ny * bitmap.height).toInt()
        val bw = (nw * bitmap.width).toInt()
        val bh = (nh * bitmap.height).toInt()

        val safeX = bx.coerceIn(0, bitmap.width - 1)
        val safeY = by.coerceIn(0, bitmap.height - 1)
        val safeW = bw.coerceIn(1, bitmap.width - safeX)
        val safeH = bh.coerceIn(1, bitmap.height - safeY)

        return Bitmap.createBitmap(bitmap, safeX, safeY, safeW, safeH)
    }


    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
