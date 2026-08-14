package com.example.data.repository

import com.example.data.DbLogEntry
import com.example.data.LogDao
import kotlinx.coroutines.flow.Flow

class LogRepository(
    private val logDao: LogDao
) {
    fun getLogsStream(): Flow<List<DbLogEntry>> = logDao.getAllLogs()

    suspend fun getAllLogsList(): List<DbLogEntry> = logDao.getAllLogsList()

    suspend fun getWatchedTitleIds(): Set<String> = logDao.getWatchedTitleIds().toSet()

    fun getLogsForTitle(titleId: String): Flow<List<DbLogEntry>> = logDao.getLogsForTitle(titleId)

    suspend fun saveLogEntry(entry: DbLogEntry) = logDao.insertLog(entry)

    suspend fun saveLogs(entries: List<DbLogEntry>) = logDao.insertLogs(entries)

    suspend fun deleteLogById(id: Int) = logDao.deleteLogById(id)
}
