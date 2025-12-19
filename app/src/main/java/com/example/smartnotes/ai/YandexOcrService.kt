package com.example.smartnotes.ai

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

object YandexOcrService {

    private const val TAG = "YandexOcrService"

    // !!! сюда вставь свой API-ключ, который тебе выдал Yandex Cloud
    // пример: "AQVNy6BGPqhn_Rm_........"
    private const val API_KEY = "AQVNy6BGPqhn_Rm_z1diXC9w877wvnzsEMgrHB9q"

    // эндпоинт из документации
    private const val OCR_URL = "https://ocr.api.cloud.yandex.net/ocr/v1/recognizeText"

    // model: "handwritten" для рукописного, "page" для обычного печатного текста
    private const val MODEL = "handwritten"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Основной метод: принимает Bitmap, возвращает распознанный текст или пустую строку.
     */
    suspend fun recognizeText(bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        try {
            // 1. Перегоняем Bitmap в JPEG-байты
            val jpegBytes = bitmapToJpegBytes(bitmap)

            // 2. Кодируем в Base64 (важно: NO_WRAP, без переносов строки)
            val base64Image = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)

            // 3. Собираем JSON строго как в документации
            val json = JSONObject().apply {
                // тип соответствует тому, во что мы сжали (JPEG)
                put("mimeType", "JPEG")
                put("languageCodes", JSONArray().apply {
                    put("ru")
                    put("en")
                })
                put("model", MODEL)
                put("content", base64Image)
            }

            val mediaType = "application/json".toMediaType()
            val body = json.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url(OCR_URL)
                .addHeader("Content-Type", "application/json")
                // для сервисного аккаунта с API-ключом:
                .addHeader("Authorization", "Api-Key $API_KEY")
                // x-folder-id НЕ добавляем — он не нужен при API-key
                .post(body)
                .build()

            Log.d(TAG, "Sending Yandex OCR request: ${json.toString().take(500)}")

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""

                Log.d(
                    TAG,
                    "Yandex OCR response: code=${response.code}, body=${responseBody.take(1000)}"
                )

                if (!response.isSuccessful) {
                    Log.e(TAG, "Yandex OCR HTTP error: ${response.code}")
                    return@withContext ""
                }

                // 4. Парсим JSON-ответ и вытаскиваем текст
                return@withContext parseTextFromResponse(responseBody)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in recognizeText", e)
            ""
        }
    }

    /**
     * Сжимаем картинку в JPEG 90% качества.
     */
    private fun bitmapToJpegBytes(bitmap: Bitmap): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
        return stream.toByteArray()
    }

    /**
     * Разбираем JSON-ответ Yandex OCR и собираем весь текст построчно.
     */
    private fun parseTextFromResponse(jsonString: String): String {
        return try {
            val root = JSONObject(jsonString)

            if (!root.has("result")) {
                Log.e(TAG, "No 'result' field in OCR response")
                return ""
            }

            val result = root.getJSONObject("result")
            if (!result.has("textAnnotation")) {
                Log.e(TAG, "No 'textAnnotation' in OCR result")
                return ""
            }

            val textAnnotation = result.getJSONObject("textAnnotation")
            if (!textAnnotation.has("blocks")) {
                Log.e(TAG, "No 'blocks' in textAnnotation")
                return ""
            }

            val blocks = textAnnotation.getJSONArray("blocks")
            val sb = StringBuilder()

            for (i in 0 until blocks.length()) {
                val block = blocks.getJSONObject(i)
                if (!block.has("lines")) continue

                val lines = block.getJSONArray("lines")
                for (j in 0 until lines.length()) {
                    val line = lines.getJSONObject(j)

                    // В одном варианте API есть поле text,
                    // в другом — alternatives[0].text
                    val textDirect = line.optString("text", "")

                    val lineText = if (textDirect.isNotBlank()) {
                        textDirect
                    } else {
                        val alts = line.optJSONArray("alternatives")
                        if (alts != null && alts.length() > 0) {
                            alts.getJSONObject(0).optString("text", "")
                        } else {
                            ""
                        }
                    }

                    if (lineText.isNotBlank()) {
                        sb.append(lineText).append("\n")
                    }
                }
                // пустая строка между блоками
                sb.append("\n")
            }

            sb.toString().trim()
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing OCR response", e)
            ""
        }
    }
}

