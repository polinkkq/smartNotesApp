package com.example.smartnotes.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.example.smartnotes.models.Summary
import com.example.smartnotes.models.User
import com.example.smartnotes.models.Folder
import com.example.smartnotes.models.Page
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import com.google.firebase.firestore.WriteBatch

class FirebaseRepository {

    val database: FirebaseFirestore = FirebaseFirestore.getInstance()

    // -------- USERS --------

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

    // -------- FOLDERS --------

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
                    val createdAt = document.getLong("createdAt") ?: System.currentTimeMillis()
                    val docUserId = document.getString("userId") ?: ""
                    val summaryCount = document.getLong("summaryCount")?.toInt() ?: 0

                    val folder = Folder(
                        id = document.id,
                        title = title,
                        createdAt = createdAt,
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


    suspend fun createFolder(userId: String, title: String): Result<Folder> {
        return try {
            val docRef = database.collection("folders").document()
            val now = System.currentTimeMillis()

            val folder = Folder(
                id = docRef.id,
                title = title,
                createdAt = now,
                userId = userId,
                summaryCount = 0
            )

            docRef.set(
                mapOf(
                    "id" to folder.id,
                    "title" to folder.title,
                    "createdAt" to folder.createdAt,
                    "userId" to folder.userId,
                    "summaryCount" to folder.summaryCount
                )
            ).await()

            Result.success(folder)
        } catch (e: Exception) {
            Timber.e(e, "Error in createFolder for userId=$userId, title=$title")
            Result.failure(e)
        }
    }

    suspend fun createFolderWithSummaries(
        userId: String,
        title: String,
        summaryIds: List<String>
    ): Result<Folder> {
        return try {
            val folderRef = database.collection("folders").document()
            val now = System.currentTimeMillis()

            val folder = Folder(
                id = folderRef.id,
                title = title,
                createdAt = now,
                userId = userId,
                summaryCount = summaryIds.size
            )

            database.runTransaction { transaction ->
                // 1. Создаём документ папки
                transaction.set(
                    folderRef,
                    mapOf(
                        "id" to folder.id,
                        "title" to folder.title,
                        "createdAt" to folder.createdAt,
                        "userId" to folder.userId,
                        "summaryCount" to folder.summaryCount
                    )
                )

                // 2. Обновляем все выбранные конспекты: прописываем folderId
                summaryIds.forEach { summaryId ->
                    val summaryRef = database.collection("summaries").document(summaryId)
                    transaction.update(
                        summaryRef,
                        mapOf(
                            "folderId" to folder.id,
                            "userId" to userId // на всякий случай
                        )
                    )
                }
            }.await()

            Result.success(folder)
        } catch (e: Exception) {
            Timber.e(e, "Error in createFolderWithSummaries for userId=$userId, title=$title")
            Result.failure(e)
        }
    }

    suspend fun addSummariesToFolder(
        userId: String,
        folderId: String,
        summaryIds: List<String>
    ): Result<Unit> {
        return try {
            if (summaryIds.isEmpty()) {
                return Result.success(Unit)
            }

            val folderRef = database.collection("folders").document(folderId)

            database.runBatch { batch ->
                summaryIds.forEach { summaryId ->
                    val summaryRef = database.collection("summaries").document(summaryId)
                    batch.update(
                        summaryRef,
                        mapOf(
                            "folderId" to folderId,
                            "userId" to userId // на всякий случай
                        )
                    )
                }

                batch.update(
                    folderRef,
                    "summaryCount",
                    FieldValue.increment(summaryIds.size.toLong())
                )
            }.await()

            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Error in addSummariesToFolder folderId=$folderId")
            Result.failure(e)
        }
    }

    // -------- SUMMARIES --------

    suspend fun getSummariesForUser(userId: String): Result<List<Summary>> {
        return try {
            val querySnapshot = database.collection("summaries")
                .whereEqualTo("userId", userId)
                .get()
                .await()

            val summaries = mutableListOf<Summary>()
            for (document in querySnapshot.documents) {
                try {
                    val title = document.getString("title") ?: "Без названия"
                    val pageCount = document.getLong("pageCount")?.toInt() ?: 0
                    val createdAt = document.getLong("createdAt") ?: System.currentTimeMillis()
                    val docUserId = document.getString("userId") ?: ""
                    val docFolderId = document.getString("folderId") ?: ""

                    val summary = Summary(
                        id = document.id,
                        title = title,
                        pageCount = pageCount,
                        createdAt = createdAt,
                        userId = docUserId,
                        folderId = docFolderId
                    )
                    summaries.add(summary)
                } catch (e: Exception) {
                    Timber.e(e, "Error processing summary document ${document.id}")
                }
            }

            val sorted = summaries.sortedByDescending { it.createdAt }

            Result.success(sorted)
        } catch (e: Exception) {
            Timber.e(e, "Error in getSummariesForUser")
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
                val title = document.getString("title") ?: "Без названия"
                val pageCount = document.getLong("pageCount")?.toInt() ?: 0
                val createdAt = document.getLong("createdAt") ?: System.currentTimeMillis()
                val docUserId = document.getString("userId") ?: ""
                val docFolderId = document.getString("folderId") ?: ""

                val summary = Summary(
                    id = document.id,
                    title = title,
                    pageCount = pageCount,
                    createdAt = createdAt,
                    userId = docUserId,
                    folderId = docFolderId
                )
                summaries.add(summary)
            }

            val sorted = summaries.sortedByDescending { it.createdAt }

            Result.success(sorted)
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
                    val title = document.getString("title") ?: "Без названия"
                    val pageCount = document.getLong("pageCount")?.toInt() ?: 0
                    val createdAt = document.getLong("createdAt") ?: System.currentTimeMillis()
                    val docUserId = document.getString("userId") ?: ""
                    val docFolderId = document.getString("folderId") ?: ""

                    if (docFolderId == folderId && docUserId == userId) {
                        val summary = Summary(
                            id = document.id,
                            title = title,
                            pageCount = pageCount,
                            createdAt = createdAt,
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
            val newSummary = summary.copy(id = docRef.id)
            docRef.set(newSummary).await()
            Result.success(docRef.id)
        } catch (e: Exception) {
            Timber.e(e, "Failed to create summary")
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

    // -------- PAGES --------

    suspend fun createPage(page: Page): Result<String> {
        return try {
            val docRef = database.collection("pages").document()
            val newPage = page.copy(id = docRef.id)
            docRef.set(newPage).await()
            Result.success(docRef.id)
        } catch (e: Exception) {
            Timber.e(e, "Failed to create page")
            Result.failure(e)
        }
    }

    suspend fun getPagesBySummaryId(summaryId: String): Result<List<Page>> {
        return try {
            Timber.d("Querying pages for summaryId: $summaryId")

            val querySnapshot = database.collection("pages")
                .whereEqualTo("summaryId", summaryId)
                .get()
                .await()

            Timber.d("Found ${querySnapshot.documents.size} pages for summaryId: $summaryId")

            val pages = mutableListOf<Page>()
            for (document in querySnapshot.documents) {
                val id = document.getString("id") ?: document.id
                val docSummaryId = document.getString("summaryId")
                val pageNumber = document.getLong("pageNumber")?.toInt() ?: 0
                val imageUrl = document.getString("imageUrl") ?: ""
                val recognizedText = document.getString("recognizedText") ?: ""
                val createdAt = document.getLong("createdAt") ?: System.currentTimeMillis()

                if (docSummaryId != summaryId) {
                    Timber.w(
                        "Page ${document.id} has summaryId: $docSummaryId, " +
                                "but expected: $summaryId"
                    )
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

    suspend fun savePagesForSummary(
        summaryId: String,
        pagesText: List<String>,
        createdAt: Long = System.currentTimeMillis()
    ): Result<Unit> {
        return try {
            if (pagesText.isEmpty()) {
                // просто ставим 0 страниц
                database.collection("summaries").document(summaryId)
                    .update("pageCount", 0)
                    .await()
                return Result.success(Unit)
            }

            database.runBatch { batch: WriteBatch ->
                pagesText.forEachIndexed { index, text ->
                    val docRef = database.collection("pages").document()
                    batch.set(
                        docRef,
                        mapOf(
                            "id" to docRef.id,
                            "summaryId" to summaryId,
                            "pageNumber" to (index + 1),
                            "imageUrl" to "", // больше не привязываем страницу к конкретной фотке
                            "recognizedText" to text,
                            "createdAt" to createdAt
                        )
                    )
                }

                batch.update(
                    database.collection("summaries").document(summaryId),
                    "pageCount",
                    pagesText.size
                )
            }.await()

            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Error in savePagesForSummary summaryId=$summaryId")
            Result.failure(e)
        }
    }

    suspend fun deleteSummary(userId: String, summaryId: String): Result<Unit> {
        return try {
            val summaryRef = database.collection("summaries").document(summaryId)
            val summarySnap = summaryRef.get().await()

            if (!summarySnap.exists()) {
                return Result.success(Unit) // уже удалено
            }

            val docUserId = summarySnap.getString("userId") ?: ""
            if (docUserId.isNotBlank() && docUserId != userId) {
                return Result.failure(IllegalStateException("Нет прав на удаление этого конспекта"))
            }

            val folderId = summarySnap.getString("folderId") ?: ""

            // 1) удаляем pages этого summary
            val pagesSnap = database.collection("pages")
                .whereEqualTo("summaryId", summaryId)
                .get()
                .await()

            // Firestore batch лимит 500 операций → удаляем чанками
            val pageDocs = pagesSnap.documents
            val chunks = pageDocs.chunked(450)

            for (chunk in chunks) {
                database.runBatch { batch ->
                    chunk.forEach { d -> batch.delete(d.reference) }
                }.await()
            }

            // 2) удаляем сам summary и уменьшаем счетчик в папке (если был)
            database.runBatch { batch ->
                batch.delete(summaryRef)

                if (folderId.isNotBlank()) {
                    val folderRef = database.collection("folders").document(folderId)
                    batch.update(folderRef, "summaryCount", FieldValue.increment(-1))
                }
            }.await()

            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Error in deleteSummary summaryId=$summaryId")
            Result.failure(e)
        }
    }

    suspend fun deleteFolderMoveSummariesToUnsorted(userId: String, folderId: String): Result<Unit> {
        return try {
            val folderRef = database.collection("folders").document(folderId)
            val folderSnap = folderRef.get().await()

            if (!folderSnap.exists()) return Result.success(Unit)

            val docUserId = folderSnap.getString("userId") ?: ""
            if (docUserId.isNotBlank() && docUserId != userId) {
                return Result.failure(IllegalStateException("Нет прав на удаление этой папки"))
            }

            // 1) находим все summaries в папке
            val summariesSnap = database.collection("summaries")
                .whereEqualTo("userId", userId)
                .whereEqualTo("folderId", folderId)
                .get()
                .await()

            val summaryDocs = summariesSnap.documents
            val chunks = summaryDocs.chunked(450)

            // 2) переносим их в несортированные (folderId = "")
            for (chunk in chunks) {
                database.runBatch { batch ->
                    chunk.forEach { doc ->
                        batch.update(doc.reference, "folderId", "")
                    }
                }.await()
            }

            // 3) удаляем папку
            folderRef.delete().await()

            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Error in deleteFolderMoveSummariesToUnsorted folderId=$folderId")
            Result.failure(e)
        }
    }

    suspend fun deleteFolderWithSummaries(userId: String, folderId: String): Result<Unit> {
        return try {
            val folderRef = database.collection("folders").document(folderId)
            val folderSnap = folderRef.get().await()

            if (!folderSnap.exists()) return Result.success(Unit)

            val docUserId = folderSnap.getString("userId") ?: ""
            if (docUserId.isNotBlank() && docUserId != userId) {
                return Result.failure(IllegalStateException("Нет прав на удаление этой папки"))
            }

            // 1) получаем summaries папки
            val summariesSnap = database.collection("summaries")
                .whereEqualTo("userId", userId)
                .whereEqualTo("folderId", folderId)
                .get()
                .await()

            val summaryIds = summariesSnap.documents.map { it.id }

            // 2) удаляем каждый summary каскадно (pages + summary)
            for (sid in summaryIds) {
                // тут folder summaryCount уменьшается, но папку мы удалим — это ок
                deleteSummary(userId, sid)
            }

            // 3) удаляем папку
            folderRef.delete().await()

            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Error in deleteFolderWithSummaries folderId=$folderId")
            Result.failure(e)
        }
    }
    suspend fun removeSummaryFromFolder(userId: String, summaryId: String): Result<Unit> {
        return try {
            val summaryRef = database.collection("summaries").document(summaryId)
            val summarySnap = summaryRef.get().await()

            if (!summarySnap.exists()) {
                return Result.success(Unit) // уже нет — считаем ок
            }

            val docUserId = summarySnap.getString("userId") ?: ""
            if (docUserId.isNotBlank() && docUserId != userId) {
                return Result.failure(IllegalStateException("Нет прав на изменение этого конспекта"))
            }

            val oldFolderId = summarySnap.getString("folderId") ?: ""
            if (oldFolderId.isBlank()) {
                // уже несортированный
                return Result.success(Unit)
            }

            val folderRef = database.collection("folders").document(oldFolderId)

            database.runBatch { batch ->
                batch.update(summaryRef, "folderId", "")
                batch.update(folderRef, "summaryCount", FieldValue.increment(-1))
            }.await()

            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Error in removeSummaryFromFolder summaryId=$summaryId")
            Result.failure(e)
        }
    }
    suspend fun deleteUserData(userId: String): Result<Unit> {
        return try {
            // 1) Удаляем все summaries пользователя + их pages
            val summariesSnap = database.collection("summaries")
                .whereEqualTo("userId", userId)
                .get().await()

            for (summaryDoc in summariesSnap.documents) {
                val summaryId = summaryDoc.id

                // удалить pages этого summary (чанками)
                val pagesSnap = database.collection("pages")
                    .whereEqualTo("summaryId", summaryId)
                    .get().await()

                val pageDocs = pagesSnap.documents.chunked(450)
                for (chunk in pageDocs) {
                    database.runBatch { batch ->
                        chunk.forEach { batch.delete(it.reference) }
                    }.await()
                }

                // удалить summary
                database.collection("summaries").document(summaryId).delete().await()
            }

            // 2) Удаляем все folders пользователя
            val foldersSnap = database.collection("folders")
                .whereEqualTo("userId", userId)
                .get().await()

            val folderDocs = foldersSnap.documents.chunked(450)
            for (chunk in folderDocs) {
                database.runBatch { batch ->
                    chunk.forEach { batch.delete(it.reference) }
                }.await()
            }

            // 3) Удаляем документ пользователя
            database.collection("users").document(userId).delete().await()

            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Error in deleteUserData userId=$userId")
            Result.failure(e)
        }
    }


    private suspend fun deleteUserSummariesAndFolders(userId: String) {
        try {
            // Удаляем все папки и конспекты пользователя
            val foldersSnapshot = database.collection("folders").whereEqualTo("userId", userId).get().await()
            for (folder in foldersSnapshot.documents) {
                // Удаляем конспекты из папки
                val summariesSnapshot = database.collection("summaries").whereEqualTo("folderId", folder.id).get().await()
                for (summary in summariesSnapshot.documents) {
                    database.collection("pages").whereEqualTo("summaryId", summary.id).get().await().documents.forEach {
                        database.collection("pages").document(it.id).delete().await()
                    }
                    database.collection("summaries").document(summary.id).delete().await()
                }
                database.collection("folders").document(folder.id).delete().await()
            }
        } catch (e: Exception) {
            Timber.e(e, "Error deleting user's summaries and folders")
        }
    }
}



