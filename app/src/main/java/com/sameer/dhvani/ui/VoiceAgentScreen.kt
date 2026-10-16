package com.sameer.dhvani.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sameer.dhvani.pipeline.AgentState
import com.sameer.dhvani.pipeline.AssistantMode
import com.sameer.dhvani.pipeline.ChatMessage
import com.sameer.dhvani.pipeline.MessageSender
import com.sameer.dhvani.pipeline.VoiceAgentPipeline
import com.sameer.dhvani.utils.LatencyMetrics

// ==========================================
// PALETTE: Warm, Humanized, Editorial AI
// ==========================================
val SaffronGlow = Color(0xFFFF6B35)
val SaffronWarm = Color(0xFFF97316)
val IndigoElectric = Color(0xFF4F46E5)
val VioletSoft = Color(0xFF7C3AED)
val TealAccent = Color(0xFF0D9488)
val GreenLive = Color(0xFF10B981)

// Light Mode: Warm Silk & Porcelain
val LightCanvas = Color(0xFFF8F9FA)
val LightGlass = Color(0xFFFFFFFF)
val LightGlassSubtle = Color(0xFFF3F4F6)
val LightBorderSoft = Color(0x12000000)
val LightTextMain = Color(0xFF111827)
val LightTextSub = Color(0xFF4B5563)
val LightTextFaint = Color(0xFF9CA3AF)

// Dark Mode: Obsidian Midnight & Starlight
val DarkCanvas = Color(0xFF090D16)
val DarkGlass = Color(0xFF131A2A)
val DarkGlassSubtle = Color(0xFF1B2337)
val DarkBorderSoft = Color(0x22FFFFFF)
val DarkTextMain = Color(0xFFF9FAFB)
val DarkTextSub = Color(0xFF94A3B8)
val DarkTextFaint = Color(0xFF64748B)

// Compatibility aliases
val DarkBackground = DarkCanvas
val AccentSaffron = SaffronWarm
val AccentCyan = IndigoElectric
val TextPrimary = LightTextMain
val TextSecondary = LightTextSub

