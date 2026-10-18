package com.recipewallet.app

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

internal data class PantryDetection(val item: PantryItem, val selected: Boolean = true)

/** Only the development backend talks to Perceptron. No provider credential enters the APK. */
internal object PantryScanner {
    suspend fun scan(path: String): List<PantryDetection> = withContext(Dispatchers.IO) {
        val file = File(path)
        check(file.length() in 4..3_000_000) { "This photo is too large or unavailable. Please choose another." }
        val payload = JSONObject().put("mimeType", "image/jpeg")
            .put("imageBase64", Base64.encodeToString(file.readBytes(), Base64.NO_WRAP))
        val connection = URL(BuildConfig.BACKEND_URL.trimEnd('/') + "/pantry-scan").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 10000; connection.readTimeout = 85000; connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            val bytes = payload.toString().toByteArray(Charsets.UTF_8)
            connection.setFixedLengthStreamingMode(bytes.size)
            connection.outputStream.use { it.write(bytes) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val result = runCatching { JSONObject(text) }.getOrElse { error("Couldn’t read the scan response. Please retry.") }
            check(status in 200..299) { result.optString("error", "Couldn’t scan this photo. Please retry.") }
            val rows = result.getJSONArray("ingredients")
            check(rows.length() <= 40) { "The scan returned too many ingredients. Please retry." }
            List(rows.length()) { index ->
                val row = rows.getJSONObject(index)
                val name = row.getString("name").trim(); val amount = row.getString("amount").trim()
                check(name.isNotBlank() && name.length <= 80 && amount.length <= 40) { "The scan returned an invalid ingredient. Please retry." }
                PantryDetection(PantryItem(UUID.randomUUID().toString(), name, amount))
            }
        } finally { connection.disconnect() }
    }
}
