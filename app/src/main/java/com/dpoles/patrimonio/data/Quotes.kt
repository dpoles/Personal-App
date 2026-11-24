package com.dpoles.patrimonio.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class Quote(val price: Double, val prevClose: Double?, val currency: String, val timeMs: Long) {
    /** Variazione rispetto alla chiusura precedente. */
    val changePct: Double? get() = prevClose?.takeIf { it > 0 }?.let { price / it - 1 }
}

/** Quotazioni da Yahoo Finance (endpoint pubblico, senza chiavi). Simboli es. VWCE.DE, XEON.DE (Xetra, in euro). */
object QuoteApi {
    fun fetch(symbol: String): Quote {
        val url = "https://query1.finance.yahoo.com/v8/finance/chart/${java.net.URLEncoder.encode(symbol, "UTF-8")}?range=1d&interval=1d"
        val c = URL(url).openConnection() as HttpURLConnection
        c.setRequestProperty("User-Agent", "Mozilla/5.0")
        c.connectTimeout = 10000; c.readTimeout = 15000
        try {
            if (c.responseCode != 200) error("simbolo $symbol non trovato (${c.responseCode})")
            val meta = JSONObject(c.inputStream.bufferedReader().readText())
                .getJSONObject("chart").getJSONArray("result").getJSONObject(0).getJSONObject("meta")
            return Quote(
                price = meta.getDouble("regularMarketPrice"),
                prevClose = if (meta.has("chartPreviousClose")) meta.getDouble("chartPreviousClose") else null,
                currency = meta.optString("currency", "EUR"),
                timeMs = meta.optLong("regularMarketTime", 0L) * 1000,
            )
        } finally {
            c.disconnect()
        }
    }
}
