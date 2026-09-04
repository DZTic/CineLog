package com.example.data.repository

import com.example.data.*
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BackupRepository(
    private val logDao: LogDao,
    private val watchlistDao: WatchlistDao,
    private val customListDao: CustomListDao,
    private val seasonProgressDao: SeasonProgressDao,
    private val moshi: Moshi = Moshi.Builder()
        .addLast(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
        .build()
) {

    companion object {
        private const val CSV_DATE_PATTERN = "yyyy-MM-dd HH:mm:ss"

        private val csvDateFormat = object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue(): SimpleDateFormat {
                return SimpleDateFormat(CSV_DATE_PATTERN, Locale.getDefault())
            }
        }
    }

    suspend fun exportBackupJson(): String = withContext(Dispatchers.IO) {
        val backup = CineLogBackup(
            version = 1,
            exportedAt = System.currentTimeMillis(),
            logs = logDao.getAllLogsList(),
            watchlist = watchlistDao.getAllWatchlistList(),
            customLists = customListDao.getAllCustomListsList(),
            customListTitles = customListDao.getAllCustomListTitlesList(),
            seasonProgress = seasonProgressDao.getAllSeasonProgressList()
        )
        val adapter = moshi.adapter(CineLogBackup::class.java).indent("  ")
        adapter.toJson(backup)
    }

    suspend fun exportBackupCsv(): String = withContext(Dispatchers.IO) {
        val logs = logDao.getAllLogsList()
        val watchlist = watchlistDao.getAllWatchlistList()
        val customLists = customListDao.getAllCustomListsList()
        val customListTitles = customListDao.getAllCustomListTitlesList()

        val sb = StringBuilder()
        val dateFormat = csvDateFormat.get()!!

        sb.append("=== LOGS DE VISIONNAGE ===\n")
        sb.append("ID,ID_Titre,Type,Titre,Date_Vue,Note,Critique,Revisionnage,Spoiler,Collection\n")
        for (e in logs) {
            val dateStr = dateFormat.format(Date(e.dateVue))
            sb.append("${e.id},\"${e.titleId}\",\"${e.titleType}\",\"${escapeCsv(e.titleName)}\",\"$dateStr\",${e.note},\"${escapeCsv(e.critique)}\",${e.revisionnage},${e.spoiler},\"${escapeCsv(e.collectionName ?: "")}\"\n")
        }

        sb.append("\n=== WATCHLIST ===\n")
        sb.append("ID_Titre,Type,Titre,Date_Ajout,Annee,Genres,Note_Moyenne,Collection\n")
        for (w in watchlist) {
            val dateStr = dateFormat.format(Date(w.dateAdded))
            sb.append("\"${w.titleId}\",\"${w.titleType}\",\"${escapeCsv(w.titleName)}\",\"$dateStr\",\"${w.titleYear ?: ""}\",\"${escapeCsv(w.titleGenres ?: "")}\",${w.titleVoteAverage ?: ""},\"${escapeCsv(w.collectionName ?: "")}\"\n")
        }

        sb.append("\n=== LISTES PERSONNALISEES ===\n")
        sb.append("ID_Liste,Nom_Liste,Description\n")
        for (l in customLists) {
            sb.append("${l.id},\"${escapeCsv(l.name)}\",\"${escapeCsv(l.description)}\"\n")
        }

        sb.append("\n=== TITRES EN LISTES ===\n")
        sb.append("ID_Entree,ID_Liste,ID_Titre,Type,Titre,Ordre\n")
        for (t in customListTitles) {
            sb.append("${t.id},${t.listId},\"${t.titleId}\",\"${t.titleType}\",\"${escapeCsv(t.titleName)}\",${t.orderIndex}\n")
        }

        sb.toString()
    }

    private fun escapeCsv(text: String): String {
        return text.replace("\"", "\"\"")
    }

    suspend fun importBackup(content: String): ImportSummary = withContext(Dispatchers.IO) {
        val trimmed = content.trim()
        if (trimmed.startsWith("{")) {
            val adapter = moshi.adapter(CineLogBackup::class.java)
            val backup = adapter.fromJson(trimmed) ?: throw IllegalArgumentException("Format JSON invalide")
            if (backup.logs.isNotEmpty()) logDao.insertLogs(backup.logs)
            if (backup.watchlist.isNotEmpty()) watchlistDao.insertWatchlists(backup.watchlist)
            if (backup.customLists.isNotEmpty()) customListDao.insertCustomLists(backup.customLists)
            if (backup.customListTitles.isNotEmpty()) customListDao.insertCustomListTitles(backup.customListTitles)
            if (backup.seasonProgress.isNotEmpty()) seasonProgressDao.upsertAll(backup.seasonProgress)

            ImportSummary(
                logsCount = backup.logs.size,
                watchlistCount = backup.watchlist.size,
                customListsCount = backup.customLists.size,
                seasonProgressCount = backup.seasonProgress.size
            )
        } else {
            val (logs, watchlist) = parseCsvImport(trimmed)
            if (logs.isEmpty() && watchlist.isEmpty()) {
                throw IllegalArgumentException("Fichier CSV non reconnu ou vide")
            }
            if (logs.isNotEmpty()) logDao.insertLogs(logs)
            if (watchlist.isNotEmpty()) watchlistDao.insertWatchlists(watchlist)

            ImportSummary(
                logsCount = logs.size,
                watchlistCount = watchlist.size,
                customListsCount = 0,
                seasonProgressCount = 0
            )
        }
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (c == '"') {
                if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                    current.append('"')
                    i++
                } else {
                    inQuotes = !inQuotes
                }
            } else if (c == ',' && !inQuotes) {
                result.add(current.toString())
                current.clear()
            } else {
                current.append(c)
            }
            i++
        }
        result.add(current.toString())
        return result
    }

    private fun parseCsvImport(content: String): Pair<List<DbLogEntry>, List<DbWatchlist>> {
        val logs = mutableListOf<DbLogEntry>()
        val watchlist = mutableListOf<DbWatchlist>()

        var currentSection = ""
        val lines = content.lines()
        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isBlank()) continue
            if (line.startsWith("===")) {
                when {
                    line.contains("LOGS") -> currentSection = "LOGS"
                    line.contains("WATCHLIST") -> currentSection = "WATCHLIST"
                    else -> currentSection = ""
                }
                continue
            }

            val cols = parseCsvLine(line)
            if (cols.isEmpty()) continue

            if (cols[0].equals("ID", ignoreCase = true) || cols[0].equals("ID_Titre", ignoreCase = true) || cols[0].startsWith("ID_")) {
                continue
            }

            if (currentSection == "LOGS" || (currentSection == "" && cols.size >= 8)) {
                try {
                    val id = cols.getOrNull(0)?.toIntOrNull() ?: 0
                    val titleId = cols.getOrNull(1) ?: continue
                    val type = cols.getOrNull(2) ?: "FILM"
                    val name = cols.getOrNull(3) ?: ""
                    val dateStr = cols.getOrNull(4) ?: ""
                    val dateVue = parseDateOrTimestamp(dateStr)
                    val note = cols.getOrNull(5)?.toFloatOrNull() ?: 0f
                    val critique = cols.getOrNull(6) ?: ""
                    val revisionnage = cols.getOrNull(7)?.toBoolean() ?: false
                    val spoiler = cols.getOrNull(8)?.toBoolean() ?: false
                    val collectionName = cols.getOrNull(9).takeIf { !it.isNullOrBlank() }

                    logs.add(
                        DbLogEntry(
                            id = id,
                            titleId = titleId,
                            titleType = type,
                            titleName = name,
                            titlePosterUrl = null,
                            dateVue = dateVue,
                            note = note,
                            critique = critique,
                            revisionnage = revisionnage,
                            spoiler = spoiler,
                            collectionName = collectionName
                        )
                    )
                } catch (e: Exception) {
                    // Ignore malformed lines
                }
            } else if (currentSection == "WATCHLIST") {
                try {
                    val titleId = cols.getOrNull(0) ?: continue
                    val type = cols.getOrNull(1) ?: "FILM"
                    val name = cols.getOrNull(2) ?: ""
                    val dateStr = cols.getOrNull(3) ?: ""
                    val dateAdded = parseDateOrTimestamp(dateStr)
                    val year = cols.getOrNull(4)
                    val genres = cols.getOrNull(5)
                    val voteAverage = cols.getOrNull(6)?.toFloatOrNull()
                    val collectionName = cols.getOrNull(7).takeIf { !it.isNullOrBlank() }

                    watchlist.add(
                        DbWatchlist(
                            titleId = titleId,
                            titleType = type,
                            titleName = name,
                            titlePosterUrl = null,
                            dateAdded = dateAdded,
                            titleYear = year,
                            titleGenres = genres,
                            titleVoteAverage = voteAverage,
                            collectionName = collectionName
                        )
                    )
                } catch (e: Exception) {
                    // Ignore malformed lines
                }
            }
        }

        return Pair(logs, watchlist)
    }

    private fun parseDateOrTimestamp(str: String): Long {
        val longVal = str.toLongOrNull()
        if (longVal != null) return longVal
        return try {
            csvDateFormat.get()?.parse(str)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }
}
