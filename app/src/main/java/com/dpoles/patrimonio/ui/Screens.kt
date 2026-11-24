package com.dpoles.patrimonio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dpoles.patrimonio.data.*
import kotlin.math.abs
import kotlin.math.max

@Composable
private fun Page(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        content()
        Spacer(Modifier.height(24.dp))
    }
}

private val BigNumber = TextStyle(fontSize = 44.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp)

@Composable
private fun Dot(color: Color) = Box(Modifier.size(10.dp).background(color, CircleShape))

// ═════════════════════════ PATRIMONIO ═════════════════════════

@Composable
fun HomeScreen(
    bal: List<BalRow>, onAdd: () -> Unit, onEdit: (Long) -> Unit, onImport: () -> Unit,
    liveDelta: Double = 0.0, invGrowth: Double = 0.0, taxNow: Double = 0.0, taxProj: Double = 0.0,
) {
    if (bal.isEmpty()) {
        EmptyState("💰", "Il tuo patrimonio", "Registra il primo mese: entrate, uscite e quanto hai su conti, depositi e investimenti.", "Aggiungi il primo mese", onAdd, "oppure importa da Excel" to onImport)
        return
    }
    if (bal.size < 2) {
        val m = bal[0]
        Page {
            Spacer(Modifier.height(4.dp))
            GlassCard {
                Label("Patrimonio · ${monthLong(m.date)}")
                Spacer(Modifier.height(6.dp))
                CountUp(m.total, BigNumber)
                Spacer(Modifier.height(8.dp))
                Text("Aggiungi un altro mese per vedere grafico, andamento e risparmio.", color = Pal.Muted, fontSize = 14.sp)
            }
            MonthList(bal, onEdit)
        }
        return
    }
    val curIdx = bal.indexOfLast { it.type == 'C' }.takeIf { it >= 0 } ?: bal.indexOfLast { !it.isProjection }
    val cur = bal[curIdx]
    val prev = bal.getOrNull(curIdx - 1)
    val yearAgo = bal.getOrNull(curIdx - 12)

    var range by remember { mutableIntStateOf(1) }
    val months = listOf(12, 36, Int.MAX_VALUE)[range]
    val start = if (months == Int.MAX_VALUE) 0 else max(0, curIdx - months)
    val window = bal.subList(start, bal.size)
    var sel by remember { mutableStateOf<Int?>(null) }
    val shown = sel?.let { window.getOrNull(it) } ?: cur

    Page {
        Spacer(Modifier.height(4.dp))
        GlassCard {
            Label(if (sel == null) "Patrimonio · ${monthLong(cur.date)}" + (if (liveDelta != 0.0) " · live" else "") else
                monthLong(shown.date) + if (shown.isProjection) " · previsione" else "")
            Spacer(Modifier.height(6.dp))
            if (sel == null) CountUp(cur.total + liveDelta, BigNumber)
            else Text(eur(shown.total), style = BigNumber, color = Pal.Text, maxLines = 1)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (prev != null) { val d = shown.total + (if (sel == null) liveDelta else 0.0) - (bal.getOrNull(bal.indexOf(shown) - 1)?.total ?: prev.total); DeltaChip(signed(d) + " mese", d >= 0) }
                if (yearAgo != null && sel == null) { val d = cur.total + liveDelta - yearAgo.total; DeltaChip(signed(d) + " 12 mesi", d >= 0) }
            }
            if (sel == null && taxNow > 0) {
                Spacer(Modifier.height(8.dp))
                Text("Al netto delle tasse sugli investimenti: " + eur(cur.total + liveDelta - taxNow), color = Pal.Muted, fontSize = 12.sp)
            }
            Spacer(Modifier.height(14.dp))
            AreaChart(
                values = window.map { it.total }, dashedFrom = curIdx - start,
                modifier = Modifier.fillMaxWidth().height(190.dp), onSelect = { sel = it },
            )
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("${monthShort(window.first().date)} ${window.first().date.year % 100}", color = Pal.Muted, fontSize = 11.sp)
                Segmented(listOf("12M", "3A", "Tutto"), range) { range = it; sel = null }
                Text("${monthShort(window.last().date)} ${window.last().date.year % 100}", color = Pal.Muted, fontSize = 11.sp)
            }
        }

        // Composizione
        val others = cur.azioni + cur.monetari + cur.santander
        val liquid = max(0.0, cur.total - others)
        val slices = listOf(
            Slice("Azioni (ETF)", cur.azioni, Pal.Violet), Slice("Monetari", cur.monetari, Pal.Cyan),
            Slice("Conto deposito", cur.santander, Pal.Amber), Slice("Liquidità e c/c", liquid, Pal.Blue),
        )
        GlassCard {
            Label("Composizione")
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Box(Modifier.size(140.dp), contentAlignment = Alignment.Center) {
                    Donut(slices, Modifier.fillMaxSize())
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(pct(cur.azioni / cur.total, 0), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text("azioni", color = Pal.Muted, fontSize = 11.sp)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                    slices.forEach { s ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Dot(s.color)
                            Column(Modifier.weight(1f)) {
                                Text(s.label, fontSize = 12.sp, color = Pal.Muted, maxLines = 1)
                                Text(eur(s.value), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Text(pct(s.value / cur.total, 0), color = Pal.Muted, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Flussi di cassa
        val flow = bal.filter { it.type == 'A' }.takeLast(12)
        if (flow.isNotEmpty()) {
            val inc = flow.map { it.income }.average(); val exp = flow.map { it.expense }.average()
            GlassCard {
                Label("Entrate e uscite · ultimi ${flow.size} mesi")
                Spacer(Modifier.height(14.dp))
                PairBars(flow.map { it.income }, flow.map { it.expense }, Modifier.fillMaxWidth().height(110.dp))
                Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    flow.forEach { Text(monthShort(it.date).take(1), Modifier.weight(1f), color = Pal.Muted, fontSize = 10.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
                }
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth()) {
                    Stat("Entrate/mese", eur(inc), Modifier.weight(1f), Pal.Emerald)
                    Stat("Uscite/mese", eur(exp), Modifier.weight(1f), Pal.Rose)
                    Stat("Risparmio", if (inc > 0) pct((inc - exp) / inc, 0) else "–", Modifier.weight(1f))
                }
            }
        }

        // Proiezioni
        val future = bal.drop(curIdx + 1).filter { it.date.monthValue == 12 }
        if (future.isNotEmpty()) {
            GlassCard {
                Label("Proiezione a fine anno")
                Spacer(Modifier.height(10.dp))
                future.forEach { f ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Dic ${f.date.year}", color = Pal.Muted, modifier = Modifier.width(80.dp))
                        Text(eur(f.total), fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.weight(1f))
                        DeltaChip(signed(f.total - cur.total), f.total >= cur.total)
                    }
                }
            }
        }
        EndOfYearCard(bal, cur.total + liveDelta, invGrowth, taxProj)
        MonthList(bal, onEdit)
    }
}

@Composable
private fun EndOfYearCard(bal: List<BalRow>, totalNow: Double, invGrowth: Double, taxProj: Double) {
    val today = java.time.LocalDate.now()
    val monthsLeft = (12 - today.monthValue).coerceAtLeast(0)
    val flow = bal.filter { it.type == 'A' }.takeLast(12)
    val saving = if (flow.isEmpty()) 0.0 else flow.map { it.saving }.average() * monthsLeft
    val projected = totalNow + invGrowth + saving
    GlassCard {
        Label("Proiezione a fine anno · Dic ${today.year}")
        Spacer(Modifier.height(6.dp))
        Text(eur(projected), style = BigNumber, maxLines = 1)
        Spacer(Modifier.height(10.dp))
        listOf(
            "Patrimonio oggi (live)" to totalNow,
            "+ Crescita degli investimenti" to invGrowth,
            "+ Risparmio previsto ($monthsLeft ${if (monthsLeft == 1) "mese" else "mesi"})" to saving,
        ).forEach { (l, v) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Text(l, color = Pal.Muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text(if (l.startsWith("+")) signed(v) else eur(v), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        }
        if (taxProj > 0) {
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Text("Netto delle tasse sugli investimenti", color = Pal.Muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text(eur(projected - taxProj), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Pal.Emerald)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Stima: gli investimenti proseguono al rendimento storico di ogni acquisto (prudente per quelli recenti) e il risparmio è la media degli ultimi mesi.",
            color = Pal.Muted, fontSize = 11.sp,
        )
    }
}

@Composable
private fun MonthList(bal: List<BalRow>, onEdit: (Long) -> Unit) {
    Label("Mesi registrati", Modifier.padding(start = 4.dp, top = 6.dp))
    bal.reversed().forEach { m ->
        GlassCard(onClick = { onEdit(m.id) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(monthLong(m.date), fontWeight = FontWeight.Bold)
                    Text("Entrate ${eur(m.income)} · Uscite ${eur(m.expense)}", color = Pal.Muted, fontSize = 12.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(eur(m.total), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    DeltaChip(signed(m.saving), m.saving >= 0)
                }
            }
        }
    }
}

// ═════════════════════════ AZIONI / MONETARI ═════════════════════════

@Composable
fun PortfolioScreen(
    p: Portfolio, accent: Color, unit: String,
    onAdd: () -> Unit, onEditLot: (Long) -> Unit, onEditPrice: (String) -> Unit,
    quotes: Map<String, Quote> = emptyMap(), quoteError: String? = null, onRefreshQuotes: () -> Unit = {},
) {
    if (p.lots.isEmpty()) {
        EmptyState(
            if (unit == "azioni") "📈" else "🏦", "Nessun acquisto",
            "Registra i tuoi acquisti di $unit: calcolo valore, plusvalenza e andamento.", "Aggiungi acquisto", onAdd,
        )
        return
    }
    var sel by remember { mutableStateOf<Int?>(null) }
    val series = p.series

    Page {
        Spacer(Modifier.height(4.dp))
        GlassCard {
            val s = sel?.let { series.getOrNull(it) }
            Label(if (s == null) "Valore $unit" else "Versato al " + dayMonth(s.first))
            Spacer(Modifier.height(6.dp))
            if (s == null) CountUp(p.value, BigNumber) else Text(eur(s.second), style = BigNumber, maxLines = 1)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DeltaChip(signed(p.gain) + (p.gainPct?.let { " · " + pct(it) } ?: "") + " lordo", p.gain >= 0)
                DeltaChip(signed(p.netGain) + " netto", p.netGain >= 0)
            }
            val hasSymbol = p.instruments.any { it.symbol != null }
            val liveAt = p.instruments.mapNotNull { it.quoteAt }.maxOrNull()
            val live = quotes.isNotEmpty()
            val lastLive = quotes.values.maxOfOrNull { it.timeMs } ?: liveAt ?: 0L
            val timeFmt = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT)
            val dateFmt = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT)
            val status = when {
                !hasSymbol -> "○ Quotazione live non attiva: tocca un prezzo qui sotto e imposta il simbolo (es. VWCE.DE)"
                live && marketOpen() -> "● Live · ultimo prezzo " + timeFmt.format(java.util.Date(lastLive)) + " · aggiornamento ogni minuto"
                live -> "○ Mercato chiuso (Xetra 9:00–17:30, lun–ven): ultimo prezzo " + dateFmt.format(java.util.Date(lastLive))
                liveAt != null && liveAt > 0 -> "○ Ultima quotazione " + dateFmt.format(java.util.Date(liveAt)) + " · tocca per aggiornare"
                else -> "○ In attesa della prima quotazione… tocca per aggiornare"
            }
            Spacer(Modifier.height(8.dp))
            Text(
                status, color = if (live && marketOpen()) Pal.Emerald else Pal.Muted, fontSize = 12.sp,
                modifier = Modifier.clickableNoRipple(onRefreshQuotes),
            )
            quoteError?.let { Spacer(Modifier.height(6.dp)); Text("⚠ $it", color = Pal.Rose, fontSize = 12.sp) }
            if (series.size >= 2) {
                Spacer(Modifier.height(14.dp))
                AreaChart(series.map { it.second }, Modifier.fillMaxWidth().height(160.dp), accent, onSelect = { sel = it })
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(dayMonth(series.first().first), color = Pal.Muted, fontSize = 11.sp)
                    Text("capitale versato", color = Pal.Muted, fontSize = 11.sp)
                    Text(dayMonth(series.last().first), color = Pal.Muted, fontSize = 11.sp)
                }
            }
        }

        GlassCard {
            Row(Modifier.fillMaxWidth()) {
                Stat("Investito", eur(p.invested), Modifier.weight(1f))
                Stat("Plusvalenza lorda", signed(p.gain), Modifier.weight(1f), if (p.gain >= 0) Pal.Emerald else Pal.Rose)
            }
        }

        GlassCard {
            Row(Modifier.fillMaxWidth()) {
                Stat("Tasse stimate", eur(p.taxDue), Modifier.weight(1f), Pal.Amber)
                Stat("Valore netto", eur(p.netValue), Modifier.weight(1f))
            }
        }

        GlassCard {
            Label("Proiezione a fine anno · ${dayMonth(p.projDate)}")
            Spacer(Modifier.height(6.dp))
            Text(eur(p.projNetValue), style = BigNumber, maxLines = 1)
            Text("valore netto previsto (lordo " + eur(p.projValue) + ")", color = Pal.Muted, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            DeltaChip("Guadagno previsto " + signed(p.projNetGain) + " netto · " + signed(p.projGain) + " lordo", p.projNetGain >= 0)
            Spacer(Modifier.height(8.dp))
            Text(
                "Stima al rendimento storico di ogni acquisto (prudente per quelli recenti), con le tasse sulle plusvalenze.",
                color = Pal.Muted, fontSize = 12.sp,
            )
        }

        Label("Prezzi attuali", Modifier.padding(start = 4.dp))
        p.instruments.forEach { i ->
            GlassCard(onClick = { onEditPrice(i.name) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(i.name, fontWeight = FontWeight.Bold)
                        Text(num(i.qty, 2) + " quote" + (if (!i.priceSet) " · prezzo = ultimo acquisto" else "") + (i.symbol?.let { " · $it" } ?: " · senza simbolo: tocca per attivare il live") + " · tasse " + pct(i.taxRate, 1), color = Pal.Muted, fontSize = 12.sp)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(eur(i.price, 2), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = accent)
                        quotes.entries.firstOrNull { it.key.endsWith("|" + i.name) }?.value?.changePct?.let {
                            Text((if (it >= 0) "▲ " else "▼ ") + pct(kotlin.math.abs(it), 2) + " oggi", color = if (it >= 0) Pal.Emerald else Pal.Rose, fontSize = 12.sp)
                        }
                    }
                    Text("  ✎", color = Pal.Muted)
                }
            }
        }

        Label("Acquisti (${p.lots.size})", Modifier.padding(start = 4.dp))
        p.lots.sortedByDescending { it.date }.forEach { LotCard(it, onEditLot) }
    }
}

@Composable
private fun LotCard(l: Lot, onClick: (Long) -> Unit) {
    GlassCard(onClick = { onClick(l.id) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(l.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(
                    listOfNotNull(l.date?.let(::dayMonth), l.qty?.let { num(it, 2) + " quote" }, l.buyPrice?.let { "a " + eur(it, 2) }).joinToString(" · "),
                    color = Pal.Muted, fontSize = 12.sp,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(eur(l.value), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(4.dp))
                DeltaChip(signed(l.gain) + (l.gainPct?.let { " · " + pct(it) } ?: ""), l.gain >= 0)
            }
        }
    }
}

// ═════════════════════════ RIFORNIMENTI ═════════════════════════

@Composable
fun FuelScreen(
    vehicles: List<Vehicle>, idx: Int, onIdx: (Int) -> Unit,
    onAdd: (Long?) -> Unit, onEditFill: (Long) -> Unit, onEditVehicle: (Long) -> Unit,
) {
    if (vehicles.isEmpty()) {
        EmptyState("⛽", "Nessun veicolo", "Registra i rifornimenti: calcolo spesa, prezzo medio e consumi.", "Aggiungi rifornimento", onClick = { onAdd(null) })
        return
    }
    val v = vehicles[idx.coerceIn(0, vehicles.lastIndex)]
    val priced = v.fills.filter { it.price != null && it.amount > 0 }

    Page {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            vehicles.forEachIndexed { i, it ->
                val on = i == idx
                Box(
                    Modifier.background(if (on) Pal.Amber else Pal.Surface2, RoundedCornerShape(50))
                        .clickableNoRipple { onIdx(i) }.padding(horizontal = 16.dp, vertical = 8.dp),
                ) { Text(it.name, color = if (on) Pal.Bg else Pal.Muted, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
            }
            Box(
                Modifier.background(Pal.Surface2, RoundedCornerShape(50)).clickableNoRipple { onAdd(null) }.padding(horizontal = 16.dp, vertical = 8.dp),
            ) { Text("＋", color = Pal.Muted, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
            Box(
                Modifier.background(Pal.Surface2, RoundedCornerShape(50)).clickableNoRipple { onEditVehicle(v.id) }.padding(horizontal = 16.dp, vertical = 8.dp),
            ) { Text("⚙ Periodo e limite", color = Pal.Muted, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
        }
        GlassCard {
            Label("Speso in carburante")
            Spacer(Modifier.height(6.dp))
            key(v.id) { CountUp(v.spent, BigNumber, decimals = 0) }
            Text("${v.fills.count { it.amount > 0 }} rifornimenti · ${num(v.liters, 0)} litri", color = Pal.Muted, fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth()) {
                Stat("Prezzo medio", v.avgPrice?.let { eur(it, 3) + "/L" } ?: "–", Modifier.weight(1f), Pal.Amber)
                Stat("Consumo", v.kmPerLiter?.let { num(it, 1) + " km/L" } ?: "–", Modifier.weight(1f))
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth()) {
                Stat("Costo al km", v.costPerKm?.let { eur(it, 3) } ?: "–", Modifier.weight(1f))
                Stat("Km percorsi", v.kmDriven?.let { num(it, 0) } ?: "–", Modifier.weight(1f))
            }
            if (v.kmPerLiter == null) {
                Spacer(Modifier.height(12.dp))
                Text("Consumi e costo al km compaiono dal secondo rifornimento, indicando i chilometri.", color = Pal.Muted, fontSize = 12.sp)
            }
        }

        ForecastCard(v) { onEditVehicle(v.id) }

        if (priced.size >= 2) {
            var sel by remember(v.id) { mutableStateOf<Int?>(null) }
            GlassCard {
                Label("Prezzo al litro")
                Spacer(Modifier.height(4.dp))
                val s = sel?.let { priced.getOrNull(it) }
                Text(
                    if (s != null) "${dayMonth(s.date)} · ${eur(s.price!!, 3)}/L" else "Trascina sul grafico",
                    color = if (s != null) Pal.Amber else Pal.Muted, fontSize = 13.sp,
                )
                Spacer(Modifier.height(10.dp))
                AreaChart(priced.map { it.price!! }, Modifier.fillMaxWidth().height(130.dp), Pal.Amber, onSelect = { sel = it })
            }
        }
        val paid = v.fills.filter { it.amount > 0 }
        if (paid.size >= 2) {
            GlassCard {
                Label("Spesa per rifornimento")
                Spacer(Modifier.height(12.dp))
                Bars(paid.map { it.amount }, Modifier.fillMaxWidth().height(100.dp), Pal.Cyan)
            }
        }

        Label("Storico", Modifier.padding(start = 4.dp))
        v.fills.reversed().forEach { f ->
            GlassCard(onClick = { onEditFill(f.id) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(dayMonth(f.date), fontWeight = FontWeight.Bold)
                        Text(
                            if (f.amount > 0) listOfNotNull(f.liters?.let { num(it, 1) + " L" }, f.price?.let { eur(it, 3) + "/L" }, if (f.full) "pieno" else null).joinToString(" · ")
                            else "Solo lettura km",
                            color = Pal.Muted, fontSize = 12.sp,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        if (f.amount > 0) Text(eur(f.amount, 2), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        f.km?.let { Text(num(it, 0) + " km", color = if (f.amount > 0) Pal.Muted else Pal.Text, fontSize = if (f.amount > 0) 12.sp else 16.sp, fontWeight = if (f.amount > 0) FontWeight.Normal else FontWeight.Bold) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ForecastCard(v: Vehicle, onSettings: () -> Unit) {
    val f = v.forecast ?: return
    GlassCard(onClick = onSettings) {
        Label("Km a chiusura · " + dayMonth(f.endDate))
        Spacer(Modifier.height(6.dp))
        if (f.projectedKm == null) {
            Text("–", style = BigNumber)
            Text("Servono almeno due letture dei km in date diverse.", color = Pal.Muted, fontSize = 13.sp)
        } else {
            Text(num(f.projectedKm, 0) + " km", style = BigNumber, maxLines = 1)
            Text(
                "Mancano ${f.daysLeft} giorni · ritmo ${num(f.kmPerDay ?: 0.0, 1)} km/giorno · previsti ${num(f.projectedDriven ?: 0.0, 0)} km nel periodo",
                color = Pal.Muted, fontSize = 13.sp,
            )
        }
        val lim = f.limit
        if (lim != null && f.projectedKm != null) {
            val over = f.overLimit ?: 0.0
            val c = if (over <= 0) Pal.Emerald else Pal.Rose
            Spacer(Modifier.height(14.dp))
            val span = (lim - f.startKm).coerceAtLeast(1.0)
            val done = (f.driven / span).coerceIn(0.0, 1.0).toFloat()
            Box(Modifier.fillMaxWidth().height(10.dp).background(Pal.Surface2, RoundedCornerShape(50))) {
                Box(Modifier.fillMaxWidth(done).fillMaxHeight().background(c, RoundedCornerShape(50)))
            }
            Spacer(Modifier.height(10.dp))
            DeltaChip(
                if (over <= 0) "Entro il limite di ${num(lim, 0)} km · restano ${num(-over, 0)} km"
                else "Oltre il limite di ${num(lim, 0)} km di ${num(over, 0)} km",
                over <= 0,
            )
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth()) {
                Stat("Ritmo attuale", num(f.kmPerDay ?: 0.0, 1) + " km/g", Modifier.weight(1f))
                Stat(
                    "Ritmo consentito",
                    f.allowedPerDay?.let { if (it >= 0) num(it, 1) + " km/g" else "superato" } ?: "–",
                    Modifier.weight(1f), c,
                )
            }
        } else if (lim == null) {
            Spacer(Modifier.height(10.dp))
            Text("Tocca per impostare un limite di km e confrontarlo con la previsione.", color = Pal.Muted, fontSize = 12.sp)
        }
    }
}

@Composable
fun Empty(text: String) = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Text(text, color = Pal.Muted)
}

/** Xetra: lun–ven 9:00–17:30 (ora di Berlino). */
private fun marketOpen(): Boolean {
    val t = java.time.ZonedDateTime.now(java.time.ZoneId.of("Europe/Berlin"))
    if (t.dayOfWeek.value > 5) return false
    val m = t.hour * 60 + t.minute
    return m in (9 * 60)..(17 * 60 + 30)
}
