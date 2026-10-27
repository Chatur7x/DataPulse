package com.datapulse.app.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.datapulse.app.data.PulseRepository
import com.datapulse.app.data.local.PlanType
import com.datapulse.app.data.local.RechargePlan
import com.datapulse.app.sync.SyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import javax.inject.Inject
import android.content.Context
import kotlinx.coroutines.launch
import androidx.datastore.preferences.core.edit

@HiltViewModel
class PlanViewModel @Inject constructor(
    private val repo: PulseRepository,
    @ApplicationContext private val ctx: Context,
    private val settings: com.datapulse.app.sync.SettingsStore
) : ViewModel() {
    val activePlan: StateFlow<RechargePlan?> =
        repo.activePlan().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val allPlans: StateFlow<List<RechargePlan>> =
        repo.allPlans().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // FR-03 + FR-24: archive current cycle to history, insert new active plan (Room only —
    // §4 rule #1, no DataStore plan writes), then full-cycle backfill (FR-04).
    fun savePlan(
        carrier: String, amountRs: Int, dataGb: Float, cycleDays: Int, start: LocalDate,
        type: PlanType, nightStart: Int = 2, nightEnd: Int = 5, onSaved: () -> Unit = {}
    ) {
        viewModelScope.launch {
            repo.savePlanAtomically(
                RechargePlan(
                    carrierName = carrier.ifBlank { "Other" }, rechargeAmount = amountRs,
                    dailyDataMb = com.datapulse.app.domain.decimalGbToMib(dataGb),
                    cycleDays = cycleDays.coerceAtLeast(1),
                    cycleStartDate = start.toString(), planType = type,
                    nightStartHour = nightStart, nightEndHour = nightEnd,
                    isActive = 1, createdAt = LocalDate.now().toString()
                )
            )
            settings.ds.edit { it[com.datapulse.app.sync.KEY_TRACKING_ENABLED]=true }
            SyncWorker.syncNow(ctx)
            onSaved()
        }
    }
}
