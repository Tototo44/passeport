package com.passeport.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Récupère le taux euro -> peso philippin du jour, gratuitement. */
object Rates {
    /** Au-delà de ce délai, on va chercher un taux plus récent. */
    const val MAX_AGE_MS = 6 * 60 * 60 * 1000L

    suspend fun fetch(): Rate = withContext(Dispatchers.IO) {
        try {
            // Taux de référence de la Banque centrale européenne
            val json = JSONObject(get("https://api.frankfurter.dev/v1/latest?base=EUR&symbols=PHP"))
            Rate(json.getJSONObject("rates").getDouble("PHP"), json.getString("date"), System.currentTimeMillis(), "BCE")
        } catch (e: Exception) {
            // Solution de secours
            val json = JSONObject(get("https://open.er-api.com/v6/latest/EUR"))
            val day = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                .format(Date(json.getLong("time_last_update_unix") * 1000))
            Rate(json.getJSONObject("rates").getDouble("PHP"), day, System.currentTimeMillis(), "ER-API")
        }
    }

    private fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            if (conn.responseCode != 200) throw IllegalStateException("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}

/** Traduction « Précis » en ligne avec DeepL. */
object DeepL {
    class Problem(val userMessage: String) : Exception(userMessage)

    suspend fun translate(key: String, text: String, from: Lang): String = withContext(Dispatchers.IO) {
        val cleanKey = key.trim()
        val host = if (cleanKey.endsWith(":fx")) "api-free.deepl.com" else "api.deepl.com"
        val conn = URL("https://$host/v2/translate").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 8000
            conn.readTimeout = 10000
            conn.doOutput = true
            conn.setRequestProperty("Authorization", "DeepL-Auth-Key $cleanKey")
            conn.setRequestProperty("Content-Type", "application/json")
            val body = JSONObject()
                .put("text", JSONArray().put(text))
                .put("source_lang", if (from == Lang.FR) "FR" else "EN")
                .put("target_lang", if (from == Lang.FR) "EN-US" else "FR")
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            when (val code = conn.responseCode) {
                200 -> {}
                401, 403 -> throw Problem("Clé DeepL refusée. Vérifie-la dans le Carnet.")
                456 -> throw Problem("Quota DeepL du mois atteint. Le mode Rapide reste disponible.")
                429 -> throw Problem("DeepL est très demandé, réessaie dans un instant.")
                else -> throw Problem("DeepL ne répond pas (erreur $code).")
            }
            val resp = conn.inputStream.bufferedReader().use { it.readText() }
            JSONObject(resp).getJSONArray("translations").getJSONObject(0).getString("text")
        } finally {
            conn.disconnect()
        }
    }
}
