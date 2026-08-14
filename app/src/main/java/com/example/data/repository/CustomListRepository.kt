package com.example.data.repository

import com.example.data.CustomListDao
import com.example.data.DbCustomList
import com.example.data.DbCustomListTitle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class CustomListRepository(
    private val customListDao: CustomListDao
) {
    fun getCustomListsStream(): Flow<List<DbCustomList>> = customListDao.getAllCustomLists()

    suspend fun getAllCustomListsList(): List<DbCustomList> = customListDao.getAllCustomListsList()

    fun getCustomListById(listId: Int): Flow<DbCustomList?> = customListDao.getCustomListById(listId)

    fun getTitlesForListStream(listId: Int): Flow<List<DbCustomListTitle>> =
        customListDao.getCustomListTitles(listId)

    suspend fun createCustomList(name: String, description: String): Long =
        customListDao.insertCustomList(DbCustomList(name = name, description = description))

    suspend fun deleteCustomList(listId: Int) {
        customListDao.deleteCustomListTitlesForList(listId)
        customListDao.deleteCustomListById(listId)
    }

    suspend fun addTitleToList(listId: Int, titleId: String, titleType: String, titleName: String, titlePosterUrl: String?) {
        val existingTitles = customListDao.getCustomListTitles(listId).first()
        val entry = DbCustomListTitle(
            listId = listId,
            titleId = titleId,
            titleType = titleType,
            titleName = titleName,
            titlePosterUrl = titlePosterUrl,
            orderIndex = existingTitles.size
        )
        customListDao.insertCustomListTitle(entry)
    }

    suspend fun deleteListTitleById(id: Int) = customListDao.deleteCustomListTitleById(id)
}
