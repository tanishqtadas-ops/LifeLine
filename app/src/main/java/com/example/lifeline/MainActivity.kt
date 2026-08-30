package com.example.lifeline

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lifeline.ui.theme.LifeLineTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LifeLineTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    CprMonitorScreen(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }
    }
}

// ── Colour palette ────────────────────────────────────────────────────────────

private val ColorGoodGreen  = Color(0xFF2ECC71)  // GOOD RHYTHM
private val ColorWarnAmber  = Color(0xFFF39C12)  // TOO SLOW
private val ColorDangerRed  = Color(0xFFE74C3C)  // TOO FAST / Stop button
private val ColorIdleGrey   = Color(0xFF7F8C8D)  // IDLE / waiting
private val ColorOnBadge    = Color(0xFFFFFFFF)  // Text on coloured badge
private val ColorCardBg     = Color(0xFF1E2A3A)  // Metric card background
private val ColorCardText   = Color(0xFFB2BEC3)  // Muted label inside card
private val ColorCardValue  = Color(0xFFECF0F1)  // Bright value inside card
private val ColorBackground = Color(0xFF0D1B2A)  // Deep navy screen background
private val ColorSurface    = Color(0xFF16283B)  // Slightly lighter surface

// ── Screen state ──────────────────────────────────────────────────────────────

/**
 * Represents the three distinct states the CPR monitor screen can be in.
 *
 * NOT_STARTED — app just opened; no session has ever run.
 * Active      — sensor is live; compressions are being counted.
 * Complete    — session was stopped; summary is displayed.
 */
private sealed class ScreenState {
    object NotStarted : ScreenState()
    object Active : ScreenState()
    data class Complete(val summary: CprSessionSummary) : ScreenState()
}

// ── Root composable ───────────────────────────────────────────────────────────

