package com.sensorial.data.persistence.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "history_meta")
data class HistoryMetaEntity(
    @PrimaryKey val id: Int = 1,
    val currentIndex: Int,
    val maxSize: Int,
)

@Entity(tableName = "history_tracks")
data class HistoryTrackEntity(
    @PrimaryKey val position: Int,
    val trackJson: String,
)

@Entity(tableName = "interactions")
data class InteractionEntity(
    @PrimaryKey val id: String,
    val trackId: String,
    val interactionType: String,
    val verticalIntent: String,
    val horizontalIntent: String,
    val gestureIntensity: String,
    val gestureVelocity: Double,
    val listeningDurationMs: Long,
    val timestampEpochMs: Long,
    val artistId: String? = null,
    val trackDurationMs: Long? = null,
    val listeningProgress: Double = 0.0,
    val discoveryMode: String = "Normal",
    val localHour: Int = 12,
    val dayOfWeek: Int = 1,
    val navigationAction: String = "None",
)

@Entity(tableName = "preference_profile")
data class PreferenceEntity(
    @PrimaryKey val id: Int = 1,
    val profileJson: String,
)

@Entity(tableName = "app_settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = 1,
    val settingsJson: String,
)
