package com.example.smartnotes.models

import com.google.firebase.firestore.PropertyName

data class Page(
    @PropertyName("id") val id: String = "",
    @PropertyName("summaryId") val summaryId: String, // <-- Измени на summaryId
    @PropertyName("pageNumber") val pageNumber: Int,
    @PropertyName("imageUrl") val imageUrl: String,
    @PropertyName("recognizedText") val recognizedText: String,
    @PropertyName("createdAt") val createdAt: Long
) {
    // Конструктор для Firebase
    constructor() : this("", "", 0, "", "", 0)}