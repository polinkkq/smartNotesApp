package com.example.smartnotes.models

data class ApiResponse (
    val message: String? = null,
    val error: String? = null,
    val status: String,
    val user_id: Int? = null,
    val name: String? = null,
    val email: String? = null,
    val role: String? = null
)