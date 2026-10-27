package com.datapulse.app.sync

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import com.datapulse.app.data.PulseRepository
import com.datapulse.app.data.dayString
import com.datapulse.app.data.local.PlanType
import com.datapulse.app.domain.Anomalies
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

// Volatile settings ONLY (§4 rule #1). Plans/measured data never touch DataStore.
val KEY_THRESHOLDS = stringSetPreferencesKey("thresholds_pct") // e.g. 75,90,100
val KEY_TRACKING_ENABLED = booleanPreferencesKey("tracking_enabled")
val KEY_CUSTOM_PCT = intPreferencesKey("custom_pct")
val KEY_SPIKE = booleanPreferencesKey("spike_enabled")
val KEY_CYCLE_END = booleanPreferencesKey("cycle_end_enabled")
val KEY_QUIET = booleanPreferencesKey("quiet_enabled")
val KEY_QUIET_START = intPreferencesKey("quiet_start_h")
val KEY_QUIET_END = intPreferencesKey("quiet_end_h")

@Singleton
class SettingsStore @Inject constructor(val ds: DataStore<Preferences>) {
    suspend fun thresholds(): Set<Int> =
        ds.data.first()[KEY_THRESHOLDS]?.mapNotNull { it.toIntOrNull() }?.toSet() ?: setOf(75, 90, 100)
    suspend fun inQuietHours(nowH: Int): Boolean {
        val p = ds.data.first()
        if (p[KEY_QUIET] != true) return false
        val s = p[KEY_QUIET_START] ?: 23
        val e = p[KEY_QUIET_END] ?: 7
        return if (s <= e) nowH in s until e else nowH >= s || nowH < e // wrap-midnight safe
    }
}

