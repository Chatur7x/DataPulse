package com.datapulse.app.presentation

import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.datapulse.app.data.PulseRepository
import com.datapulse.app.data.local.AppUsage
import com.datapulse.app.data.local.DailySnapshot
import com.datapulse.app.data.local.HourlySnapshot
import com.datapulse.app.data.local.RechargePlan
import com.datapulse.app.sync.KEY_CUSTOM_PCT
import com.datapulse.app.sync.KEY_CYCLE_END
import com.datapulse.app.sync.KEY_QUIET
import com.datapulse.app.sync.KEY_QUIET_END
import com.datapulse.app.sync.KEY_QUIET_START
import com.datapulse.app.sync.KEY_SPIKE
import com.datapulse.app.sync.KEY_THRESHOLDS
import com.datapulse.app.sync.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

// S3: 14-day bars + limit line + tap drill-down (top-5 sheet matches per-app table).
@HiltViewModel
class DailyViewModel @Inject constructor(private val repo: PulseRepository) : ViewModel() {
    val days: StateFlow<List<DailySnapshot>> =
        repo.recentDays(14).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val limit: StateFlow<Int> = repo.activePlan().map { it?.dailyDataMb ?: 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun topApps(date: String): Flow<List<AppUsage>> =
        repo.appsSince(date).map { rows -> rows.filter { it.date == date }.take(5) }
}

// S4: ranked list, mobile/wifi/both filter, sorts, ranges, drill-down history.
enum class NetFilter { MOBILE, WIFI, BOTH }
enum class AppSort { DATA, NAME }

@HiltViewModel
class PerAppViewModel @Inject constructor(private val repo: PulseRepository) : ViewModel() {
    private val filter = MutableStateFlow(NetFilter.MOBILE)
    private val sort = MutableStateFlow(AppSort.DATA)
    private val rangeDays = MutableStateFlow(31)

    fun setFilter(f: NetFilter) { filter.value = f }
    fun setSort(s: AppSort) { sort.value = s }
    fun setRange(days: Int) { rangeDays.value = days }

