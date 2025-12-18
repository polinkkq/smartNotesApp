package com.example.smartnotes.repository

import com.example.smartnotes.models.Folder
import com.example.smartnotes.models.Page
import com.example.smartnotes.models.Summary

object GuestDataStore {
    val folders: MutableList<Folder> = mutableListOf()
    val summaries: MutableList<Summary> = mutableListOf()
    val pages: MutableList<Page> = mutableListOf()

    fun clearAll() {
        folders.clear()
        summaries.clear()
        pages.clear()
    }
}
