package com.example.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationHistoryDao {
    @Insert
    suspend fun insert(notification: NotificationHistory)

    @Query("SELECT * FROM notification_history ORDER BY timestamp DESC")
    fun getAll(): Flow<List<NotificationHistory>>
    
    @Query("SELECT * FROM notification_history WHERE packageName NOT IN (:excludedPackages) ORDER BY timestamp DESC")
    fun getFiltered(excludedPackages: List<String>): Flow<List<NotificationHistory>>

    @Query("SELECT * FROM notification_history WHERE (title LIKE '%' || :query || '%' OR text LIKE '%' || :query || '%' OR appName LIKE '%' || :query || '%') AND packageName NOT IN (:excludedPackages) ORDER BY timestamp DESC")
    fun search(query: String, excludedPackages: List<String>): Flow<List<NotificationHistory>>

    @Query("DELETE FROM notification_history")
    suspend fun deleteAll()
}
