package com.example.smartnotes.models

data class User(
    val id: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val role: UserRole? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    constructor() : this("", "", "", "", null, System.currentTimeMillis())
}
