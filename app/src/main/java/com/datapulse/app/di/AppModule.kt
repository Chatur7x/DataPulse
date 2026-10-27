package com.datapulse.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.datapulse.app.data.local.AppDatabase
import com.datapulse.app.data.stats.NetworkStatsSource
import com.datapulse.app.data.stats.SystemNetworkStatsSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun db(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "datapulse.db")
            .addMigrations(com.datapulse.app.data.local.MIGRATION_2_3, com.datapulse.app.data.local.MIGRATION_3_4, com.datapulse.app.data.local.MIGRATION_4_5)
            .build()

    // §4 rule #1: plans + measured data ONLY in Room. DataStore = volatile settings
    // (alert thresholds, quiet hours, category overrides, widget prefs). No dual writes.
    @Provides @Singleton
    fun settingsStore(@ApplicationContext ctx: Context): DataStore<Preferences> =
        androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            produceFile = { ctx.preferencesDataStoreFile("pulse_settings") }
        )

    // §4 rule #2 seam binding (prod). androidTest Hilt module overrides with Fake.
    @Provides @Singleton
    fun source(impl: SystemNetworkStatsSource): NetworkStatsSource = impl
}
