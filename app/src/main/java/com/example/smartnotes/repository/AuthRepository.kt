package com.example.smartnotes.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await
import com.example.smartnotes.models.User

class AuthRepository {
    private val auth: FirebaseAuth = Firebase.auth
    private val firebaseRepository = FirebaseRepository()

    suspend fun registerUser(
        email: String,
        password: String,
        firstName: String,
        lastName: String,
        role: String,
        birthDate: String? = null,
        phone: String? = null
    ): Result<String> {
        return try {
            // 1. Создаем пользователя в Firebase Authentication
            val authResult = auth.createUserWithEmailAndPassword(email, password).await()
            val user = authResult.user

            if (user != null) {
                // 2. Обновляем display name
                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setDisplayName("$firstName $lastName")
                    .build()
                user.updateProfile(profileUpdates).await()

                // 3. Создаем запись в Firestore
                val firestoreUser = User(
                    id = user.uid,
                    firstName = firstName,
                    lastName = lastName,
                    email = email,
                    role = role,
                    birthDate = birthDate,
                    phone = phone
                )

                val result = firebaseRepository.createUser(firestoreUser)

                if (result.isSuccess) {
                    Result.success(user.uid)
                } else {
                    user.delete().await()
                    Result.failure(result.exceptionOrNull() ?: Exception("Failed to create user in Firestore"))
                }
            } else {
                Result.failure(Exception("User creation failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun checkEmailExists(email: String): Boolean {
        return try {
            val user = firebaseRepository.getUserByEmail(email)
            user.getOrNull() != null
        } catch (e: Exception) {
            false
        }
    }

    suspend fun loginUser(email: String, password: String): Result<String> {
        return try {
            val authResult = auth.signInWithEmailAndPassword(email, password).await()
            val user = authResult.user

            if (user != null) {
                Result.success(user.uid)
            } else {
                Result.failure(Exception("Login failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun logoutUser() {
        auth.signOut()
    }

    fun getCurrentUser() = auth.currentUser
}