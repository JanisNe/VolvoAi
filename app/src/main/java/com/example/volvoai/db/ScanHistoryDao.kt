package com.example.volvoai.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ScanHistoryEntity)

    @Query("SELECT * FROM scan_history ORDER BY scannedAtMillis DESC")
    fun observeAll(): Flow<List<ScanHistoryEntity>>
}
