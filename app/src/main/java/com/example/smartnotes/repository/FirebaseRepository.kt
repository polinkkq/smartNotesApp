package com.example.smartnotes.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObject
import com.example.smartnotes.models.User
import kotlinx.coroutines.tasks.await

class FirebaseRepository {
    private val db = FirebaseFirestore.getInstance()

    private val usersCollection = db.collection("users")

    suspend fun createUser(user: User): Result<String> {
        return try {
            val documentRef = if (user.id.isNotEmpty()) {
                usersCollection.document(user.id)
            } else {
                usersCollection.document()
            }

            val userWithId = user.copy(id = documentRef.id)
            documentRef.set(userWithId).await()
            Result.success(documentRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getUser(userId: String): Result<User?> {
        return try {
            val document = usersCollection.document(userId).get().await()
            if (document.exists()) {
                val user = document.toObject<User>()
                Result.success(user)
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getUserByEmail(email: String): Result<User?> {
        return try {
            val query = usersCollection.whereEqualTo("email", email).get().await()
            val user = if (!query.isEmpty) {
                query.documents.first().toObject<User>()
            } else {
                null
            }
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateUser(userId: String, updates: Map<String, Any>): Result<Boolean> {
        return try {
            usersCollection.document(userId).update(updates).await()
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}