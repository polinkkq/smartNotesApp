package com.example.smartnotes.models

import com.google.firebase.firestore.PropertyName

data class Summary(
    @PropertyName("id") val id: String = "",
    @PropertyName("title") val title: String = "",
    @PropertyName("pageCount") val pageCount: Int = 0,
    @PropertyName("createdAt") val createdAt: Long = System.currentTimeMillis(),
    @PropertyName("userId") val userId: String = "",
    @PropertyName("folderId") val folderId: String = "" // "" -> Несортированные
) {
    constructor() : this("", "", 0, System.currentTimeMillis(), "", "")
}
