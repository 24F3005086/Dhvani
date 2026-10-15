package com.sameer.dhvani.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sameer.dhvani.pipeline.AgentState
import com.sameer.dhvani.pipeline.AssistantMode
import com.sameer.dhvani.pipeline.ChatMessage
import com.sameer.dhvani.pipeline.MessageSender
import com.sameer.dhvani.pipeline.VoiceAgentPipeline
import com.sameer.dhvani.utils.LatencyMetrics

// Light Theme Palette (Default)
val LightBg = Color(0xFFF8FAFC)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceAlt = Color(0xFFF1F5F9)
val LightBorder = Color(0xFFE2E8F0)
val LightTextPrimary = Color(0xFF0F172A)
val LightTextSecondary = Color(0xFF475569)
val LightTextMuted = Color(0xFF94A3B8)

// Dark Theme Palette (Toggleable)
val DarkBg = Color(0xFF0B0F19)
val DarkSurface = Color(0xFF131927)
val DarkSurfaceAlt = Color(0xFF1C2438)
val DarkBorder = Color(0xFF232D44)
val DarkTextPrimary = Color(0xFFF8FAFC)
val DarkTextSecondary = Color(0xFF94A3B8)
val DarkTextMuted = Color(0xFF64748B)

// Brand & Accent Colors
val SaffronPrimary = Color(0xFFE65100)
val SaffronLight = Color(0xFFFF9800)
val BluePrimary = Color(0xFF2563EB)
val GreenSuccess = Color(0xFF16A34A)
val PurpleAccent = Color(0xFF7C3AED)