// §11: evaluated at end of every sync. Dedupe key rule+subject+period → alert_log.
// Quiet hours suppress thresholds/anomalies; cycle-end ALWAYS fires.
class AlertEngine @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val repo: PulseRepository,
    private val settings: SettingsStore? = null
) {
    suspend fun evaluate(zone: ZoneId) {
        val plan = repo.activePlanOnce() ?: return
        val today = LocalDate.now(zone).toString()
        val days = repo.daysSince90()
        val todayRow = days.firstOrNull { it.date == today } ?: return
        val usedToday = todayRow.effectiveMobile()
        val limit = plan.dailyDataMb
        val quiet = settings?.inQuietHours(java.time.LocalTime.now(zone).hour) ?: false

        // Daily thresholds (TOTAL/FUP aware: denominator = plan total; DAILY = daily cap)
        if (!quiet && limit > 0 && plan.planType == PlanType.DAILY) {
            val pct = usedToday * 100 / limit
            for (t in (settings?.thresholds() ?: setOf(75, 90, 100)) + listOfNotNull(customPct())) {
                if (pct >= t && !repo.alertFired("THRESHOLD", "daily", "$today/$t")) {
                    fire("pulse_alerts", 100 + t, "DataPulse: $t% of daily limit",
                        "Used $usedToday of $limit MiB today.", "THRESHOLD", "daily", "$today/$t")
                }
            }
            if (Anomalies.r6OverLimitDay(usedToday.toFloat(), limit.toFloat()) &&
                !repo.alertFired("R6", "daily", today)
            ) fire("pulse_alerts", 199, "Daily limit exceeded", "$usedToday MiB over $limit MiB cap.", "R6", "daily", today)
        }

        // Cycle-end reminder (3 d, contextual — never suppressed)
        val start = runCatching { LocalDate.parse(plan.cycleStartDate) }.getOrNull()
        if (start != null) {
            val left = plan.cycleDays - java.time.temporal.ChronoUnit.DAYS.between(start, LocalDate.now(zone)).toInt()
            if (!quiet && left in 0..3 && !repo.alertFired("CYCLE_END", "cycle", today)) {
                val total = if (plan.planType == PlanType.TOTAL || plan.planType == PlanType.FUP) plan.dailyDataMb else plan.dailyDataMb * plan.cycleDays
                val used = days.filter { it.date >= plan.cycleStartDate && it.date < start.plusDays(plan.cycleDays.toLong()).toString() }.sumOf { it.effectiveMobile() }
                fire("pulse_cycle", 300, "Cycle ends in $left day(s)",
                    "${(total - used).coerceAtLeast(0)} MiB left of $total MiB.", "CYCLE_END", "cycle", today)
            }
        }

        // Anomaly R1 (top app spike) + app caps
        if (!quiet) {
            val apps = repo.topAppsToday(today)
            val avg7 = apps.associate { it.packageName to weekAvg(it.packageName, days) }
            for (a in apps.take(5)) {
                val avg = avg7[a.packageName] ?: 0f
                if (Anomalies.r1AppSpike(a.mobileMb.toFloat(), avg) && !repo.alertFired("R1", a.packageName, today))
                    fire("pulse_anomaly", 400 + a.packageName.hashCode() % 100, "Usage spike: ${a.appName}", "${a.mobileMb} MiB today.", "R1", a.packageName, today)
                val cap = repo.appCap(a.packageName)
                if (cap != null && cap.alertsEnabled && cap.dailyCapMb > 0) {
                    val p = a.mobileMb * 100 / cap.dailyCapMb
                    if (p >= 100 && !repo.alertFired("APP_CAP", a.packageName, today))
                        fire("pulse_alerts", 500, "${a.appName} hit its cap", "${a.mobileMb} MiB of ${cap.dailyCapMb} MiB.", "APP_CAP", a.packageName, today)
                    else if (p >= 80 && !repo.alertFired("APP_CAP80", a.packageName, today))
                        fire("pulse_alerts", 501, "${a.appName} at 80% of cap", "${a.mobileMb} MiB of ${cap.dailyCapMb} MiB.", "APP_CAP80", a.packageName, today)
                }
                if (Anomalies.r3BackgroundDrain(a.fgMb.toFloat(), a.bgMb.toFloat()) && !repo.alertFired("R3", a.packageName, today))
                    fire("pulse_anomaly", 402, "Background drain: ${a.appName}", "${a.bgMb} MiB in background.", "R3", a.packageName, today)
                if (a.txBytes + a.rxBytes > 0 && Anomalies.r5UploadAnomaly(a.txBytes, a.rxBytes, a.mobileMb.toFloat()) && !repo.alertFired("R5", a.packageName, today))
                    fire("pulse_anomaly", 403, "High upload: ${a.appName}", "Unusual upload share today.", "R5", a.packageName, today)
            }
        }
    }

    private suspend fun customPct(): Int? =
        settings?.let { runCatching { it.ds.data.first()[KEY_CUSTOM_PCT] }.getOrNull()?.takeIf { value -> value in 1..100 } }

    private suspend fun weekAvg(pkg: String, days: List<com.datapulse.app.data.local.DailySnapshot>): Float {
        val from = days.minOfOrNull { it.date } ?: return 0f
        val hist = repo.appHistory(pkg, from).filter { it.date < LocalDate.now().toString() }.takeLast(7).map { it.mobileMb.toFloat() }
        return if (hist.isEmpty()) 0f else com.datapulse.app.domain.ewma(hist)
    }

    // Denied POST_NOTIFICATIONS = in-app banner path (§11 THREAT): log intent, skip post.
    // alert_log records intent, not delivery.
    private suspend fun fire(channel: String, id: Int, title: String, text: String, rule: String, subject: String, period: String) {
        repo.logAlert(rule, subject, period, "$title|$text")
        if (Build.VERSION.SDK_INT >= 33 &&
            ActivityCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return // degraded mode: dashboard banner reads recent alert_log
        NotificationManagerCompat.from(ctx).notify(
            id, NotificationCompat.Builder(ctx, channel)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title).setContentText(text).setAutoCancel(true).build()
        )
    }
}

// Small repo read helpers used by the engine (kept here to avoid touching PulseRepository twice).
suspend fun PulseRepository.daysSince90(): List<com.datapulse.app.data.local.DailySnapshot> =
    recentDaysList(90)

suspend fun PulseRepository.topAppsToday(date: String) = topOn(date)
suspend fun PulseRepository.appCap(pkg: String) = limitOf(pkg)