@Composable
fun VoiceAgentScreen(
    pipeline: VoiceAgentPipeline,
    modifier: Modifier = Modifier
) {
    val state by pipeline.agentState.collectAsState()
    val mode by pipeline.assistantMode.collectAsState()
    val messages by pipeline.messages.collectAsState()
    val metrics by pipeline.currentMetrics.collectAsState()
    val audioLevel by pipeline.audioLevel.collectAsState()

    var isDarkMode by remember { mutableStateOf(false) }
    var showTelemetryDrawer by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val canvasColor = if (isDarkMode) DarkCanvas else LightCanvas
    val glassColor = if (isDarkMode) DarkGlass else LightGlass
    val glassSubtle = if (isDarkMode) DarkGlassSubtle else LightGlassSubtle
    val borderSoft = if (isDarkMode) DarkBorderSoft else LightBorderSoft
    val textMain = if (isDarkMode) DarkTextMain else LightTextMain
    val textSub = if (isDarkMode) DarkTextSub else LightTextSub
    val textFaint = if (isDarkMode) DarkTextFaint else LightTextFaint

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(canvasColor)
    ) {
        // Subtle ambient atmospheric glow (giving depth and warmth)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = if (isDarkMode) listOf(
                            Color(0x1F4F46E5),
                            Color(0x0A090D16),
                            Color.Transparent
                        ) else listOf(
                            Color(0x12FF6B35),
                            Color(0x084F46E5),
                            Color.Transparent
                        ),
                        radius = 1200f
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // ==========================================
            // 1. SLEEK, HUMANIZED HEADER BAR
            // ==========================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Identity
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .shadow(if (isDarkMode) 0.dp else 4.dp, CircleShape)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(SaffronWarm, IndigoElectric)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "ध",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "dhvani",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = textMain,
                                letterSpacing = (-0.5).sp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(SaffronWarm)
                            )
                        }
                        Text(
                            text = "IISc Bangalore · Voice AI",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = textSub
                        )
                    }
                }

                // Controls: Telemetry Pill + Dark Mode Toggle
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Live Telemetry Pill (Sleek capsule showing real-time E2E)
                    Surface(
                        onClick = { showTelemetryDrawer = !showTelemetryDrawer },
                        shape = RoundedCornerShape(20.dp),
                        color = if (showTelemetryDrawer) {
                            if (isDarkMode) Color(0xFF1E293B) else Color(0xFFEEF2FF)
                        } else glassColor,
                        shadowElevation = if (isDarkMode) 0.dp else 2.dp,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (showTelemetryDrawer) IndigoElectric else borderSoft
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = "Telemetry",
                                tint = IndigoElectric,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (metrics.totalE2EMs > 0) "${metrics.totalE2EMs}ms" else "Telemetry",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (metrics.totalE2EMs > 0) GreenLive else textSub
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Icon(
                                imageVector = if (showTelemetryDrawer) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = textFaint,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    // Theme Toggle Button
                    Surface(
                        onClick = { isDarkMode = !isDarkMode },
                        shape = CircleShape,
                        color = glassColor,
                        shadowElevation = if (isDarkMode) 0.dp else 2.dp,
                        border = androidx.compose.foundation.BorderStroke(1.dp, borderSoft),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = "Toggle Theme",
                                tint = if (isDarkMode) Color(0xFFFFD54F) else Color(0xFF475569),
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }
            }

            // ==========================================
            // 2. EXPANDABLE TELEMETRY HUD (Clean Drawer)
            // ==========================================
            AnimatedVisibility(
                visible = showTelemetryDrawer,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                ModernTelemetryDrawer(
                    metrics = metrics,
                    pipeline = pipeline,
                    isDarkMode = isDarkMode,
                    onClose = { showTelemetryDrawer = false }
                )
            }

            // ==========================================
            // 3. FLUID SEGMENTED MODE SELECTOR
            // ==========================================
            FluidModeSelector(
                selectedMode = mode,
                onModeSelected = { pipeline.setAssistantMode(it) },
                isDarkMode = isDarkMode
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ==========================================
            // 4. CONVERSATIONAL STREAM (Humanized Dialogue)
            // ==========================================
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (messages.isEmpty()) {
                    EmptyConversationHero(
                        isDarkMode = isDarkMode,
                        onPromptSelected = { pipeline.testUtterance(it) }
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(top = 6.dp, bottom = 12.dp)
                    ) {
                        items(messages, key = { it.id }) { msg ->
                            ModernChatBubble(
                                message = msg,
                                isDarkMode = isDarkMode
                            )
                        }
                    }
                }
            }

            // ==========================================
            // 5. CONVERSATIONAL SPARKS ("Try saying...")
            // ==========================================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 6.dp, start = 4.dp)
                ) {
                    Text(
                        text = "✨ Try saying",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = textSub
                    )
                }

                val promptSparks = listOf(
                    "🌤️ Bangalore weather" to "What is the weather forecast for Bangalore tomorrow?",
                    "⏰ 7 baje alarm (Hinglish)" to "Mujhe kal subah 7 baje ka alarm set karna hai.",
                    "💰 500 me se 150 (Hinglish)" to "Agar mere paas 500 rupaye hain aur 150 kharch ho gaye, toh kitne bache?",
                    "🏛️ Campus canteen (Hinglish)" to "Campus mein nearest canteen kahan hai, mujhe chai peeni hai?",
                    "🇮🇳 Pradhan Mantri (Hindi)" to "भारत के वर्तमान प्रधानमंत्री कौन हैं?",
                    "⛅ मौसम कैसा रहेगा (Hindi)" to "कल बैंगलोर का मौसम कैसा रहेगा?",
                    "🧮 24 × 4 (English)" to "What is 24 multiplied by 4?"
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp)
                ) {
                    items(promptSparks) { (label, fullQuery) ->
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = glassSubtle,
                            modifier = Modifier
                                .border(1.dp, borderSoft, RoundedCornerShape(20.dp))
                                .clickable { pipeline.testUtterance(fullQuery) }
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = textMain,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                            )
                        }
                    }
                }
            }

            // ==========================================
            // 6. THE LIVING VOICE CORE (ChatGPT / Gemini style)
            // ==========================================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                LivingVoiceCore(
                    state = state,
                    audioLevel = audioLevel,
                    isDarkMode = isDarkMode,
                    onClick = { pipeline.onOrbTapped() }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Organic Status Pill (Tap to start / Tap to stop)
                StatusIndicatorPill(
                    state = state,
                    isDarkMode = isDarkMode,
                    onClick = { pipeline.onOrbTapped() }
                )
            }
        }
    }
}

