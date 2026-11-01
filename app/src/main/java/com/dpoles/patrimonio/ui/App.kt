package com.dpoles.patrimonio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dpoles.patrimonio.MainViewModel
import com.dpoles.patrimonio.data.*

private data class Tab(val title: String, val icon: String, val fab: String)

private val TABS = listOf(
    Tab("Patrimonio", "💰", "Nuovo mese"), Tab("Azioni", "📈", "Acquisto"),
    Tab("Monetari", "🏦", "Acquisto"), Tab("Auto", "⛽", "Rifornimento"),
)

@Composable
fun PatrimonioApp(db: Db, vm: MainViewModel, onImport: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var hide by rememberSaveable { mutableStateOf(false) }
    var vehIdx by rememberSaveable { mutableIntStateOf(0) }
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    val preview by vm.importPreview.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()

    val bal = remember(db) { db.balRows() }
    val azioni = remember(db) { db.portfolio(AZIONI) }
    val monetari = remember(db) { db.portfolio(MONETARI) }
    val vehicles = remember(db) { db.vehicleList() }
    val currentVehicle = vehicles.getOrNull(vehIdx.coerceIn(0, (vehicles.size - 1).coerceAtLeast(0)))?.id

    fun add() { sheet = when (tab) { 0 -> Sheet.NewMonth; 1 -> Sheet.NewLot(AZIONI); 2 -> Sheet.NewLot(MONETARI); else -> Sheet.NewFill(currentVehicle) } }
    val empty = when (tab) { 0 -> bal.isEmpty(); 1 -> azioni.lots.isEmpty(); 2 -> monetari.lots.isEmpty(); else -> vehicles.isEmpty() }

    CompositionLocalProvider(LocalHide provides hide, LocalContentColor provides Pal.Text) {
        Box(
            Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Pal.Surface2.copy(alpha = 0.6f), Pal.Bg), endY = 900f))
        ) {
            Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                    Row(
                        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(TABS[tab].title, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                        Text("📥", fontSize = 22.sp, modifier = Modifier.clickableNoRipple(onImport).padding(8.dp))
                        Text(if (hide) "🙈" else "👁", fontSize = 22.sp, modifier = Modifier.clickableNoRipple { hide = !hide }.padding(8.dp))
                    }
                },
                floatingActionButton = {
                    if (!empty) ExtendedFloatingActionButton(
                        onClick = ::add, containerColor = Pal.Emerald, contentColor = Pal.Bg,
                        text = { Text(TABS[tab].fab, fontWeight = FontWeight.Bold) },
                        icon = { Text("＋", fontSize = 20.sp, fontWeight = FontWeight.Bold) },
                    )
                },
                bottomBar = {
                    NavigationBar(containerColor = Pal.Surface.copy(alpha = 0.96f)) {
                        TABS.forEachIndexed { i, t ->
                            NavigationBarItem(
                                selected = tab == i, onClick = { tab = i },
                                icon = { Text(t.icon, fontSize = 20.sp) },
                                label = { Text(t.title, fontSize = 11.sp) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedTextColor = Pal.Emerald, unselectedTextColor = Pal.Muted,
                                    indicatorColor = Pal.Emerald.copy(alpha = 0.16f),
                                ),
                            )
                        }
                    }
                },
            ) { pad ->
                Box(Modifier.padding(pad)) {
                    when (tab) {
                        0 -> HomeScreen(bal, onAdd = ::add, onEdit = { sheet = Sheet.EditMonth(it) }, onImport = onImport)
                        1 -> PortfolioScreen(azioni, Pal.Violet, "azioni", ::add, { sheet = Sheet.EditLot(it) }, { sheet = Sheet.EditPrice(AZIONI, it) })
                        2 -> PortfolioScreen(monetari, Pal.Cyan, "monetari", ::add, { sheet = Sheet.EditLot(it) }, { sheet = Sheet.EditPrice(MONETARI, it) })
                        else -> FuelScreen(vehicles, vehIdx, { vehIdx = it }, { sheet = Sheet.NewFill(it ?: currentVehicle).takeIf { _ -> it != null } ?: Sheet.NewFill(null) }, { sheet = Sheet.EditFill(it) }, { sheet = Sheet.EditVehicle(it) })
                    }
                }
            }
            sheet?.let { s -> SheetHost(s, db, vm) { sheet = null } }
            preview?.let { p ->
                AlertDialog(
                    onDismissRequest = vm::cancelImport,
                    title = { Text("Importare dal file Excel?") },
                    text = {
                        Text(
                            "${p.result.months} mesi, ${p.result.vehicles} veicoli (${p.result.fills} rifornimenti), ${p.result.lots} acquisti." +
                                if (p.replacing) "\n\nI dati attuali dell'app verranno sostituiti." else ""
                        )
                    },
                    confirmButton = { TextButton(vm::confirmImport) { Text("Importa", color = Pal.Emerald) } },
                    dismissButton = { TextButton(vm::cancelImport) { Text("Annulla", color = Pal.Muted) } },
                    containerColor = Pal.Surface, titleContentColor = Pal.Text, textContentColor = Pal.Muted,
                )
            }
            message?.let { m ->
                AlertDialog(
                    onDismissRequest = vm::dismissMessage, title = { Text("Attenzione") }, text = { Text(m) },
                    confirmButton = { TextButton(vm::dismissMessage) { Text("OK", color = Pal.Emerald) } },
                    containerColor = Pal.Surface, titleContentColor = Pal.Text, textContentColor = Pal.Muted,
                )
            }
        }
    }
}

@Composable
private fun SheetHost(s: Sheet, db: Db, vm: MainViewModel, close: () -> Unit) {
    when (s) {
        Sheet.NewMonth -> MonthForm(db, null, { vm.saveMonth(it); close() }, null, close)
        is Sheet.EditMonth -> db.months.firstOrNull { it.id == s.id }?.let { m ->
            MonthForm(db, m, { vm.saveMonth(it); close() }, { vm.deleteMonth(m.id); close() }, close)
        }
        is Sheet.NewLot -> LotForm(db, s.kind, null, { vm.saveLot(it); close() }, null, close)
        is Sheet.EditLot -> db.lots.firstOrNull { it.id == s.id }?.let { l ->
            LotForm(db, l.kind, l, { vm.saveLot(it); close() }, { vm.deleteLot(l.id); close() }, close)
        }
        is Sheet.EditPrice -> PriceForm(
            db.portfolio(s.kind).instruments.firstOrNull { it.name == s.name }?.price ?: 0.0, s.name,
            { vm.setPrice(s.kind, s.name, it); close() }, close,
        )
        is Sheet.NewFill -> FillForm(db, s.vehicleId, null, { f, n -> vm.saveFill(f, n); close() }, null, close)
        is Sheet.EditVehicle -> db.vehicles.firstOrNull { it.id == s.id }?.let { v ->
            val first = db.fills.filter { it.vehicleId == v.id && it.km != null }.minByOrNull { it.date }
            VehicleForm(v, first?.km, first?.date, { vm.saveVehicle(it); close() }, { vm.deleteVehicle(v.id); close() }, close)
        }
        is Sheet.EditFill -> db.fills.firstOrNull { it.id == s.id }?.let { f ->
            FillForm(db, f.vehicleId, f, { x, n -> vm.saveFill(x, n); close() }, { vm.deleteFill(f.id); close() }, close)
        }
    }
}
