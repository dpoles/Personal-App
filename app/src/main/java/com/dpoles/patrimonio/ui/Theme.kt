package com.dpoles.patrimonio.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.composed
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.TextStyle as DateTextStyle
import java.util.Locale

object Pal {
    val Bg = Color(0xFF070B14)
    val Surface = Color(0xFF0F1626)
    val Surface2 = Color(0xFF151E33)
    val Line = Color(0xFF223050)
    val Text = Color(0xFFE8EEF9)
    val Muted = Color(0xFF8A97B2)
    val Emerald = Color(0xFF2DD4A4)
    val Cyan = Color(0xFF22D3EE)
    val Violet = Color(0xFF8B5CF6)
    val Amber = Color(0xFFF5B544)
    val Rose = Color(0xFFFB7185)
    val Blue = Color(0xFF60A5FA)
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Pal.Emerald, onPrimary = Pal.Bg, background = Pal.Bg, onBackground = Pal.Text,
            surface = Pal.Surface, onSurface = Pal.Text, surfaceVariant = Pal.Surface2,
            onSurfaceVariant = Pal.Muted, secondaryContainer = Pal.Surface2, outline = Pal.Line,
        ),
        content = content,
    )
}

/** Quando true gli importi sono nascosti (modalità privacy). */
val LocalHide = compositionLocalOf { false }

private val fmt0: NumberFormat = NumberFormat.getCurrencyInstance(Locale.ITALY).apply { maximumFractionDigits = 0 }
private val fmt2: NumberFormat = NumberFormat.getCurrencyInstance(Locale.ITALY).apply { maximumFractionDigits = 2 }
private val fmtN: NumberFormat = NumberFormat.getNumberInstance(Locale.ITALY).apply { maximumFractionDigits = 2 }

@Composable
fun eur(v: Double, decimals: Int = 0): String =
    if (LocalHide.current) "••••••" else (if (decimals == 0) fmt0 else fmt2).format(v)

fun num(v: Double, decimals: Int = 2): String =
    NumberFormat.getNumberInstance(Locale.ITALY).apply { maximumFractionDigits = decimals; minimumFractionDigits = decimals }.format(v)

fun pct(v: Double, decimals: Int = 1): String = num(v * 100, decimals) + "%"
fun signed(v: Double) = (if (v >= 0) "+" else "−") + fmt0.format(kotlin.math.abs(v))

fun monthShort(d: LocalDate): String =
    d.month.getDisplayName(DateTextStyle.SHORT, Locale.ITALY).replaceFirstChar { it.uppercase() }.removeSuffix(".")

fun monthLong(d: LocalDate): String =
    d.month.getDisplayName(DateTextStyle.FULL, Locale.ITALY).replaceFirstChar { it.uppercase() } + " ${d.year}"

fun dayMonth(d: LocalDate) = "${d.dayOfMonth} ${monthShort(d)} ${d.year % 100}"

@Composable
fun GlassCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = Pal.Surface,
        border = BorderStroke(1.dp, Brush.verticalGradient(listOf(Pal.Line, Pal.Line.copy(alpha = 0.3f)))),
    ) {
        Column(
            (if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(18.dp),
            content = content,
        )
    }
}

@Composable
fun Label(text: String, modifier: Modifier = Modifier) = Text(
    text.uppercase(), modifier, color = Pal.Muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp,
)

@Composable
fun CountUp(value: Double, style: TextStyle, color: Color = Pal.Text, decimals: Int = 0) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(value) { a.animateTo(value.toFloat(), tween(1300, easing = FastOutSlowInEasing)) }
    Text(eur(a.value.toDouble(), decimals), style = style, color = color, maxLines = 1)
}

@Composable
fun DeltaChip(text: String, positive: Boolean) {
    val c = if (positive) Pal.Emerald else Pal.Rose
    Box(
        Modifier.background(c.copy(alpha = 0.14f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = c, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
fun Stat(label: String, value: String, modifier: Modifier = Modifier, color: Color = Pal.Text, sub: String? = null) {
    Column(modifier) {
        Label(label)
        Spacer(Modifier.height(4.dp))
        Text(value, color = color, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        if (sub != null) Text(sub, color = Pal.Muted, fontSize = 12.sp)
    }
}

@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.background(Pal.Surface2, RoundedCornerShape(50)).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEachIndexed { i, o ->
            val on = i == selected
            Box(
                Modifier.background(if (on) Pal.Emerald else Color.Transparent, RoundedCornerShape(50))
                    .clickableNoRipple { onSelect(i) }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            ) { Text(o, color = if (on) Pal.Bg else Pal.Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}

fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = composed {
    clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null, onClick = onClick,
    )
}

@Composable
fun EmptyState(emoji: String, title: String, text: String, button: String, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(96.dp).background(Brush.linearGradient(listOf(Pal.Surface2, Pal.Surface)), RoundedCornerShape(28.dp)),
            contentAlignment = Alignment.Center,
        ) { Text(emoji, fontSize = 44.sp) }
        Spacer(Modifier.height(24.dp))
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(8.dp))
        Text(text, color = Pal.Muted, fontSize = 15.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onClick, shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = Pal.Emerald, contentColor = Pal.Bg),
            contentPadding = PaddingValues(horizontal = 30.dp, vertical = 14.dp),
        ) { Text(button, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
    }
}
