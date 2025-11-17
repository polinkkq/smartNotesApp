package com.example.smartnotes.models

import com.google.firebase.firestore.PropertyName

data class Course(
    @PropertyName("id") val id: String = "",
    @PropertyName("title") val title: String = "",
    @PropertyName("description") val description: String = "",
    @PropertyName("teacherId") val teacherId: String = "",
    @PropertyName("createdAt") val createdAt: Long = System.currentTimeMillis()
) {
    constructor() : this("", "", "", "")
}
