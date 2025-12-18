package com.example.smartnotes.repository

import com.example.smartnotes.models.Folder
import com.example.smartnotes.models.Page
import com.example.smartnotes.models.Summary

interface NotesRepository {
    suspend fun getUserFolders(userId: String): Result<List<Folder>>
    suspend fun createFolder(userId: String, title: String): Result<Folder>
    suspend fun deleteFolderMoveSummariesToUnsorted(userId: String, folderId: String): Result<Unit>
    suspend fun deleteFolderWithSummaries(userId: String, folderId: String): Result<Unit>

    suspend fun getUnsortedSummaries(userId: String): Result<List<Summary>>
    suspend fun getSummariesByFolderId(userId: String, folderId: String): Result<List<Summary>>
    suspend fun createSummary(summary: Summary): Result<String>
    suspend fun deleteSummary(userId: String, summaryId: String): Result<Unit>
    suspend fun updateSummaryPageCount(summaryId: String, newCount: Int)

    suspend fun createPage(page: Page): Result<String>
    suspend fun getPagesBySummaryId(summaryId: String): Result<List<Page>>

    suspend fun addSummariesToFolder(userId: String, folderId: String, summaryIds: List<String>): Result<Unit>
    suspend fun removeSummaryFromFolder(userId: String, summaryId: String): Result<Unit>
}
