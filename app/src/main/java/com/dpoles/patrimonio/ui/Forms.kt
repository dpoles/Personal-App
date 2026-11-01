package com.dpoles.patrimonio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dpoles.patrimonio.data.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

sealed interface Sheet {
    data object NewMonth : Sheet
    data class EditMonth(val id: Long) : Sheet
    data class NewLot(val kind: String) : Sheet
    data class EditLot(val id: Long) : Sheet
    data class NewFill(val vehicleId: Long?) : Sheet
    data class EditFill(val id: Long) : Sheet
    data class EditPrice(val kind: String, val name: String) : Sheet
}

private fun String.d(): Double? = trim().replace(',', '.').toDoubleOrNull()
private fun fmtIn(v: Double?): String = if (v == null) "" else
    (if (v == Math.rint(v) && kotlin.math.abs(v) < 1e12) v.toLong().toString() else "%.4f".format(java.util.Locale.ROOT, v).trimEnd('0').trimEnd('.')).replace('.', ',')

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Pal.Emerald, unfocusedBorderColor = Pal.Line,
    focusedLabelColor = Pal.Emerald, unfocusedLabelColor = Pal.Muted, cursorColor = Pal.Emerald,
)

@Composable
fun Field(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, suffix: String? = null, decimal: Boolean = true) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = fieldColors(),
        suffix = suffix?.let { { Text(it, color = Pal.Muted) } },
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Text),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(label: String, date: LocalDate, onChange: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Column(modifier) {
        Label(label)
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier.fillMaxWidth().background(Pal.Surface2, RoundedCornerShape(16.dp))
                .clickableNoRipple { open = true }.padding(horizontal = 16.dp, vertical = 15.dp),
        ) { Text("📅  ${date.dayOfMonth} ${monthLong(date)}", fontSize = 16.sp) }
    }
    if (open) {
        val st = rememberDatePickerState(initialSelectedDateMillis = date.toEpochDay() * 86_400_000L)
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton({
                    st.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    open = false
                }) { Text("OK", color = Pal.Emerald) }
            },
            dismissButton = { TextButton({ open = false }) { Text("Annulla", color = Pal.Muted) } },
        ) { DatePicker(st) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormSheet(
    title: String, onDismiss: () -> Unit, onSave: (() -> Unit)?, onDelete: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss, containerColor = Pal.Surface, contentColor = Pal.Text,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            content()
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
                if (onDelete != null) OutlinedButton(
                    onClick = onDelete, shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Pal.Rose),
                ) { Text("Elimina") }
                Button(
                    onClick = { onSave?.invoke() }, enabled = onSave != null, modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(50), contentPadding = PaddingValues(vertical = 14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Pal.Emerald, contentColor = Pal.Bg),
                ) { Text("Salva", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
            }
        }
    }
}

@Composable
private fun Hint(text: String, color: Color = Pal.Muted) = Text(text, color = color, fontSize = 13.sp)

// ───────────────────────── Mese ─────────────────────────

