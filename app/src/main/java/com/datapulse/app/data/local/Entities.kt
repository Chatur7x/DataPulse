package com.datapulse.app.data.local

import androidx.room.*

// Table 5 (v2): 8 entities. MB = MiB (1_048_576 bytes, §6.3). Dates YYYY-MM-DD device zone.
enum class PlanType { DAILY, TOTAL, DAILY_NIGHT, FUP }

@Entity(tableName = "recharge_plans")
data class RechargePlan(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val carrierName: String,
    val rechargeAmount: Int,
    val dailyDataMb: Int, // DAILY*: per-day cap L; TOTAL/FUP: total P stored here
    val cycleDays: Int,
    val cycleStartDate: String,
    val planType: PlanType = PlanType.DAILY,
    val boundaryHour: Int = 0, // carrier midnight offset
    val nightStartHour: Int = 2, // DAILY_NIGHT free window (FR-25)
    val nightEndHour: Int = 5,
    val rollover: Boolean = false, // daily leftover rolls over (default: Jio-style no)
    val isActive: Int = 1,
    val createdAt: String = ""
)

@Entity(
    tableName = "daily_snapshots",
    indices = [Index("date", unique = true), Index("planId")]
)
data class DailySnapshot(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val totalMbUsed: Int = 0,
    val wifiMbUsed: Int = 0,
    val mobileMbUsed: Int = 0,
    val unattributedMb: Int = 0, // §6.3: total − Σ apps (tether/VPN/system)
    val overrideMb: Int? = null, // FR-22 manual correction, null = none
    val planId: Long? = null,
    val mobileBytes: Long = 0,
    val wifiBytes: Long = 0,
    val collectedAtMs: Long = 0,
    val partialDay: Boolean = false,
    val timezoneId: String = ""
) {
    fun effectiveMobile(): Int = overrideMb ?: mobileMbUsed
}

@Entity(
    tableName = "app_usage",
    indices = [Index(value = ["date", "packageName"], unique = true), Index("uid")]
)
data class AppUsage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val packageName: String,
    val appName: String,
    val uid: Int = -1,
    val mobileMb: Int = 0,
    val wifiMb: Int = 0,
    val fgMb: Int = 0, // T3 split (§6.2), -1 = not measured
    val bgMb: Int = 0,
    val rxBytes: Long = 0,
    val txBytes: Long = 0
)

@Entity(
    tableName = "hourly_snapshots",
    indices = [Index(value = ["date", "hour"], unique = true)]
)
data class HourlySnapshot(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val hour: Int, // 0..23 device zone
    val mobileMb: Int = 0,
    val wifiMb: Int = 0
)

@Entity(
    tableName = "uid_map",
    indices = [Index(value = ["uid", "packageName"], unique = true)]
)
data class UidMap(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: Int,
    val packageName: String,
    val firstSeen: Long = 0,
    val lastSeen: Long = 0
)

@Entity(tableName = "app_limits")
data class AppLimit(
    @PrimaryKey val packageName: String,
    val dailyCapMb: Int,
    val alertsEnabled: Boolean = true
)

@Entity(tableName = "alert_log")
data class AlertLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rule: String, // R1..R6, THRESHOLD, CYCLE_END, APP_CAP
    val subject: String, // package, date, or "daily"
    val period: String, // YYYY-MM-DD or hour key
    val firedAt: Long = 0,
    val payloadHash: Int = 0 // audit trail (§12 repudiation)
)

@Entity(tableName = "sync_state")
data class SyncState(
    @PrimaryKey val id: Int = 1, // singleton row
    val lastSuccessMs: Long = 0, // watermark: hours < this are converged
    val lastAttemptMs: Long = 0,
    val failures: Int = 0
)
