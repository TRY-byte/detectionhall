package com.onvifguard.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onvifguard.app.data.CameraConfig
import com.onvifguard.app.data.MotionEvent
import com.onvifguard.app.data.MotionType
import com.onvifguard.app.service.NotificationHelper
import com.onvifguard.app.service.OnvifMonitorService
import com.onvifguard.app.ui.components.VideoPlayer
import com.onvifguard.app.ui.theme.BorderColor
import com.onvifguard.app.ui.theme.DarkBackground
import com.onvifguard.app.ui.theme.DarkSurface
import com.onvifguard.app.ui.theme.DarkSurfaceVariant
import com.onvifguard.app.ui.theme.NeonCyan
import com.onvifguard.app.ui.theme.SecurityRed
import com.onvifguard.app.ui.theme.StatusGreen
import com.onvifguard.app.ui.theme.TextPrimary
import com.onvifguard.app.ui.theme.TextSecondary

@Composable
fun DashboardScreen(
    cameraConfig: CameraConfig,
    onNavigateToSetup: () -> Unit
) {
    val context = LocalContext.current
    val isRunning by OnvifMonitorService.isRunning.collectAsState()
    val statusText by OnvifMonitorService.currentStatus.collectAsState()
    val lastEvent by OnvifMonitorService.lastMotionEvent.collectAsState()
    val eventHistory by OnvifMonitorService.eventHistory.collectAsState()
    val latencyMs by OnvifMonitorService.lastLatencyMs.collectAsState()

    // Determine if an alert was triggered in the last 6 seconds
    val isAlertActive = remember(lastEvent) {
        val event = lastEvent
        event != null && (System.currentTimeMillis() - event.timestamp < 6000L)
    }

    val flashingColor by animateColorAsState(
        targetValue = if (isAlertActive) SecurityRed else Color.Transparent,
        animationSpec = infiniteRepeatable(
            animation = tween(400),
            repeatMode = RepeatMode.Reverse
        ),
        label = "AlertPulse"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // Live RTSP Stream Player
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(2.dp, if (isAlertActive) SecurityRed else BorderColor)
        ) {
            VideoPlayer(rtspUrl = cameraConfig.rtspUrl)

            // Latency badge overlay
            if (isRunning) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = "Latency",
                        tint = NeonCyan,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${latencyMs}ms",
                        color = NeonCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Real-Time Motion Alert Banner
        AnimatedVisibility(visible = isAlertActive) {
            val event = lastEvent
            val isPerson = event?.type == MotionType.PERSON

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                colors = CardDefaults.cardColors(containerColor = SecurityRed.copy(alpha = 0.95f)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isPerson) Icons.Default.DirectionsRun else Icons.Default.Warning,
                        contentDescription = "Alert",
                        tint = TextPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (isPerson) "🚨 PERSON ENTERED MONITORED ZONE!" else "🚨 MOTION DETECTED RIGHT NOW!",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Detected at ${event?.getFormattedTime() ?: ""}",
                            color = TextPrimary.copy(alpha = 0.9f),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // Monitoring Control Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(10.dp)
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
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(if (isRunning) StatusGreen else SecurityRed)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isRunning) "GUARD ACTIVE • FAST ALERTS ON" else "GUARD STOPPED",
                            color = if (isRunning) StatusGreen else TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = {
                            if (isRunning) {
                                OnvifMonitorService.stopService(context)
                            } else {
                                OnvifMonitorService.startService(context)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRunning) SecurityRed else StatusGreen
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isRunning) "Stop Guard" else "Start Guard",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = statusText,
                    color = TextSecondary,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            // Instant notification test
                            NotificationHelper.sendInstantMotionAlert(
                                context = context,
                                event = MotionEvent(
                                    type = MotionType.PERSON,
                                    details = "Test: Person detected at front door"
                                ),
                                playSound = cameraConfig.alarmSound,
                                vibrate = cameraConfig.vibrate
                            )
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = NeonCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Test Alert", fontSize = 12.sp, color = NeonCyan)
                    }

                    OutlinedButton(
                        onClick = onNavigateToSetup,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = TextPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Config Camera", fontSize = 12.sp, color = TextPrimary)
                    }
                }
            }
        }

        // Recent Detections
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "RECENT DETECTIONS (${eventHistory.size})",
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            if (eventHistory.isNotEmpty()) {
                Text(
                    text = "Clear",
                    color = SecurityRed,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable { OnvifMonitorService.clearHistory() }
                        .padding(4.dp)
                )
            }
        }

        if (eventHistory.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No motion events detected yet.\nStart Guard to listen for instant ONVIF triggers.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(eventHistory) { event ->
                    EventCard(event = event)
                }
            }
        }
    }
}

@Composable
fun EventCard(event: MotionEvent) {
    val isPerson = event.type == MotionType.PERSON

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (isPerson) SecurityRed.copy(alpha = 0.2f) else DarkSurface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPerson) Icons.Default.DirectionsRun else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (isPerson) SecurityRed else NeonCyan,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isPerson) "Person Detected" else "Motion Activity",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = event.details,
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
            Text(
                text = event.getFormattedTime(),
                color = NeonCyan,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
