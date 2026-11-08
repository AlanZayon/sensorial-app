package com.sensorial.data.persistence

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.sensorial.data.persistence.dao.HistoryDao
import com.sensorial.data.persistence.dao.InteractionDao
import com.sensorial.data.persistence.dao.PreferenceDao
import com.sensorial.data.persistence.dao.SettingsDao
import com.sensorial.data.persistence.entity.HistoryMetaEntity
import com.sensorial.data.persistence.entity.HistoryTrackEntity
import com.sensorial.data.persistence.entity.InteractionEntity
import com.sensorial.data.persistence.entity.PreferenceEntity
import com.sensorial.data.persistence.entity.SettingsEntity

@Database(
    entities = [
        HistoryMetaEntity::class,
        HistoryTrackEntity::class,
        InteractionEntity::class,
        PreferenceEntity::class,
        SettingsEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class SensorialDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    abstract fun interactionDao(): InteractionDao
    abstract fun preferenceDao(): PreferenceDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        fun create(context: Context): SensorialDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                SensorialDatabase::class.java,
                "sensorial.db",
            ).fallbackToDestructiveMigration()
                .build()

        fun createInMemory(context: Context): SensorialDatabase =
            Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                SensorialDatabase::class.java,
            ).allowMainThreadQueries()
                .build()
    }
}
