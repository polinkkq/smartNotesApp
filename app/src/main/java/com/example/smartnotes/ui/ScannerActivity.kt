package com.example.smartnotes.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
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
import androidx.exifinterface.media.ExifInterface
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.min

class ScannerActivity : AppCompatActivity() {

    private lateinit var viewFinder: PreviewView
    private lateinit var captureButton: ImageButton

    private lateinit var scanOverlay: ScanOverlayView
    private lateinit var flashView: View
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView

    private lateinit var frozenImage: ImageView

    private val authRepository = AuthRepository()
    private lateinit var notesRepository: NotesRepository

    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
        private const val MAX_CHARS_PER_PAGE = 1800
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_scanner)

        notesRepository = RepositoryProvider.notes(this)

        viewFinder = findViewById(R.id.viewFinder)
        captureButton = findViewById(R.id.captureButton)

        scanOverlay = findViewById(R.id.scanOverlay)
        flashView = findViewById(R.id.flashView)
        progressBar = findViewById(R.id.progressBar)
        statusText = findViewById(R.id.statusText)

        frozenImage = findViewById(R.id.frozenImage)


        viewFinder.scaleType = PreviewView.ScaleType.FIT_CENTER

        // ✅ Чтобы "замороженный кадр" не выглядел увеличенным
        frozenImage.scaleType = ImageView.ScaleType.FIT_CENTER

        cameraExecutor = Executors.newSingleThreadExecutor()

        captureButton.setOnClickListener {
            takePhotoAndRecognize()
        }

        if (allPermissionsGranted()) startCamera()
        else ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
    }

    private fun resolveUserId(): String? {
        return if (SessionManager.isGuestMode(this)) "guest" else authRepository.getCurrentUser()?.uid
    }

    private fun takePhotoAndRecognize() {
        val capture = imageCapture ?: return

        captureButton.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        shutterFlash()
        setProcessing(true, "Съёмка...")

        val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US).format(Date())
        val file = File(externalCacheDir, "$name.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()

        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exception: ImageCaptureException) {
                    setProcessing(false)
                    Toast.makeText(this@ScannerActivity, "Ошибка при захвате фото", Toast.LENGTH_SHORT).show()
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val savedUri = output.savedUri ?: Uri.fromFile(file)

                    lifecycleScope.launch {
                        val bitmap = loadBitmapFromUri(savedUri)
                        if (bitmap == null) {
                            setProcessing(false)
                            Toast.makeText(this@ScannerActivity, "Не удалось загрузить изображение", Toast.LENGTH_SHORT).show()
                            return@launch
                        }

                        val fixed = rotateIfNeeded(file, bitmap)


                        val cropped = cropByOverlayFitCenter(fixed)

                        // замораживаем именно то, что пойдёт в OCR
                        freezeFrame(cropped)

                        recognizeThenAskTitleAndSave(cropped)
                    }
                }
            }
        )
    }

    private suspend fun recognizeThenAskTitleAndSave(bitmapForOcr: Bitmap) {
        val userId = resolveUserId()
        if (userId.isNullOrBlank()) {
            unfreezeFrame()
            setProcessing(false)
            Toast.makeText(this, "Пользователь не авторизован", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            setProcessing(true, "Распознавание...")

            val recognizedText = withContext(Dispatchers.IO) {
                YandexOcrService.recognizeText(bitmapForOcr).trim()
            }

            if (recognizedText.isBlank()) {
                unfreezeFrame()
                setProcessing(false)
                Toast.makeText(this, "Не удалось распознать текст", Toast.LENGTH_SHORT).show()
                return
            }

            val pagesText = TextPaginator.splitIntoPages(recognizedText, MAX_CHARS_PER_PAGE)

            setProcessing(false)

            showTitleInputDialog(
                onConfirm = { title ->
                    lifecycleScope.launch {
                        saveRecognizedResult(userId, title, pagesText)
                    }
                },
                onCancel = {
                    unfreezeFrame()
                    finish()
                }
            )

        } catch (e: Exception) {
            Timber.e(e, "OCR failed")
            unfreezeFrame()
            setProcessing(false)
            Toast.makeText(this, "Ошибка распознавания: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun saveRecognizedResult(userId: String, summaryTitle: String, pagesText: List<String>) {
        try {
            setProcessing(true, "Сохранение...")

            val summaryId = createNewSummary(userId, summaryTitle)

            val saveOk = savePages(summaryId, pagesText)
            if (!saveOk) {
                unfreezeFrame()
                setProcessing(false)
                Toast.makeText(this, "Ошибка сохранения страниц", Toast.LENGTH_LONG).show()
                return
            }

            notesRepository.updateSummaryPageCount(summaryId, pagesText.size)

            unfreezeFrame()
            setProcessing(false)
            Toast.makeText(this, "Конспект сохранён, страниц: ${pagesText.size}", Toast.LENGTH_LONG).show()
            finish()

        } catch (e: Exception) {
            Timber.e(e, "Save failed")
            unfreezeFrame()
            setProcessing(false)
            Toast.makeText(this, "Ошибка сохранения: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun loadBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            MediaStore.Images.Media.getBitmap(contentResolver, uri)
        } catch (e: Exception) {
            Timber.e(e, "loadBitmapFromUri failed")
            null
        }
    }

    private fun freezeFrame(bitmap: Bitmap) {
        frozenImage.setImageBitmap(bitmap)
        frozenImage.visibility = View.VISIBLE
        viewFinder.visibility = View.INVISIBLE
        scanOverlay.visibility = View.INVISIBLE
    }

    private fun unfreezeFrame() {
        frozenImage.setImageDrawable(null)
        frozenImage.visibility = View.GONE
        viewFinder.visibility = View.VISIBLE
        scanOverlay.visibility = View.VISIBLE
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

    private fun setProcessing(isProcessing: Boolean, text: String = "Обработка...") {
        captureButton.isEnabled = !isProcessing
        progressBar.visibility = if (isProcessing) View.VISIBLE else View.GONE
        statusText.visibility = if (isProcessing) View.VISIBLE else View.GONE
        if (isProcessing) statusText.text = text
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


    private fun cropByOverlayFitCenter(bitmap: Bitmap): Bitmap {
        val rect = scanOverlay.getFrameRect()

        val vw = viewFinder.width.toFloat().coerceAtLeast(1f)
        val vh = viewFinder.height.toFloat().coerceAtLeast(1f)

        val bw = bitmap.width.toFloat().coerceAtLeast(1f)
        val bh = bitmap.height.toFloat().coerceAtLeast(1f)

        // FIT_CENTER => масштаб = min(vw/bw, vh/bh)
        val scale = min(vw / bw, vh / bh)

        val displayedW = bw * scale
        val displayedH = bh * scale

        // “поля” по краям
        val offsetX = (vw - displayedW) / 2f
        val offsetY = (vh - displayedH) / 2f

        // переводим overlay rect из координат View в координаты Bitmap
        val left = ((rect.left - offsetX) / scale).toInt()
        val top = ((rect.top - offsetY) / scale).toInt()
        val right = ((rect.right - offsetX) / scale).toInt()
        val bottom = ((rect.bottom - offsetY) / scale).toInt()

        // безопасные границы
        val safeLeft = left.coerceIn(0, bitmap.width - 1)
        val safeTop = top.coerceIn(0, bitmap.height - 1)
        val safeRight = right.coerceIn(safeLeft + 1, bitmap.width)
        val safeBottom = bottom.coerceIn(safeTop + 1, bitmap.height)

        val cropW = (safeRight - safeLeft).coerceAtLeast(1)
        val cropH = (safeBottom - safeTop).coerceAtLeast(1)

        return Bitmap.createBitmap(bitmap, safeLeft, safeTop, cropW, cropH)
    }

    private fun showTitleInputDialog(onConfirm: (String) -> Unit, onCancel: () -> Unit) {
        val editText = EditText(this).apply {
            hint = "Название конспекта"
        }

        AlertDialog.Builder(this)
            .setTitle("Сохранить конспект")
            .setMessage("Введите название конспекта")
            .setView(editText)
            .setCancelable(false)
            .setPositiveButton("Сохранить") { _, _ ->
                val title = editText.text?.toString()?.trim().orEmpty()
                val finalTitle =
                    if (title.isBlank()) {
                        "Конспект от ${SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())}"
                    } else title
                onConfirm(finalTitle)
            }
            .setNegativeButton("Отмена") { _, _ ->
                onCancel()
            }
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
