package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val date: String, // format: yyyy-MM-dd
    val time: String, // format: HH:mm
    val isAlarmEnabled: Boolean = false,
    val status: String = "PENDING" // PENDING, COMPLETED, FAILED
)
