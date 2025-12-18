package com.example.smartnotes.repository

import android.content.Context

object SessionManager {

    private const val PREFS = "smartnotes_session"
    private const val KEY_GUEST = "guest_mode"

    fun setGuestMode(context: Context, isGuest: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_GUEST, isGuest)
            .apply()
    }

    fun isGuestMode(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_GUEST, false)
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}
