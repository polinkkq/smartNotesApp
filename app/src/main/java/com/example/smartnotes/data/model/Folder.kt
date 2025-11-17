package com.example.smartnotes.models

import com.google.firebase.firestore.PropertyName

data class Folder(
    @PropertyName("id") val id: String = "",
    @PropertyName("title") val title: String = "",
    @PropertyName("createdAt") val createdAt: Long = System.currentTimeMillis(),
    @PropertyName("userId") val userId: String = "",
    @PropertyName("summaryCount") val summaryCount: Int = 0
) {
    constructor() : this("", "", System.currentTimeMillis(), "", 0)
}
