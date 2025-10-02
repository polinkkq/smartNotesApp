package com.example.smartnotes.models

import com.google.firebase.firestore.PropertyName

data class Summary(
    @PropertyName("id") val id: String = "",
    @PropertyName("user_id") val userId: String = "",
    @PropertyName("folder_id") val folderId: String? = null,
    @PropertyName("title") val title: String = "",
    @PropertyName("course_id") val courseId: String? = null,
    @PropertyName("created_at") val createdAt: Long = System.currentTimeMillis()
) {
    constructor() : this("", "", null, "", null)
}