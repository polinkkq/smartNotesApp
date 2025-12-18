package com.example.smartnotes.repository

import android.content.Context

object RepositoryProvider {
    private val guestRepo: NotesRepository = GuestNotesRepository()

    fun notes(context: Context): NotesRepository {
        return if (SessionManager.isGuestMode(context)) guestRepo
        else FirebaseRepositoryAdapter(FirebaseRepository())
    }
}

