package com.zksrus.pulse.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zksrus.pulse.viewmodel.PulseViewModel
import kotlinx.coroutines.delay

/**
 * Main heart-rate display. Shows the current BPM in a large number, with a pulsing heart icon
 * that beats at the reported heart-rate cadence.
 */
@Composable
fun HeartRateScreen(viewModel: PulseViewModel) {
    val bpm by viewModel.heartRate.collectAsStateLifecycle()
    val contact by viewModel.sensorContact.collectAsStateLifecycle()
    val bodyLocation by viewModel.bodyLocation.collectAsStateLifecycle()
    val uiState by viewModel.uiState.collectAsStateLifecycle()

    val beatBpm = bpm ?: 60
    // One beat = scale up + back down; duration scales inversely with BPM.
    val beatDurationMs = (60_000f / beatBpm.coerceIn(30, 220)).toInt().coerceAtLeast(300)

    val transition = rememberInfiniteTransition(label = "heartbeat")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(beatDurationMs / 2, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "scale",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(180.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = null,
                modifier = Modifier
                    .size(120.dp)
                    .scale(scale),
                tint = if (bpm != null) MaterialTheme.colorScheme.primary else Color.Gray,
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = bpm?.toString() ?: "--",
            fontSize = 96.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Text(
            text = "BPM",
            fontSize = 20.sp,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(modifier = Modifier.height(16.dp))

        val statusText = when {
            uiState is PulseViewModel.UiState.Connecting ->
                "Connecting…"
            bpm != null && contact?.first == true ->
                if (contact?.second == true) "Sensor contact good" else "Adjust sensor: no contact"
            bpm != null -> "Receiving heart-rate data"
            uiState is PulseViewModel.UiState.Error ->
                (uiState as PulseViewModel.UiState.Error).message
            else -> "Waiting for data…"
        }
        Text(
            text = statusText,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onBackground,
        )

        bodyLocation?.let {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Sensor location: $it",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(onClick = { viewModel.disconnect() }) {
            Text("Disconnect")
        }

        // Keep the UI in sync if Bluetooth is turned off while connected.
        LaunchedEffect(Unit) {
            while (true) {
                viewModel.refreshBluetoothState()
                delay(2000)
            }
        }
    }
}