    val rows: StateFlow<List<AppRow>> = combine(filter, sort, rangeDays) { f, s, r -> Triple(f, s, r) }
        .flatMapLatest { (f, s, r) ->
            val from = LocalDate.now().minusDays(r.toLong()).toString()
            repo.appsSince(from).map { all ->
                val grouped = all.groupBy { it.packageName }.map { (pkg, rows) ->
                    val m = rows.sumOf { it.mobileMb }
                    val w = rows.sumOf { it.wifiMb }
                    AppRow(pkg, rows.first().appName, m, w, shown = 0)
                }
                val shown = grouped.map {
                    it.copy(shown = when (f) {
                        NetFilter.MOBILE -> it.mobile
                        NetFilter.WIFI -> it.wifi
                        NetFilter.BOTH -> it.mobile + it.wifi
                    })
                }.filter { it.shown > 0 }
                val total = shown.sumOf { it.shown }.coerceAtLeast(1)
                shown.map { it.copy(share = it.shown.toFloat() / total) }
                    .let { list -> if (s == AppSort.NAME) list.sortedBy { it.name.lowercase() } else list.sortedByDescending { it.shown } }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun history(pkg: String, from: String): Flow<List<AppUsage>> =
        repo.appsSince(from).map { rows -> rows.filter { it.packageName == pkg }.sortedBy { it.date } }
}

data class AppRow(val pkg: String, val name: String, val mobile: Int, val wifi: Int, val shown: Int, val share: Float = 0f)

// S5: 24×7 heatmap from hourly rows (single-pass Canvas in UI).
@HiltViewModel
class HeatmapViewModel @Inject constructor(repo: PulseRepository) : ViewModel() {
    private val weekStart = MutableStateFlow(LocalDate.now().minusDays(6))
    fun shiftWeeks(d: Long) { weekStart.value = weekStart.value.plusDays(d * 7) }
    val week: StateFlow<LocalDate> = weekStart.asStateFlow()

    val cells: StateFlow<Map<Pair<String, Int>, Int>> = weekStart.flatMapLatest { start ->
        repo.hoursSince(start.toString()).map { rows ->
            rows.filter { it.date <= start.plusDays(6).toString() }
                .associate { (it.date to it.hour) to it.mobileMb }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val avgRow: StateFlow<Map<Int, Float>> = combine(cells, week) { m, w ->
        (0..23).associateWith { h ->
            val vals = (0..6).mapNotNull { d -> m[w.plusDays(d.toLong()).toString() to h] }
            if (vals.isEmpty()) 0f else vals.average().toFloat()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())
}

// S6: cycle rows + 2-cycle compare with signed deltas.
data class CycleStats(val plan: RechargePlan, val usedMb: Int, val avgDaily: Float, val overDays: Int)

@HiltViewModel
class CyclesViewModel @Inject constructor(repo: PulseRepository) : ViewModel() {
    val cycles: StateFlow<List<CycleStats>> =
        combine(repo.allPlans(), repo.recentDays(365)) { plans, days ->
            plans.map { p ->
                val rows = days.filter { it.date >= p.cycleStartDate && it.date < LocalDate.parse(p.cycleStartDate).plusDays(p.cycleDays.toLong()).toString() }
                val used = rows.sumOf { it.effectiveMobile() }
                CycleStats(p, used,
                    if (rows.isEmpty()) 0f else used.toFloat() / rows.size,
                    rows.count { it.effectiveMobile() > p.dailyDataMb })
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

// S7: alert toggles → DataStore only (never measured data).
@HiltViewModel
class AlertsViewModel @Inject constructor(private val settings: SettingsStore,
    @dagger.hilt.android.qualifiers.ApplicationContext private val ctx: android.content.Context) : ViewModel() {
    val prefs: StateFlow<Map<String, Any>> = settings.ds.data.map { p ->
        mapOf(
            "tracking" to (p[com.datapulse.app.sync.KEY_TRACKING_ENABLED] ?: true),
            "thresholds" to (p[KEY_THRESHOLDS] ?: setOf("75", "90", "100")),
            "custom" to (p[KEY_CUSTOM_PCT] ?: 0),
            "spike" to (p[KEY_SPIKE] ?: true),
            "cycleEnd" to (p[KEY_CYCLE_END] ?: true),
            "quiet" to (p[KEY_QUIET] ?: false),
            "qStart" to (p[KEY_QUIET_START] ?: 23),
            "qEnd" to (p[KEY_QUIET_END] ?: 7)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun toggleThreshold(pct: Int, on: Boolean) = update { p ->
        val cur = (p[KEY_THRESHOLDS] ?: setOf("75", "90", "100")).toMutableSet()
        if (on) cur += pct.toString() else cur -= pct.toString()
        p[KEY_THRESHOLDS] = cur
    }
    fun setCustom(pct: Int) = update { it[KEY_CUSTOM_PCT] = pct.coerceIn(0, 100) }
    fun setSpike(on: Boolean) = update { it[KEY_SPIKE] = on }
    fun setTracking(on: Boolean) = viewModelScope.launch {
        settings.ds.edit { it[com.datapulse.app.sync.KEY_TRACKING_ENABLED]=on }
        if(on){com.datapulse.app.sync.SyncWorker.schedule(ctx);com.datapulse.app.sync.SyncWorker.syncNow(ctx)}
    }
    fun setCycleEnd(on: Boolean) = update { it[KEY_CYCLE_END] = on }
    fun setQuiet(on: Boolean, s: Int = 23, e: Int = 7) = update {
        it[KEY_QUIET] = on; it[KEY_QUIET_START] = s; it[KEY_QUIET_END] = e
    }

    private fun update(block: suspend (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        viewModelScope.launch { settings.ds.edit(block) }
    }
}

// Export sheet (Table 7): 3 CSV actions through scoped FileProvider.
@HiltViewModel
class ExportViewModel @Inject constructor(
    private val exporter: com.datapulse.app.data.Exporter
) : ViewModel() {
    fun shareDaily() = viewModelScope.launch { exporter.shareDaily() }
    fun shareApps() = viewModelScope.launch {
        exporter.shareApps(java.time.LocalDate.now().minusDays(31).toString())
    }
    fun shareHourly() = viewModelScope.launch {
        exporter.shareHourly(java.time.LocalDate.now().minusDays(7).toString())
    }
}

// FR-20 per-app caps (80%/100% alerts in AlertEngine).
@HiltViewModel
class CapViewModel @Inject constructor(private val repo: PulseRepository) : ViewModel() {
    fun setCap(pkg: String, capMb: Int) = viewModelScope.launch { repo.setCap(pkg, capMb) }
}
