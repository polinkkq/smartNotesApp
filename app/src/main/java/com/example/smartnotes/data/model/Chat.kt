package com.example.smartnotes.models

import com.google.firebase.firestore.PropertyName

data class Chat(
    @PropertyName("id") val id: String = "",
    @PropertyName("summaryId") val summaryId: String? = null,
    @PropertyName("startedAt") val startedAt: Long = System.currentTimeMillis(),
    @PropertyName("updatedAt") val updatedAt: Long = System.currentTimeMillis()
) {
    constructor() : this("", "")
}
