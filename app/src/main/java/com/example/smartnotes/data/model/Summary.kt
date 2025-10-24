package com.example.smartnotes.models

import com.google.firebase.Timestamp

data class Summary(
    val id: String = "",
    val title: String = "",
    val pageCount: Int = 0,
    val createdAt: Timestamp = Timestamp.now(),
    val userId: String = "",
    val folderId: String = ""
)