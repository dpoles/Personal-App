package com.dpoles.patrimonio.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

/**
 * Grafico ad area con curva morbida. I punti da [dashedFrom] in poi (proiezioni) sono tratteggiati.
 * Trascinando il dito si seleziona un punto ([onSelect]).
 */
@Composable
fun AreaChart(
    values: List<Double>,
    modifier: Modifier = Modifier,
    accent: Color = Pal.Emerald,
    dashedFrom: Int = Int.MAX_VALUE,
    secondary: List<Double>? = null,
    secondaryColor: Color = Pal.Violet,
    onSelect: (Int?) -> Unit = {},
) {
    if (values.size < 2) return
    val progress = remember(values) { Animatable(0f) }
    LaunchedEffect(values) { progress.animateTo(1f, tween(1100, easing = FastOutSlowInEasing)) }
    var sel by remember(values) { mutableStateOf<Int?>(null) }
    val n = values.size

    Canvas(
        modifier
            .pointerInput(values) {
                fun idx(x: Float) = ((x / size.width) * (n - 1)).let { Math.round(it) }.coerceIn(0, n - 1)
                detectHorizontalDragGestures(
                    onDragStart = { sel = idx(it.x); onSelect(sel) },
                    onHorizontalDrag = { ch, _ -> sel = idx(ch.position.x); onSelect(sel) },
                    onDragEnd = { sel = null; onSelect(null) },
                    onDragCancel = { sel = null; onSelect(null) },
                )
            }
            .pointerInput(values) {
                fun idx(x: Float) = ((x / size.width) * (n - 1)).let { Math.round(it) }.coerceIn(0, n - 1)
                detectTapGestures(onPress = { sel = idx(it.x); onSelect(sel); tryAwaitRelease(); sel = null; onSelect(null) })
            },
    ) {
        val all = values + (secondary ?: emptyList())
        var lo = all.min(); var hi = all.max()
        if (hi - lo < 1e-9) { hi += 1; lo -= 1 }
        val pad = (hi - lo) * 0.08; lo -= pad; hi += pad
        val top = 8.dp.toPx(); val bottom = size.height - 6.dp.toPx()
        fun x(i: Int) = i * size.width / (n - 1)
        fun y(v: Double) = (top + (1 - (v - lo) / (hi - lo)) * (bottom - top)).toFloat()

        // griglia
        for (k in 0..3) {
            val gy = top + k * (bottom - top) / 3
            drawLine(Pal.Line.copy(alpha = 0.45f), Offset(0f, gy), Offset(size.width, gy), 1.dp.toPx())
        }

        fun smooth(list: List<Double>, from: Int, to: Int): Path {
            val p = Path()
            p.moveTo(x(from), y(list[from]))
            for (i in from + 1..to) {
                val cx = (x(i - 1) + x(i)) / 2
                p.cubicTo(cx, y(list[i - 1]), cx, y(list[i]), x(i), y(list[i]))
            }
            return p
        }

        val cut = min(dashedFrom, n - 1)
        clipRect(right = size.width * progress.value) {
            secondary?.takeIf { it.size == n }?.let {
                drawPath(smooth(it, 0, n - 1), secondaryColor.copy(alpha = 0.9f), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
            }
            // area + linea reale
            val real = smooth(values, 0, cut)
            val fill = Path().apply {
                addPath(real); lineTo(x(cut), bottom); lineTo(0f, bottom); close()
            }
            drawPath(fill, Brush.verticalGradient(listOf(accent.copy(alpha = 0.38f), accent.copy(alpha = 0f)), startY = top, endY = bottom))
            drawPath(real, accent, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            // proiezione
            if (cut < n - 1) {
                val proj = smooth(values, cut, n - 1)
                val pf = Path().apply { addPath(proj); lineTo(x(n - 1), bottom); lineTo(x(cut), bottom); close() }
                drawPath(pf, Brush.verticalGradient(listOf(accent.copy(alpha = 0.10f), accent.copy(alpha = 0f)), startY = top, endY = bottom))
                drawPath(
                    proj, accent.copy(alpha = 0.75f),
                    style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 12f))),
                )
            }
        }
        // punto "oggi" + selezione
        if (cut < n - 1) {
            drawCircle(accent, 5.dp.toPx(), Offset(x(cut), y(values[cut])))
            drawCircle(Pal.Bg, 2.5.dp.toPx(), Offset(x(cut), y(values[cut])))
        }
        sel?.let { i ->
            drawLine(Pal.Text.copy(alpha = 0.35f), Offset(x(i), top), Offset(x(i), bottom), 1.dp.toPx())
            drawCircle(accent.copy(alpha = 0.25f), 11.dp.toPx(), Offset(x(i), y(values[i])))
            drawCircle(accent, 5.5.dp.toPx(), Offset(x(i), y(values[i])))
            drawCircle(Pal.Bg, 2.5.dp.toPx(), Offset(x(i), y(values[i])))
        }
    }
}

