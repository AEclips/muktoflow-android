package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.SessionLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionLogDao {
    @Query("SELECT * FROM session_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<SessionLogEntity>>

    @Query("SELECT * FROM session_logs WHERE dateString = :date ORDER BY timestamp DESC")
    fun getLogsForDate(date: String): Flow<List<SessionLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: SessionLogEntity): Long

    @Query("SELECT SUM(durationSeconds) FROM session_logs WHERE dateString = :date AND sessionType = 'WORK'")
    fun getFocusSecondsForDate(date: String): Flow<Int?>

    @Query("SELECT DISTINCT dateString FROM session_logs WHERE sessionType = 'WORK' ORDER BY dateString DESC")
    fun getDistinctWorkDates(): Flow<List<String>>

    @Query("DELETE FROM session_logs")
    suspend fun clearAllLogs()
}
