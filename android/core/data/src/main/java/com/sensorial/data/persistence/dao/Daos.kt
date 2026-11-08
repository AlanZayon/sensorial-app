package com.sensorial.data.persistence.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.sensorial.data.persistence.entity.HistoryMetaEntity
import com.sensorial.data.persistence.entity.HistoryTrackEntity
import com.sensorial.data.persistence.entity.InteractionEntity
import com.sensorial.data.persistence.entity.PreferenceEntity
import com.sensorial.data.persistence.entity.SettingsEntity

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history_meta WHERE id = 1 LIMIT 1")
    suspend fun getMeta(): HistoryMetaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMeta(meta: HistoryMetaEntity)

    @Query("SELECT * FROM history_tracks ORDER BY position ASC")
    suspend fun getTracks(): List<HistoryTrackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTracks(tracks: List<HistoryTrackEntity>)

    @Query("DELETE FROM history_tracks")
    suspend fun clearTracks()

    @Transaction
    suspend fun replaceHistory(meta: HistoryMetaEntity, tracks: List<HistoryTrackEntity>) {
        clearTracks()
        upsertTracks(tracks)
        upsertMeta(meta)
    }
}

@Dao
interface InteractionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: InteractionEntity)

    @Query("SELECT * FROM interactions ORDER BY timestampEpochMs ASC")
    suspend fun getAll(): List<InteractionEntity>

    @Query(
        """
        SELECT * FROM interactions
        ORDER BY timestampEpochMs DESC
        LIMIT :limit
        """,
    )
    suspend fun getRecentDesc(limit: Int): List<InteractionEntity>

    @Query("DELETE FROM interactions")
    suspend fun clear()
}

@Dao
interface PreferenceDao {
    @Query("SELECT * FROM preference_profile WHERE id = 1 LIMIT 1")
    suspend fun get(): PreferenceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PreferenceEntity)

    @Query("DELETE FROM preference_profile")
    suspend fun clear()
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM app_settings WHERE id = 1 LIMIT 1")
    suspend fun get(): SettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SettingsEntity)
}
