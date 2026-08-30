package com.example.lifeline

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── Colours (intentionally match the CPR screen palette) ─────────────────────

private val AiColorBackground = Color(0xFF0D1B2A)
private val AiColorSurface    = Color(0xFF16283B)
private val AiColorCardBg     = Color(0xFF1E2A3A)
private val AiColorCardText   = Color(0xFFB2BEC3)
private val AiColorCardValue  = Color(0xFFECF0F1)
private val AiColorGreen      = Color(0xFF2ECC71)
private val AiColorAmber      = Color(0xFFF39C12)
private val AiColorRed        = Color(0xFFE74C3C)
private val AiColorGrey       = Color(0xFF7F8C8D)
private val AiColorAccent     = Color(0xFF3498DB)  // Calm blue for AI branding

// ─────────────────────────────────────────────────────────────────────────────
// AskLifeLineScreen
//
// Entry point for the AI guidance UI layer.
//
// CURRENT BEHAVIOUR (mock mode):
//   Displays one of two AiMockData objects. A toggle button at the bottom
//   switches between VERIFIED and UNKNOWN so you can test both states.
//
// FUTURE INTEGRATION:
//   Remove the mock toggle. Pass the real AiResult from the AI module:
//
//     AskLifeLineScreen(result = myAiModule.getResult())
//
//   The screen renders correctly for any AiResult — no changes needed here.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AskLifeLineScreen(modifier: Modifier = Modifier) {

    // ── Mock toggle state ─────────────────────────────────────────────────────
    // TODO (AI integration): replace `currentResult` with a real AiResult
    //      passed in from the offline AI module. Remove the toggle button below.
    var showVerified by remember { mutableStateOf(true) }
    val currentResult: AiResult = if (showVerified) {
        AiMockData.verifiedResult
    } else {
        AiMockData.unknownResult
    }

    // ── Layout ────────────────────────────────────────────────────────────────
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AiColorBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // Header
        AiHeader()

        // Result panel — switches on status
        when (currentResult.status) {
            AiResultStatus.VERIFIED -> VerifiedPanel(result = currentResult)
            AiResultStatus.UNKNOWN  -> UnknownPanel(result = currentResult)
        }

        Spacer(Modifier.height(8.dp))

        // ── DEV TOGGLE — remove when integrating real AI module ──────────────
        AiDevToggle(
            showingVerified = showVerified,
            onToggle        = { showVerified = !showVerified }
        )
        // ── END DEV TOGGLE ────────────────────────────────────────────────────

        // Disclaimer
        Text(
            text      = "For CPR training use only · Not a medical device",
            fontSize  = 11.sp,
            color     = Color(0xFF506070),
            textAlign = TextAlign.Center
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// VERIFIED panel — shown when AiResultStatus == VERIFIED
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun VerifiedPanel(result: AiResult) {

    // Status badge — green
    AiStatusBadge(
        label = "✓  Verified Guidance Found",
        color = AiColorGreen
    )

    // Protocol identity card
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = AiColorSurface),
        shape    = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier            = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AiDetailRow(label = "Protocol", value = result.title)
            AiDivider()
            AiDetailRow(label = "Confidence", value = result.confidenceLevel)
            if (result.source.isNotBlank()) {
                AiDivider()
                AiDetailRow(label = "Source", value = result.source)
            }
        }
    }

    // Guidance response card
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = AiColorCardBg),
        shape    = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text       = "Guidance",
                fontSize   = 12.sp,
                color      = AiColorCardText,
                letterSpacing = 1.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text       = result.response,
                fontSize   = 14.sp,
                color      = AiColorCardValue,
                lineHeight = 22.sp
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// UNKNOWN panel — shown when AiResultStatus == UNKNOWN
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun UnknownPanel(result: AiResult) {

    // Status badge — amber (caution, not red — this is not an error, just uncertain)
    AiStatusBadge(
        label = "⚠  Low Confidence",
        color = AiColorAmber
    )

    // Explanation card
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = AiColorSurface),
        shape    = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier            = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text       = "LifeLine cannot safely guide this situation",
                fontSize   = 16.sp,
                fontWeight = FontWeight.Bold,
                color      = AiColorCardValue,
                textAlign  = TextAlign.Center
            )
            Text(
                text      = "The query did not match any verified protocol in the " +
                        "local knowledge base with sufficient confidence.\n\n" +
                        "Please follow the advice of trained emergency responders " +
                        "or contact emergency services immediately.",
                fontSize  = 13.sp,
                color     = AiColorCardText,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
        }
    }

    // Emergency services reminder card
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = Color(0xFF1A1000)),
        shape    = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier            = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment   = Alignment.CenterVertically
        ) {
            Text(text = "📞", fontSize = 28.sp)
            Column {
                Text(
                    text       = "Call Emergency Services",
                    fontSize   = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = AiColorAmber
                )
                Text(
                    text     = "112 · 108 · local emergency number",
                    fontSize = 12.sp,
                    color    = AiColorCardText
                )
            }
        }
    }

    // Confidence label for reference
    Text(
        text       = "Confidence: ${result.confidenceLevel}",
        fontSize   = 11.sp,
        color      = AiColorGrey,
        fontStyle  = FontStyle.Italic,
        textAlign  = TextAlign.Center
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared composables
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AiHeader() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text       = "❤ LifeLine",
            fontSize   = 32.sp,
            fontWeight = FontWeight.ExtraBold,
            color      = AiColorRed
        )
        Text(
            text          = "Ask LifeLine",
            fontSize      = 14.sp,
            color         = AiColorAccent,
            letterSpacing = 1.5.sp,
            fontWeight    = FontWeight.Medium
        )
    }
}

