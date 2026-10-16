package com.sameer.dhvani

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.sameer.dhvani.pipeline.VoiceAgentPipeline
import com.sameer.dhvani.ui.AccentCyan
import com.sameer.dhvani.ui.AccentSaffron
import com.sameer.dhvani.ui.DarkBackground
import com.sameer.dhvani.ui.TextPrimary
import com.sameer.dhvani.ui.TextSecondary
import com.sameer.dhvani.ui.VoiceAgentScreen
import com.sameer.dhvani.ui.theme.DhvaniTheme

class MainActivity : ComponentActivity() {

    private var pipeline: VoiceAgentPipeline? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val agentPipeline = VoiceAgentPipeline(this, lifecycleScope)
        pipeline = agentPipeline

        setContent {
            DhvaniTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = DarkBackground
                ) { innerPadding ->
                    MainScreenContent(
                        pipeline = agentPipeline,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }

        handleBenchmarkIntent(intent)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleBenchmarkIntent(intent)
    }

    private fun handleBenchmarkIntent(intent: android.content.Intent?) {
        val action = intent?.getStringExtra("action") ?: intent?.action
        if (action == "benchmark" || action == "com.sameer.dhvani.RUN_BENCHMARK") {
            android.util.Log.i("MainActivity", "Received benchmark trigger intent, launching benchmark suite...")
            pipeline?.runAutomatedBenchmark()
        }
        val utterance = intent?.getStringExtra("test_utterance") ?: intent?.getStringExtra("phrase")
        if (!utterance.isNullOrBlank()) {
            android.util.Log.i("MainActivity", "Received test utterance intent: '$utterance'")
            pipeline?.testUtterance(utterance)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        pipeline?.release()
        pipeline = null
    }
}

@Composable
fun MainScreenContent(
    pipeline: VoiceAgentPipeline,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasAudioPermission = isGranted
        if (isGranted) {
            pipeline.start()
        }
    }

    LaunchedEffect(hasAudioPermission) {
        if (hasAudioPermission) {
            pipeline.start()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    if (hasAudioPermission) {
        VoiceAgentScreen(pipeline = pipeline, modifier = modifier)
    } else {
        PermissionRequiredScreen(
            onRequestPermission = {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            },
            modifier = modifier
        )
    }
}

@Composable
fun PermissionRequiredScreen(
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "ध्वनि (Dhvani)",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = AccentSaffron
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Microphone Permission Required",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Dhvani runs on-device speech recognition (STT) and voice activity detection (VAD). Microphone access is required to listen to your voice.",
            fontSize = 13.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(28.dp))
        Button(
            onClick = onRequestPermission,
            colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = "Grant Microphone Permission",
                color = Color.Black,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
    }
}