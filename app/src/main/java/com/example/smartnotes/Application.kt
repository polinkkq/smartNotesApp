package com.example.smartnotes

import android.app.Application
import timber.log.Timber

class SmartNotesApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())
    }
}