// ==========================================
// COMPONENT: Fluid Segmented Control
// ==========================================
@Composable
fun FluidModeSelector(
    selectedMode: AssistantMode,
    onModeSelected: (AssistantMode) -> Unit,
    isDarkMode: Boolean
) {
    val containerBg = if (isDarkMode) Color(0xFF111726) else Color(0xFFF1F3F5)
    val borderCol = if (isDarkMode) Color(0x1AFFFFFF) else Color(0x10000000)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(containerBg)
            .border(1.dp, borderCol, RoundedCornerShape(24.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        val modes = listOf(
            Triple(AssistantMode.AUTO, "Auto", "✦"),
            Triple(AssistantMode.ENGLISH, "English", "EN"),
            Triple(AssistantMode.HINGLISH, "Hinglish", "HI"),
            Triple(AssistantMode.HINDI, "Hindi", "हिंदी")
        )

        modes.forEach { (mode, label, symbol) ->
            val isSelected = selectedMode == mode
            val activeBg = if (isDarkMode) Color(0xFF1E293B) else Color(0xFFFFFFFF)
            val activeText = if (isDarkMode) Color(0xFFF8FAFC) else Color(0xFF0F172A)
            val inactiveText = if (isDarkMode) DarkTextSub else LightTextSub

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isSelected) activeBg else Color.Transparent)
                    .shadow(if (isSelected && !isDarkMode) 2.dp else 0.dp, RoundedCornerShape(20.dp))
                    .clickable { onModeSelected(mode) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = symbol,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) SaffronWarm else inactiveText
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = label,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (isSelected) activeText else inactiveText
                    )
                }
            }
        }
    }
}

// ==========================================
// COMPONENT: Modern Telemetry Drawer
// ==========================================
@Composable
fun ModernTelemetryDrawer(
    metrics: LatencyMetrics,
    pipeline: VoiceAgentPipeline,
    isDarkMode: Boolean,
    onClose: () -> Unit
) {
    val cardBg = if (isDarkMode) Color(0xFF131A2A) else Color(0xFFFFFFFF)
    val cardBorder = if (isDarkMode) Color(0x334F46E5) else Color(0x224F46E5)
    val textMain = if (isDarkMode) DarkTextMain else LightTextMain
    val textSub = if (isDarkMode) DarkTextSub else LightTextSub

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .shadow(if (isDarkMode) 0.dp else 6.dp, RoundedCornerShape(18.dp))
            .border(1.dp, cardBorder, RoundedCornerShape(18.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(GreenLive)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "LIVE PIPELINE METRICS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = IndigoElectric,
                        letterSpacing = 0.5.sp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isDarkMode) Color(0xFF0F172A) else Color(0xFFECFDF5)
                    ) {
                        Text(
                            text = "E2E: ${metrics.totalE2EMs} ms",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = GreenLive,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = textSub,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Latency Steps
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ModernMetricTile("VAD", "${metrics.vadDurationMs}ms", "Silero VAD", isDarkMode)
                ModernMetricTile("STT", "${metrics.sttDurationMs}ms", "Whisper-v3", isDarkMode)
                ModernMetricTile("LLM", "${metrics.llmTtftMs}ms", "Groq LPU", isDarkMode)
                ModernMetricTile("TTS", "${metrics.ttsTtfaMs}ms", "Supertonic 3", isDarkMode)
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(
                color = if (isDarkMode) Color(0x1AFFFFFF) else Color(0x0F000000),
                thickness = 1.dp
            )
            Spacer(modifier = Modifier.height(10.dp))

            // System Context
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ModernMetricTile("DETECTED", metrics.detectedLanguage, "Bilingual", isDarkMode)
                ModernMetricTile("EMOTION", metrics.detectedEmotion, "Affective", isDarkMode)
                ModernMetricTile("RAM", "${metrics.ramUsageMb} MB", "Snapdragon", isDarkMode)
                ModernMetricTile("DUPLEX", "Active", "Echo Guard", isDarkMode)
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(
                color = if (isDarkMode) Color(0x1AFFFFFF) else Color(0x0F000000),
                thickness = 1.dp
            )
            Spacer(modifier = Modifier.height(10.dp))

            // Benchmark Suite Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "15-TRIAL BENCHMARK SUITE",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = textSub,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Recorded: ${pipeline.benchmarkLogger.trialCount}/15 trials",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (pipeline.benchmarkLogger.trialCount >= 15) GreenLive else textMain
                    )
                }

                Surface(
                    onClick = {
                        onClose()
                        pipeline.runAutomatedBenchmark()
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = IndigoElectric
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Run 15 Trials",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ModernMetricTile(
    label: String,
    value: String,
    detail: String,
    isDarkMode: Boolean
) {
    val textMain = if (isDarkMode) DarkTextMain else LightTextMain
    val textSub = if (isDarkMode) DarkTextSub else LightTextSub
    val textFaint = if (isDarkMode) DarkTextFaint else LightTextFaint

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = textSub)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = textMain)
        Spacer(modifier = Modifier.height(1.dp))
        Text(text = detail, fontSize = 8.sp, color = textFaint)
    }
}

