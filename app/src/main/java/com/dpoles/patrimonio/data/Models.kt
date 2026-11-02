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

/** Previsione dei km a chiusura del periodo (ogni veicolo ha il suo periodo e il suo limite). */
data class Forecast(
    val startDate: LocalDate, val endDate: LocalDate, val startKm: Double,
    val lastKm: Double, val lastDate: LocalDate,
    val kmPerDay: Double?,          // ritmo attuale
    val projectedKm: Double?,       // contachilometri previsto a fine periodo
    val daysLeft: Int,
    val limit: Double?,             // km massimi a chiusura (facoltativo)
    val allowedPerDay: Double?,     // km/giorno consentiti per restare nel limite
) {
    val driven get() = lastKm - startKm
    val projectedDriven get() = projectedKm?.minus(startKm)
    val overLimit get() = if (projectedKm != null && limit != null) projectedKm - limit else null
}

data class Vehicle(
    val name: String,
    val fills: List<Fill>,          // tutte le voci, comprese le sole letture dei km (importo 0)
    val id: Long = 0,
    val forecast: Forecast? = null,
    val startKmSet: Boolean = false,
) {
    private val real get() = fills.filter { it.amount > 0 }
    val lastKm: Double? get() = fills.mapNotNull { it.km }.maxOrNull()
    val spent get() = real.sumOf { it.amount }
    val liters get() = real.sumOf { it.liters ?: 0.0 }
    val avgPrice: Double?
        get() {
            val l = real.filter { (it.liters ?: 0.0) > 0 }
            val sum = l.sumOf { it.liters!! }
            return if (sum > 0) l.sumOf { it.amount } / sum else null
        }
    // Con i km di inizio periodo noti, tutti i pieni contano; altrimenti il primo rifornimento fa da base.
    private val counted get() = if (startKmSet) real else real.drop(1)
    private val baseKm get() = if (startKmSet) forecast?.startKm else real.firstOrNull()?.km
    private val kmToLastFill: Double?
        get() {
            val a = baseKm
            val b = real.lastOrNull { it.km != null }?.km
            return if (a != null && b != null && b > a) b - a else null
        }
    val kmPerLiter: Double?
        get() {
            val km = kmToLastFill ?: return null
            val l = counted.sumOf { it.liters ?: 0.0 }
            return if (l > 0) km / l else null
        }
    val costPerKm: Double?
        get() {
            val km = kmToLastFill ?: return null
            return counted.sumOf { it.amount } / km
        }
    val kmDriven: Double?
        get() {
            val a = baseKm ?: fills.firstOrNull { it.km != null }?.km ?: return null
            val b = lastKm ?: return null
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
