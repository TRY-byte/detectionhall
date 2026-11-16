package com.onvifguard.app.ui

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onvifguard.app.data.CameraConfig
import com.onvifguard.app.data.CameraPreferences
import com.onvifguard.app.onvif.DiscoveredCamera
import com.onvifguard.app.onvif.OnvifClient
import com.onvifguard.app.onvif.OnvifDiscovery
import com.onvifguard.app.ui.theme.BorderColor
import com.onvifguard.app.ui.theme.DarkBackground
import com.onvifguard.app.ui.theme.DarkSurface
import com.onvifguard.app.ui.theme.DarkSurfaceVariant
import com.onvifguard.app.ui.theme.NeonCyan
import com.onvifguard.app.ui.theme.SecurityRed
import com.onvifguard.app.ui.theme.StatusGreen
import com.onvifguard.app.ui.theme.TextPrimary
import com.onvifguard.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@Composable
fun CameraConfigScreen(
    currentConfig: CameraConfig,
    onConfigSaved: (CameraConfig) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var host by remember { mutableStateOf(currentConfig.host) }
    var portStr by remember { mutableStateOf(currentConfig.port.toString()) }
    var rtspPortStr by remember { mutableStateOf(currentConfig.rtspPort.toString()) }
    var username by remember { mutableStateOf(currentConfig.username) }
    var password by remember { mutableStateOf(currentConfig.password) }
    var cooldown by remember { mutableFloatStateOf(currentConfig.cooldownSeconds.toFloat()) }
    var alarmSound by remember { mutableStateOf(currentConfig.alarmSound) }
    var vibrate by remember { mutableStateOf(currentConfig.vibrate) }
    var lowLatency by remember { mutableStateOf(currentConfig.lowLatencyPolling) }

    var isScanning by remember { mutableStateOf(false) }
    var discoveredList by remember { mutableStateOf<List<DiscoveredCamera>>(emptyList()) }

    var isTesting by remember { mutableStateOf(false) }
    var testResultText by remember { mutableStateOf<String?>(null) }
    var isSuccess by remember { mutableStateOf<Boolean?>(null) }
    var resolvedRtspUrl by remember { mutableStateOf(currentConfig.rtspUrl) }
    var resolvedMediaUrl by remember { mutableStateOf(currentConfig.mediaServiceUrl) }
    var resolvedEventUrl by remember { mutableStateOf(currentConfig.eventServiceUrl) }
    var resolvedProfileToken by remember { mutableStateOf(currentConfig.profileToken) }

    val onvifClient = remember { OnvifClient() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "CAMERA & FAST ALERT SETUP",
            color = TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )

        // WS-Discovery Section
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(10.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Auto-Discovery (WS-Discovery)", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("Scan local WiFi for ONVIF cameras", color = TextSecondary, fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            isScanning = true
                            scope.launch {
                                val found = OnvifDiscovery.discoverCameras(context, timeoutMs = 3000)
                                discoveredList = found
                                isScanning = false
                                if (found.isEmpty()) {
                                    Toast.makeText(context, "No cameras discovered. Enter IP manually.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant),
                        enabled = !isScanning,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = NeonCyan, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Search, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Scan", color = NeonCyan, fontSize = 12.sp)
                        }
                    }
                }

                if (discoveredList.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Select a discovered camera to autofill:", color = NeonCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))

                    discoveredList.forEach { cam ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .background(DarkSurfaceVariant, RoundedCornerShape(6.dp))
                                .clickable {
                                    host = cam.ip
                                    portStr = cam.port.toString()
                                    Toast.makeText(context, "Selected ${cam.ip}", Toast.LENGTH_SHORT).show()
                                }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Videocam, contentDescription = null, tint = StatusGreen, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(cam.name, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text("${cam.ip}:${cam.port}", color = TextSecondary, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        // Manual Connection Settings
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(10.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Connection Details", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)

                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("Camera IP or Hostname (e.g. 192.168.1.120)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedContainerColor = DarkSurfaceVariant,
                        unfocusedContainerColor = DarkSurfaceVariant
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = portStr,
                        onValueChange = { portStr = it },
                        label = { Text("ONVIF Port (80)") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedContainerColor = DarkSurfaceVariant,
                            unfocusedContainerColor = DarkSurfaceVariant
                        )
                    )

                    OutlinedTextField(
                        value = rtspPortStr,
                        onValueChange = { rtspPortStr = it },
                        label = { Text("RTSP Port (554)") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedContainerColor = DarkSurfaceVariant,
                            unfocusedContainerColor = DarkSurfaceVariant
                        )
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedContainerColor = DarkSurfaceVariant,
                            unfocusedContainerColor = DarkSurfaceVariant
                        )
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        modifier = Modifier.weight(1f),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedContainerColor = DarkSurfaceVariant,
                            unfocusedContainerColor = DarkSurfaceVariant
                        )
                    )
                }
            }
        }

        // Fast Response & Notification Parameters
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(10.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Fast Response & Alert Settings", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Low-Latency Polling (PT3S)", color = TextPrimary, fontSize = 13.sp)
                        Text("Instantaneous long-poll socket returns under 100ms", color = TextSecondary, fontSize = 11.sp)
                    }
                    Switch(
                        checked = lowLatency,
                        onCheckedChange = { lowLatency = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = NeonCyan, checkedTrackColor = NeonCyan.copy(alpha = 0.5f))
                    )
                }

                Divider(color = BorderColor)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Audible Alert Siren", color = TextPrimary, fontSize = 13.sp)
                        Text("Play high-priority alert sound on person detection", color = TextSecondary, fontSize = 11.sp)
                    }
                    Switch(
                        checked = alarmSound,
                        onCheckedChange = { alarmSound = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = SecurityRed, checkedTrackColor = SecurityRed.copy(alpha = 0.5f))
                    )
                }

                Divider(color = BorderColor)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Urgent Vibration", color = TextPrimary, fontSize = 13.sp)
                        Text("Vibrate phone with tactical alert pattern", color = TextSecondary, fontSize = 11.sp)
                    }
                    Switch(
                        checked = vibrate,
                        onCheckedChange = { vibrate = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = NeonCyan, checkedTrackColor = NeonCyan.copy(alpha = 0.5f))
                    )
                }

                Divider(color = BorderColor)

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Alert Cooldown", color = TextPrimary, fontSize = 13.sp)
                        Text("${cooldown.toInt()}s", color = NeonCyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("Avoids notification spam while retaining instant initial response", color = TextSecondary, fontSize = 11.sp)
                    Slider(
                        value = cooldown,
                        onValueChange = { cooldown = it },
                        valueRange = 1f..30f,
                        steps = 29,
                        colors = SliderDefaults.colors(thumbColor = NeonCyan, activeTrackColor = NeonCyan)
                    )
                }
            }
        }

        // Test & Diagnostics Output
        if (testResultText != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isSuccess == true) StatusGreen.copy(alpha = 0.15f) else SecurityRed.copy(alpha = 0.15f)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = if (isSuccess == true) Icons.Default.CheckCircle else Icons.Default.Error,
                        contentDescription = null,
                        tint = if (isSuccess == true) StatusGreen else SecurityRed,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = testResultText ?: "",
                        color = TextPrimary,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = {
                    val port = portStr.toIntOrNull() ?: 80
                    val rtspPort = rtspPortStr.toIntOrNull() ?: 554
                    val tempConfig = currentConfig.copy(
                        host = host.trim(),
                        port = port,
                        rtspPort = rtspPort,
                        username = username.trim(),
                        password = password.trim()
                    )

                    isTesting = true
                    testResultText = "Connecting and probing ONVIF services..."
                    isSuccess = null

                    scope.launch {
                        val result = onvifClient.testAndInitialize(tempConfig)
                        isTesting = false
                        if (result.isSuccess) {
                            val resolved = result.getOrThrow()
                            resolvedRtspUrl = resolved.rtspUrl
                            resolvedMediaUrl = resolved.mediaServiceUrl
                            resolvedEventUrl = resolved.eventServiceUrl
                            resolvedProfileToken = resolved.profileToken
                            isSuccess = true
                            testResultText = "Connected successfully!\nProfile: ${resolved.profileToken}\nRTSP URL: ${resolved.rtspUrl}\nEvents: ${resolved.eventServiceUrl}"
                        } else {
                            isSuccess = false
                            testResultText = "Connection failed: ${result.exceptionOrNull()?.message}"
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                enabled = !isTesting && host.isNotBlank()
            ) {
                if (isTesting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = NeonCyan, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Test Connection", color = NeonCyan, fontSize = 12.sp)
                }
            }

            Button(
                onClick = {
                    try {
                        val port = portStr.toIntOrNull() ?: 80
                        val rtspPort = rtspPortStr.toIntOrNull() ?: 554

                        val newConfig = currentConfig.copy(
                            host = host.trim(),
                            port = port,
                            rtspPort = rtspPort,
                            username = username.trim(),
                            password = password.trim(),
                            rtspUrl = resolvedRtspUrl.ifBlank { "rtsp://${username.trim()}:${password.trim()}@${host.trim()}:$rtspPort/live" },
                            mediaServiceUrl = resolvedMediaUrl.ifBlank { currentConfig.mediaServiceUrl },
                            eventServiceUrl = resolvedEventUrl.ifBlank { currentConfig.eventServiceUrl },
                            profileToken = resolvedProfileToken.ifBlank { currentConfig.profileToken },
                            cooldownSeconds = cooldown.toInt(),
                            alarmSound = alarmSound,
                            vibrate = vibrate,
                            lowLatencyPolling = lowLatency
                        )

                        val prefs = CameraPreferences(context)
                        prefs.saveConfig(newConfig)
                        onConfigSaved(newConfig)
                        Toast.makeText(context, "Configuration Saved!", Toast.LENGTH_SHORT).show()
                    } catch (e: Throwable) {
                        Toast.makeText(context, "Error saving: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = SecurityRed),
                shape = RoundedCornerShape(8.dp),
                enabled = host.isNotBlank()
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Save & Apply", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
