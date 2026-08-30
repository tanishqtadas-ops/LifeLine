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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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

// ── Palette used by the CPR monitor ──────────────────────────────────────────

private val ColorGoodGreen  = Color(0xFF2ECC71)  // GOOD RHYTHM
private val ColorWarnAmber  = Color(0xFFF39C12)  // TOO SLOW
private val ColorDangerRed  = Color(0xFFE74C3C)  // TOO FAST
private val ColorIdleGrey   = Color(0xFF7F8C8D)  // IDLE / waiting
private val ColorOnBadge    = Color(0xFFFFFFFF)  // Text on coloured badge
private val ColorCardBg     = Color(0xFF1E2A3A)  // Dark teal metric card bg
private val ColorCardText   = Color(0xFFB2BEC3)  // Muted label
private val ColorCardValue  = Color(0xFFECF0F1)  // Bright value text
private val ColorBackground = Color(0xFF0D1B2A)  // Deep navy screen background
private val ColorSurface    = Color(0xFF16283B)  // Slightly lighter surface

// ── Screen ────────────────────────────────────────────────────────────────────

@Composable
fun CprMonitorScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    // ── State ─────────────────────────────────────────────────────────────────
    var isSessionActive   by remember { mutableStateOf(false) }
    var sensorAvailable   by remember { mutableStateOf(true) }
    var compressionCount  by remember { mutableIntStateOf(0) }
    var bpm               by remember { mutableStateOf(0f) }
    var status            by remember { mutableStateOf(CprStatus.IDLE) }
    var sessionStartMs    by remember { mutableLongStateOf(0L) }
    var elapsedSeconds    by remember { mutableLongStateOf(0L) }

    // ── Signal processor (stable across recompositions) ───────────────────────
    val processor = remember { CprProcessor() }

    // ── Sensor wiring — only active during a session ──────────────────────────
    DisposableEffect(isSessionActive) {
        if (!isSessionActive) return@DisposableEffect onDispose { /* nothing to clean up */ }

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
                // Push processor outputs to Compose state (runs on sensor thread,
                // but Compose state writes are thread-safe via snapshot system).
                compressionCount = processor.compressionCount
                bpm              = processor.bpm
                status           = processor.status
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        sensorManager.registerListener(
            listener,
            accelerometer,
            SensorManager.SENSOR_DELAY_GAME   // ~50 Hz — enough to resolve 120 BPM peaks
        )

        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }

    // ── Session timer — ticks every second while session is active ────────────
    // delay() runs first so the displayed value is always a whole elapsed second,
    // preventing a spurious non-zero reading on the very first recomposition.
    LaunchedEffect(isSessionActive) {
        if (!isSessionActive) return@LaunchedEffect
        while (true) {
            delay(1_000L)
            elapsedSeconds = (System.currentTimeMillis() - sessionStartMs) / 1000L
        }
    }

    // ── Animated status badge colour ──────────────────────────────────────────
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
        CprStatus.IDLE        -> if (isSessionActive) "WAITING…" else "READY"
    }

    // ── Layout ────────────────────────────────────────────────────────────────
    Column(
        modifier = modifier
            .background(ColorBackground)
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {

        // Header
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text       = "❤ LifeLine",
                fontSize   = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color      = Color(0xFFE74C3C)
            )
            Text(
                text      = "CPR Training Monitor",
                fontSize  = 14.sp,
                color     = Color(0xFF8899AA),
                letterSpacing = 1.5.sp
            )
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

        // Metric cards row
        Row(
            modifier            = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricCard(
                label    = "BPM",
                value    = if (bpm > 0f) "%.0f".format(bpm) else "—",
                modifier = Modifier.weight(1f),
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

        // Target range info card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors   = CardDefaults.cardColors(containerColor = ColorSurface),
            shape    = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier              = Modifier.padding(16.dp),
                horizontalAlignment   = Alignment.CenterHorizontally
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

        Spacer(Modifier.weight(1f))

        // Sensor not available warning
        if (!sensorAvailable) {
            Text(
                text      = "⚠ Accelerometer not found on this device.",
                color     = ColorDangerRed,
                fontSize  = 14.sp,
                textAlign = TextAlign.Center
            )
        }

        // Start / Stop button
        Button(
            onClick = {
                if (isSessionActive) {
                    // Stop session — DisposableEffect will unregister the listener.
                    isSessionActive = false
                } else {
                    // Reset processor and all UI state atomically before enabling the
                    // session. The sensor thread is not running yet at this point
                    // (DisposableEffect fires after this lambda returns), so there is
                    // no race between the reset writes and incoming sensor data.
                    processor.reset()
                    compressionCount = 0
                    bpm              = 0f
                    status           = CprStatus.IDLE
                    elapsedSeconds   = 0L
                    sessionStartMs   = System.currentTimeMillis()
                    isSessionActive  = true   // triggers DisposableEffect → registers listener
                }
            },
            enabled = sensorAvailable,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isSessionActive) ColorDangerRed else ColorGoodGreen
            ),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(
                text       = if (isSessionActive) "■  Stop Session" else "▶  Start CPR Training",
                fontSize   = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color      = Color.White
            )
        }

        // Disclaimer
        Text(
            text      = "For CPR training use only · Not a medical device",
            fontSize  = 11.sp,
            color     = Color(0xFF506070),
            textAlign = TextAlign.Center
        )
    }
}

// ── Reusable composables ──────────────────────────────────────────────────────

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
            modifier              = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalAlignment   = Alignment.CenterHorizontally
        ) {
            Text(
                text      = value,
                fontSize  = 34.sp,
                fontWeight = FontWeight.ExtraBold,
                color     = if (highlight) ColorGoodGreen else ColorCardValue
            )
            Spacer(Modifier.size(2.dp))
            Text(
                text      = label,
                fontSize  = 11.sp,
                color     = ColorCardText,
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
            text      = label,
            fontSize  = 12.sp,
            color     = color,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// ── Utility ───────────────────────────────────────────────────────────────────

/** Format elapsed seconds as  m:ss  (e.g. "2:07"). */
private fun formatDuration(totalSeconds: Long): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "%d:%02d".format(m, s)
}