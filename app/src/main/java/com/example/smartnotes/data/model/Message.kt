package com.example.smartnotes.models

import com.google.firebase.firestore.PropertyName

data class Message(
    @PropertyName("id") val id: String = "",
    @PropertyName("chatId") val chatId: String = "",
    @PropertyName("sender") val sender: String = "", // "user" or "ai"
    @PropertyName("text") val text: String = "",
    @PropertyName("sendDate") val sendDate: Long = System.currentTimeMillis()
) {
    constructor() : this("", "", "", "")
}
