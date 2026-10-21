package com.wisechat.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reply_history")
data class HistoryEntity(
    @PrimaryKey
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val targetMessage: String,
    val selectedReply: String,
    val style: String,
    val providerName: String,
    val wasCopied: Boolean = true
)
