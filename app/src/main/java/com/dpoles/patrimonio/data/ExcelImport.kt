package com.dpoles.patrimonio.data

import java.time.LocalDate
import kotlin.random.Random

typealias Grid = List<List<Any?>>

private fun Grid.at(r: Int, c: Int): Any? = getOrNull(r)?.getOrNull(c)
private fun Grid.num(r: Int, c: Int): Double? = (at(r, c) as? Number)?.toDouble()
private fun Grid.str(r: Int, c: Int): String? = (at(r, c) as? String)?.trim()?.takeIf { it.isNotEmpty() }

/** Le date di Excel sono numeri seriali (giorni dal 30/12/1899). */
private fun serialToDate(d: Double): LocalDate = LocalDate.of(1899, 12, 30).plusDays(d.toLong())

/**
 * Converte i primi 4 fogli del file Excel di partenza (Bal, Rif, Azioni, Monetari) nei dati dell'app.
 * Il layout atteso è descritto in docs/STRUTTURA_FOGLIO.md.
 */
object ExcelImport {
    val SHEETS = listOf("Bal", "Rif", "Azioni", "Monetari")

    data class Result(val db: Db, val months: Int, val vehicles: Int, val fills: Int, val lots: Int)

    private fun id() = Random.nextLong(1, Long.MAX_VALUE)

    fun build(g: Map<String, Grid>): Result {
        val months = months(g["Bal"] ?: error("Foglio 'Bal' mancante"))
        val (vehicles, fills) = rif(g["Rif"] ?: error("Foglio 'Rif' mancante"))
        val prices = mutableMapOf<String, Double>()
        val lots = lots(g["Azioni"] ?: error("Foglio 'Azioni' mancante"), AZIONI, prices) +
            lots(g["Monetari"] ?: error("Foglio 'Monetari' mancante"), MONETARI, prices)
        val db = Db(months, vehicles, fills, lots, prices)
        return Result(db, months.size, vehicles.size, fills.count { it.amount > 0 }, lots.size)
    }

    // ───── Bal: un mese per riga; si importano solo i mesi reali (tipo A e C), non le stime (E) ─────
    private fun months(g: Grid): List<MonthE> {
        val h = g.firstOrNull()?.map { (it as? String)?.trim() } ?: error("Foglio 'Bal' vuoto")
        fun col(n: String) = h.indexOf(n).also { require(it >= 0) { "Colonna '$n' non trovata nel foglio Bal" } }
        val cY = col("Y"); val cM = col("M"); val cT = col("T"); val cIn = col("IN"); val cOut = col("OUT")
        val cSan = col("SANTANDER"); val cAz = col("AZIONI"); val cMon = col("MONETARI"); val cTot = col("TOT")
        return (1 until g.size).mapNotNull { r ->
            val y = g.num(r, cY)?.toInt() ?: return@mapNotNull null
            val m = g.num(r, cM)?.toInt() ?: return@mapNotNull null
            val t = g.str(r, cT)?.firstOrNull() ?: return@mapNotNull null
            if (t != 'A' && t != 'C') return@mapNotNull null
            val tot = g.num(r, cTot) ?: return@mapNotNull null
            val dep = g.num(r, cSan) ?: 0.0; val az = g.num(r, cAz) ?: 0.0; val mon = g.num(r, cMon) ?: 0.0
            MonthE(id(), y, m, g.num(r, cIn) ?: 0.0, g.num(r, cOut) ?: 0.0, tot - dep - az - mon, dep, az, mon)
        }
    }

    // ───── Rif: un blocco per veicolo e periodo (es. "Auto 2025"): diventa un veicolo con il suo periodo ─────
    private fun rif(g: Grid): Pair<List<VehicleE>, List<FillE>> {
        class Row(val date: LocalDate?, val amount: Double?, val full: Boolean, val km: Double?, val liters: Double?)
        val blocks = mutableListOf<Pair<String?, List<Row>>>()
        var title: String? = null
        var rows: MutableList<Row>? = null
        fun finish() { rows?.let { if (it.isNotEmpty()) blocks += title to it.toList() }; rows = null }

        for (i in g.indices) {
            val a = g.at(i, 0)
            when {
                a is String && a.trim() == "Data" -> { val t = title; finish(); title = t; rows = mutableListOf() }
                a is String && a.isNotBlank() -> { finish(); title = a.trim() }
                rows != null -> {
                    val km = g.num(i, 5)
                    val amount = g.num(i, 1)
                    if (a is Number || (a == null && km != null && amount == null)) {
                        rows!!.add(Row((a as? Number)?.let { serialToDate(it.toDouble()) }, amount, g.str(i, 3)?.equals("si", true) == true, km, g.num(i, 7)))
                    }
                }
            }
        }
        finish()

        val vehicles = mutableListOf<VehicleE>(); val fills = mutableListOf<FillE>()
        for ((t, r) in blocks) {
            val first = r.firstOrNull { it.date != null } ?: continue
            val maxKm = r.maxOf { it.km ?: 0.0 }
            val name = t ?: ((if (maxKm < 30000) "Moto" else "Auto") + " " + first.date!!.year)
            val start = first.date!!
            val vid = id()
            vehicles += VehicleE(vid, name, start, start.plusYears(1).minusDays(1), first.km, null)
            var lastDate = start
            for (x in r) {
                // le righe senza data sono la lettura finale del contachilometri: la data è quella dell'importazione
                val d = x.date ?: LocalDate.now().coerceAtLeast(lastDate)
                lastDate = d
                val amt = x.amount ?: 0.0
                if (amt > 0 || x.km != null) fills += FillE(id(), vid, d, amt, x.liters?.takeIf { it > 0 }, x.km, x.full)
            }
        }
        return vehicles to fills
    }

    // ───── Azioni / Monetari: un acquisto per riga ─────
    private fun lots(g: Grid, kind: String, prices: MutableMap<String, Double>): List<LotE> {
        val end = g.indices.firstOrNull { g.str(it, 0)?.startsWith("TOTALE INVESTITO", true) == true } ?: g.size
        val out = mutableListOf<LotE>()
        for (r in 2 until end) {
            val name = g.str(r, 0) ?: continue
            val date = g.num(r, 3)?.let(::serialToDate) ?: continue
            val qty = g.num(r, 5) ?: continue
            val price = g.num(r, 4) ?: continue
            out += LotE(id(), kind, name, date, qty, price, g.num(r, 6) ?: 0.0)
            g.num(r, 9)?.takeIf { it > 0 }?.let { prices["$kind|$name"] = it }   // prezzo attuale (ultima riga vince)
        }
        return out
    }

    private fun LocalDate.coerceAtLeast(min: LocalDate) = if (isBefore(min)) min else this
}
