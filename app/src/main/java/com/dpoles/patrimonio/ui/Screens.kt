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
fun HomeScreen(bal: List<BalRow>) {
    if (bal.size < 2) { Empty("Nessun dato nel foglio Bal"); return }
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
            Label(if (sel == null) "Patrimonio · ${monthLong(cur.date)}" else
                monthLong(shown.date) + if (shown.isProjection) " · previsione" else "")
            Spacer(Modifier.height(6.dp))
            if (sel == null) CountUp(cur.total, BigNumber)
            else Text(eur(shown.total), style = BigNumber, color = Pal.Text, maxLines = 1)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (prev != null) { val d = shown.total - (bal.getOrNull(bal.indexOf(shown) - 1)?.total ?: prev.total); DeltaChip(signed(d) + " mese", d >= 0) }
                if (yearAgo != null && sel == null) { val d = cur.total - yearAgo.total; DeltaChip(signed(d) + " 12 mesi", d >= 0) }
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
            Slice("Deposito Santander", cur.santander, Pal.Amber), Slice("Liquidità e c/c", liquid, Pal.Blue),
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
    }
}

// ═════════════════════════ AZIONI / MONETARI ═════════════════════════

@Composable
fun PortfolioScreen(p: Portfolio, accent: Color, unit: String) {
    var sel by remember { mutableStateOf<Int?>(null) }
    val series = p.series
    val capMap = p.capital.toMap()
    val aligned = series.map { capMap[it.first] }.takeIf { l -> l.isNotEmpty() && l.all { it != null } }?.map { it!! }

    Page {
        Spacer(Modifier.height(4.dp))
        GlassCard {
            val s = sel?.let { series.getOrNull(it) }
            Label(if (s == null) "Valore $unit" else monthLong(s.first))
            Spacer(Modifier.height(6.dp))
            if (s == null) CountUp(p.value, BigNumber, decimals = 0) else Text(eur(s.second), style = BigNumber, maxLines = 1)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DeltaChip(signed(p.gain) + (p.gainPct?.let { " · " + pct(it) } ?: ""), p.gain >= 0)
                if (s != null && aligned != null) {
                    Text("Versato " + eur(aligned[sel!!]), color = Pal.Violet, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterVertically))
                }
            }
            if (series.size >= 2) {
                Spacer(Modifier.height(14.dp))
                AreaChart(series.map { it.second }, Modifier.fillMaxWidth().height(180.dp), accent, secondary = aligned, onSelect = { sel = it })
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(monthShort(series.first().first) + " ${series.first().first.year % 100}", color = Pal.Muted, fontSize = 11.sp)
                    if (aligned != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Dot(accent); Text("valore", color = Pal.Muted, fontSize = 11.sp)
                        Dot(Pal.Violet); Text("versato", color = Pal.Muted, fontSize = 11.sp)
                    }
                    Text(monthShort(series.last().first) + " ${series.last().first.year % 100}", color = Pal.Muted, fontSize = 11.sp)
                }
            }
        }

        GlassCard {
            Row(Modifier.fillMaxWidth()) {
                Stat("Investito", eur(p.invested), Modifier.weight(1f))
                Stat("Plusvalenza", signed(p.gain), Modifier.weight(1f), if (p.gain >= 0) Pal.Emerald else Pal.Rose)
                Stat("Netto tasse", p.net?.let { signed(it) } ?: "–", Modifier.weight(1f), Pal.Text)
            }
        }

        if (p.lots.isNotEmpty()) {
            Label("Acquisti (${p.lots.size})", Modifier.padding(start = 4.dp))
            p.lots.sortedByDescending { it.date }.forEach { LotCard(it) }
        }

        if (p.deposits.isNotEmpty()) {
            GlassCard {
                Label("Versamenti")
                Spacer(Modifier.height(8.dp))
                p.deposits.takeLast(8).reversed().forEach { d ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(d.date?.let(::dayMonth) ?: "–", color = Pal.Muted, fontSize = 13.sp, modifier = Modifier.width(84.dp))
                        Text(d.label ?: "", color = Pal.Muted, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 1)
                        Text(signed(d.amount), fontWeight = FontWeight.SemiBold, color = if (d.amount >= 0) Pal.Text else Pal.Amber)
                    }
                }
            }
        }
    }
}

@Composable
private fun LotCard(l: Lot) {
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(l.date?.let(::dayMonth) ?: "–", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(
                    listOfNotNull(l.qty?.let { num(it, 0) + " quote" }, l.buyPrice?.let { "a " + eur(it, 2) }).joinToString(" "),
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
fun FuelScreen(vehicles: List<Vehicle>) {
    if (vehicles.isEmpty()) { Empty("Nessun rifornimento nel foglio Rif"); return }
    var idx by remember { mutableIntStateOf(0) }
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
                        .clickableNoRipple { idx = i }.padding(horizontal = 16.dp, vertical = 8.dp),
                ) { Text(it.name, color = if (on) Pal.Bg else Pal.Muted, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
            }
        }
        GlassCard {
            Label("Speso in carburante")
            Spacer(Modifier.height(6.dp))
            key(v.name) { CountUp(v.spent, BigNumber, decimals = 0) }
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
        }

        if (priced.size >= 2) {
            var sel by remember(v.name) { mutableStateOf<Int?>(null) }
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
        paid.reversed().forEach { f ->
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(dayMonth(f.date), fontWeight = FontWeight.Bold)
                        Text(
                            listOfNotNull(f.liters?.let { num(it, 1) + " L" }, f.price?.let { eur(it, 3) + "/L" }, if (f.full) "pieno" else null).joinToString(" · "),
                            color = Pal.Muted, fontSize = 12.sp,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(eur(f.amount, 2), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        f.km?.let { Text(num(it, 0) + " km", color = Pal.Muted, fontSize = 12.sp) }
                    }
                }
            }
        }
    }
}

@Composable
fun Empty(text: String) = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Text(text, color = Pal.Muted)
}
