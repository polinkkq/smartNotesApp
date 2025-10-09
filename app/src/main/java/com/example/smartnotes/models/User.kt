package com.example.smartnotes.models

import com.google.firebase.firestore.PropertyName

data class User(
    @PropertyName("id") val id: String = "",
    @PropertyName("firstName") val firstName: String = "",
    @PropertyName("lastName") val lastName: String = "",
    @PropertyName("email") val email: String = "",
    @PropertyName("role") val role: String = "student",
    @PropertyName("birthDate") val birthDate: String? = null,
    @PropertyName("phone") val phone: String? = null,
    @PropertyName("createdAt") val createdAt: Long = System.currentTimeMillis(),
    @PropertyName("updatedAt") val updatedAt: Long = System.currentTimeMillis()
) {
    // Пустой конструктор для Firebase
    constructor() : this("", "", "", "", "student", null, null)

    companion object {
        fun fromUserRole(
            firstName: String,
            lastName: String,
            email: String,
            role: UserRole,
            birthDate: String? = null,
            phone: String? = null
        ): User {
            return User(
                firstName = firstName,
                lastName = lastName,
                email = email,
                role = role.name.lowercase(),
                birthDate = birthDate,
                phone = phone
            )
        }
    }
}