// ==========================================
// COMPONENT: Modern Conversational Bubbles
// ==========================================
@Composable
fun ModernChatBubble(
    message: ChatMessage,
    isDarkMode: Boolean
) {
    val isUser = message.sender == MessageSender.USER

    val userGradient = Brush.linearGradient(
        listOf(Color(0xFF3B82F6), Color(0xFF2563EB))
    )
    val agentBg = if (isDarkMode) Color(0xFF151C2C) else Color(0xFFFFFFFF)
    val agentBorder = if (isDarkMode) Color(0x22FFFFFF) else Color(0x0D000000)
    val textMain = if (isDarkMode) DarkTextMain else LightTextMain
    val textSub = if (isDarkMode) DarkTextSub else LightTextSub

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        if (!isUser) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(SaffronWarm),
                    contentAlignment = Alignment.Center
                ) {
                    Text("ध", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Dhvani",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = SaffronWarm
                )
                if (message.language != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isDarkMode) Color(0xFF1E293B) else Color(0xFFF3F4F6)
                    ) {
                        Text(
                            text = message.language.name,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = IndigoElectric,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .widthIn(min = 60.dp, max = 310.dp)
                .shadow(
                    elevation = if (isDarkMode) 0.dp else (if (isUser) 3.dp else 2.dp),
                    shape = RoundedCornerShape(
                        topStart = 20.dp,
                        topEnd = 20.dp,
                        bottomStart = if (isUser) 20.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 20.dp
                    )
                )
                .clip(
                    RoundedCornerShape(
                        topStart = 20.dp,
                        topEnd = 20.dp,
                        bottomStart = if (isUser) 20.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 20.dp
                    )
                )
                .then(
                    if (isUser) Modifier.background(userGradient)
                    else Modifier
                        .background(agentBg)
                        .border(
                            1.dp,
                            agentBorder,
                            RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 4.dp, bottomEnd = 20.dp)
                        )
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = message.text,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                color = if (isUser) Color.White else textMain,
                fontWeight = if (isUser) FontWeight.Medium else FontWeight.Normal
            )
        }
    }
}

// ==========================================
// COMPONENT: Empty Conversation Hero
// ==========================================
@Composable
fun EmptyConversationHero(
    isDarkMode: Boolean,
    onPromptSelected: (String) -> Unit
) {
    val textMain = if (isDarkMode) DarkTextMain else LightTextMain
    val textSub = if (isDarkMode) DarkTextSub else LightTextSub

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .shadow(if (isDarkMode) 0.dp else 8.dp, CircleShape)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(SaffronWarm.copy(alpha = 0.2f), IndigoElectric.copy(alpha = 0.2f))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = SaffronWarm,
                modifier = Modifier.size(28.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "How can I help you today?",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = textMain
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "आप हिंदी या English में कुछ भी पूछ सकते हैं",
            fontSize = 13.sp,
            color = textSub,
            textAlign = TextAlign.Center
        )
    }
}

