package com.example.smartnotes.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.example.smartnotes.models.Summary
import com.example.smartnotes.models.User
import com.example.smartnotes.models.Folder
import kotlinx.coroutines.tasks.await
import java.lang.Exception

class FirebaseRepository {

    private val database = FirebaseFirestore.getInstance()

    // Добавляем недостающие методы для AuthRepository
    suspend fun createUser(user: User): Result<String> {
        return try {
            database.collection("users").document(user.id).set(user).await()
            Result.success(user.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getUserByEmail(email: String): Result<User?> {
        return try {
            val querySnapshot = database.collection("users")
                .whereEqualTo("email", email)
                .limit(1)
                .get()
                .await()

            if (querySnapshot.documents.isNotEmpty()) {
                val document = querySnapshot.documents[0]
                val user = document.toObject(User::class.java)
                Result.success(user)
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Метод для получения пользователя по ID
    suspend fun getUser(userId: String): Result<User?> {
        return try {
            val document = database.collection("users").document(userId).get().await()
            if (document.exists()) {
                val user = document.toObject(User::class.java)
                Result.success(user)
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Методы для работы с папками и конспектами
    suspend fun getUserFolders(userId: String): Result<List<Folder>> {
        return try {
            println("DEBUG: Getting folders for user: $userId")

            val querySnapshot = database.collection("folders")
                .whereEqualTo("userId", userId)
                .get()
                .await()

            println("DEBUG: Folders query completed, found ${querySnapshot.documents.size} documents")

            val folders = mutableListOf<Folder>()
            for (document in querySnapshot.documents) {
                try {
                    val title = document.getString("title") ?: ""
                    val createdAt = document.getTimestamp("createdAt")
                    val docUserId = document.getString("userId") ?: ""
                    val summaryCount = document.getLong("summaryCount")?.toInt() ?: 0

                    println("DEBUG: Processing folder: $title, userId: $docUserId")

                    val folder = Folder(
                        id = document.id,
                        title = title,
                        createdAt = createdAt ?: com.google.firebase.Timestamp.now(),
                        userId = docUserId,
                        summaryCount = summaryCount
                    )
                    folders.add(folder)
                    println("DEBUG: Added folder: ${folder.title}")
                } catch (e: Exception) {
                    println("DEBUG: Error processing folder document ${document.id}: ${e.message}")
                }
            }

            println("DEBUG: Final folders count: ${folders.size}")
            Result.success(folders)
        } catch (e: Exception) {
            println("DEBUG: Error in getUserFolders: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun getUnsortedSummaries(userId: String): Result<List<Summary>> {
        return try {
            val querySnapshot = database.collection("summaries")
                .whereEqualTo("userId", userId)
                .whereEqualTo("folderId", "")
                .get()
                .await()

            val summaries = mutableListOf<Summary>()
            for (document in querySnapshot.documents) {
                val title = document.getString("title") ?: ""
                val pageCount = document.getLong("pageCount")?.toInt() ?: 0
                val createdAt = document.getTimestamp("createdAt")
                val docUserId = document.getString("userId") ?: ""
                val docFolderId = document.getString("folderId") ?: ""

                val summary = Summary(
                    id = document.id,
                    title = title,
                    pageCount = pageCount,
                    createdAt = createdAt ?: com.google.firebase.Timestamp.now(),
                    userId = docUserId,
                    folderId = docFolderId
                )
                summaries.add(summary)
            }
            Result.success(summaries)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getSummariesByFolderId(userId: String, folderId: String): Result<List<Summary>> {
        return try {
            println("DEBUG: Getting summaries for user: $userId, folder: $folderId")

            val querySnapshot = database.collection("summaries")
                .whereEqualTo("userId", userId)
                .whereEqualTo("folderId", folderId)
                .get()
                .await()

            println("DEBUG: Query completed, found ${querySnapshot.documents.size} documents")

            val summaries = mutableListOf<Summary>()
            for (document in querySnapshot.documents) {
                try {
                    val title = document.getString("title") ?: ""
                    val pageCount = document.getLong("pageCount")?.toInt() ?: 0
                    val createdAt = document.getTimestamp("createdAt")
                    val docUserId = document.getString("userId") ?: ""
                    val docFolderId = document.getString("folderId") ?: ""

                    println("DEBUG: Processing document: $title, folderId: $docFolderId, userId: $docUserId")

                    if (docFolderId == folderId && docUserId == userId) {
                        val summary = Summary(
                            id = document.id,
                            title = title,
                            pageCount = pageCount,
                            createdAt = createdAt ?: com.google.firebase.Timestamp.now(),
                            userId = docUserId,
                            folderId = docFolderId
                        )
                        summaries.add(summary)
                        println("DEBUG: Added summary: ${summary.title}")
                    }
                } catch (e: Exception) {
                    println("DEBUG: Error processing document ${document.id}: ${e.message}")
                }
            }

            println("DEBUG: Final summaries count: ${summaries.size}")
            Result.success(summaries)
        } catch (e: Exception) {
            println("DEBUG: Error in getSummariesByFolderId: ${e.message}")
            Result.failure(e)
        }
    }
}