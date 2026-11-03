package com.rizzwise.app.data.repository

import com.rizzwise.app.data.local.dao.HistoryDao
import com.rizzwise.app.data.local.entity.HistoryEntity
import kotlinx.coroutines.flow.Flow

class HistoryRepository(private val historyDao: HistoryDao) {

    fun getAllHistory(): Flow<List<HistoryEntity>> = historyDao.getAllHistory()

    fun searchHistory(query: String): Flow<List<HistoryEntity>> = historyDao.searchHistory(query)

    suspend fun saveHistory(
        targetMessage: String,
        selectedReply: String,
        style: String,
        providerName: String
    ) {
        historyDao.insert(
            HistoryEntity(
                targetMessage = targetMessage,
                selectedReply = selectedReply,
                style = style,
                providerName = providerName
            )
        )
    }

    suspend fun deleteHistory(entry: HistoryEntity) {
        historyDao.delete(entry)
    }

    suspend fun clearAllHistory() {
        historyDao.clearAll()
    }
}
