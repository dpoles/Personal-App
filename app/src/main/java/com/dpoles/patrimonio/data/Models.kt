package com.dpoles.patrimonio.data

import java.time.LocalDate

typealias Grid = List<List<Any?>>

fun Grid.at(r: Int, c: Int): Any? = getOrNull(r)?.getOrNull(c)
fun Grid.num(r: Int, c: Int): Double? = (at(r, c) as? Number)?.toDouble()
fun Grid.str(r: Int, c: Int): String? = (at(r, c) as? String)?.trim()?.takeIf { it.isNotEmpty() }

/** Le date arrivano come numeri seriali di Google Sheets (giorni dal 30/12/1899). */
fun serialToDate(d: Double): LocalDate = LocalDate.of(1899, 12, 30).plusDays(d.toLong())

// ───────────────────────── Foglio "Bal" ─────────────────────────

data class BalRow(
    val date: LocalDate,
    val type: Char,           // A = consuntivo, C = mese corrente, E = stima
    val income: Double,
    val expense: Double,
    val cash: Double,         // liquidità (BAL. TOT.)
    val currentAccount: Double,
    val santander: Double,
    val azioni: Double,
    val monetari: Double,
    val total: Double,
) {
    val saving get() = income - expense
    val isProjection get() = type == 'E'
}

fun parseBal(g: Grid): List<BalRow> {
    val h = g.firstOrNull()?.map { (it as? String)?.trim() } ?: return emptyList()
    fun col(n: String): Int {
        val i = h.indexOf(n)
        require(i >= 0) { "Colonna '$n' non trovata nel foglio Bal" }
        return i
    }
    val cY = col("Y"); val cM = col("M"); val cT = col("T")
    val cIn = col("IN"); val cOut = col("OUT"); val cCash = col("BAL. TOT.")
    val cCc = col("C/C"); val cSan = col("SANTANDER"); val cAz = col("AZIONI")
    val cMon = col("MONETARI"); val cTot = col("TOT")
    return g.indices.drop(1).mapNotNull { r ->
        val y = g.num(r, cY)?.toInt() ?: return@mapNotNull null
        val m = g.num(r, cM)?.toInt() ?: return@mapNotNull null
        val t = g.str(r, cT)?.firstOrNull() ?: return@mapNotNull null
        val tot = g.num(r, cTot) ?: return@mapNotNull null
        BalRow(
            date = LocalDate.of(y, m, 1), type = t,
            income = g.num(r, cIn) ?: 0.0, expense = g.num(r, cOut) ?: 0.0,
            cash = g.num(r, cCash) ?: 0.0, currentAccount = g.num(r, cCc) ?: 0.0,
            santander = g.num(r, cSan) ?: 0.0, azioni = g.num(r, cAz) ?: 0.0,
            monetari = g.num(r, cMon) ?: 0.0, total = tot,
        )
    }
}

// ───────────────────────── Foglio "Rif" ─────────────────────────

data class Fill(
    val date: LocalDate,
    val amount: Double,
    val full: Boolean,
    val price: Double?,
    val km: Double?,
    val days: Int?,
    val liters: Double?,
)

data class Vehicle(val name: String, val fills: List<Fill>, val lastKm: Double?) {
    private val paid get() = fills.drop(1).filter { it.amount > 0 }
    val spent get() = fills.sumOf { it.amount }
    val liters get() = fills.sumOf { it.liters ?: 0.0 }
    val avgPrice: Double?
        get() {
            val l = fills.filter { (it.liters ?: 0.0) > 0 }
            val sum = l.sumOf { it.liters!! }
            return if (sum > 0) l.sumOf { it.amount } / sum else null
        }
    private val kmToLastFill: Double?
        get() {
            val a = fills.firstOrNull()?.km
            val b = fills.lastOrNull { it.amount > 0 }?.km
            return if (a != null && b != null && b > a) b - a else null
        }
    val kmPerLiter: Double?
        get() {
            val km = kmToLastFill ?: return null
            val l = paid.sumOf { it.liters ?: 0.0 }
            return if (l > 0) km / l else null
        }
    val costPerKm: Double?
        get() {
            val km = kmToLastFill ?: return null
            return paid.sumOf { it.amount } / km
        }
    val kmDriven: Double?
        get() {
            val a = fills.firstOrNull()?.km ?: return null
            val b = lastKm ?: fills.lastOrNull()?.km ?: return null
            return (b - a).takeIf { it > 0 }
        }
}