@Composable
fun CprMonitorScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    // ── Persistent objects ────────────────────────────────────────────────────
    val processor = remember { CprProcessor() }

    // ── Screen state ──────────────────────────────────────────────────────────
    var screenState   by remember { mutableStateOf<ScreenState>(ScreenState.NotStarted) }
    var sensorAvailable by remember { mutableStateOf(true) }

    // ── Live session values (written from sensor thread; valid only when Active) ─
    var compressionCount by remember { mutableStateOf(0) }
    var bpm              by remember { mutableStateOf(0f) }
    var status           by remember { mutableStateOf(CprStatus.IDLE) }
    var sessionStartMs   by remember { mutableLongStateOf(0L) }
    var elapsedSeconds   by remember { mutableLongStateOf(0L) }

    val isActive = screenState is ScreenState.Active

    // ── Sensor wiring — only live when screen is Active ───────────────────────
    // DisposableEffect re-runs when screenState changes. The local `listener`
    // reference is captured in onDispose, so exactly one listener is ever live.
    DisposableEffect(screenState) {
        if (!isActive) return@DisposableEffect onDispose { /* nothing registered */ }

        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        if (accelerometer == null) {
            sensorAvailable = false
            return@DisposableEffect onDispose { }
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                event ?: return
                processor.processSample(
                    x = event.values[0],
                    y = event.values[1],
                    z = event.values[2]
                )
                // Compose snapshot writes from the sensor thread are safe.
                compressionCount = processor.compressionCount
                bpm              = processor.bpm
                status           = processor.status
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        sensorManager.registerListener(
            listener,
            accelerometer,
            SensorManager.SENSOR_DELAY_GAME  // ~50 Hz — sufficient to resolve 120 BPM peaks
        )

        onDispose {
            // Called when screen leaves Active (Stop or screen leaves composition).
            // Guarantees zero sensor events reach the processor after this point.
            sensorManager.unregisterListener(listener)
        }
    }

    // ── Session timer ─────────────────────────────────────────────────────────
    // delay-first pattern: elapsed always reads a whole second, never a spurious
    // non-zero on the very first recomposition.
    LaunchedEffect(screenState) {
        if (!isActive) return@LaunchedEffect
        while (true) {
            delay(1_000L)
            elapsedSeconds = (System.currentTimeMillis() - sessionStartMs) / 1000L
        }
    }

    // ── Layout ────────────────────────────────────────────────────────────────
    Column(
        modifier = modifier
            .background(ColorBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {

        // ── Header — always visible ───────────────────────────────────────────
        AppHeader()

        // ── Content switches on screen state ─────────────────────────────────
        when (val state = screenState) {

            // ─────────────────────────── NOT STARTED ─────────────────────────
            is ScreenState.NotStarted -> {
                NotStartedPanel(
                    sensorAvailable = sensorAvailable,
                    onStart = {
                        processor.reset()
                        compressionCount = 0
                        bpm              = 0f
                        status           = CprStatus.IDLE
                        elapsedSeconds   = 0L
                        sessionStartMs   = System.currentTimeMillis()
                        screenState      = ScreenState.Active
                    }
                )
            }

            // ─────────────────────────── ACTIVE ──────────────────────────────
            is ScreenState.Active -> {
                ActivePanel(
                    compressionCount = compressionCount,
                    bpm              = bpm,
                    status           = status,
                    elapsedSeconds   = elapsedSeconds,
                    onStop = {
                        // Capture the summary BEFORE flipping state so the
                        // sensor thread cannot write new values into it.
                        val summary = CprSessionSummary(
                            durationSeconds  = elapsedSeconds,
                            compressionCount = compressionCount,
                            finalBpm         = bpm,
                            finalStatus      = status
                        )
                        // Flipping screenState to Complete triggers DisposableEffect
                        // which calls onDispose → unregisterListener.
                        screenState = ScreenState.Complete(summary)
                    }
                )
            }

            // ─────────────────────────── COMPLETE ────────────────────────────
            is ScreenState.Complete -> {
                CompletePanel(
                    summary = state.summary,
                    onNewSession = {
                        processor.reset()
                        compressionCount = 0
                        bpm              = 0f
                        status           = CprStatus.IDLE
                        elapsedSeconds   = 0L
                        sessionStartMs   = System.currentTimeMillis()
                        screenState      = ScreenState.Active
                    },
                    onBackToStart = {
                        screenState = ScreenState.NotStarted
                    }
                )
            }
        }

        Spacer(Modifier.weight(1f))

        // ── Sensor error ──────────────────────────────────────────────────────
        if (!sensorAvailable) {
            Text(
                text      = "⚠ Accelerometer not found on this device.",
                color     = ColorDangerRed,
                fontSize  = 14.sp,
                textAlign = TextAlign.Center
            )
        }

        // ── Disclaimer — always visible ───────────────────────────────────────
        Text(
            text      = "For CPR training use only · Not a medical device",
            fontSize  = 11.sp,
            color     = Color(0xFF506070),
            textAlign = TextAlign.Center
        )
    }
}

// ── Panel composables ─────────────────────────────────────────────────────────

@Composable
private fun AppHeader() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text       = "❤ LifeLine",
            fontSize   = 32.sp,
            fontWeight = FontWeight.ExtraBold,
            color      = ColorDangerRed
        )
        Text(
            text          = "CPR Training Monitor",
            fontSize      = 14.sp,
            color         = Color(0xFF8899AA),
            letterSpacing = 1.5.sp
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// NOT STARTED panel
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun NotStartedPanel(
    sensorAvailable: Boolean,
    onStart: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = ColorSurface),
        shape    = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier            = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text       = "Ready to Train",
                fontSize   = 22.sp,
                fontWeight = FontWeight.Bold,
                color      = ColorCardValue
            )
            Text(
                text      = "Place your phone on the patient's chest or manikin and tap Start when ready.",
                fontSize  = 13.sp,
                color     = ColorCardText,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text      = "Target: 100–120 compressions/min",
                fontSize  = 13.sp,
                color     = ColorGoodGreen,
                fontWeight = FontWeight.Medium
            )
        }
    }

    // Target range legend
    TargetRangeCard()

    Spacer(Modifier.height(8.dp))

    Button(
        onClick  = onStart,
        enabled  = sensorAvailable,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        colors = ButtonDefaults.buttonColors(containerColor = ColorGoodGreen),
        shape  = RoundedCornerShape(14.dp)
    ) {
        Text(
            text       = "▶  Start CPR Training",
            fontSize   = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color      = Color.White
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ACTIVE panel
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ActivePanel(
    compressionCount: Int,
    bpm: Float,
    status: CprStatus,
    elapsedSeconds: Long,
    onStop: () -> Unit
) {
    // Animated badge colour
    val badgeColor by animateColorAsState(
        targetValue = when (status) {
            CprStatus.GOOD_RHYTHM -> ColorGoodGreen
            CprStatus.TOO_SLOW    -> ColorWarnAmber
            CprStatus.TOO_FAST    -> ColorDangerRed
            CprStatus.IDLE        -> ColorIdleGrey
        },
        animationSpec = tween(durationMillis = 400),
        label         = "badge_color"
    )

    val statusLabel = when (status) {
        CprStatus.GOOD_RHYTHM -> "GOOD RHYTHM ✓"
        CprStatus.TOO_SLOW    -> "TOO SLOW ↓"
        CprStatus.TOO_FAST    -> "TOO FAST ↑"
        CprStatus.IDLE        -> "WAITING…"
    }

    // Status badge
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(badgeColor)
            .padding(vertical = 22.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text       = statusLabel,
            fontSize   = 26.sp,
            fontWeight = FontWeight.Bold,
            color      = ColorOnBadge,
            textAlign  = TextAlign.Center
        )
    }

    // Metric cards — BPM · COUNT · TIME
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MetricCard(
            label     = "BPM",
            value     = if (bpm > 0f) "%.0f".format(bpm) else "—",
            modifier  = Modifier.weight(1f),
            highlight = status == CprStatus.GOOD_RHYTHM
        )
        MetricCard(
            label    = "COUNT",
            value    = compressionCount.toString(),
            modifier = Modifier.weight(1f)
        )
        MetricCard(
            label    = "TIME",
            value    = formatDuration(elapsedSeconds),
            modifier = Modifier.weight(1f)
        )
    }

    // Target range legend
    TargetRangeCard()

    // Stop button
    Button(
        onClick  = onStop,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        colors = ButtonDefaults.buttonColors(containerColor = ColorDangerRed),
        shape  = RoundedCornerShape(14.dp)
    ) {
        Text(
            text       = "■  Stop Session",
            fontSize   = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color      = Color.White
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SESSION COMPLETE panel
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CompletePanel(
    summary: CprSessionSummary,
    onNewSession: () -> Unit,
    onBackToStart: () -> Unit
) {
    // Summary header badge
    val summaryBadgeColor = when (summary.finalStatus) {
        CprStatus.GOOD_RHYTHM -> ColorGoodGreen
        CprStatus.TOO_SLOW    -> ColorWarnAmber
        CprStatus.TOO_FAST    -> ColorDangerRed
        CprStatus.IDLE        -> ColorIdleGrey
    }
    val summaryStatusLabel = when (summary.finalStatus) {
        CprStatus.GOOD_RHYTHM -> "GOOD RHYTHM ✓"
        CprStatus.TOO_SLOW    -> "TOO SLOW"
        CprStatus.TOO_FAST    -> "TOO FAST"
        CprStatus.IDLE        -> "NOT ENOUGH DATA"
    }

    // Session complete label
    Text(
        text       = "Session Complete",
        fontSize   = 20.sp,
        fontWeight = FontWeight.Bold,
        color      = ColorCardValue
    )

    // Final status badge
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(summaryBadgeColor)
            .padding(vertical = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text       = summaryStatusLabel,
            fontSize   = 24.sp,
            fontWeight = FontWeight.Bold,
            color      = ColorOnBadge,
            textAlign  = TextAlign.Center
        )
    }

    // Summary detail card
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = ColorSurface),
        shape    = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier            = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SummaryRow(
                label = "Duration",
                value = formatDuration(summary.durationSeconds)
            )
            SummaryDivider()
            SummaryRow(
                label = "Compressions",
                value = summary.compressionCount.toString()
            )
            SummaryDivider()
            SummaryRow(
                label = "Final BPM",
                value = if (summary.finalBpm > 0f) "%.0f".format(summary.finalBpm) else "—"
            )
            SummaryDivider()
            SummaryRow(
                label = "Target Range",
                value = "100–120 /min"
            )
        }
    }

    // Action buttons
    Button(
        onClick  = onNewSession,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        colors = ButtonDefaults.buttonColors(containerColor = ColorGoodGreen),
        shape  = RoundedCornerShape(14.dp)
    ) {
        Text(
            text       = "▶  New Session",
            fontSize   = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color      = Color.White
        )
    }

    OutlinedButton(
        onClick  = onBackToStart,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        shape  = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorIdleGrey)
    ) {
        Text(
            text      = "Back to Start",
            fontSize  = 15.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

// ── Shared reusable composables ───────────────────────────────────────────────

@Composable
private fun TargetRangeCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = ColorSurface),
        shape    = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier            = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text       = "Target: 100–120 compressions/min",
                fontSize   = 13.sp,
                color      = Color(0xFF8899AA),
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TargetTag(label = "< 100", color = ColorWarnAmber)
                TargetTag(label = "100–120", color = ColorGoodGreen)
                TargetTag(label = "> 120", color = ColorDangerRed)
            }
        }
    }
}

@Composable
private fun MetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    highlight: Boolean = false
) {
    Card(
        modifier = modifier,
        colors   = CardDefaults.cardColors(containerColor = ColorCardBg),
        shape    = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier            = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text       = value,
                fontSize   = 34.sp,
                fontWeight = FontWeight.ExtraBold,
                color      = if (highlight) ColorGoodGreen else ColorCardValue
            )
            Spacer(Modifier.size(2.dp))
            Text(
                text          = label,
                fontSize      = 11.sp,
                color         = ColorCardText,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
private fun TargetTag(label: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.18f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text       = label,
            fontSize   = 12.sp,
            color      = color,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text(
            text      = label,
            fontSize  = 14.sp,
            color     = ColorCardText,
            fontWeight = FontWeight.Medium
        )
        Text(
            text       = value,
            fontSize   = 16.sp,
            color      = ColorCardValue,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SummaryDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color(0xFF253545))
    )
}

// ── Utility ───────────────────────────────────────────────────────────────────

/** Format elapsed seconds as  m:ss  (e.g. "2:07"). */
private fun formatDuration(totalSeconds: Long): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "%d:%02d".format(m, s)
}