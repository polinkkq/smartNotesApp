package com.example.smartnotes.models

import com.google.firebase.Timestamp

data class Folder(
    val id: String = "",
    val title: String = "",
    val createdAt: Timestamp = Timestamp.now(),
    val userId: String = "",
    val summaryCount: Int = 0
)