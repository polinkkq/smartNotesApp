package com.example.smartnotes.models

import com.google.firebase.firestore.PropertyName

data class Course(
    @PropertyName("id") val id: String = "",
    @PropertyName("title") val title: String = "",
    @PropertyName("description") val description: String = "",
    @PropertyName("teacher_id") val teacherId: String = "",
    @PropertyName("created_at") val createdAt: Long = System.currentTimeMillis()
) {
    constructor() : this("", "", "", "")
}