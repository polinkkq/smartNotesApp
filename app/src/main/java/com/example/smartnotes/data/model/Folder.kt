package com.example.smartnotes.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName

data class Folder(
    val id: String = "",
    val title: String = "",
    @PropertyName("createdAt")
    val createdAt: Timestamp = Timestamp.now(),
    val userId: String = "",
    @PropertyName("summaryCount")
    val summaryCount: Int = 0
)