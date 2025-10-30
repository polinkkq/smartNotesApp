package com.example.smartnotes.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.example.smartnotes.models.Summary
import com.example.smartnotes.models.User
import com.example.smartnotes.models.Folder
import com.example.smartnotes.models.Page
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue

class FirebaseRepository {


    val database = FirebaseFirestore.getInstance()


    suspend fun createUser(user: User): Result<String> {
        return try {
            database.collection("users").document(user.id).set(user).await()
            Result.success(user.id)
        } catch (e: Exception) {
            Timber.e(e, "Failed to create user in Firestore")
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
            Timber.e(e, "Failed to get user by email: $email")
            Result.failure(e)
        }
    }

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
            Timber.e(e, "Failed to get user by ID: $userId")
            Result.failure(e)
        }
    }

    suspend fun getUserFolders(userId: String): Result<List<Folder>> {
        return try {
            Timber.d("Getting folders for user: $userId")

            val querySnapshot = database.collection("folders")
                .whereEqualTo("userId", userId)
                .get()
                .await()

            Timber.d("Folders query completed, found ${querySnapshot.documents.size} documents")

            val folders = mutableListOf<Folder>()
            for (document in querySnapshot.documents) {
                try {
                    val title = document.getString("title") ?: "Без названия"
                    val createdAt = document.getTimestamp("createdAt")
                    val docUserId = document.getString("userId") ?: ""
                    val summaryCount = document.getLong("summaryCount")?.toInt() ?: 0

                    val folder = Folder(
                        id = document.id,
                        title = title,
                        createdAt = createdAt ?: Timestamp.now(),
                        userId = docUserId,
                        summaryCount = summaryCount
                    )
                    folders.add(folder)
                } catch (e: Exception) {
                    Timber.e(e, "Error processing folder document ${document.id}")
                }
            }

            Timber.d("Final folders count: ${folders.size}")
            Result.success(folders)
        } catch (e: Exception) {
            Timber.e(e, "Error in getUserFolders")
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
                val title = document.getString("title")
                val pageCount = document.getLong("pageCount")?.toInt() ?: 0
                val createdAt = document.getTimestamp("createdAt")
                val docUserId = document.getString("userId") ?: ""
                val docFolderId = document.getString("folderId") ?: ""

                val summary = Summary(
                    id = document.id,
                    title = title,
                    pageCount = pageCount,
                    createdAt = createdAt ?: Timestamp.now(),
                    userId = docUserId,
                    folderId = docFolderId
                )
                summaries.add(summary)
            }
            Result.success(summaries)
        } catch (e: Exception) {
            Timber.e(e, "Error in getUnsortedSummaries")
            Result.failure(e)
        }
    }

    suspend fun getSummariesByFolderId(userId: String, folderId: String): Result<List<Summary>> {
        return try {
            Timber.d("Getting summaries for user: $userId, folder: $folderId")

            val querySnapshot = database.collection("summaries")
                .whereEqualTo("userId", userId)
                .whereEqualTo("folderId", folderId)
                .get()
                .await()

            Timber.d("Query completed, found ${querySnapshot.documents.size} documents")

            val summaries = mutableListOf<Summary>()
            for (document in querySnapshot.documents) {
                try {
                    val title = document.getString("title")
                    val pageCount = document.getLong("pageCount")?.toInt() ?: 0
                    val createdAt = document.getTimestamp("createdAt")
                    val docUserId = document.getString("userId") ?: ""
                    val docFolderId = document.getString("folderId") ?: ""

                    if (docFolderId == folderId && docUserId == userId) {
                        val summary = Summary(
                            id = document.id,
                            title = title,
                            pageCount = pageCount,
                            createdAt = createdAt ?: Timestamp.now(),
                            userId = docUserId,
                            folderId = docFolderId
                        )
                        summaries.add(summary)
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error processing document ${document.id}")
                }
            }

            Timber.d("Final summaries count: ${summaries.size}")
            Result.success(summaries)
        } catch (e: Exception) {
            Timber.e(e, "Error in getSummariesByFolderId")
            Result.failure(e)
        }
    }


    suspend fun createSummary(summary: Summary): Result<String> {
        return try {
            val docRef = database.collection("summaries").document()
            val newSummary = summary.copy(id = docRef.id) // копируем с новым ID
            docRef.set(newSummary).await()
            Result.success(docRef.id)
        } catch (e: Exception) {
            Timber.e(e, "Failed to create summary")
            Result.failure(e)
        }
    }

    suspend fun createPage(page: Page): Result<String> {
        return try {
            val docRef = database.collection("pages").document()
            val newPage = page.copy(id = docRef.id) // копируем с новым ID
            docRef.set(newPage).await()
            Result.success(docRef.id)
        } catch (e: Exception) {
            Timber.e(e, "Failed to create page")
            Result.failure(e)
        }
    }

    suspend fun updateSummaryPageCount(summaryId: String, newCount: Int) {
        try {
            database.collection("summaries").document(summaryId)
                .update("pageCount", newCount).await()
        } catch (e: Exception) {
            Timber.e(e, "Failed to update page count for summary: $summaryId")
        }
    }

    suspend fun getPagesBySummaryId(summaryId: String): Result<List<Page>> {
        return try {
            Timber.d("Querying pages for summaryId: $summaryId") // <-- Обнови лог

            val querySnapshot = database.collection("pages")
                .whereEqualTo("summaryId", summaryId) // <-- Измени на summaryId
                .get()
                .await()

            Timber.d("Found ${querySnapshot.documents.size} pages for summaryId: $summaryId") // <-- Обнови лог

            val pages = mutableListOf<Page>()
            for (document in querySnapshot.documents) {
                val id = document.getString("id") ?: document.id
                val docSummaryId = document.getString("summaryId") // <-- Измени на summaryId
                val pageNumber = document.getLong("pageNumber")?.toInt() ?: 0
                val imageUrl = document.getString("imageUrl") ?: ""
                val recognizedText = document.getString("recognizedText") ?: ""
                val createdAt = document.getLong("createdAt") ?: System.currentTimeMillis()

                if (docSummaryId != summaryId) {
                    Timber.w("Page ${document.id} has summaryId: $docSummaryId, but expected: $summaryId")
                }

                val page = Page(
                    id = id,
                    summaryId = docSummaryId ?: "",
                    pageNumber = pageNumber,
                    imageUrl = imageUrl,
                    recognizedText = recognizedText,
                    createdAt = createdAt
                )
                pages.add(page)
            }
            Result.success(pages)
        } catch (e: Exception) {
            Timber.e(e, "Error in getPagesBySummaryId for summaryId: $summaryId")
            Result.failure(e)
        }
    }
}