@Composable
fun MonthForm(db: Db, existing: MonthE?, onSave: (MonthE) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit) {
    val last = db.months.maxWithOrNull(compareBy({ it.year }, { it.month }))
    val start = existing?.let { LocalDate.of(it.year, it.month, 1) }
        ?: last?.let { LocalDate.of(it.year, it.month, 1).plusMonths(1) } ?: LocalDate.now().withDayOfMonth(1)
    var date by remember { mutableStateOf(start) }
    val az = db.portfolio(AZIONI).value; val mon = db.portfolio(MONETARI).value
    var income by remember { mutableStateOf(fmtIn(existing?.income)) }
    var expense by remember { mutableStateOf(fmtIn(existing?.expense)) }
    var cash by remember { mutableStateOf(fmtIn(existing?.cash ?: last?.cash)) }
    var deposit by remember { mutableStateOf(fmtIn(existing?.deposit ?: last?.deposit)) }
    var azioni by remember { mutableStateOf(fmtIn(existing?.azioni ?: az.takeIf { it > 0 }?.let { Math.round(it).toDouble() } ?: last?.azioni)) }
    var monetari by remember { mutableStateOf(fmtIn(existing?.monetari ?: mon.takeIf { it > 0 }?.let { Math.round(it).toDouble() } ?: last?.monetari)) }

    val clash = db.months.any { it.year == date.year && it.month == date.monthValue && it.id != (existing?.id ?: 0L) }
    fun v(s: String) = if (s.isBlank()) 0.0 else s.d()
    val nums = listOf(income, expense, cash, deposit, azioni, monetari).map(::v)
    val ok = nums.all { it != null } && !clash
    val total = (nums[2] ?: 0.0) + (nums[3] ?: 0.0) + (nums[4] ?: 0.0) + (nums[5] ?: 0.0)

    FormSheet(
        if (existing == null) "Nuovo mese" else "Modifica mese", onDismiss,
        onSave = if (!ok) null else ({
            onSave(MonthE(existing?.id ?: 0L, date.year, date.monthValue, nums[0]!!, nums[1]!!, nums[2]!!, nums[3]!!, nums[4]!!, nums[5]!!))
        }),
        onDelete = onDelete,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("‹", fontSize = 32.sp, modifier = Modifier.clickableNoRipple { date = date.minusMonths(1) }.padding(horizontal = 18.dp))
            Text(monthLong(date), fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("›", fontSize = 32.sp, modifier = Modifier.clickableNoRipple { date = date.plusMonths(1) }.padding(horizontal = 18.dp))
        }
        if (clash) Hint("Questo mese è già registrato: modificalo dalla lista.", Pal.Rose)
        Label("Flussi del mese")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Field("Entrate", income, { income = it }, Modifier.weight(1f), "€")
            Field("Uscite", expense, { expense = it }, Modifier.weight(1f), "€")
        }
        Label("A fine mese ho")
        Field("Liquidità (conto corrente, contanti)", cash, { cash = it }, suffix = "€")
        Field("Conto deposito / risparmio", deposit, { deposit = it }, suffix = "€")
        Field("Valore azioni / ETF", azioni, { azioni = it }, suffix = "€")
        Field("Valore monetari", monetari, { monetari = it }, suffix = "€")
        Hint("Patrimonio totale: " + eur(total))
    }
}

// ───────────────────────── Acquisto (azioni / monetari) ─────────────────────────

@Composable
fun LotForm(db: Db, kind: String, existing: LotE?, onSave: (LotE) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit) {
    val names = db.lots.filter { it.kind == kind }.map { it.name }.distinct()
    var name by remember { mutableStateOf(existing?.name ?: names.firstOrNull().orEmpty()) }
    var date by remember { mutableStateOf(existing?.date ?: LocalDate.now()) }
    var qty by remember { mutableStateOf(fmtIn(existing?.qty)) }
    var price by remember { mutableStateOf(fmtIn(existing?.price)) }
    var fee by remember { mutableStateOf(fmtIn(existing?.fee ?: 0.0)) }
    val q = qty.d(); val p = price.d(); val f = if (fee.isBlank()) 0.0 else fee.d()
    val ok = name.isNotBlank() && q != null && q > 0 && p != null && p > 0 && f != null

    FormSheet(
        if (existing == null) "Nuovo acquisto" else "Modifica acquisto", onDismiss,
        onSave = if (!ok) null else ({ onSave(LotE(existing?.id ?: 0L, kind, name.trim(), date, q!!, p!!, f!!)) }),
        onDelete = onDelete,
    ) {
        Field("Strumento (es. VWCE, XEON)", name, { name = it }, decimal = false)
        if (names.size > 1 || (names.size == 1 && names[0] != name)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                names.forEach { n ->
                    Box(Modifier.background(if (n == name) Pal.Emerald else Pal.Surface2, RoundedCornerShape(50)).clickableNoRipple { name = n }.padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Text(n, fontSize = 12.sp, color = if (n == name) Pal.Bg else Pal.Muted)
                    }
                }
            }
        }
        DateField("Data acquisto", date, { date = it })
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Field("Quantità", qty, { qty = it }, Modifier.weight(1f))
            Field("Prezzo unitario", price, { price = it }, Modifier.weight(1f), "€")
        }
        Field("Commissioni", fee, { fee = it }, suffix = "€")
        if (q != null && p != null) Hint("Costo totale: " + eur(q * p + (f ?: 0.0), 2))
    }
}

