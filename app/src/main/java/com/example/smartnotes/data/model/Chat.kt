package com.example.smartnotes.models

import com.google.firebase.firestore.PropertyName

data class Chat(
    @PropertyName("id") val id: String = "",
    @PropertyName("summary_id") val summaryId: String? = null,
    @PropertyName("started_at") val startedAt: Long = System.currentTimeMillis(),
    @PropertyName("updated_at") val updatedAt: Long = System.currentTimeMillis()
) {
    constructor() : this("", "")
}