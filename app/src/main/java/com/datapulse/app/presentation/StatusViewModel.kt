package com.datapulse.app.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.datapulse.app.data.PulseRepository
import com.datapulse.app.data.local.AppDatabase
import com.datapulse.app.sync.SettingsStore
import androidx.datastore.preferences.core.edit
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

@HiltViewModel
class StatusViewModel @Inject constructor(private val repo: PulseRepository, db: AppDatabase,
 private val settings: SettingsStore, @ApplicationContext private val ctx: Context) : ViewModel() {
 val stamp = db.meta().observeSync().map { it?.lastSuccessMs ?: 0L }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)
 fun deleteAll() { viewModelScope.launch(Dispatchers.IO) {
  WorkManager.getInstance(ctx).cancelAllWork().result.get()
  repo.syncMutex.withLock {
   repo.deleteAll()
   settings.ds.edit { it.clear(); it[com.datapulse.app.sync.KEY_TRACKING_ENABLED]=false }
   java.io.File(ctx.cacheDir, "exports").listFiles()?.forEach { it.delete() }
  }
 } }
}
