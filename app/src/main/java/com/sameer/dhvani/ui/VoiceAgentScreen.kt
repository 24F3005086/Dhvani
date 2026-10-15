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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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

// Color Palette for Dhvani
val DarkBackground = Color(0xFF0F111A)
val SurfaceCard = Color(0xFF181B28)
val AccentSaffron = Color(0xFFFF9933)
val AccentCyan = Color(0xFF00E5FF)
val AccentGreen = Color(0xFF00E676)
val AccentPurple = Color(0xFFB388FF)
val TextPrimary = Color(0xFFF0F2F8)
val TextSecondary = Color(0xFF9EA3B5)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VoiceAgentScreen(
    pipeline: VoiceAgentPipeline,
    modifier: Modifier = Modifier
) {
    val state by pipeline.agentState.collectAsState()
    val mode by pipeline.assistantMode.collectAsState()
    val persona by pipeline.voicePersona.collectAsState()
    val messages by pipeline.messages.collectAsState()
    val metrics by pipeline.currentMetrics.collectAsState()
    val audioLevel by pipeline.audioLevel.collectAsState()

    var showHud by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "ध्वनि",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentSaffron
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "DHVANI",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = AccentCyan
                        )
                    }
                    Text(
                        text = "IISc Computational Intelligence Lab",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }

                IconButton(
                    onClick = { showHud = !showHud },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(SurfaceCard)
                ) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = "Toggle Latency HUD",
                        tint = if (showHud) AccentCyan else TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Dedicated Assistant Mode Selector (Language)
            AssistantModeSelector(
                selectedMode = mode,
                onModeSelected = { pipeline.setAssistantMode(it) }
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Dedicated Voice Profile Selector (Accent / Engine)
            VoicePersonaSelector(
                selectedPersona = persona,
                onPersonaSelected = { pipeline.setVoicePersona(it) }
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Real-time Latency HUD Overlay
            AnimatedVisibility(visible = showHud) {
                LatencyDashboardHUD(metrics = metrics)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Conversation Messages Area
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (messages.isEmpty()) {
                    item {
                        EmptyStatePrompt()
                    }
                } else {
                    items(messages, key = { it.id }) { msg ->
                        ChatBubble(message = msg)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Quick Test Sentences Chips (1-tap evaluation for IISc criteria)
            Text(
                text = "Tap any test phrase to run instant evaluation:",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val testPhrases = listOf(
                    "What is the weather forecast for Bangalore tomorrow?",
                    "Can you set a reminder for my meeting at 5 PM?",
                    "Mujhe kal subah 7 baje ka alarm set karna hai.",
                    "Aaj ka schedule kya hai, aur meri next meeting kab hai?"
                )
                testPhrases.forEach { phrase ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SurfaceCard,
                        modifier = Modifier
                            .border(0.5.dp, Color(0xFF2C3247), RoundedCornerShape(12.dp))
                            .clickable {
                                pipeline.testUtterance(phrase)
                            }
                    ) {
                        Text(
                            text = phrase,
                            fontSize = 11.sp,
                            color = AccentCyan,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Central Dynamic Orb Visualizer (Clickable for manual talk or barge-in)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
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
                    AgentState.SPEAKING -> "Speaking with Piper TTS (Tap orb to interrupt)"
                },
                fontSize = 12.sp,
                color = when (state) {
                    AgentState.IDLE -> TextSecondary
                    AgentState.LISTENING -> AccentGreen
                    AgentState.THINKING -> AccentSaffron
                    AgentState.SPEAKING -> AccentPurple
                },
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun AssistantModeSelector(
    selectedMode: AssistantMode,
    onModeSelected: (AssistantMode) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceCard)
            .padding(3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        val modes = listOf(
            Triple(AssistantMode.AUTO, "Auto Detect", "🌐"),
            Triple(AssistantMode.ENGLISH, "English", "🇬🇧"),
            Triple(AssistantMode.HINGLISH, "Hinglish", "🇮🇳")
        )

        modes.forEach { (mode, label, icon) ->
            val isSelected = selectedMode == mode
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) Color(0xFF2B334E) else Color.Transparent)
                    .clickable { onModeSelected(mode) }
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = icon, fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) AccentCyan else TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
fun VoicePersonaSelector(
    selectedPersona: com.sameer.dhvani.tts.VoicePersona,
    onPersonaSelected: (com.sameer.dhvani.tts.VoicePersona) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceCard)
            .padding(3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        val personas = com.sameer.dhvani.tts.VoicePersona.values()

        personas.forEach { p ->
            val isSelected = selectedPersona == p
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) Color(0xFF2B334E) else Color.Transparent)
                    .clickable { onPersonaSelected(p) }
                    .padding(vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = p.icon, fontSize = 11.sp)
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = p.displayName,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) AccentSaffron else TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
fun LatencyDashboardHUD(metrics: LatencyMetrics) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF282D42), RoundedCornerShape(12.dp))
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "REAL-TIME PIPELINE TELEMETRY",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentCyan,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "TOTAL E2E: ${metrics.totalE2EMs} ms",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = AccentGreen
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TelemetryMetricItem("VAD", "${metrics.vadDurationMs}ms", "Silero ONNX")
                TelemetryMetricItem("STT", "${metrics.sttDurationMs}ms", "Groq / Whisper")
                TelemetryMetricItem("LLM TTFT", "${metrics.llmTtftMs}ms", "Groq Qwen")
                TelemetryMetricItem("TTS TTFA", "${metrics.ttsTtfaMs}ms", "Piper VITS")
            }
        }
    }
}