@Composable
private fun AiStatusBadge(label: String, color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(color)
            .padding(vertical = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text       = label,
            fontSize   = 20.sp,
            fontWeight = FontWeight.Bold,
            color      = Color.White,
            textAlign  = TextAlign.Center
        )
    }
}

@Composable
private fun AiDetailRow(label: String, value: String) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.Top
    ) {
        Text(
            text       = label,
            fontSize   = 13.sp,
            color      = AiColorCardText,
            fontWeight = FontWeight.Medium,
            modifier   = Modifier.weight(0.35f)
        )
        Text(
            text       = value,
            fontSize   = 13.sp,
            color      = AiColorCardValue,
            fontWeight = FontWeight.SemiBold,
            textAlign  = TextAlign.End,
            modifier   = Modifier.weight(0.65f)
        )
    }
}

@Composable
private fun AiDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color(0xFF253545))
    )
}

/**
 * DEV-ONLY toggle button.
 * Lets testers switch between VERIFIED and UNKNOWN mock results at runtime.
 * Remove this composable and its call site when integrating the real AI module.
 */
@Composable
private fun AiDevToggle(showingVerified: Boolean, onToggle: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = Color(0xFF0A1520)),
        shape    = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier            = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text      = "[ DEV ] Mock data toggle — remove before release",
                fontSize  = 10.sp,
                color     = AiColorGrey,
                textAlign = TextAlign.Center,
                letterSpacing = 0.5.sp
            )
            Text(
                text      = "Currently showing: ${if (showingVerified) "VERIFIED (HIGH confidence)" else "UNKNOWN (LOW confidence)"}",
                fontSize  = 12.sp,
                color     = if (showingVerified) AiColorGreen else AiColorAmber,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
            OutlinedButton(
                onClick  = onToggle,
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(8.dp),
                colors   = ButtonDefaults.outlinedButtonColors(contentColor = AiColorAccent)
            ) {
                Text(
                    text     = if (showingVerified) "Switch → UNKNOWN" else "Switch → VERIFIED",
                    fontSize = 13.sp
                )
            }
        }
    }
}
