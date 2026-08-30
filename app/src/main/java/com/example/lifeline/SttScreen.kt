package com.example.lifeline

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

// ── Palette used by the Voice Assistant Screen ──────────────────────────────

private val ColorGoodGreen  = Color(0xFF2ECC71)  // Ready / Listening OK
private val ColorWarnAmber  = Color(0xFFF39C12)  // Loading / Initializing
private val ColorDangerRed  = Color(0xFFE74C3C)  // Stop / Error
private val ColorIdleGrey   = Color(0xFF7F8C8D)  // Idle
private val ColorAccentBlue = Color(0xFF3498DB)  // Listening / Active
private val ColorCardBg     = Color(0xFF1E2A3A)  // Dark teal card bg
private val ColorCardText   = Color(0xFFB2BEC3)  // Muted label
private val ColorCardValue  = Color(0xFFECF0F1)  // Bright value text
private val ColorBackground = Color(0xFF0D1B2A)  // Deep navy screen background

@Composable
fun SttScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    // ── UI States ─────────────────────────────────────────────────────────────
    var isListening by remember { mutableStateOf(false) }
    var isModelLoaded by remember { mutableStateOf(false) }
    var isLoadingModel by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("Initializing speech recognition…") }
    var isError by remember { mutableStateOf(false) }
    var transcript by remember { mutableStateOf("") }
    var partialResult by remember { mutableStateOf("") }

    val scrollState = rememberScrollState()

    // ── STT Engine reference ──────────────────────────────────────────────────
    var sttRecognizer by remember { mutableStateOf<VoskSpeechRecognizer?>(null) }

    val listener = remember {
        object : VoskSpeechRecognizer.SttListener {
            override fun onPartialResult(text: String) {
                partialResult = text
            }

            override fun onResult(text: String) {
                if (text.isNotBlank()) {
                    transcript = if (transcript.isEmpty()) {
                        text
                    } else {
                        "$transcript\n$text"
                    }
                    partialResult = ""
                }
            }

            override fun onModelLoaded() {
                isLoadingModel = false
                isModelLoaded = true
                isError = false
                statusText = "Ready — tap Start to speak"
            }

            override fun onError(message: String) {
                isLoadingModel = false
                isError = true
                statusText = message
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    // ── Permission Launcher ───────────────────────────────────────────────────
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            isLoadingModel = true
            statusText = "Loading offline speech model…"
            sttRecognizer?.initModel()
        } else {
            isError = true
            statusText = "Microphone permission denied"
            Toast.makeText(
                context,
                "Microphone permission is required for speech recognition.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // ── Lifecycle Handling ────────────────────────────────────────────────────
    DisposableEffect(Unit) {
        val recognizer = VoskSpeechRecognizer(context.applicationContext, listener)
        sttRecognizer = recognizer

        // Check permission and initialize model
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            isLoadingModel = true
            statusText = "Loading offline speech model…"
            recognizer.initModel()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }

        onDispose {
            recognizer.destroy()
        }
    }

    // Auto-scroll to bottom when transcript updates
    LaunchedEffect(transcript, partialResult) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    // Animated status badge color
    val badgeColor by animateColorAsState(
        targetValue = when {
            isError -> ColorDangerRed
            isListening -> ColorAccentBlue
            isModelLoaded -> ColorGoodGreen
            else -> ColorWarnAmber
        },
        animationSpec = tween(durationMillis = 400),
        label = "stt_badge_color"
    )

    // ── Layout ────────────────────────────────────────────────────────────────
    Column(
        modifier = modifier
            .background(ColorBackground)
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // Header
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "🎙 Offline Voice Assistant",
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                color = ColorCardValue
            )
            Text(
                text = "Vosk Speech-to-Text · 100% On-Device",
                fontSize = 13.sp,
                color = ColorCardText,
                letterSpacing = 1.sp
            )
        }

        // Status Badge
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(badgeColor)
                .padding(vertical = 14.dp, horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (isLoadingModel) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.size(10.dp))
                }
                Text(
                    text = statusText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Live Transcript Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors(containerColor = ColorCardBg),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Text(
                    text = "TRANSCRIPTION",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColorCardText,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                ) {
                    val displayText = buildString {
                        if (transcript.isNotEmpty()) {
                            append(transcript)
                        }
                        if (partialResult.isNotEmpty()) {
                            if (isNotEmpty()) append("\n")
                            append("… $partialResult")
                        }
                    }

                    if (displayText.isEmpty()) {
                        Text(
                            text = "Recognized speech will appear here.\n\nTap Start and begin speaking.",
                            fontSize = 15.sp,
                            color = ColorCardText.copy(alpha = 0.6f),
                            lineHeight = 22.sp
                        )
                    } else {
                        Text(
                            text = displayText,
                            fontSize = 16.sp,
                            color = ColorCardValue,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 24.sp
                        )
                    }
                }
            }
        }

        // Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Start / Stop Button
            Button(
                onClick = {
                    val recognizer = sttRecognizer ?: return@Button
                    if (isListening) {
                        recognizer.stopListening()
                        isListening = false
                        statusText = "Ready — tap Start to speak"
                    } else {
                        recognizer.startListening()
                        isListening = recognizer.isListening
                        if (isListening) {
                            statusText = "🎙 Listening… speak now"
                        }
                    }
                },
                enabled = isModelLoaded && !isLoadingModel,
                modifier = Modifier
                    .weight(1.5f)
                    .height(54.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isListening) ColorDangerRed else ColorAccentBlue
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = if (isListening) "■  Stop Listening" else "▶  Start Speaking",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }

            // Clear Button
            OutlinedButton(
                onClick = {
                    transcript = ""
                    partialResult = ""
                },
                enabled = transcript.isNotEmpty() || partialResult.isNotEmpty(),
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Clear",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ColorCardValue
                )
            }
        }

        // ── TASK 4B: CONFIDENCE / UNKNOWN EVALUATION (DEBUG ONLY) ──
        var task4Result by remember { mutableStateOf("") }

        Button(
            onClick = {
                val retriever = ProtocolRetriever(context)
                val policy    = ConfidencePolicy()
                val tag       = "Task4B"

                data class TC(
                    val query: String,
                    val expectedDecision: RetrievalDecision,
                    val note: String = ""
                )

                val cases = listOf(
                    // ── OUT-OF-DOMAIN — expect UNKNOWN ───────────────────────────
                    TC("Can you tell whether this is a heart attack?", RetrievalDecision.UNKNOWN,   "out-of-scope"),
                    TC("Should I give this person medicine?",       RetrievalDecision.UNKNOWN,   "out-of-scope"),
                    TC("What disease does this person have?",       RetrievalDecision.UNKNOWN,   "out-of-scope"),
                    TC("Can you measure their blood pressure?",     RetrievalDecision.UNKNOWN,   "out-of-scope"),
                    TC("What medication should I use?",             RetrievalDecision.UNKNOWN,   "out-of-scope"),

                    // ── UNSUPPORTED / POSITIVE RESPONDING — expect AMBIGUOUS ───────
                    TC("The person is responding.",                 RetrievalDecision.AMBIGUOUS, "pos-responding"),
                    TC("The person seems responsive.",              RetrievalDecision.AMBIGUOUS, "pos-responding"),
                    TC("They are awake and answering me.",          RetrievalDecision.AMBIGUOUS, "pos-responding"),

                    // ── SUPPORTED — expect VERIFIED ──────────────────────────────
                    TC("The person vomited during CPR.",            RetrievalDecision.VERIFIED,  "supported"),
                    TC("How fast should CPR compressions be?",      RetrievalDecision.VERIFIED,  "supported"),
                    TC("The person is breathing.",                  RetrievalDecision.VERIFIED,  "supported"),
                    TC("The person is not responding.",             RetrievalDecision.VERIFIED,  "supported"),
                    TC("How do I give chest compressions?",         RetrievalDecision.VERIFIED,  "supported"),

                    // ── SAFETY-CRITICAL / CONTRADICTION — expect AMBIGUOUS ───────
                    TC("The person is breathing but not responding.", RetrievalDecision.AMBIGUOUS,"contradiction"),
                    TC("The person is not breathing.",              RetrievalDecision.AMBIGUOUS, "negated-breathing"),
                    TC("The person is not responding but is breathing normally.", RetrievalDecision.AMBIGUOUS, "contradiction")
                )

                android.util.Log.i(tag, "=== Task 4B Confidence Policy Evaluation (${cases.size} cases) ===")
                android.util.Log.i(tag, "  MIN_SCORE_VERIFIED=${ConfidencePolicy.MIN_SCORE_VERIFIED}")
                android.util.Log.i(tag, "  MIN_GAP_VERIFIED=${ConfidencePolicy.MIN_GAP_VERIFIED}")
                android.util.Log.i(tag, "  MIN_GAP_SAFETY_CRITICAL_PAIR=${ConfidencePolicy.MIN_GAP_SAFETY_CRITICAL_PAIR}")

                var pass = 0
                val failures = mutableListOf<String>()

                val sb = StringBuilder()
                sb.appendLine("=== Task 4B: Confidence Policy ===")
                sb.appendLine("cases: ${cases.size}")
                sb.appendLine()

                cases.forEachIndexed { idx, tc ->
                    val match    = retriever.findBestMatch(tc.query)
                    val decision = policy.evaluate(tc.query, match)
                    val gap      = match.score - match.runnerUpScore
                    val hit      = decision.decision == tc.expectedDecision
                    val status   = if (hit) "PASS" else "FAIL"
                    if (hit) pass++ else failures.add(
                        "\"${tc.query}\"\n  expected=${tc.expectedDecision} got=${decision.decision}"
                    )

                    android.util.Log.i(tag, "[$status][${idx+1}] \"${tc.query}\"")
                    android.util.Log.i(tag, "  top=${match.protocol.protocolId} score=${"%.4f".format(match.score)}")
                    android.util.Log.i(tag, "  runner=${match.runnerUpId} score=${"%.4f".format(match.runnerUpScore)} gap=${"%.4f".format(gap)}")
                    android.util.Log.i(tag, "  signals=${decision.detectedSignals}")
                    android.util.Log.i(tag, "  decision=${decision.decision}  expected=${tc.expectedDecision}")
                    android.util.Log.i(tag, "  reason=${decision.reason}")

                    val sig = if (decision.detectedSignals.isNotEmpty())
                        " sig=${decision.detectedSignals.joinToString(",")}" else ""
                    sb.appendLine("${idx+1}. $status [${tc.note}]")
                    sb.appendLine("   ${decision.decision} top=${match.protocol.protocolId}")
                    sb.appendLine("   score=${"%.4f".format(match.score)} gap=${"%.4f".format(gap)}$sig")
                    sb.appendLine("   ${decision.reason}")
                    sb.appendLine()
                }

                val total    = cases.size
                val accuracy = pass * 100f / total

                android.util.Log.i(tag, "--- SUMMARY ---")
                android.util.Log.i(tag, "Total: $total  PASS: $pass  Accuracy: ${"%.1f".format(accuracy)}%")
                failures.forEach { android.util.Log.i(tag, "FAIL: $it") }

                sb.appendLine("─────────────────────")
                sb.appendLine("TOTAL    : $total")
                sb.appendLine("PASS     : $pass")
                sb.appendLine("FAIL     : ${total - pass}")
                sb.appendLine("ACCURACY : ${"%.1f".format(accuracy)}%")
                if (failures.isNotEmpty()) {
                    sb.appendLine()
                    sb.appendLine("FAILURES:")
                    failures.forEach { sb.appendLine(it) }
                }

                retriever.close()
                task4Result = sb.toString().trim()
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6A1B9A)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = "🛡 Run Task 4B: Confidence Policy — 16 cases",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        // Show Task 4 results inline (scrollable)
        if (task4Result.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A0027)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = task4Result,
                        modifier = Modifier.padding(12.dp),
                        fontSize = 11.sp,
                        color = Color(0xFFCE93D8),
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        lineHeight = 17.sp
                    )
                }
            }
        }

        // ── TASK 5: AI-RESULT PIPELINE DEMO (DEBUG ONLY) ──
        var aiResultDemo by remember { mutableStateOf("") }

        Button(
            onClick = {
                val engine = LifeLineAiEngine(context)
                val tag = "Task5"

                val testQueries = listOf(
                    "The person vomited during CPR.",             // Expect VERIFIED
                    "Should I give this person medicine?",        // Expect UNKNOWN
                    "The person is breathing but not responding." // Expect AMBIGUOUS
                )

                val sb = StringBuilder()
                sb.appendLine("=== Task 5: AiResult Pipeline Demo ===")
                sb.appendLine()

                testQueries.forEachIndexed { i, q ->
                    val res = engine.process(q)
                    android.util.Log.i(tag, "[$i] Query: \"${res.query}\"")
                    android.util.Log.i(tag, "  decision=${res.decision}")
                    android.util.Log.i(tag, "  protocolId=${res.protocolId}")
                    android.util.Log.i(tag, "  title=${res.title}")
                    android.util.Log.i(tag, "  response=${res.response}")
                    android.util.Log.i(tag, "  reference=${res.reference}")
                    android.util.Log.i(tag, "  version=${res.version}")
                    android.util.Log.i(tag, "  topScore=${res.topScore}")
                    android.util.Log.i(tag, "  runnerUpScore=${res.runnerUpScore}")
                    android.util.Log.i(tag, "  gap=${res.gap}")
                    android.util.Log.i(tag, "  reasonCode=${res.reasonCode}")
                    android.util.Log.i(tag, "  signals=${res.detectedSignals}")

                    sb.appendLine("Query: \"${res.query}\"")
                    sb.appendLine("  Decision: ${res.decision}")
                    sb.appendLine("  Protocol: ${res.protocolId ?: "(none)"}")
                    sb.appendLine("  Title: ${res.title ?: "(none)"}")
                    sb.appendLine("  Response: ${res.response ?: "(none)"}")
                    sb.appendLine("  Reference: ${res.reference ?: "(none)"}")
                    sb.appendLine("  Version: ${res.version ?: "(none)"}")
                    sb.appendLine("  Scores: top=${"%.4f".format(res.topScore)} runner=${"%.4f".format(res.runnerUpScore)} gap=${"%.4f".format(res.gap)}")
                    sb.appendLine("  Reason: ${res.reasonCode}")
                    sb.appendLine("  Signals: ${res.detectedSignals}")
                    sb.appendLine()
                }

                engine.close()
                aiResultDemo = sb.toString().trim()
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00796B)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = "✨ Run Task 5: AiResult Pipeline Demo",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        // Show Task 5 results inline (scrollable)
        if (aiResultDemo.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF00241E)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = aiResultDemo,
                        modifier = Modifier.padding(12.dp),
                        fontSize = 11.sp,
                        color = Color(0xFF80CBC4),
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        lineHeight = 17.sp
                    )
                }
            }
        }

        // Disclaimer
        Text(
            text = "Emergency Voice Assistant · Offline Speech Recognition",
            fontSize = 11.sp,
            color = Color(0xFF506070),
            textAlign = TextAlign.Center
        )
    }
}
