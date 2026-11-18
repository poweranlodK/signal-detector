package com.example.signaldetector

import android.annotation.SuppressLint
import android.app.*
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.*
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

class SignalMonitorService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var wifiManager: WifiManager
    private var bleScanner: BluetoothLeScanner? = null

    companion object {
        val baselineDeviceIds = ConcurrentHashMap.newKeySet<String>()
        val currentScannedDevices = ConcurrentHashMap<String, SignalDevice>()
        val newlyDetectedAlerts = ConcurrentHashMap<String, SignalDevice>()
        @Volatile var isMonitoring = false

        const val CHANNEL_ID = "SignalMonitorChannel"
        const val ALERT_CHANNEL_ID = "NewSignalAlertChannel"
    }

    override fun onCreate() {
        super.onCreate()
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bleScanner = bluetoothManager.adapter?.bluetoothLeScanner
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, createServiceNotification())
        startScanning()
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startScanning() {
        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val address = result.device.address ?: return
                val name = result.device.name ?: "알 수 없는 BLE 기기"
                val rssi = result.rssi
                if (rssi < -85) return
                processDetectedDevice(SignalDevice(address, name, "BLE", rssi))
            }
        }

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            bleScanner?.startScan(null, scanSettings, scanCallback)
        } catch (_: Exception) {}

        serviceScope.launch {
            while (isActive) {
                scanWifi()
                delay(30_000L)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun scanWifi() {
        try {
            @Suppress("DEPRECATION")
            wifiManager.startScan()
            val results = wifiManager.scanResults
            for (ap in results) {
                if (ap.level < -85) continue
                val ssid = if (ap.SSID.isNullOrEmpty()) "숨겨진 Wi-Fi" else ap.SSID
                processDetectedDevice(SignalDevice(ap.BSSID, ssid, "Wi-Fi", ap.level))
            }
        } catch (_: Exception) {}
    }

    private fun processDetectedDevice(device: SignalDevice) {
        currentScannedDevices[device.id] = device
        if (isMonitoring && !baselineDeviceIds.contains(device.id)) {
            if (newlyDetectedAlerts.putIfAbsent(device.id, device) == null) {
                sendNewDeviceAlert(device)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun sendNewDeviceAlert(device: SignalDevice) {
        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("⚠️ 새로운 전자장치 신호 감지!")
            .setContentText("[${device.type}] ${device.name} (${device.id}) / ${device.rssi}dBm")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(device.id.hashCode(), notification)
    }

    private fun createServiceNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("전파 감시 작동 중")
            .setContentText("주변 Wi-Fi 및 블루투스 신호를 실시간 감시 중입니다.")
            .build()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID, "백그라운드 감시 상태", NotificationManager.IMPORTANCE_LOW
            )
            val alertChannel = NotificationChannel(
                ALERT_CHANNEL_ID, "새 전자장치 감지 경고", NotificationManager.IMPORTANCE_HIGH
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
            manager.createNotificationChannel(alertChannel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
