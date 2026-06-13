package com.casio.lockscreen

import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// ── Palette ───────────────────────────────────────────────────────────────────
private val LCD_BG       = Color(0xFFD2DEC8)
private val LCD_SEG_LIT  = Color(0xFF1A2010)
private val BEZEL_BLACK  = Color(0xFF101010)
private val BEZEL_INNER  = Color(0xFF1C1C1C)
private val ACCENT_BLUE  = Color(0xFF1A3FA0)
private val LABEL_YELLOW = Color(0xFFF5C400)
private val LABEL_WHITE  = Color(0xFFE8E8E8)
private val LABEL_RED    = Color(0xFFCC1111)
private val LCD_BORDER   = Color(0xFF444444)

// ── Entry point ───────────────────────────────────────────────────────────────
@Composable
fun CasioFaceView(engine: CasioEngine) {
    val state   by engine.state.collectAsState()
    val context = LocalContext.current

    val dsegBold = remember {
        Typeface.createFromAsset(context.assets, "fonts/DSEG7Classic-Bold.ttf")
    }
    val dsegRegular = remember {
        Typeface.createFromAsset(context.assets, "fonts/DSEG7Classic-Regular.ttf")
    }

    var burnX by remember { mutableFloatStateOf(0f) }
    var burnY by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(state.aodMode) {
        if (state.aodMode) {
            var angle = 0.0
            while (true) {
                delay(60_000L)
                angle += Math.PI / 4
                burnX = (cos(angle) * 10f).toFloat()
                burnY = (sin(angle) * 10f).toFloat()
            }
        } else { burnX = 0f; burnY = 0f }
    }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(state.aodMode) {
                if (!state.aodMode) detectTapGestures { offset ->
                    if (offset.y > size.height * 0.55f) when {
                        offset.x < size.width * 0.33f -> engine.onLeftTap()
                        offset.x > size.width * 0.67f -> engine.onRightTap()
                        else                           -> engine.onMiddleTap()
                    }
                }
            }
    ) {
        drawRect(Color.Black)
        if (state.aodMode) drawAod(state, burnX, burnY, dsegBold)
        else               drawWatch(state, burnX, burnY, dsegBold, dsegRegular)
    }
}

