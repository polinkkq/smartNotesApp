package com.example.smartnotes.repository

import com.example.smartnotes.models.Folder
import com.example.smartnotes.models.Page
import com.example.smartnotes.models.Summary
import java.util.UUID

class GuestNotesRepository : NotesRepository {

    private val folders get() = GuestDataStore.folders
    private val summaries get() = GuestDataStore.summaries
    private val pages get() = GuestDataStore.pages

    override suspend fun getUserFolders(userId: String): Result<List<Folder>> {
        // userId игнорируем: для гостя всё одно
        return Result.success(folders.sortedByDescending { it.createdAt })
    }

    override suspend fun createFolder(userId: String, title: String): Result<Folder> {
        val folder = Folder(
            id = UUID.randomUUID().toString(),
            title = title,
            createdAt = System.currentTimeMillis(),
            userId = "guest",
            summaryCount = 0
        )
        folders.add(folder)
        return Result.success(folder)
    }

    override suspend fun deleteFolderMoveSummariesToUnsorted(userId: String, folderId: String): Result<Unit> {
        val folder = folders.find { it.id == folderId } ?: return Result.success(Unit)

        // все summaries из этой папки -> в несортированные
        val inFolder = summaries.filter { it.folderId == folderId }
        inFolder.forEach { s ->
            val idx = summaries.indexOfFirst { it.id == s.id }
            if (idx >= 0) summaries[idx] = s.copy(folderId = "")
        }

        folders.remove(folder)
        return Result.success(Unit)
    }

    override suspend fun deleteFolderWithSummaries(userId: String, folderId: String): Result<Unit> {
        val folder = folders.find { it.id == folderId } ?: return Result.success(Unit)

        val idsToDelete = summaries.filter { it.folderId == folderId }.map { it.id }.toSet()

        // удаляем pages
        pages.removeAll { it.summaryId in idsToDelete }
        // удаляем summaries
        summaries.removeAll { it.id in idsToDelete }
        // удаляем folder
        folders.remove(folder)

        return Result.success(Unit)
    }


    override suspend fun getUnsortedSummaries(userId: String): Result<List<Summary>> {
        return Result.success(
            summaries
                .filter { it.folderId.isBlank() }
                .sortedByDescending { it.createdAt }
        )
    }

    override suspend fun getSummariesByFolderId(userId: String, folderId: String): Result<List<Summary>> {
        return Result.success(
            summaries
                .filter { it.folderId == folderId }
                .sortedByDescending { it.createdAt }
        )
    }

    override suspend fun createSummary(summary: Summary): Result<String> {
        val id = UUID.randomUUID().toString()
        val newSummary = summary.copy(
            id = id,
            userId = "guest" // фиксируем гостя
        )
        summaries.add(newSummary)
        return Result.success(id)
    }


    override suspend fun deleteSummary(userId: String, summaryId: String): Result<Unit> {
        val s = summaries.find { it.id == summaryId } ?: return Result.success(Unit)

        // удаляем страницы
        pages.removeAll { it.summaryId == summaryId }

        // если был в папке — уменьшаем summaryCount
        if (s.folderId.isNotBlank()) {
            val folderIdx = folders.indexOfFirst { it.id == s.folderId }
            if (folderIdx >= 0) {
                val f = folders[folderIdx]
                folders[folderIdx] = f.copy(summaryCount = (f.summaryCount - 1).coerceAtLeast(0))
            }
        }

        // удаляем сам summary
        summaries.removeAll { it.id == summaryId }
        return Result.success(Unit)
    }

    override suspend fun updateSummaryPageCount(summaryId: String, newCount: Int) {
        val idx = summaries.indexOfFirst { it.id == summaryId }
        if (idx >= 0) {
            val s = summaries[idx]
            summaries[idx] = s.copy(pageCount = newCount)
        }
    }


    override suspend fun createPage(page: Page): Result<String> {
        val id = UUID.randomUUID().toString()
        val newPage = page.copy(id = id)
        pages.add(newPage)
        return Result.success(id)
    }

    override suspend fun getPagesBySummaryId(summaryId: String): Result<List<Page>> {
        return Result.success(
            pages
                .filter { it.summaryId == summaryId }
                .sortedBy { it.pageNumber }
        )
    }


    override suspend fun addSummariesToFolder(userId: String, folderId: String, summaryIds: List<String>): Result<Unit> {
        if (summaryIds.isEmpty()) return Result.success(Unit)

        val folderIdx = folders.indexOfFirst { it.id == folderId }
        if (folderIdx < 0) return Result.failure(IllegalStateException("Папка не найдена"))

        // сколько реально переносим (только те, кто существует и сейчас не в этой папке)
        var moved = 0

        summaryIds.forEach { sid ->
            val sIdx = summaries.indexOfFirst { it.id == sid }
            if (sIdx >= 0) {
                val s = summaries[sIdx]
                if (s.folderId != folderId) {
                    summaries[sIdx] = s.copy(folderId = folderId)
                    moved++
                }
            }
        }

        if (moved > 0) {
            val f = folders[folderIdx]
            folders[folderIdx] = f.copy(summaryCount = f.summaryCount + moved)
        }

        return Result.success(Unit)
    }

    // убрать конспект из папки -> в несортированные
    override suspend fun removeSummaryFromFolder(userId: String, summaryId: String): Result<Unit> {
        val sIdx = summaries.indexOfFirst { it.id == summaryId }
        if (sIdx < 0) return Result.success(Unit)

        val s = summaries[sIdx]
        val oldFolderId = s.folderId
        if (oldFolderId.isBlank()) return Result.success(Unit)

        // summary -> folderId = ""
        summaries[sIdx] = s.copy(folderId = "")

        // folder.summaryCount--
        val fIdx = folders.indexOfFirst { it.id == oldFolderId }
        if (fIdx >= 0) {
            val f = folders[fIdx]
            folders[fIdx] = f.copy(summaryCount = (f.summaryCount - 1).coerceAtLeast(0))
        }

        return Result.success(Unit)
    }
}
