package com.example.lab08

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "description") val description: String,
    @ColumnInfo(name = "is_completed") val isCompleted: Boolean = false,
    @ColumnInfo(name = "priority") val priority: String = "Media",
    @ColumnInfo(name = "category") val category: String = "General",
    @ColumnInfo(name = "repeat_interval") val repeatInterval: String = "Ninguna",
    @ColumnInfo(name = "is_synced") val isSynced: Boolean = false
)