// ── Watch body ────────────────────────────────────────────────────────────────
private fun DrawScope.drawWatch(
    state: CasioState, ox: Float, oy: Float,
    dsegBold: Typeface, dsegRegular: Typeface
) {
    val sw     = min(size.width, size.height)
    val watchW = sw * 0.78f
    val watchH = watchW * 1.18f
    val wx     = (size.width  - watchW) / 2f + ox
    val wy     = (size.height - watchH) / 2f + oy

    // Outer bezel
    drawRoundRect(BEZEL_BLACK, Offset(wx, wy), Size(watchW, watchH), CornerRadius(watchW * 0.10f))
    // Blue accent ring
    val bp = watchW * 0.025f
    drawRoundRect(
        color        = ACCENT_BLUE,
        topLeft      = Offset(wx + bp, wy + bp),
        size         = Size(watchW - 2*bp, watchH - 2*bp),
        cornerRadius = CornerRadius(watchW * 0.08f),
        style        = Stroke(width = watchW * 0.012f)
    )
    // Inner body
    val ip = bp + watchW * 0.018f
    drawRoundRect(BEZEL_INNER, Offset(wx+ip, wy+ip), Size(watchW-2*ip, watchH-2*ip), CornerRadius(watchW * 0.07f))

    // Brand labels
    drawTextNative("CASIO",              wx + watchW*0.08f, wy + watchH*0.055f + watchW*0.045f, watchW*0.058f, LABEL_WHITE,  bold=true)
    drawTextNative("F-91W",              wx + watchW*0.55f, wy + watchH*0.055f + watchW*0.045f, watchW*0.055f, LABEL_YELLOW, bold=true)
    drawTextNative("ALARM CHRONOGRAPH",  wx + watchW*0.15f, wy + watchH*0.055f + watchW*0.085f, watchW*0.030f, LABEL_YELLOW, bold=false)

    // Side buttons (visual)
    val btnW = watchW * 0.04f; val btnH = watchH * 0.09f
    drawRoundRect(BEZEL_BLACK, Offset(wx - btnW + 2f, wy + watchH*0.22f), Size(btnW, btnH), CornerRadius(2f))
    drawRoundRect(BEZEL_BLACK, Offset(wx - btnW + 2f, wy + watchH*0.55f), Size(btnW, btnH), CornerRadius(2f))
    drawRoundRect(BEZEL_BLACK, Offset(wx + watchW - 2f, wy + watchH*0.22f), Size(btnW, btnH), CornerRadius(2f))

    // LCD panel
    val lcdPad = watchW * 0.065f
    val lcdTop = wy + watchH * 0.14f
    val lcdX   = wx + lcdPad
    val lcdW   = watchW - 2*lcdPad
    val lcdH   = watchH * 0.58f
    drawRoundRect(LCD_BORDER, Offset(lcdX-3f, lcdTop-3f), Size(lcdW+6f, lcdH+6f), CornerRadius(6f))
    drawRoundRect(LCD_BG,     Offset(lcdX,    lcdTop),    Size(lcdW,    lcdH),    CornerRadius(4f))

    drawLcdContent(state, lcdX, lcdTop, lcdW, lcdH, dsegBold, dsegRegular)

    // Bottom mode-strip
    val stripY = lcdTop + lcdH + watchH*0.012f
    val stripH = watchH * 0.055f
    drawRoundRect(ACCENT_BLUE, Offset(lcdX, stripY), Size(lcdW, stripH), CornerRadius(3f))
    drawTextNative("◄ MODE",               lcdX + lcdW*0.03f, stripY + stripH*0.72f, lcdW*0.065f, LABEL_WHITE, bold=false)
    drawTextNative("ALARM ON·OFF/24HR ►",  lcdX + lcdW*0.38f, stripY + stripH*0.72f, lcdW*0.055f, LABEL_WHITE, bold=false)

    // WATER WR RESIST
    val wrY = stripY + stripH + watchH*0.012f
    drawTextNative("WATER",  lcdX + lcdW*0.04f,  wrY + watchH*0.03f, lcdW*0.065f, LABEL_WHITE, bold=true)
    drawRoundRect(BEZEL_BLACK, Offset(lcdX + lcdW*0.37f, wrY - watchH*0.005f), Size(lcdW*0.26f, watchH*0.042f), CornerRadius(4f))
    drawTextNative("WR",     lcdX + lcdW*0.43f,  wrY + watchH*0.03f, lcdW*0.080f, LABEL_RED,   bold=true)
    drawTextNative("RESIST", lcdX + lcdW*0.66f,  wrY + watchH*0.03f, lcdW*0.065f, LABEL_WHITE, bold=true)
}

// ── LCD content ───────────────────────────────────────────────────────────────
private fun DrawScope.drawLcdContent(
    state: CasioState, lx: Float, ly: Float, lw: Float, lh: Float,
    dsegBold: Typeface, dsegRegular: Typeface
) {
    // Info row
    val infoH = lh * 0.20f
    drawInfoRow(state, lx, ly + lh*0.04f, lw, infoH)

    // Blue separator
    val sepY = ly + infoH + lh*0.06f
    drawRect(ACCENT_BLUE, Offset(lx + lw*0.01f, sepY), Size(lw*0.98f, lh*0.016f))

    // Main display — takes the space between separator and bottom bar
    val botBarY = ly + lh*0.91f
    val mainTop = sepY + lh*0.016f + lh*0.02f
    val mainH   = botBarY - mainTop - lh*0.02f
    drawMainDisplay(state, lx, mainTop, lw, mainH, dsegBold, dsegRegular)

    // Bottom LCD bar
    drawRect(ACCENT_BLUE, Offset(lx + lw*0.01f, botBarY), Size(lw*0.98f, lh*0.016f))
}

