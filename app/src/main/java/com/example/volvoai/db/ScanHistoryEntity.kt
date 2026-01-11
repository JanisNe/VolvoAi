package com.example.volvoai.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scan_history")
data class ScanHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val partName: String,
    val manufacturerPartId: String,
    val priceEurText: String,
    val buyLink: String,
    val scannedAtMillis: Long
)
