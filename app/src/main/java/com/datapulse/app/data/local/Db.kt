package com.datapulse.app.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// FR-07: every measured write is @Upsert keyed by unique index — re-syncs converge, never duplicate.
@Dao
interface PlanDao {
    @Query("SELECT * FROM recharge_plans WHERE isActive = 1 LIMIT 1")
    fun activePlan(): Flow<RechargePlan?>

    @Query("SELECT * FROM recharge_plans WHERE isActive = 1 LIMIT 1")
    suspend fun activePlanOnce(): RechargePlan?

    @Query("SELECT * FROM recharge_plans ORDER BY id DESC")
    fun allPlans(): Flow<List<RechargePlan>>

    @Insert suspend fun insertPlan(plan: RechargePlan): Long

    @Query("UPDATE recharge_plans SET isActive = 0 WHERE isActive = 1")
    suspend fun archiveActive()
}

@Dao
interface DailyDao {
    @Query("SELECT * FROM daily_snapshots WHERE date = :date LIMIT 1")
    fun observeDay(date: String): Flow<DailySnapshot?>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(row: DailySnapshot)
    @Upsert suspend fun upsertAll(rows: List<DailySnapshot>)

    @Query("SELECT * FROM daily_snapshots ORDER BY date DESC LIMIT :limit")
    fun recentDays(limit: Int): Flow<List<DailySnapshot>>

    @Query("SELECT * FROM daily_snapshots WHERE date >= :from ORDER BY date")
    suspend fun daysSince(from: String): List<DailySnapshot>

    @Query("SELECT * FROM daily_snapshots WHERE date = :date LIMIT 1")
    suspend fun dayOnce(date: String): DailySnapshot?
}

@Dao
interface AppUsageDao {
    @Query("SELECT * FROM app_usage WHERE date = :date ORDER BY (mobileMb + wifiMb) DESC")
    fun observeDay(date: String): Flow<List<AppUsage>>
    // idempotent re-sync backbone: insert-or-update keyed by unique(date, packageName)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAllRows(rows: List<AppUsage>)
    @Query("DELETE FROM app_usage WHERE date = :date")
    suspend fun deleteDay(date: String)

    @Query("SELECT * FROM app_usage WHERE date >= :fromDate ORDER BY mobileMb DESC")
    fun appsSince(fromDate: String): Flow<List<AppUsage>>

    @Query("SELECT * FROM app_usage WHERE date >= :from ORDER BY date")
    suspend fun appsSinceOnce(from: String): List<AppUsage>

    @Query("SELECT * FROM app_usage WHERE date = :date ORDER BY mobileMb DESC LIMIT :limit")
    suspend fun topOn(date: String, limit: Int): List<AppUsage>

    @Query("SELECT * FROM app_usage WHERE packageName = :pkg AND date >= :from ORDER BY date")
    suspend fun historyFor(pkg: String, from: String): List<AppUsage>
}

@Dao
interface HourlyDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(row: HourlySnapshot)
    @Upsert suspend fun upsertAll(rows: List<HourlySnapshot>)

    @Query("SELECT * FROM hourly_snapshots WHERE date >= :fromDate ORDER BY date, hour")
    fun hoursSince(fromDate: String): Flow<List<HourlySnapshot>>

    @Query("SELECT * FROM hourly_snapshots WHERE date >= :from ORDER BY date, hour")
    suspend fun hoursSinceOnce(from: String): List<HourlySnapshot>
}

@Dao
interface MetaDao {
    @Query("SELECT * FROM sync_state WHERE id = 1")
    fun observeSync(): Flow<SyncState?>
    @Upsert suspend fun upsertUid(row: UidMap)

    @Query("SELECT * FROM sync_state WHERE id = 1 LIMIT 1")
    suspend fun syncStateOnce(): SyncState?

    @Upsert suspend fun putSyncState(row: SyncState)

    @Query("SELECT firedAt FROM alert_log WHERE rule = :rule AND subject = :subject AND period = :period LIMIT 1")
    suspend fun alertFired(rule: String, subject: String, period: String): Long?

    @Insert suspend fun logAlert(row: AlertLog)

    @Upsert suspend fun putLimit(row: AppLimit)

    @Query("SELECT * FROM app_limits WHERE packageName = :pkg LIMIT 1")
    suspend fun limitOnce(pkg: String): AppLimit?
}

@Database(
    entities = [
        RechargePlan::class, DailySnapshot::class, AppUsage::class, HourlySnapshot::class,
        UidMap::class, AppLimit::class, AlertLog::class, SyncState::class,
        com.datapulse.app.speed.SpeedResult::class, com.datapulse.app.sim.SimRecharge::class
    ],
    version = 5, exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun plans(): PlanDao
    abstract fun daily(): DailyDao
    abstract fun apps(): AppUsageDao
    abstract fun hourly(): HourlyDao
    abstract fun meta(): MetaDao
    abstract fun sims(): com.datapulse.app.sim.SimRechargeDao
    abstract fun speed(): com.datapulse.app.speed.SpeedDao
}
