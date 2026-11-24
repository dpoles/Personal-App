package com.dpoles.patrimonio.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.temporal.ChronoUnit

const val AZIONI = "azioni"
const val MONETARI = "monetari"
const val DEFAULT_TAX = 0.26

/** Un nome breve che sembra un ticker (es. VWCE, XEON) viene quotato su Xetra in euro. */
fun guessSymbol(name: String): String? =
    name.trim().takeIf { Regex("[A-Za-z0-9]{3,6}").matches(it) }?.uppercase()?.plus(".DE")

data class MonthE(
    val id: Long, val year: Int, val month: Int,
    val income: Double, val expense: Double,
    val cash: Double, val deposit: Double, val azioni: Double, val monetari: Double,
)

data class VehicleE(
    val id: Long, val name: String,
    val periodStart: LocalDate? = null, val periodEnd: LocalDate? = null,
    val startKm: Double? = null, val kmLimit: Double? = null,
)

data class FillE(
    val id: Long, val vehicleId: Long, val date: LocalDate,
    val amount: Double, val liters: Double?, val km: Double?, val full: Boolean,
)

data class LotE(
    val id: Long, val kind: String, val name: String, val date: LocalDate,
    val qty: Double, val price: Double, val fee: Double,
)

/** Tutti i dati dell'utente. Vengono salvati in un unico file JSON privato dell'app. */
data class Db(
    val months: List<MonthE> = emptyList(),
    val vehicles: List<VehicleE> = emptyList(),
    val fills: List<FillE> = emptyList(),
    val lots: List<LotE> = emptyList(),
    val prices: Map<String, Double> = emptyMap(),   // "kind|nome" -> prezzo attuale
    val symbols: Map<String, String> = emptyMap(),  // "kind|nome" -> simbolo per la quotazione live
    val quoteAt: Map<String, Long> = emptyMap(),    // "kind|nome" -> ora dell'ultima quotazione (ms)
    val taxRates: Map<String, Double> = emptyMap(), // "kind|nome" -> aliquota sulle plusvalenze (es. 0.26)
) {
    fun priceKey(kind: String, name: String) = "$kind|$name"

    // ───────── Derivazioni per le schermate ─────────

    fun balRows(): List<BalRow> = months.sortedWith(compareBy({ it.year }, { it.month })).map {
        BalRow(
            LocalDate.of(it.year, it.month, 1), 'A', it.income, it.expense, it.cash, 0.0,
            it.deposit, it.azioni, it.monetari, it.cash + it.deposit + it.azioni + it.monetari, it.id,
        )
    }

    fun portfolio(kind: String): Portfolio {
        val ls = lots.filter { it.kind == kind }.sortedBy { it.date }
        val today = LocalDate.now()
        val out = ls.map { l ->
            val cur = prices[priceKey(kind, l.name)] ?: l.price
            val cost = l.qty * l.price + l.fee
            val value = l.qty * cur
            Lot(
                l.name, l.date, l.price, l.qty, cost, cur, value, value - cost,
                if (cost > 0) (value - cost) / cost else null,
                ChronoUnit.DAYS.between(l.date, today).toInt(), l.id,
            )
        }
        var run = 0.0
        val series = out.map { run += it.cost; it.date!! to run }
        val inst = ls.groupBy { it.name }.map { (n, g) ->
            val k = priceKey(kind, n)
            Instrument(n, prices[k] ?: g.last().price, prices.containsKey(k), g.sumOf { it.qty }, symbols[k] ?: guessSymbol(n), quoteAt[k], taxRates[k] ?: DEFAULT_TAX)
        }
        val inv = out.sumOf { it.cost }; val v = out.sumOf { it.value }
        // Proiezione a fine anno: ogni acquisto prosegue al suo rendimento giornaliero storico
        // (solo se detenuto da almeno 30 giorni; gli acquisti più recenti restano fermi, in modo prudente).
        val end = LocalDate.of(today.year, 12, 31)
        val left = ChronoUnit.DAYS.between(today, end).coerceAtLeast(0)
        val extra = out.sumOf { l -> val d = l.days ?: 0; if (d >= 30) l.gain / d * left else 0.0 }
        // tasse: aliquota di ogni strumento sulla sua plusvalenza complessiva (se positiva)
        var tax = 0.0; var projTax = 0.0
        for ((n, g) in out.groupBy { it.name }) {
            val rate = taxRates[priceKey(kind, n)] ?: DEFAULT_TAX
            val gain = g.sumOf { it.gain }
            val projGain = gain + g.sumOf { l -> val d = l.days ?: 0; if (d >= 30) l.gain / d * left else 0.0 }
            tax += rate * maxOf(0.0, gain); projTax += rate * maxOf(0.0, projGain)
        }
        val pv = v + extra; val pg = v - inv + extra
        return Portfolio(
            out, inv, v, v - inv, if (inv > 0) (v - inv) / inv else null, series, inst, pv, pg, end,
            tax, v - inv - tax, v - tax, projTax, pg - projTax, pv - projTax,
        )
    }

    fun vehicleList(): List<Vehicle> = vehicles.map { v ->
        val f = fills.filter { it.vehicleId == v.id }.sortedBy { it.date }
        var prev: LocalDate? = null
        val list = f.map {
            val days = if (it.amount > 0) prev?.let { p -> ChronoUnit.DAYS.between(p, it.date).toInt() } else null
            if (it.amount > 0) prev = it.date
            Fill(it.date, it.amount, it.full, it.liters?.takeIf { l -> l > 0 }?.let { l -> it.amount / l }, it.km, days, it.liters, it.id)
        }
        Vehicle(v.name, list, v.id, forecast(v, f), v.startKm != null)
    }

    private fun forecast(v: VehicleE, f: List<FillE>): Forecast? {
        val readings = f.filter { it.km != null }
        val first = readings.firstOrNull() ?: return null
        val last = readings.maxByOrNull { it.km!! }!!
        val startKm = v.startKm ?: first.km!!
        val startDate = v.periodStart ?: first.date
        val endDate = v.periodEnd ?: startDate.plusYears(1).minusDays(1)
        val elapsed = ChronoUnit.DAYS.between(startDate, last.date)
        val total = ChronoUnit.DAYS.between(startDate, endDate)
        val lastKm = last.km!!
        val perDay = if (elapsed > 0 && lastKm > startKm) (lastKm - startKm) / elapsed else null
        val projected = perDay?.let { startKm + it * total }
        val left = ChronoUnit.DAYS.between(LocalDate.now(), endDate).toInt().coerceAtLeast(0)
        val allowed = v.kmLimit?.let { (it - lastKm) / ChronoUnit.DAYS.between(last.date, endDate).coerceAtLeast(1) }
        return Forecast(startDate, endDate, startKm, lastKm, last.date, perDay, projected, left, v.kmLimit, allowed)
    }

    // ───────── Salvataggio ─────────

    fun toJson(): String = JSONObject()
        .put("v", 1)
        .put("months", JSONArray(months.map {
            JSONObject().put("id", it.id).put("y", it.year).put("m", it.month).put("in", it.income).put("out", it.expense)
                .put("cash", it.cash).put("dep", it.deposit).put("az", it.azioni).put("mon", it.monetari)
        }))
        .put("vehicles", JSONArray(vehicles.map {
            JSONObject().put("id", it.id).put("name", it.name)
                .put("ps", it.periodStart?.toEpochDay() ?: JSONObject.NULL).put("pe", it.periodEnd?.toEpochDay() ?: JSONObject.NULL)
                .put("sk", it.startKm ?: JSONObject.NULL).put("lim", it.kmLimit ?: JSONObject.NULL)
        }))
        .put("fills", JSONArray(fills.map {
            JSONObject().put("id", it.id).put("veh", it.vehicleId).put("d", it.date.toEpochDay()).put("amt", it.amount)
                .put("l", it.liters ?: JSONObject.NULL).put("km", it.km ?: JSONObject.NULL).put("full", it.full)
        }))
        .put("lots", JSONArray(lots.map {
            JSONObject().put("id", it.id).put("kind", it.kind).put("name", it.name).put("d", it.date.toEpochDay())
                .put("q", it.qty).put("p", it.price).put("fee", it.fee)
        }))
        .put("prices", JSONObject(prices))
        .put("sym", JSONObject(symbols))
        .put("qat", JSONObject(quoteAt))
        .put("tax", JSONObject(taxRates))
        .toString()

    companion object {
        private fun JSONObject.dbl(k: String): Double? = if (isNull(k)) null else getDouble(k)
        private fun <T> JSONArray.map(f: (JSONObject) -> T) = (0 until length()).map { f(getJSONObject(it)) }

        fun fromJson(s: String): Db {
            val o = JSONObject(s)
            val pr = o.optJSONObject("prices")
            return Db(
                months = o.getJSONArray("months").map {
                    MonthE(it.getLong("id"), it.getInt("y"), it.getInt("m"), it.getDouble("in"), it.getDouble("out"),
                        it.getDouble("cash"), it.getDouble("dep"), it.getDouble("az"), it.getDouble("mon"))
                },
                vehicles = o.getJSONArray("vehicles").map {
                    VehicleE(
                        it.getLong("id"), it.getString("name"),
                        if (it.isNull("ps")) null else LocalDate.ofEpochDay(it.getLong("ps")),
                        if (it.isNull("pe")) null else LocalDate.ofEpochDay(it.getLong("pe")),
                        it.dbl("sk"), it.dbl("lim"),
                    )
                },
                fills = o.getJSONArray("fills").map {
                    FillE(it.getLong("id"), it.getLong("veh"), LocalDate.ofEpochDay(it.getLong("d")), it.getDouble("amt"),
                        it.dbl("l"), it.dbl("km"), it.getBoolean("full"))
                },
                lots = o.getJSONArray("lots").map {
                    LotE(it.getLong("id"), it.getString("kind"), it.getString("name"), LocalDate.ofEpochDay(it.getLong("d")),
                        it.getDouble("q"), it.getDouble("p"), it.getDouble("fee"))
                },
                prices = pr?.keys()?.asSequence()?.associateWith { pr.getDouble(it) } ?: emptyMap(),
                symbols = o.optJSONObject("sym")?.let { j -> j.keys().asSequence().associateWith { j.getString(it) } } ?: emptyMap(),
                taxRates = o.optJSONObject("tax")?.let { j -> j.keys().asSequence().associateWith { j.getDouble(it) } } ?: emptyMap(),
                quoteAt = o.optJSONObject("qat")?.let { j -> j.keys().asSequence().associateWith { j.getLong(it) } } ?: emptyMap(),
            )
        }
    }
}
