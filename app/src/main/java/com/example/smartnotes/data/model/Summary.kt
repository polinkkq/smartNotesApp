package com.example.smartnotes.models

import com.google.firebase.Timestamp

data class Summary(
    val id: String,
    val title: String?,
    val pageCount: Int,
    val createdAt: Timestamp,
    val userId: String,
    val folderId: String?
)