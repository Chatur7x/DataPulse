package com.datapulse.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.room.Room
import com.datapulse.app.data.PulseRepository
import com.datapulse.app.data.local.AppDatabase
import com.datapulse.app.data.local.RechargePlan
import com.datapulse.app.data.stats.UidBucket
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RepositoryTest {
 @Test fun repeatedCollectionReplacesDayAndPreservesRealRxTx() = runBlocking {
  val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
  try {
   val repo = PulseRepository(db)
   val mobile = listOf(UidBucket(10001, 2_097_152, 1_048_576, 0))
   repeat(2) { repo.storeDay("2026-10-26", mobile, emptyList(), 3_145_728, 0) { "test.app" } }
   val day = db.daily().dayOnce("2026-10-26")!!
   val apps = db.apps().topOn("2026-10-26", 10)
   assertEquals(3, day.mobileMbUsed)
   assertEquals(1, apps.size)
   assertEquals(2_097_152L, apps.single().rxBytes)
   assertEquals(1_048_576L, apps.single().txBytes)
   repo.storeDay("2026-10-26", emptyList(), emptyList(), 0, 0) { null }
   assertTrue(db.apps().topOn("2026-10-26", 10).isEmpty())
  } finally { db.close() }
 }
 @Test fun rechargeArchivesPreviousPlanAtomically() = runBlocking {
  val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
  try {
   val repo = PulseRepository(db)
   val plan = RechargePlan(carrierName = "First", rechargeAmount = 299, dailyDataMb = 1430, cycleDays = 28, cycleStartDate = "2026-10-26")
   repo.savePlanAtomically(plan)
   repo.savePlanAtomically(plan.copy(carrierName = "Second"))
   assertEquals("Second", db.plans().activePlanOnce()!!.carrierName)
  } finally { db.close() }
 }
}