data class Slice(val label: String, val value: Double, val color: Color)

@Composable
fun Donut(slices: List<Slice>, modifier: Modifier = Modifier, thickness: Float = 26f) {
    val total = slices.sumOf { max(0.0, it.value) }
    if (total <= 0) return
    val p = remember(slices) { Animatable(0f) }
    LaunchedEffect(slices) { p.animateTo(1f, tween(1200, easing = FastOutSlowInEasing)) }
    Canvas(modifier) {
        val stroke = thickness.dp.toPx()
        val d = min(size.width, size.height) - stroke
        val tl = Offset((size.width - d) / 2, (size.height - d) / 2)
        var start = -90f
        val gap = 3f
        val sweepTotal = 360f * p.value
        var used = 0f
        slices.filter { it.value > 0 }.forEach { s ->
            val full = (s.value / total * 360f).toFloat()
            val sw = max(0f, min(full - gap, sweepTotal - used - gap))
            if (sw > 0f) drawArc(s.color, start + gap / 2, sw, false, tl, Size(d, d), style = Stroke(stroke, cap = StrokeCap.Round))
            start += full; used += full
        }
    }
}

/** Barre affiancate (es. entrate vs uscite). */
@Composable
fun PairBars(a: List<Double>, b: List<Double>, modifier: Modifier = Modifier, colorA: Color = Pal.Emerald, colorB: Color = Pal.Rose) {
    val p = remember(a, b) { Animatable(0f) }
    LaunchedEffect(a, b) { p.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    Canvas(modifier) {
        val hi = max(a.maxOrNull() ?: 1.0, b.maxOrNull() ?: 1.0).coerceAtLeast(1.0)
        val n = a.size
        val slot = size.width / n
        val bw = slot * 0.30f
        for (i in 0 until n) {
            val cx = slot * i + slot / 2
            fun bar(v: Double, c: Color, ox: Float) {
                val h = (v / hi).toFloat() * size.height * p.value
                drawRoundRect(c, Offset(cx + ox, size.height - h), Size(bw, h), CornerRadius(bw / 2, bw / 2))
            }
            bar(a[i], colorA, -bw - 1.dp.toPx())
            bar(b.getOrElse(i) { 0.0 }, colorB.copy(alpha = 0.85f), 1.dp.toPx())
        }
    }
}

/** Barre singole, con la selezione evidenziata. */
@Composable
fun Bars(values: List<Double>, modifier: Modifier = Modifier, color: Color = Pal.Cyan) {
    val p = remember(values) { Animatable(0f) }
    LaunchedEffect(values) { p.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    Canvas(modifier) {
        val hi = (values.maxOrNull() ?: 1.0).coerceAtLeast(1.0)
        val slot = size.width / values.size
        val bw = (slot * 0.62f).coerceAtMost(26.dp.toPx())
        values.forEachIndexed { i, v ->
            val h = (v / hi).toFloat() * size.height * p.value
            drawRoundRect(
                Brush.verticalGradient(listOf(color, color.copy(alpha = 0.35f)), startY = size.height - h, endY = size.height),
                Offset(slot * i + (slot - bw) / 2, size.height - h), Size(bw, h), CornerRadius(6.dp.toPx(), 6.dp.toPx()),
            )
        }
    }
}
