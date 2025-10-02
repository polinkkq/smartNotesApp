package com.example.smartnotes.models

import com.google.firebase.firestore.PropertyName

data class Folder(
    @PropertyName("id") val id: String = "",
    @PropertyName("user_id") val userId: String = "",
    @PropertyName("title") val title: String = "",
    @PropertyName("created_at") val createdAt: Long = System.currentTimeMillis()
) {
    constructor() : this("", "", "")
}