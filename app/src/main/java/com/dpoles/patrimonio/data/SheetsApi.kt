package com.dpoles.patrimonio.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class AuthExpired : Exception("Sessione scaduta")

object SheetsApi {
    const val SHEET_ID = "1w_O9NKcD1cZ6LzdovwwgNsJU7MyUN2OO7V0hUKC6w1U"
    const val SCOPE = "https://www.googleapis.com/auth/spreadsheets.readonly"

    /** I primi 4 fogli, nell'ordine in cui compaiono nel file. */
    val RANGES = listOf(
        "Bal" to "Bal!A1:AG120",
        "Rif" to "Rif!A1:P140",
        "Azioni" to "Azioni!A1:AV80",
        "Monetari" to "Monetari!A1:AV80",
    )

    /** Scarica i valori grezzi (numeri veri, date seriali) e restituisce il JSON. */
    fun fetchRaw(token: String): String {
        val q = RANGES.joinToString("&") { "ranges=" + URLEncoder.encode(it.second, "UTF-8") }
        val url = "https://sheets.googleapis.com/v4/spreadsheets/$SHEET_ID/values:batchGet" +
            "?$q&valueRenderOption=UNFORMATTED_VALUE&dateTimeRenderOption=SERIAL_NUMBER&majorDimension=ROWS"
        val c = URL(url).openConnection() as HttpURLConnection
        c.setRequestProperty("Authorization", "Bearer $token")
        c.connectTimeout = 15000; c.readTimeout = 30000
        try {
            when (c.responseCode) {
                200 -> return c.inputStream.bufferedReader().readText()
                401 -> throw AuthExpired()
                else -> {
                    val err = c.errorStream?.bufferedReader()?.readText().orEmpty()
                    val msg = runCatching { JSONObject(err).getJSONObject("error").getString("message") }
                        .getOrDefault(err.take(200))
                    throw Exception("Errore ${c.responseCode}: $msg")
                }
            }
        } finally {
            c.disconnect()
        }
    }

    fun parse(json: String): Map<String, Grid> {
        val arr = JSONObject(json).getJSONArray("valueRanges")
        val out = mutableMapOf<String, Grid>()
        for (i in 0 until arr.length()) {
            val rows = arr.getJSONObject(i).optJSONArray("values") ?: JSONArray()
            out[RANGES[i].first] = (0 until rows.length()).map { r ->
                val row = rows.getJSONArray(r)
                (0 until row.length()).map { c ->
                    when (val v = row.get(c)) {
                        JSONObject.NULL -> null
                        is Int -> v.toDouble()
                        is Long -> v.toDouble()
                        is Number -> v.toDouble()
                        is String -> v.takeIf { it.isNotEmpty() }
                        else -> v
                    }
                }
            }
        }
        return out
    }
}

/** Copia locale dell'ultimo download, per aprire l'app anche offline. */
class Cache(context: Context) {
    private val file = File(context.filesDir, "sheets-cache.json")
    fun save(json: String) = runCatching { file.writeText(json) }
    fun load(): Pair<String, Long>? =
        if (file.exists()) runCatching { file.readText() to file.lastModified() }.getOrNull() else null
}
