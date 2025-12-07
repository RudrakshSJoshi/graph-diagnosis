package com.example.medicaldocai.utils


import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

// Changed to internal/public so it can be accessed by UI components
fun formatTimestamp(timestamp: Long): String {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = timestamp
    val format = SimpleDateFormat("HH:mm", Locale.getDefault())
    return format.format(calendar.time)
}