package com.rdm.eq.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rdm.eq.dsp.CurveMath
import com.rdm.eq.model.EqBands
import com.rdm.eq.data.PresetNames
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/** Large-target vertical gain fader: drag to adjust (0.5 dB steps), +/- for fine steps, double-tap to reset. */
@Composable
fun VerticalGainSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    label: String,
    sublabel: String,
    modifier: Modifier = Modifier,
    min: Float = EqBands.MIN_DB,
    max: Float = EqBands.MAX_DB,
    step: Float = EqBands.STEP_DB,
    enabled: Boolean = true,
    showStepButtons: Boolean = true,
    resetValue: Float = 0f,
    narrow: Boolean = false,
) {
    val current by rememberUpdatedState(value)
    val onChange by rememberUpdatedState(onValueChange)
    var acc by remember { mutableFloatStateOf(value) }
    val cs = MaterialTheme.colorScheme
    val accent = if (enabled) cs.primary else cs.onSurfaceVariant
    fun emit(v: Float) {
        val snapped = ((v / step).roundToInt() * step).coerceIn(min, max)
        if (snapped != current) onChange(snapped)
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            if (narrow) EqBands.formatDb(value).removeSuffix(".0") else EqBands.formatDb(value),
            style = if (narrow) MaterialTheme.typography.labelMedium else MaterialTheme.typography.titleMedium,
            color = if (value != 0f) accent else cs.onSurfaceVariant,
            maxLines = 1,
        )
        if (showStepButtons) StepButton("+", enabled) { emit(current + step) }
        Box(
            Modifier
                .weight(1f)
                .widthIn(max = 64.dp)
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .pointerInput(enabled, min, max) {
                    if (!enabled) return@pointerInput
                    detectVerticalDragGestures(onDragStart = { acc = current }) { change, dy ->
                        change.consume()
                        acc = (acc - dy / size.height * (max - min)).coerceIn(min, max)
                        emit(acc)
                    }
                }
                .pointerInput(enabled) {
                    detectTapGestures(onDoubleTap = { if (enabled) onChange(resetValue) })
                }
                .semantics {
                    contentDescription = "$label gain"
                    stateDescription = "${EqBands.formatDb(value)} dB"
                    progressBarRangeInfo = ProgressBarRangeInfo(value, min..max, ((max - min) / step).toInt() - 1)
                    if (enabled) setProgress { v -> emit(v); true }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val trackW = 10.dp.toPx()
                val thumbR = 15.dp.toPx()
                val top = thumbR; val bottom = size.height - thumbR
                fun y(db: Float) = bottom - (db - min) / (max - min) * (bottom - top)
                val cx = size.width / 2
                drawRoundRect(cs.surfaceVariant, Offset(cx - trackW / 2, top), Size(trackW, bottom - top), CornerRadius(trackW / 2))
                // tick marks every 6 dB
                var t = (min / 6f).toInt() * 6f
                while (t <= max) {
                    val ty = y(t)
                    drawLine(cs.outline, Offset(cx - trackW * 1.6f, ty), Offset(cx - trackW * 0.9f, ty), 1.dp.toPx())
                    drawLine(cs.outline, Offset(cx + trackW * 0.9f, ty), Offset(cx + trackW * 1.6f, ty), 1.dp.toPx())
                    t += 6f
                }
                val zero = y(0f.coerceIn(min, max)); val vy = y(value)
                drawRoundRect(
                    Brush.verticalGradient(listOf(accent, accent.copy(alpha = 0.55f))),
                    Offset(cx - trackW / 2, minOf(zero, vy)), Size(trackW, kotlin.math.abs(zero - vy)), CornerRadius(trackW / 2),
                )
                drawLine(cs.onSurfaceVariant, Offset(cx - trackW * 1.6f, zero), Offset(cx + trackW * 1.6f, zero), 2.dp.toPx())
                drawCircle(accent, thumbR, Offset(cx, vy))
                drawCircle(cs.surface, thumbR * 0.45f, Offset(cx, vy))
            }
        }
        if (showStepButtons) StepButton("−", enabled) { emit(current - step) }
        Spacer(Modifier.height(4.dp))
        Text(label, style = if (narrow) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1)
        if (!narrow) Text(sublabel, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun StepButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface) }
}

