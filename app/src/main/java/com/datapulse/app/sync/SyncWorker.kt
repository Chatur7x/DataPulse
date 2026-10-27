package com.datapulse.app.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.datapulse.app.data.PulseRepository
import com.datapulse.app.data.stats.NetworkStatsSource
import com.datapulse.app.data.stats.hasUsageAccess
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

@HiltWorker
class SyncWorker @AssistedInject constructor(
 @Assisted ctx: Context, @Assisted params: WorkerParameters,
 private val source: NetworkStatsSource, private val repo: PulseRepository,
 private val alerts: AlertEngine, private val settings: SettingsStore
) : CoroutineWorker(ctx, params) {
 override suspend fun doWork(): Result = repo.syncMutex.withLock {
  if (!hasUsageAccess(applicationContext)) return@withLock Result.failure()
  if (settings.ds.data.first()[KEY_TRACKING_ENABLED] == false) return@withLock Result.success()
  try {
   val zone = ZoneId.systemDefault()
   val now = System.currentTimeMillis()
   val today = LocalDate.now(zone)
   val pm = applicationContext.packageManager
   val count = if (repo.watermark() == 0L) 31 else 7
   for (offset in (count - 1) downTo 0) {
    val date = today.minusDays(offset.toLong())
    val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
    val end = minOf(date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(), now)
    if (end <= start) continue
    repo.storeDay(date.toString(), source.perUidMobile(start, end), source.perUidWifi(start, end),
      source.mobileTotalBytes(start, end), source.wifiTotalBytes(start, end)) { uid ->
      pm.getPackagesForUid(uid)?.singleOrNull()
    }
   }
   repo.advanceWatermark(now)
   alerts.evaluate(zone)
   com.datapulse.app.widget.updatePulseWidgets(applicationContext)
   Result.success()
  } catch (e: CancellationException) { throw e
  } catch (e: SecurityException) { repo.markAttempt(true); Result.failure()
  } catch (e: Exception) { repo.markAttempt(true); if (runAttemptCount < 3) Result.retry() else Result.failure() }
 }
 companion object {
  fun schedule(ctx: Context) {
   val req = PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.HOURS)
    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES).build()
   WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("pulse-sync", ExistingPeriodicWorkPolicy.KEEP, req)
  }
  fun syncNow(ctx: Context) {
   val req = OneTimeWorkRequestBuilder<SyncWorker>().build()
   WorkManager.getInstance(ctx).enqueueUniqueWork("pulse-sync-now", ExistingWorkPolicy.KEEP, req)
  }
 }
}
