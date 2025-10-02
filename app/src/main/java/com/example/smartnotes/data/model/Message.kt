package com.example.smartnotes.models

import com.google.firebase.firestore.PropertyName

data class Message(
    @PropertyName("id") val id: String = "",
    @PropertyName("chat_id") val chatId: String = "",
    @PropertyName("sender") val sender: String = "", // "user" or "ai"
    @PropertyName("text") val text: String = "",
    @PropertyName("send_date") val sendDate: Long = System.currentTimeMillis()
) {
    constructor() : this("", "", "", "")
}