package com.example.signaldetector

data class SignalDevice(
    val id: String,
    val name: String,
    val type: String,
    val rssi: Int,
    val timestamp: Long = System.currentTimeMillis()
)