@Composable
fun PriceForm(current: Double, name: String, onSave: (Double) -> Unit, onDismiss: () -> Unit) {
    var price by remember { mutableStateOf(fmtIn(current)) }
    val p = price.d()
    FormSheet("Prezzo attuale · $name", onDismiss, onSave = if (p == null || p <= 0) null else ({ onSave(p) })) {
        Field("Prezzo per quota", price, { price = it }, suffix = "€")
        Hint("Serve a calcolare valore e plusvalenza degli acquisti.")
    }
}

// ───────────────────────── Rifornimento ─────────────────────────

@Composable
fun FillForm(db: Db, vehicleId: Long?, existing: FillE?, onSave: (FillE, String?) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit) {
    var veh by remember { mutableStateOf(existing?.vehicleId ?: vehicleId ?: db.vehicles.firstOrNull()?.id) }
    var newName by remember { mutableStateOf("") }
    val creating = veh == null || db.vehicles.isEmpty()
    var date by remember { mutableStateOf(existing?.date ?: LocalDate.now()) }
    var amount by remember { mutableStateOf(fmtIn(existing?.amount)) }
    var liters by remember { mutableStateOf(fmtIn(existing?.liters)) }
    var price by remember { mutableStateOf(fmtIn(existing?.liters?.takeIf { it > 0 }?.let { existing!!.amount / it })) }
    var km by remember { mutableStateOf(fmtIn(existing?.km)) }
    var full by remember { mutableStateOf(existing?.full ?: true) }

    val a = amount.d(); val l = liters.d(); val p = price.d()
    val amt = a ?: if (p != null && l != null) p * l else null
    val lit = l ?: if (a != null && p != null && p > 0) a / p else null
    val kmv = if (km.isBlank()) null else km.d()
    val ok = amt != null && amt > 0 && (!creating || newName.isNotBlank()) && (km.isBlank() || kmv != null) &&
        (liters.isBlank() || l != null) && (price.isBlank() || p != null)

    FormSheet(
        if (existing == null) "Nuovo rifornimento" else "Modifica rifornimento", onDismiss,
        onSave = if (!ok) null else ({
            onSave(FillE(existing?.id ?: 0L, veh ?: 0L, date, amt!!, lit, kmv, full), if (creating) newName else null)
        }),
        onDelete = onDelete,
    ) {
        Label("Veicolo")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            db.vehicles.forEach { v ->
                val on = !creating && v.id == veh
                Box(Modifier.background(if (on) Pal.Amber else Pal.Surface2, RoundedCornerShape(50)).clickableNoRipple { veh = v.id }.padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Text(v.name, color = if (on) Pal.Bg else Pal.Muted, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
            if (db.vehicles.isNotEmpty()) Box(Modifier.background(if (creating) Pal.Amber else Pal.Surface2, RoundedCornerShape(50)).clickableNoRipple { veh = null }.padding(horizontal = 14.dp, vertical = 8.dp)) {
                Text("＋ Nuovo", color = if (creating) Pal.Bg else Pal.Muted, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        }
        if (creating) Field("Nome del veicolo (es. Auto, Moto)", newName, { newName = it }, decimal = false)
        DateField("Data", date, { date = it })
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Field("Importo", amount, { amount = it }, Modifier.weight(1f), "€")
            Field("Litri", liters, { liters = it }, Modifier.weight(1f), "L")
        }
        Field("Prezzo al litro (se non conosci importo o litri)", price, { price = it }, suffix = "€/L")
        Field("Chilometri del contachilometri", km, { km = it }, suffix = "km")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Pieno", Modifier.weight(1f), fontSize = 16.sp)
            Switch(full, { full = it }, colors = SwitchDefaults.colors(checkedTrackColor = Pal.Emerald, checkedThumbColor = Pal.Bg))
        }
        if (amt != null && lit != null && lit > 0) Hint("Prezzo: " + eur(amt / lit, 3) + "/L")
        Hint("Il primo rifornimento fa da base per i chilometri: calcolo consumi dal secondo in poi.")
    }
}
