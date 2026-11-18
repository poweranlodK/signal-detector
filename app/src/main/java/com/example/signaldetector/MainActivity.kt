package com.example.signaldetector

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                SignalDetectorScreen()
            }
        }
    }
}

@Composable
fun SignalDetectorScreen() {
    val context = LocalContext.current
    var isMonitoring by remember { mutableStateOf(SignalMonitorService.isMonitoring) }
    var baselineCount by remember { mutableIntStateOf(SignalMonitorService.baselineDeviceIds.size) }
    var scannedList by remember { mutableStateOf(listOf<SignalDevice>()) }
    var alertList by remember { mutableStateOf(listOf<SignalDevice>()) }

    val permissions = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        val intent = Intent(context, SignalMonitorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    LaunchedEffect(Unit) {
        launcher.launch(permissions)
        while (true) {
            scannedList = SignalMonitorService.currentScannedDevices.values
                .sortedByDescending { it.rssi }
            alertList = SignalMonitorService.newlyDetectedAlerts.values.toList()
            delay(1500L)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("주변 전파 및 전자장치 탐지기", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    SignalMonitorService.baselineDeviceIds.clear()
                    SignalMonitorService.baselineDeviceIds.addAll(SignalMonitorService.currentScannedDevices.keys)
                    SignalMonitorService.newlyDetectedAlerts.clear()
                    baselineCount = SignalMonitorService.baselineDeviceIds.size
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("1. 현재 신호 기록 ($baselineCount)")
            }

            Button(
                onClick = {
                    isMonitoring = !isMonitoring
                    SignalMonitorService.isMonitoring = isMonitoring
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isMonitoring) Color(0xFFD32F2F) else MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.weight(1f)
            ) {
                Text(if (isMonitoring) "2. 감시 중지" else "2. 새 신호 감시")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (alertList.isNotEmpty()) {
            Text("⚠️ 새로 감지된 장치 (${alertList.size}개)", color = Color.Red, fontWeight = FontWeight.Bold)
            LazyColumn(modifier = Modifier.heightIn(max = 180.dp)) {
                items(alertList) { device ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("[${device.type}] ${device.name}", fontWeight = FontWeight.Bold, color = Color.Red)
                            Text("MAC/BSSID: ${device.id} | 신호세기: ${device.rssi} dBm", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        Text("현재 잡히는 전체 전파 (${scannedList.size}개)", fontWeight = FontWeight.SemiBold)
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(scannedList) { device ->
                val isBaseline = SignalMonitorService.baselineDeviceIds.contains(device.id)
                ListItem(
                    headlineContent = { Text("[${device.type}] ${device.name}") },
                    supportingContent = { Text("ID: ${device.id} | 신호: ${device.rssi} dBm") },
                    trailingContent = {
                        Text(
                            text = if (isBaseline) "기록됨" else "미등록",
                            color = if (isBaseline) Color(0xFF2E7D32) else Color.Gray
                        )
                    }
                )
                HorizontalDivider()
            }
        }
    }
}
