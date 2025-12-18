package com.example.smartnotes.models

data class User(
    val id: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    constructor() : this("", "", "", "", System.currentTimeMillis())
}
