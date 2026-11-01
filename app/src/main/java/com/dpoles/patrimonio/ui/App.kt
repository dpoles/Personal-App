package com.dpoles.patrimonio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dpoles.patrimonio.Ui
import java.text.DateFormat
import java.util.Date

private data class Tab(val title: String, val icon: String)

private val TABS = listOf(Tab("Patrimonio", "💰"), Tab("Azioni", "📈"), Tab("Monetari", "🏦"), Tab("Auto", "⛽"))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatrimonioApp(ui: Ui, onRefresh: () -> Unit, onLogin: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var hide by rememberSaveable { mutableStateOf(false) }
    val data = ui.data

    CompositionLocalProvider(LocalHide provides hide, LocalContentColor provides Pal.Text) {
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Pal.Surface2.copy(alpha = 0.6f), Pal.Bg), endY = 900f)
            )
        ) {
            if (data == null) {
                LoginScreen(ui, onLogin, onRefresh)
            } else {
                Scaffold(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                    topBar = {
                        Row(
                            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(TABS[tab].title, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                                val sub = if (ui.error != null) "⚠ ${ui.error}"
                                else if (ui.needsLogin) "Offline · tocca per accedere"
                                else "Aggiornato " + DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(data.updatedAt))
                                Text(
                                    sub, fontSize = 12.sp,
                                    color = if (ui.error != null) Pal.Rose else Pal.Muted,
                                    modifier = if (ui.needsLogin) Modifier.clickableNoRipple(onLogin) else Modifier,
                                )
                            }
                            Text(if (hide) "🙈" else "👁", fontSize = 22.sp, modifier = Modifier.clickableNoRipple { hide = !hide }.padding(8.dp))
                        }
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
                    PullToRefreshBox(isRefreshing = ui.loading, onRefresh = onRefresh, modifier = Modifier.padding(pad)) {
                        Section(
                            err = data.errors.firstOrNull { it.startsWith(listOf("Bal", "Azioni", "Monetari", "Rif")[tab]) },
                        ) {
                            when (tab) {
                                0 -> data.bal?.let { HomeScreen(it) }
                                1 -> data.azioni?.let { PortfolioScreen(it, Pal.Violet, "azioni") }
                                2 -> data.monetari?.let { PortfolioScreen(it, Pal.Cyan, "monetari") }
                                else -> data.vehicles?.let { FuelScreen(it) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(err: String?, content: @Composable () -> Unit) {
    if (err != null) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Non riesco a leggere questo foglio", fontWeight = FontWeight.Bold)
                Text(err, color = Pal.Muted, fontSize = 13.sp, textAlign = TextAlign.Center)
            }
        }
    } else content()
}

@Composable
private fun LoginScreen(ui: Ui, onLogin: () -> Unit, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp).systemBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(96.dp).background(Brush.linearGradient(listOf(Pal.Emerald, Pal.Cyan)), RoundedCornerShape(28.dp)),
            contentAlignment = Alignment.Center,
        ) { Text("📈", fontSize = 44.sp) }
        Spacer(Modifier.height(28.dp))
        Text("Patrimonio", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Il tuo foglio Google, in una dashboard.\nAccedi per leggere i dati in sola lettura.",
            color = Pal.Muted, textAlign = TextAlign.Center, fontSize = 15.sp,
        )
        Spacer(Modifier.height(32.dp))
        if (ui.loading) CircularProgressIndicator(color = Pal.Emerald)
        else Button(
            onClick = { if (ui.needsLogin) onLogin() else onRetry() },
            colors = ButtonDefaults.buttonColors(containerColor = Pal.Emerald, contentColor = Pal.Bg),
            shape = RoundedCornerShape(50), contentPadding = PaddingValues(horizontal = 32.dp, vertical = 14.dp),
        ) { Text(if (ui.needsLogin) "Accedi con Google" else "Riprova", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        ui.error?.let {
            Spacer(Modifier.height(20.dp))
            Text(it, color = Pal.Rose, fontSize = 13.sp, textAlign = TextAlign.Center)
        }
    }
}