// ── Info row ──────────────────────────────────────────────────────────────────
private fun DrawScope.drawInfoRow(state: CasioState, lx: Float, ly: Float, lw: Float, lh: Float) {
    val ts   = lh * 0.72f
    val base = ly + lh * 0.82f

    drawIntoCanvas { c ->
        val p = Paint()
        p.isAntiAlias = true
        p.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)

        // Signal bars
        p.color = LCD_SEG_LIT.toArgb(); p.textSize = ts * 0.50f
        c.nativeCanvas.drawText("▐▐▐", lx + lw*0.02f, base - lh*0.15f, p)

        // Alarm indicator
        p.color = (if (state.alarmEnabled) LCD_SEG_LIT else LCD_BG.copy(alpha=0f)).toArgb()
        p.textSize = ts * 0.55f
        c.nativeCanvas.drawText("◆", lx + lw*0.20f, base - lh*0.08f, p)

        // AM/PM (12h only)
        if (!state.is24Hour) {
            p.color = LCD_SEG_LIT.toArgb(); p.textSize = ts * 0.45f
            c.nativeCanvas.drawText(if (state.timeHour < 12) "AM" else "PM", lx + lw*0.02f, base + lh*0.08f, p)
        }

        // Day
        p.color = LCD_SEG_LIT.toArgb(); p.textSize = ts
        c.nativeCanvas.drawText(state.dayOfWeek, lx + lw*0.44f, base, p)

        // Date
        val dateStr = "%2d".format(state.dateDay)
        c.nativeCanvas.drawText(dateStr, lx + lw*0.72f, base, p)
    }
}