@Composable
fun TelemetryMetricItem(label: String, value: String, engine: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, fontSize = 9.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
        Text(text = value, fontSize = 12.sp, color = TextPrimary, fontWeight = FontWeight.ExtraBold)
        Text(text = engine, fontSize = 8.sp, color = Color(0xFF7E849E))
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
        AgentState.LISTENING -> (1f + (audioLevel * 1.8f)).coerceIn(1f, 1.4f)
        AgentState.SPEAKING -> pulseScale * 1.15f
        AgentState.THINKING -> pulseScale
        AgentState.IDLE -> pulseScale * 0.95f
    }

    val orbGradient = when (state) {
        AgentState.IDLE -> Brush.radialGradient(listOf(Color(0xFF00E5FF), Color(0xFF1D2237), Color.Transparent))
        AgentState.LISTENING -> Brush.radialGradient(listOf(Color(0xFF00E676), Color(0xFF00B0FF), Color.Transparent))
        AgentState.THINKING -> Brush.radialGradient(listOf(Color(0xFFFF9933), Color(0xFFFFD54F), Color.Transparent))
        AgentState.SPEAKING -> Brush.radialGradient(listOf(Color(0xFFB388FF), Color(0xFFFF4081), Color.Transparent))
    }

    Box(
        modifier = Modifier
            .size(80.dp)
            .scale(dynamicScale)
            .clip(CircleShape)
            .background(orbGradient)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = when (state) {
                AgentState.SPEAKING -> Icons.Default.VolumeUp
                else -> Icons.Default.Mic
            },
            contentDescription = "Voice Assistant State",
            tint = Color.White,
            modifier = Modifier.size(32.dp)
        )
    }
}

@Composable
fun ChatBubble(message: ChatMessage) {
    val isUser = message.sender == MessageSender.USER
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 2.dp)
        ) {
            Text(
                text = if (message.senderName.isNotEmpty()) message.senderName else if (isUser) "You" else "Dhvani",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isUser) AccentCyan else AccentSaffron
            )
            if (message.language != null) {
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF222638)
                ) {
                    Text(
                        text = message.language.name,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF82B1FF),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(
                topStart = 14.dp,
                topEnd = 14.dp,
                bottomStart = if (isUser) 14.dp else 2.dp,
                bottomEnd = if (isUser) 2.dp else 14.dp
            ),
            color = if (isUser) Color(0xFF1E2638) else SurfaceCard,
            modifier = Modifier
                .border(
                    0.5.dp,
                    if (isUser) Color(0xFF2C3955) else Color(0xFF282D42),
                    RoundedCornerShape(14.dp)
                )
        ) {
            Text(
                text = message.text,
                fontSize = 14.sp,
                color = TextPrimary,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}

@Composable
fun EmptyStatePrompt() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "ध्वनि (Dhvani) Multilingual Voice Agent",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Speak naturally or tap any test sentence below.\nSTT & TTS run on-device on your OnePlus phone.",
            fontSize = 11.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
    }
}
