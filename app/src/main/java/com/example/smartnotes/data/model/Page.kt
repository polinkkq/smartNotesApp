package com.example.smartnotes.models

import com.google.firebase.firestore.PropertyName

data class Page(
    @PropertyName("id") val id: String = "",
    @PropertyName("summaryId") val summaryId: String = "",
    @PropertyName("pageNumber") val pageNumber: Int = 0,
    @PropertyName("imageUrl") val imageUrl: String = "",
    @PropertyName("recognizedText") val recognizedText: String = "",
    @PropertyName("createdAt") val createdAt: Long = System.currentTimeMillis()
) {
    // Конструктор для Firebase
    constructor() : this("", "", 0, "", "", System.currentTimeMillis())
}
