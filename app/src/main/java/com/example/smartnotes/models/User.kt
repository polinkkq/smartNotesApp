package com.example.smartnotes.models

import com.google.firebase.Timestamp

data class User(
    val id: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val role: String = "",
    val createdAt: Timestamp = Timestamp.now()
)