// ── Main display (DSEG font) ──────────────────────────────────────────────────
private fun DrawScope.drawMainDisplay(
    state: CasioState, lx: Float, ly: Float, lw: Float, lh: Float,
    dsegBold: Typeface, dsegRegular: Typeface
) {
    val hour = if (state.is24Hour) state.timeHour
               else { val h = state.timeHour % 12; if (h == 0) 12 else h }

    drawIntoCanvas { c ->
        val bigPaint = Paint().apply {
            isAntiAlias = true
            typeface    = dsegBold
            color       = LCD_SEG_LIT.toArgb()
        }
        val smPaint = Paint().apply {
            isAntiAlias = true
            typeface    = dsegRegular
            color       = LCD_SEG_LIT.toArgb()
        }

        val maxW = lw * 0.92f   // usable horizontal space

        when (state.mode) {
            Mode.TIME -> {
                val mainStr = "%02d:%02d".format(hour, state.timeMinute)
                val secStr  = "%02d".format(state.timeSecond)
                val gap     = lw * 0.022f

                // Start at ideal height, then scale down to fit width
                var fontSize = lh * 0.82f
                bigPaint.textSize = fontSize
                smPaint.textSize  = fontSize * 0.52f
                var totalW = bigPaint.measureText(mainStr) + gap + smPaint.measureText(secStr)
                if (totalW > maxW) {
                    fontSize *= maxW / totalW
                    bigPaint.textSize = fontSize
                    smPaint.textSize  = fontSize * 0.52f
                    totalW = bigPaint.measureText(mainStr) + gap + smPaint.measureText(secStr)
                }

                val startX  = lx + (lw - totalW) / 2f
                val bigH    = textDrawHeight(bigPaint)
                val bigBase = ly + (lh + bigH) / 2f

                c.nativeCanvas.drawText(mainStr, startX, bigBase, bigPaint)
                c.nativeCanvas.drawText(secStr, startX + bigPaint.measureText(mainStr) + gap, bigBase, smPaint)
            }

            Mode.STOPWATCH -> {
                val elapsed = state.stopwatchElapsedMs
                val mins    = ((elapsed / 1000L) / 60L).coerceAtMost(99L)
                val secs    = (elapsed / 1000L) % 60L
                val cs      = (elapsed % 1000L) / 10L
                val mainStr = "%02d:%02d".format(mins, secs)
                val csStr   = "%02d".format(cs)
                val gap     = lw * 0.018f

                var fontSize = lh * 0.82f
                bigPaint.textSize = fontSize
                smPaint.textSize  = fontSize * 0.52f
                var totalW = bigPaint.measureText(mainStr) + gap + smPaint.measureText(csStr)
                if (totalW > maxW) {
                    fontSize *= maxW / totalW
                    bigPaint.textSize = fontSize
                    smPaint.textSize  = fontSize * 0.52f
                    totalW = bigPaint.measureText(mainStr) + gap + smPaint.measureText(csStr)
                }

                val startX  = lx + (lw - totalW) / 2f
                val bigH    = textDrawHeight(bigPaint)
                val bigBase = ly + (lh + bigH) / 2f
                c.nativeCanvas.drawText(mainStr, startX, bigBase, bigPaint)
                c.nativeCanvas.drawText(csStr, startX + bigPaint.measureText(mainStr) + gap, bigBase, smPaint)

                val lp = Paint().also { it.isAntiAlias = true; it.typeface = Typeface.MONOSPACE
                    it.color = LCD_SEG_LIT.toArgb(); it.textSize = lh * 0.13f }
                c.nativeCanvas.drawText(if (state.stopwatchRunning) "RUN" else if (elapsed > 0) "STOP" else "STPW",
                    lx + lw*0.04f, ly + lh*0.14f, lp)
            }

            Mode.ALARM_SET -> {
                val alarmStr = "%02d:%02d".format(state.alarmHour, state.alarmMinute)
                var fontSize = lh * 0.82f
                bigPaint.textSize = fontSize
                val w = bigPaint.measureText(alarmStr)
                if (w > maxW) { fontSize *= maxW / w; bigPaint.textSize = fontSize }

                val bigH    = textDrawHeight(bigPaint)
                val startX  = lx + (lw - bigPaint.measureText(alarmStr)) / 2f
                val bigBase = ly + (lh + bigH) / 2f
                c.nativeCanvas.drawText(alarmStr, startX, bigBase, bigPaint)

                val lp = Paint().also { it.isAntiAlias = true; it.typeface = Typeface.MONOSPACE
                    it.color = LCD_SEG_LIT.toArgb(); it.textSize = lh * 0.13f }
                c.nativeCanvas.drawText(if (state.alarmEnabled) "AL ON" else "AL OFF",
                    lx + lw*0.60f, ly + lh*0.18f, lp)
            }
        }
    }
}

// ── AOD: minimal HH:MM, DSEG on black ────────────────────────────────────────
private fun DrawScope.drawAod(state: CasioState, ox: Float, oy: Float, dsegBold: Typeface) {
    val hour = if (state.is24Hour) state.timeHour
               else { val h = state.timeHour % 12; if (h == 0) 12 else h }
    val timeStr = "%02d:%02d".format(hour, state.timeMinute)

    drawIntoCanvas { c ->
        val p = Paint().apply {
            isAntiAlias = true
            typeface    = dsegBold
            color       = LABEL_WHITE.toArgb()
            textSize    = size.width * 0.22f
        }
        val w    = p.measureText(timeStr)
        val h    = textDrawHeight(p)
        val x    = (size.width  - w) / 2f + ox
        val base = (size.height + h) / 2f + oy
        c.nativeCanvas.drawText(timeStr, x, base, p)
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

/** Returns the cap-height of the current paint (top-of-glyph to baseline). */
private fun textDrawHeight(p: Paint): Float {
    val fm = p.fontMetrics
    return -fm.ascent   // ascent is negative → negate for positive height
}

private fun DrawScope.drawTextNative(
    text: String, x: Float, y: Float, textSize: Float,
    color: Color, bold: Boolean
) {
    val ts  = textSize
    val col = color.toArgb()
    val tf  = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    drawIntoCanvas { c ->
        val p = Paint()
        p.color = col; p.textSize = ts; p.typeface = tf; p.isAntiAlias = true
        c.nativeCanvas.drawText(text, x, y, p)
    }
}
