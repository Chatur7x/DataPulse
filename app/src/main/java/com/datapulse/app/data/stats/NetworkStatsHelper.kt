package com.datapulse.app.data.stats

import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

// §5 Listing 1: API 23–35 compatible (unsafeCheck on Q+, deprecated fallback below).
fun hasUsageAccess(context: Context): Boolean {
    val ops = context.getSystemService(AppOpsManager::class.java)
    val op = AppOpsManager.OPSTR_GET_USAGE_STATS
    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        ops.unsafeCheckOpNoThrow(op, Process.myUid(), context.packageName)
    else
        @Suppress("DEPRECATION") // required below API 29
        ops.checkOpNoThrow(op, Process.myUid(), context.packageName)
    return mode == AppOpsManager.MODE_ALLOWED
}

fun openUsageAccessSettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    })
}

// §6.1: the acquisition seam — the ONLY file that talks to the platform API.
// UI + worker tests inject FakeNetworkStatsSource via Hilt test module; never real in CI.
interface NetworkStatsSource {
    fun mobileTotalBytes(start: Long, end: Long): Long
    fun wifiTotalBytes(start: Long, end: Long): Long
    fun perUidMobile(start: Long, end: Long): List<UidBucket>
    fun perUidWifi(start: Long, end: Long): List<UidBucket>
    fun perUidFgBgSplit(uid: Int, start: Long, end: Long): FgBgSplit
}

data class UidBucket(val uid: Int, val rxBytes: Long, val txBytes: Long, val ts: Long)
data class FgBgSplit(val fgBytes: Long, val bgBytes: Long)

// §6.2 tiers: T1 totals = summary (cheap); T2 per-UID = queryDetails/hour;
// T3 fg/bg = queryDetailsForUidTagState, top-20 apps only (FR-08).
class SystemNetworkStatsSource @Inject constructor(
    @ApplicationContext private val ctx: Context
) : NetworkStatsSource {
    private val nsm = ctx.getSystemService(NetworkStatsManager::class.java)

    override fun mobileTotalBytes(start: Long, end: Long): Long =
        totalBytes(ConnectivityManager.TYPE_MOBILE, start, end)

    override fun wifiTotalBytes(start: Long, end: Long): Long =
        totalBytes(ConnectivityManager.TYPE_WIFI, start, end)

    private fun totalBytes(type: Int, start: Long, end: Long): Long {
        val stats = nsm.querySummary(type, null, start, end)
        val b = NetworkStats.Bucket()
        var total = 0L
        try {
            while (stats.hasNextBucket()) {
                stats.getNextBucket(b)
                total += b.rxBytes + b.txBytes
            }
        } finally {
            stats.close()
        }
        return total.coerceAtLeast(0L)
    }

    override fun perUidMobile(start: Long, end: Long): List<UidBucket> =
        perUid(ConnectivityManager.TYPE_MOBILE, start, end)

    override fun perUidWifi(start: Long, end: Long): List<UidBucket> =
        perUid(ConnectivityManager.TYPE_WIFI, start, end)

    private fun perUid(type: Int, start: Long, end: Long): List<UidBucket> {
        val stats = nsm.querySummary(type, null, start, end)
        val out = ArrayList<UidBucket>()
        val b = NetworkStats.Bucket()
        try {
            while (stats.hasNextBucket()) {
                stats.getNextBucket(b)
                if (b.uid < 0) continue // network-level rows: skip, do not abort
                out += UidBucket(b.uid, b.rxBytes, b.txBytes, b.startTimeStamp)
            }
        } finally {
            stats.close()
        }
        return out
    }

    override fun perUidFgBgSplit(uid: Int, start: Long, end: Long): FgBgSplit {
        if (Build.VERSION.SDK_INT < 28) return FgBgSplit(-1, -1)
        // §6.1: buckets already carry fg/bg state — no UsageStatsManager cross-ref needed.
        fun stateBytes(state: Int): Long {
            val s = nsm.queryDetailsForUidTagState(
                ConnectivityManager.TYPE_MOBILE, null, start, end, uid,
                NetworkStats.Bucket.TAG_NONE, state
            )
            val b = NetworkStats.Bucket()
            var t = 0L
            try {
                while (s.hasNextBucket()) {
                    s.getNextBucket(b)
                    t += b.rxBytes + b.txBytes
                }
            } finally {
                s.close()
            }
            return t
        }
        return FgBgSplit(
            fgBytes = stateBytes(NetworkStats.Bucket.STATE_FOREGROUND),
            bgBytes = stateBytes(NetworkStats.Bucket.STATE_DEFAULT)
        )
    }
}

// Test seam: scripted platform double. Hilt test module binds this over System.
class FakeNetworkStatsSource(
    private val mobilePerHour: Map<Long, List<UidBucket>> = emptyMap(),
    private val wifiPerHour: Map<Long, List<UidBucket>> = emptyMap()
) : NetworkStatsSource {
    private fun inRange(rows: Map<Long, List<UidBucket>>, s: Long, e: Long) =
        rows.filterKeys { it in s until e }.values.flatten()

    override fun mobileTotalBytes(s: Long, e: Long) =
        inRange(mobilePerHour, s, e).sumOf { it.rxBytes + it.txBytes }

    override fun wifiTotalBytes(s: Long, e: Long) =
        inRange(wifiPerHour, s, e).sumOf { it.rxBytes + it.txBytes }

    override fun perUidMobile(s: Long, e: Long) = inRange(mobilePerHour, s, e)
    override fun perUidWifi(s: Long, e: Long) = inRange(wifiPerHour, s, e)
    override fun perUidFgBgSplit(uid: Int, s: Long, e: Long) = FgBgSplit(0, 0)
}
