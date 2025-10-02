package com.example.smartnotes.models

import com.google.firebase.firestore.PropertyName

data class Page(
    @PropertyName("id") val id: String = "",
    @PropertyName("summary_id") val summaryId: String = "",
    @PropertyName("page_number") val pageNumber: Int = 0,
    @PropertyName("image_url") val imageUrl: String = "",
    @PropertyName("recognized_text") val recognizedText: String = "",
    @PropertyName("created_at") val createdAt: Long = System.currentTimeMillis()
) {
    constructor() : this("", "", 0, "", "")
}