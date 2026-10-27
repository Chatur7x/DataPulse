package com.datapulse.app.data

import com.datapulse.app.data.local.*
import com.datapulse.app.data.stats.UidBucket
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.ZoneId
import androidx.room.withTransaction
import kotlinx.coroutines.sync.Mutex
import javax.inject.Inject
import javax.inject.Singleton

const val MB_BYTES = 1_048_576L // §6.3 units discipline: MiB, never 10^6

fun bytesToMb(b: Long): Int = (b / MB_BYTES).toInt().coerceAtLeast(0)

// §8: the ONLY writer of measured data. ViewModels observe Room; never query platform.
@Singleton
class PulseRepository @Inject constructor(private val db: AppDatabase) {
    val syncMutex = Mutex()
    fun day(date: String) = db.daily().observeDay(date)
    fun dayApps(date: String) = db.apps().observeDay(date)
    suspend fun savePlanAtomically(plan: RechargePlan) = db.withTransaction {
        db.plans().archiveActive()
        db.plans().insertPlan(plan)
    }
    suspend fun deleteAll() = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { db.clearAllTables() }
    suspend fun storeDay(date: String, mobile: List<UidBucket>, wifi: List<UidBucket>,
                         mobileTotal: Long, wifiTotal: Long, resolve: (Int) -> String?) = db.withTransaction {
        val all = (mobile.map { it.uid } + wifi.map { it.uid }).distinct()
        val rows = all.map { uid ->
            val m = mobile.filter { it.uid == uid }
            val w = wifi.filter { it.uid == uid }
            val pkg = resolve(uid) ?: "uid:$uid"
            AppUsage(date = date, packageName = pkg, appName = if (pkg.startsWith("uid:")) "System / shared UID $uid" else pkg.substringAfterLast('.'),
                uid = uid, mobileMb = bytesToMb(m.sumOf { it.rxBytes + it.txBytes }),
                wifiMb = bytesToMb(w.sumOf { it.rxBytes + it.txBytes }), fgMb = -1, bgMb = -1,
                rxBytes = (m + w).sumOf { it.rxBytes }, txBytes = (m + w).sumOf { it.txBytes })
        }
        db.apps().deleteDay(date)
        db.apps().upsertAllRows(rows)
        val old = db.daily().dayOnce(date)
        db.daily().upsert((old ?: DailySnapshot(date = date)).copy(
            mobileMbUsed = bytesToMb(mobileTotal), wifiMbUsed = bytesToMb(wifiTotal),
            mobileBytes = mobileTotal, wifiBytes = wifiTotal, collectedAtMs = System.currentTimeMillis(),
            partialDay = date == LocalDate.now().toString(), timezoneId = ZoneId.systemDefault().id,
            totalMbUsed = bytesToMb(mobileTotal + wifiTotal),
            unattributedMb = bytesToMb((mobileTotal - mobile.sumOf { it.rxBytes + it.txBytes }).coerceAtLeast(0))))
    }
    fun activePlan(): Flow<RechargePlan?> = db.plans().activePlan()
    fun allPlans(): Flow<List<RechargePlan>> = db.plans().allPlans()
    suspend fun archiveActive() = db.plans().archiveActive()
    suspend fun insertPlan(plan: RechargePlan): Long = db.plans().insertPlan(plan)
    fun recentDays(limit: Int): Flow<List<DailySnapshot>> = db.daily().recentDays(limit)
    fun appsSince(from: String): Flow<List<AppUsage>> = db.apps().appsSince(from)
    suspend fun appsList(from: String) = db.apps().appsSinceOnce(from)
    suspend fun hoursList(from: String) = db.hourly().hoursSinceOnce(from)
    fun hoursSince(from: String): Flow<List<HourlySnapshot>> = db.hourly().hoursSince(from)

    suspend fun watermark(): Long = db.meta().syncStateOnce()?.lastSuccessMs ?: 0
    suspend fun advanceWatermark(nowMs: Long) {
        val cur = db.meta().syncStateOnce()
        db.meta().putSyncState(
            (cur ?: SyncState()).copy(lastSuccessMs = nowMs, lastAttemptMs = nowMs, failures = 0)
        )
    }
    suspend fun markAttempt(failed: Boolean) {
        val cur = db.meta().syncStateOnce() ?: SyncState()
        db.meta().putSyncState(
            cur.copy(lastAttemptMs = System.currentTimeMillis(), failures = if (failed) cur.failures + 1 else cur.failures)
        )
    }

    suspend fun activePlanOnce() = db.plans().activePlanOnce()
    suspend fun recentDaysList(limit: Int) = db.daily().recentDays(limit).let {
        // one-shot read without collecting Flow: query via daysSince floor
        db.daily().daysSince("1970-01-01").takeLast(limit).reversed()
    }
    suspend fun topOn(date: String, limit: Int = 20) = db.apps().topOn(date, limit)
    suspend fun limitOf(pkg: String) = db.meta().limitOnce(pkg)
    suspend fun setCap(pkg: String, capMb: Int) =
        db.meta().putLimit(com.datapulse.app.data.local.AppLimit(pkg, capMb))
    suspend fun appHistory(pkg: String, from: String) = db.apps().historyFor(pkg, from)

    suspend fun alertFired(rule: String, subject: String, period: String): Boolean =
        db.meta().alertFired(rule, subject, period) != null

    suspend fun logAlert(rule: String, subject: String, period: String, payload: Any?) {
        db.meta().logAlert(
            AlertLog(rule = rule, subject = subject, period = period,
                firedAt = System.currentTimeMillis(), payloadHash = payload.hashCode())
        )
    }


}

fun startOfHour(ms: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
    val z = java.time.Instant.ofEpochMilli(ms).atZone(zone)
    return z.toLocalDate().atTime(z.hour, 0).atZone(zone).toInstant().toEpochMilli()
}

fun dayString(ms: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    java.time.Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().toString()

fun monthAgoDate(): String = LocalDate.now().minusDays(31).toString()