// ==========================================
// COMPONENT: The Living Voice Core (Orb)
// ==========================================
@Composable
fun LivingVoiceCore(
    state: AgentState,
    audioLevel: Float,
    isDarkMode: Boolean,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "LivingCore")

    // Smooth breathing motion
    val breathingPulse by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "BreathingPulse"
    )

    // Rotating aura angle for THINKING state
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "OrbitRotation"
    )

    // Reactive scale based on voice audio level
    val coreScale = when (state) {
        AgentState.LISTENING -> (1f + (audioLevel * 1.8f)).coerceIn(1.0f, 1.45f)
        AgentState.SPEAKING -> breathingPulse * 1.15f
        AgentState.THINKING -> breathingPulse
        AgentState.IDLE -> breathingPulse * 0.96f
    }

    val coreGradient = when (state) {
        AgentState.LISTENING -> Brush.radialGradient(
            listOf(Color(0xFF34D399), Color(0xFF10B981), Color(0xFF047857))
        )
        AgentState.THINKING -> Brush.radialGradient(
            listOf(Color(0xFFFBBF24), Color(0xFFF97316), Color(0xFFEA580C))
        )
        AgentState.SPEAKING -> Brush.radialGradient(
            listOf(Color(0xFFA78BFA), Color(0xFF7C3AED), Color(0xFF4C1D95))
        )
        AgentState.IDLE -> Brush.radialGradient(
            listOf(Color(0xFF60A5FA), Color(0xFF3B82F6), Color(0xFF1D4ED8))
        )
    }

    val glowAlpha = when (state) {
        AgentState.LISTENING -> (0.25f + audioLevel * 0.5f).coerceIn(0.2f, 0.7f)
        AgentState.SPEAKING -> 0.45f
        AgentState.THINKING -> 0.35f
        AgentState.IDLE -> 0.15f
    }

    Box(
        modifier = Modifier
            .size(110.dp)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        // Outer Translucent Breathing Halo
        Box(
            modifier = Modifier
                .size(100.dp)
                .scale(coreScale * 1.25f)
                .clip(CircleShape)
                .background(
                    when (state) {
                        AgentState.LISTENING -> GreenLive.copy(alpha = glowAlpha)
                        AgentState.THINKING -> SaffronWarm.copy(alpha = glowAlpha)
                        AgentState.SPEAKING -> VioletSoft.copy(alpha = glowAlpha)
                        AgentState.IDLE -> IndigoElectric.copy(alpha = glowAlpha)
                    }
                )
        )

        // Middle Orbit Ring (Rotates dynamically in THINKING state)
        if (state == AgentState.THINKING) {
            Box(
                modifier = Modifier
                    .size(86.dp)
                    .rotate(rotationAngle)
                    .clip(CircleShape)
                    .border(
                        2.dp,
                        Brush.sweepGradient(
                            listOf(
                                SaffronWarm,
                                IndigoElectric,
                                Color(0xFF06B6D4),
                                SaffronWarm
                            )
                        ),
                        CircleShape
                    )
            )
        }

        // Inner Solid Fluid Core
        Box(
            modifier = Modifier
                .size(72.dp)
                .scale(coreScale)
                .shadow(
                    elevation = if (isDarkMode) 12.dp else 8.dp,
                    shape = CircleShape,
                    spotColor = when (state) {
                        AgentState.LISTENING -> GreenLive
                        AgentState.THINKING -> SaffronWarm
                        AgentState.SPEAKING -> VioletSoft
                        AgentState.IDLE -> IndigoElectric
                    }
                )
                .clip(CircleShape)
                .background(coreGradient),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = when (state) {
                    AgentState.SPEAKING -> Icons.AutoMirrored.Filled.VolumeUp
                    AgentState.THINKING -> Icons.Default.GraphicEq
                    AgentState.LISTENING -> Icons.Default.Mic
                    AgentState.IDLE -> Icons.Default.Mic
                },
                contentDescription = "Voice State",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

// ==========================================
// COMPONENT: Status Indicator Pill
// ==========================================
@Composable
fun StatusIndicatorPill(
    state: AgentState,
    isDarkMode: Boolean,
    onClick: () -> Unit
) {
    val pillBg = if (isDarkMode) Color(0xFF131A2A) else Color(0xFFF3F4F6)
    val pillBorder = if (isDarkMode) Color(0x2E4F46E5) else Color(0x1A4F46E5)

    val (statusText, statusDotColor) = when (state) {
        AgentState.IDLE -> "Tap to speak" to Color(0xFF94A3B8)
        AgentState.LISTENING -> "Listening... · Tap to stop" to GreenLive
        AgentState.THINKING -> "Thinking... · Tap to cancel" to SaffronWarm
        AgentState.SPEAKING -> "Speaking · Tap to stop" to VioletSoft
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = pillBg,
        modifier = Modifier
            .border(1.dp, pillBorder, RoundedCornerShape(20.dp))
            .shadow(if (isDarkMode) 0.dp else 2.dp, RoundedCornerShape(20.dp))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(statusDotColor)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = statusText,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isDarkMode) DarkTextMain else LightTextMain
            )
        }
    }
}