fun parseRif(g: Grid): List<Vehicle> {
    val out = mutableListOf<Vehicle>()
    var title: String? = null
    var rows: MutableList<Fill>? = null
    var lastKm: Double? = null

    fun finish() {
        val r = rows ?: return
        if (r.isNotEmpty()) {
            val maxKm = r.maxOf { it.km ?: 0.0 }
            val name = title ?: ((if (maxKm < 30000) "Moto" else "Auto") + " " + r.first().date.year)
            out += Vehicle(name, r.toList(), lastKm)
        }
        rows = null; lastKm = null; title = null
    }

    for (i in g.indices) {
        val a = g.at(i, 0)
        when {
            a is String && a.trim() == "Data" -> { val t = title; finish(); title = t; rows = mutableListOf() }
            a is String && a.isNotBlank() -> { finish(); title = a.trim() }
            rows != null -> {
                val b = g.num(i, 1); val f = g.num(i, 5)
                if (a is Number && b != null) {
                    rows!!.add(
                        Fill(
                            date = serialToDate(a.toDouble()), amount = b,
                            full = g.str(i, 3)?.equals("si", true) == true,
                            price = g.num(i, 4)?.takeIf { it > 1.1 }, km = f,
                            days = g.num(i, 6)?.toInt(), liters = g.num(i, 7),
                        )
                    )
                } else if (b == null && f != null) {
                    lastKm = f
                }
            }
        }
    }
    finish()
    return out
}

// ──────────────── Fogli "Azioni" e "Monetari" (stesso layout) ────────────────

data class Lot(
    val name: String, val date: LocalDate?, val buyPrice: Double?, val qty: Double?,
    val cost: Double, val curPrice: Double?, val value: Double, val gain: Double,
    val gainPct: Double?, val net: Double?, val days: Int?,
)

data class Deposit(val amount: Double, val cumulative: Double?, val date: LocalDate?, val label: String?)

data class Portfolio(
    val lots: List<Lot>,
    val invested: Double, val value: Double, val gain: Double, val gainPct: Double?, val net: Double?,
    val series: List<Pair<LocalDate, Double>>,
    val capital: List<Pair<LocalDate, Double>>,
    val deposits: List<Deposit>,
)

fun parsePortfolio(g: Grid): Portfolio {
    fun rowOf(label: String) = g.indices.firstOrNull { g.str(it, 0)?.startsWith(label, true) == true }
    val totRow = rowOf("TOTALE INVESTITO")
    val accRow = rowOf("TOTALE CONTO")
    val depRow = rowOf("Versamenti")
    val capRow = g.indices.firstOrNull { g.str(it, 22) == "Capitale" }

    val lots = g.indices.filter { (totRow == null || it < totRow) && it >= 2 }.mapNotNull { r ->
        val name = g.str(r, 0) ?: return@mapNotNull null
        val cost = g.num(r, 7) ?: return@mapNotNull null
        Lot(
            name, g.num(r, 3)?.let(::serialToDate), g.num(r, 4), g.num(r, 5), cost,
            g.num(r, 9), g.num(r, 10) ?: cost, g.num(r, 11) ?: 0.0, g.num(r, 12),
            g.num(r, 14), g.num(r, 15)?.toInt(),
        )
    }

    fun series(row: Int?): List<Pair<LocalDate, Double>> {
        if (row == null) return emptyList()
        val width = g.firstOrNull()?.size ?: 0
        return (24 until width).mapNotNull { c ->
            val d = g.num(0, c) ?: return@mapNotNull null
            val v = g.num(row, c) ?: return@mapNotNull null
            serialToDate(d) to v
        }
    }

    val deposits = if (depRow == null) emptyList() else
        (depRow + 1 until g.size).mapNotNull { r ->
            val amt = g.num(r, 0) ?: return@mapNotNull null
            Deposit(amt, g.num(r, 1), g.num(r, 3)?.let(::serialToDate), g.str(r, 4))
        }

    return Portfolio(
        lots = lots,
        invested = totRow?.let { g.num(it, 7) } ?: lots.sumOf { it.cost },
        value = totRow?.let { g.num(it, 10) } ?: lots.sumOf { it.value },
        gain = totRow?.let { g.num(it, 11) } ?: lots.sumOf { it.gain },
        gainPct = totRow?.let { g.num(it, 12) },
        net = totRow?.let { g.num(it, 14) },
        series = series(accRow),
        capital = series(capRow),
        deposits = deposits,
    )
}

// ───────────────────────── Insieme dei dati ─────────────────────────

data class AppData(
    val bal: List<BalRow>?, val vehicles: List<Vehicle>?,
    val azioni: Portfolio?, val monetari: Portfolio?,
    val errors: List<String>, val updatedAt: Long,
)

fun buildAppData(grids: Map<String, Grid>, updatedAt: Long): AppData {
    val errors = mutableListOf<String>()
    fun <T> safe(name: String, block: (Grid) -> T): T? =
        runCatching { block(grids[name] ?: error("foglio vuoto")) }
            .onFailure { errors += "$name: ${it.message}" }.getOrNull()
    return AppData(
        bal = safe("Bal", ::parseBal), vehicles = safe("Rif", ::parseRif),
        azioni = safe("Azioni", ::parsePortfolio), monetari = safe("Monetari", ::parsePortfolio),
        errors = errors, updatedAt = updatedAt,
    )
}