// Backward compatibility aliases
val DarkBackground = DarkBg
val AccentSaffron = SaffronPrimary
val AccentCyan = BluePrimary
val TextPrimary = LightTextPrimary
val TextSecondary = LightTextSecondary

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

    var isDarkMode by remember { mutableStateOf(false) } // Default to Clean Light Mode
    var showHud by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val currentBg = if (isDarkMode) DarkBg else LightBg
    val currentSurface = if (isDarkMode) DarkSurface else LightSurface
    val currentBorder = if (isDarkMode) DarkBorder else LightBorder
    val currentTextPrimary = if (isDarkMode) DarkTextPrimary else LightTextPrimary
    val currentTextSecondary = if (isDarkMode) DarkTextSecondary else LightTextSecondary

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(currentBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "ध्वनि",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = SaffronPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "DHVANI",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = BluePrimary,
                            letterSpacing = 1.sp
                        )
                    }
                    Text(
                        text = "IISc Computational Intelligence Lab",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = currentTextSecondary
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Light / Dark Mode Toggle
                    IconButton(
                        onClick = { isDarkMode = !isDarkMode },
                        modifier = Modifier
                            .size(38.dp)
                            .shadow(if (isDarkMode) 0.dp else 2.dp, CircleShape)
                            .clip(CircleShape)
                            .background(currentSurface)
                            .border(1.dp, currentBorder, CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Toggle Theme",
                            tint = if (isDarkMode) Color(0xFFFFD54F) else Color(0xFF475569),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Telemetry HUD Toggle
                    IconButton(
                        onClick = { showHud = !showHud },
                        modifier = Modifier
                            .size(38.dp)
                            .shadow(if (isDarkMode) 0.dp else 2.dp, CircleShape)
                            .clip(CircleShape)
                            .background(currentSurface)
                            .border(1.dp, currentBorder, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = "Toggle Latency Telemetry",
                            tint = if (showHud) BluePrimary else currentTextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Language & Assistant Mode Selector
            AssistantModeSelector(
                selectedMode = mode,
                onModeSelected = { pipeline.setAssistantMode(it) },
                isDarkMode = isDarkMode
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Real-Time Telemetry HUD (Collapsible)
            AnimatedVisibility(visible = showHud) {
                LatencyDashboardHUD(
                    metrics = metrics,
                    isDarkMode = isDarkMode
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Conversation Messages Area (Takes all available vertical space with smooth scrolling)
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                if (messages.isEmpty()) {
                    item {
                        EmptyStatePrompt(isDarkMode = isDarkMode)
                    }
                } else {
                    items(messages, key = { it.id }) { msg ->
                        ChatBubble(
                            message = msg,
                            isDarkMode = isDarkMode
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Test Sentence Chips in Sleek Horizontal Scrollable Row
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Tap any test sentence to evaluate:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = currentTextSecondary,
                    modifier = Modifier.padding(bottom = 6.dp, start = 2.dp)
                )

                val testPhrases = listOf(
                    "What is the weather forecast for Bangalore tomorrow?",
                    "Can you set a reminder for my meeting at 5 PM?",
                    "Mujhe kal subah 7 baje ka alarm set karna hai.",
                    "Aaj ka schedule kya hai, aur meri next meeting kab hai?"
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp)
                ) {
                    items(testPhrases) { phrase ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isDarkMode) DarkSurfaceAlt else Color(0xFFF1F5F9),
                            modifier = Modifier
                                .border(
                                    1.dp,
                                    if (isDarkMode) DarkBorder else Color(0xFFCBD5E1),
                                    RoundedCornerShape(16.dp)
                                )
                                .clickable { pipeline.testUtterance(phrase) }
                        ) {
                            Text(
                                text = phrase,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isDarkMode) Color(0xFF93C5FD) else Color(0xFF1E40AF),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Central Animated Voice Orb Visualizer
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp),
                contentAlignment = Alignment.Center
            ) {
                VoiceOrbVisualizer(
                    state = state,
                    audioLevel = audioLevel,
                    onClick = { pipeline.onOrbTapped() }
                )
            }

            // State Description
            Text(
                text = when (state) {
                    AgentState.IDLE -> "Ready: Speak or tap a test phrase above"
                    AgentState.LISTENING -> "Listening to your voice..."
                    AgentState.THINKING -> "Whisper STT & Groq Reasoning..."
                    AgentState.SPEAKING -> "Speaking... (Tap orb to interrupt)"
                },
                fontSize = 12.sp,
                color = when (state) {
                    AgentState.IDLE -> currentTextSecondary
                    AgentState.LISTENING -> GreenSuccess
                    AgentState.THINKING -> SaffronPrimary
                    AgentState.SPEAKING -> PurpleAccent
                },
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
fun AssistantModeSelector(
    selectedMode: AssistantMode,
    onModeSelected: (AssistantMode) -> Unit,
    isDarkMode: Boolean
) {
    val containerColor = if (isDarkMode) DarkSurface else LightSurface
    val borderColor = if (isDarkMode) DarkBorder else LightBorder

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(if (isDarkMode) 0.dp else 1.dp, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(containerColor)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        val modes = listOf(
            Triple(AssistantMode.AUTO, "Auto Detect", "🌐"),
            Triple(AssistantMode.ENGLISH, "English", "🇬🇧"),
            Triple(AssistantMode.HINGLISH, "Hinglish", "🇮🇳")
        )

        modes.forEach { (mode, label, icon) ->
            val isSelected = selectedMode == mode
            val selectedBg = if (isDarkMode) Color(0xFF1E293B) else Color(0xFFEFF6FF)
            val selectedText = if (isDarkMode) Color(0xFF93C5FD) else BluePrimary
            val unselectedText = if (isDarkMode) DarkTextSecondary else LightTextSecondary

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) selectedBg else Color.Transparent)
                    .border(
                        if (isSelected) 1.dp else 0.dp,
                        if (isSelected) (if (isDarkMode) Color(0xFF334155) else Color(0xFFBFDBFE)) else Color.Transparent,
                        RoundedCornerShape(10.dp)
                    )
                    .clickable { onModeSelected(mode) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = icon, fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = label,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) selectedText else unselectedText
                    )
                }
            }
        }
    }
}

@Composable
fun LatencyDashboardHUD(
    metrics: LatencyMetrics,
    isDarkMode: Boolean
) {
    val cardBg = if (isDarkMode) DarkSurface else LightSurface
    val cardBorder = if (isDarkMode) DarkBorder else LightBorder
    val dividerColor = if (isDarkMode) Color(0xFF1F293D) else Color(0xFFF1F5F9)

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(if (isDarkMode) 0.dp else 2.dp, RoundedCornerShape(14.dp))
            .border(1.dp, cardBorder, RoundedCornerShape(14.dp))
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "REAL-TIME PIPELINE TELEMETRY",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = BluePrimary,
                    letterSpacing = 0.8.sp
                )
                Text(
                    text = "TOTAL E2E: ${metrics.totalE2EMs} ms",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = GreenSuccess
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TelemetryMetricItem("VAD", "${metrics.vadDurationMs}ms", "Silero", isDarkMode)
                TelemetryMetricItem("STT", "${metrics.sttDurationMs}ms", "Whisper", isDarkMode)
                TelemetryMetricItem("LLM TTFT", "${metrics.llmTtftMs}ms", "Groq LPU", isDarkMode)
                TelemetryMetricItem("TTS TTFA", "${metrics.ttsTtfaMs}ms", "On-Device", isDarkMode)
            }

            Spacer(modifier = Modifier.height(6.dp))
            HorizontalDivider(color = dividerColor, thickness = 1.dp)
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TelemetryMetricItem("LANGUAGE", metrics.detectedLanguage, "Bilingual", isDarkMode)
                TelemetryMetricItem("EMOTION", metrics.detectedEmotion, "Affective", isDarkMode)
                TelemetryMetricItem("RAM", "${metrics.ramUsageMb} MB", "On-Device", isDarkMode)
                TelemetryMetricItem("DUPLEX", "Active", "Echo Guard", isDarkMode)
            }
        }
    }
}

