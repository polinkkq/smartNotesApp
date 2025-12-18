package com.example.smartnotes.repository

import com.example.smartnotes.models.Folder
import com.example.smartnotes.models.Page
import com.example.smartnotes.models.Summary

class FirebaseRepositoryAdapter(
    private val firebaseRepository: FirebaseRepository = FirebaseRepository()
) : NotesRepository {

    override suspend fun getUserFolders(userId: String): Result<List<Folder>> {
        return firebaseRepository.getUserFolders(userId)
    }

    override suspend fun createFolder(userId: String, title: String): Result<Folder> {
        return firebaseRepository.createFolder(userId, title)
    }

    override suspend fun deleteFolderMoveSummariesToUnsorted(userId: String, folderId: String): Result<Unit> {
        return firebaseRepository.deleteFolderMoveSummariesToUnsorted(userId, folderId)
    }

    override suspend fun deleteFolderWithSummaries(userId: String, folderId: String): Result<Unit> {
        return firebaseRepository.deleteFolderWithSummaries(userId, folderId)
    }

    override suspend fun getUnsortedSummaries(userId: String): Result<List<Summary>> {
        return firebaseRepository.getUnsortedSummaries(userId)
    }

    override suspend fun getSummariesByFolderId(userId: String, folderId: String): Result<List<Summary>> {
        return firebaseRepository.getSummariesByFolderId(userId, folderId)
    }

    override suspend fun createSummary(summary: Summary): Result<String> {
        return firebaseRepository.createSummary(summary)
    }

    override suspend fun deleteSummary(userId: String, summaryId: String): Result<Unit> {
        return firebaseRepository.deleteSummary(userId, summaryId)
    }

    override suspend fun updateSummaryPageCount(summaryId: String, newCount: Int) {
        firebaseRepository.updateSummaryPageCount(summaryId, newCount)
    }

    override suspend fun createPage(page: Page): Result<String> {
        return firebaseRepository.createPage(page)
    }

    override suspend fun getPagesBySummaryId(summaryId: String): Result<List<Page>> {
        return firebaseRepository.getPagesBySummaryId(summaryId)
    }

    override suspend fun addSummariesToFolder(userId: String, folderId: String, summaryIds: List<String>): Result<Unit> {
        return firebaseRepository.addSummariesToFolder(userId, folderId, summaryIds)
    }

    // ✅ НОВОЕ: убрать конспект из папки (в "Несортированные")
    override suspend fun removeSummaryFromFolder(userId: String, summaryId: String): Result<Unit> {
        return firebaseRepository.removeSummaryFromFolder(userId, summaryId)
    }
}
