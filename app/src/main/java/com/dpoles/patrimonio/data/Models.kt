package com.dpoles.patrimonio.data

import java.time.LocalDate

/** Una riga del patrimonio: fotografia di un mese. */
data class BalRow(
    val date: LocalDate,
    val type: Char,           // A = consuntivo, C = mese corrente, E = stima
    val income: Double,
    val expense: Double,
    val cash: Double,         // liquidità
    val currentAccount: Double,
    val santander: Double,    // conto deposito
    val azioni: Double,
    val monetari: Double,
    val total: Double,
    val id: Long = 0,
) {
    val saving get() = income - expense
    val isProjection get() = type == 'E'
}

data class Fill(
    val date: LocalDate,
    val amount: Double,
    val full: Boolean,
    val price: Double?,
    val km: Double?,
    val days: Int?,
    val liters: Double?,
    val id: Long = 0,
)

data class Vehicle(val name: String, val fills: List<Fill>, val lastKm: Double?, val id: Long = 0) {
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

data class Lot(
    val name: String, val date: LocalDate?, val buyPrice: Double?, val qty: Double?,
    val cost: Double, val curPrice: Double?, val value: Double, val gain: Double,
    val gainPct: Double?, val days: Int?, val id: Long = 0,
)

data class Instrument(val name: String, val price: Double, val priceSet: Boolean, val qty: Double)

data class Portfolio(
    val lots: List<Lot>,
    val invested: Double, val value: Double, val gain: Double, val gainPct: Double?,
    val series: List<Pair<LocalDate, Double>>,   // capitale versato nel tempo
    val instruments: List<Instrument>,
)