@Composable
fun TelemetryMetricItem(
    label: String,
    value: String,
    engine: String,
    isDarkMode: Boolean
) {
    val labelColor = if (isDarkMode) DarkTextSecondary else LightTextSecondary
    val valueColor = if (isDarkMode) DarkTextPrimary else LightTextPrimary
    val engineColor = if (isDarkMode) DarkTextMuted else LightTextMuted

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, fontSize = 9.sp, color = labelColor, fontWeight = FontWeight.Bold)
        Text(text = value, fontSize = 12.sp, color = valueColor, fontWeight = FontWeight.ExtraBold)
        Text(text = engine, fontSize = 8.sp, color = engineColor)
    }
}

@Composable
fun VoiceOrbVisualizer(
    state: AgentState,
    audioLevel: Float,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "OrbPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )

    val dynamicScale = when (state) {
        AgentState.LISTENING -> (1f + (audioLevel * 1.6f)).coerceIn(1f, 1.35f)
        AgentState.SPEAKING -> pulseScale * 1.12f
        AgentState.THINKING -> pulseScale
        AgentState.IDLE -> pulseScale * 0.96f
    }

    val orbGradient = when (state) {
        AgentState.IDLE -> Brush.radialGradient(listOf(Color(0xFF38BDF8), Color(0xFF0284C7), Color(0xFF0369A1)))
        AgentState.LISTENING -> Brush.radialGradient(listOf(Color(0xFF4ADE80), Color(0xFF16A34A), Color(0xFF15803D)))
        AgentState.THINKING -> Brush.radialGradient(listOf(Color(0xFFFBBF24), Color(0xFFD97706), Color(0xFFB45309)))
        AgentState.SPEAKING -> Brush.radialGradient(listOf(Color(0xFFA78BFA), Color(0xFF7C3AED), Color(0xFF6D28D9)))
    }

    Box(
        modifier = Modifier
            .size(72.dp)
            .scale(dynamicScale)
            .shadow(6.dp, CircleShape)
            .clip(CircleShape)
            .background(orbGradient)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = when (state) {
                AgentState.SPEAKING -> Icons.AutoMirrored.Filled.VolumeUp
                else -> Icons.Default.Mic
            },
            contentDescription = "Voice Assistant State",
            tint = Color.White,
            modifier = Modifier.size(30.dp)
        )
    }
}

@Composable
fun ChatBubble(
    message: ChatMessage,
    isDarkMode: Boolean
) {
    val isUser = message.sender == MessageSender.USER

    // Light & Dark mode colors for bubbles
    val bubbleBg = if (isUser) {
        if (isDarkMode) Color(0xFF1E293B) else Color(0xFFEFF6FF)
    } else {
        if (isDarkMode) DarkSurface else LightSurface
    }

    val bubbleBorder = if (isUser) {
        if (isDarkMode) Color(0xFF334155) else Color(0xFFBFDBFE)
    } else {
        if (isDarkMode) DarkBorder else LightBorder
    }

    val textCol = if (isUser) {
        if (isDarkMode) Color(0xFFF1F5F9) else Color(0xFF1E3A8A)
    } else {
        if (isDarkMode) DarkTextPrimary else LightTextPrimary
    }

    val senderNameCol = if (isUser) BluePrimary else SaffronPrimary

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 3.dp, start = 4.dp, end = 4.dp)
        ) {
            Text(
                text = if (message.senderName.isNotEmpty()) message.senderName else if (isUser) "You" else "Dhvani",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = senderNameCol
            )
            if (message.language != null) {
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isDarkMode) Color(0xFF1E293B) else Color(0xFFF1F5F9)
                ) {
                    Text(
                        text = message.language.name,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isDarkMode) Color(0xFF93C5FD) else Color(0xFF2563EB),
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            color = bubbleBg,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .shadow(if (isDarkMode) 0.dp else 1.dp, RoundedCornerShape(16.dp))
                .border(1.dp, bubbleBorder, RoundedCornerShape(16.dp))
        ) {
            Text(
                text = message.text,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = textCol,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
    }
}

@Composable
fun EmptyStatePrompt(isDarkMode: Boolean) {
    val textPrimary = if (isDarkMode) DarkTextPrimary else LightTextPrimary
    val textSecondary = if (isDarkMode) DarkTextSecondary else LightTextSecondary

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "ध्वनि (Dhvani) Voice Agent",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = textPrimary
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Speak naturally or tap any test sentence below.\nAutomatic Hindi, Hinglish & English voice response.",
            fontSize = 12.sp,
            color = textSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
        )
    }
}