@Composable
fun StatusPill(text: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Frequency response of what is actually applied (effective curve + preamp). The 7 control points
 * sit above the centres of the 7 fader columns; spacing between them is logarithmic.
 */
@Composable
fun ResponseCurve(effective: List<Float>, preampDb: Float, active: Boolean, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val line = if (active) cs.primary else cs.onSurfaceVariant
    val fr = EqBands.frequenciesHz
    Canvas(modifier) {
        val range = 18f
        val col = size.width / fr.size
        fun x(f: Float): Float {
            if (f <= fr.first()) return col * 0.5f * (ln(f / 20f) / ln(fr.first() / 20f)).coerceIn(0f, 1f)
            if (f >= fr.last()) return col * (fr.size - 0.5f) + col * 0.5f * (ln(f / fr.last()) / ln(20000f / fr.last())).coerceIn(0f, 1f)
            val i = (0 until fr.size - 1).first { f <= fr[it + 1] }
            return col * (i + 0.5f) + col * (ln(f / fr[i]) / ln(fr[i + 1] / fr[i]))
        }
        fun y(db: Float) = size.height / 2 - (db / range) * (size.height / 2)
        for (db in listOf(-12f, -6f, 0f, 6f, 12f)) {
            drawLine(if (db == 0f) cs.outline else cs.outlineVariant, Offset(0f, y(db)), Offset(size.width, y(db)), if (db == 0f) 1.5.dp.toPx() else 1.dp.toPx())
        }
        for (f in fr) drawLine(cs.outlineVariant, Offset(x(f), 0f), Offset(x(f), size.height), 1.dp.toPx())
        val path = Path(); val fill = Path()
        val n = 160
        for (i in 0..n) {
            val f = 20f * 1000f.pow(i / n.toFloat())
            val px = x(f); val py = y((CurveMath.interpolate(f, effective) + preampDb).coerceIn(-range, range))
            if (i == 0) { path.moveTo(px, py); fill.moveTo(px, y(0f)); fill.lineTo(px, py) } else { path.lineTo(px, py); fill.lineTo(px, py) }
        }
        fill.lineTo(size.width, y(0f)); fill.close()
        drawPath(fill, Brush.verticalGradient(listOf(line.copy(alpha = 0.28f), line.copy(alpha = 0.02f))))
        drawPath(path, line, style = Stroke(3.dp.toPx()))
        fr.forEachIndexed { i, f ->
            drawCircle(line, 5.dp.toPx(), Offset(x(f), y((effective[i] + preampDb).coerceIn(-range, range))))
        }
    }
}

/** Tiny 7-bar sketch of a preset's curve for lists. */
@Composable
fun MiniCurve(gains: List<Float>, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme.primary
    val bg = MaterialTheme.colorScheme.surfaceVariant
    Canvas(modifier) {
        val w = size.width / (gains.size * 1.6f)
        gains.forEachIndexed { i, g ->
            val cx = (i + 0.5f) * size.width / gains.size
            val mid = size.height / 2
            drawLine(bg, Offset(cx, 2f), Offset(cx, size.height - 2f), w)
            val h = (g / 12f) * (size.height / 2 - 2f)
            drawLine(c, Offset(cx, mid), Offset(cx, mid - h - if (g == 0f) 1f else 0f), w)
        }
    }
}

@Composable
fun SectionCard(title: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 0.dp) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                trailing?.invoke()
            }
            content()
        }
    }
}

@Composable
fun SupportBadge(supported: Boolean?, text: String) {
    val (color, mark) = when (supported) {
        true -> com.rdm.eq.ui.theme.RdmColors.success to "✓"
        false -> com.rdm.eq.ui.theme.RdmColors.danger to "✕"
        null -> com.rdm.eq.ui.theme.RdmColors.warning to "?"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(mark, color = color, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, modifier = Modifier.width(22.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun KeyValue(key: String, value: String, valueColor: Color = Color.Unspecified) {
    Row(Modifier.fillMaxWidth()) {
        Text(key, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.42f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = valueColor, modifier = Modifier.weight(0.58f))
    }
}

@Composable
fun NameDialog(title: String, initial: String, confirmLabel: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial) }
    val error = PresetNames.validate(name)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name, onValueChange = { name = it }, singleLine = true,
                label = { Text("Preset name") },
                isError = error != null && name.isNotEmpty(),
                supportingText = { if (error != null && name.isNotEmpty()) Text(error) },
            )
        },
        confirmButton = { TextButton(enabled = error == null, onClick = { onConfirm(name.trim()) }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun ConfirmDialog(title: String, text: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text, textAlign = TextAlign.Start) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
