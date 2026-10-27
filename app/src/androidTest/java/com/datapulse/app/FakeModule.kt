package com.datapulse.app

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import com.datapulse.app.data.local.AppDatabase
import com.datapulse.app.data.stats.FakeNetworkStatsSource
import com.datapulse.app.data.stats.NetworkStatsSource
import com.datapulse.app.di.AppModule
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

// §4 rule #2: instrumented/UI/worker tests never touch the platform API.
// Script the fake per-test via a mutable holder before launching.
object FakeHolder { var fake: FakeNetworkStatsSource = FakeNetworkStatsSource() }

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [AppModule::class])
object FakeAppModule {
    @Provides @Singleton
    fun source(): NetworkStatsSource = FakeHolder.fake

    @Provides @Singleton
    fun db(@ApplicationContext ctx: Context): AppDatabase =
        Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).build()

    @Provides @Singleton
    fun settings(@ApplicationContext ctx: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            produceFile = { ctx.preferencesDataStoreFile("test_settings") }
        )
}
