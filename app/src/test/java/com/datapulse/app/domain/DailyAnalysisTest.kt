package com.datapulse.app.domain
import org.junit.Assert.*
import org.junit.Test

class DailyAnalysisTest {
 @Test fun emptyIsNotZeroDaysMeasured(){val r=analyzeUsage(emptyList());assertEquals(0,r.observedDays);assertNull(r.peakDate);assertNull(r.changePercent)}
 @Test fun missingDaysAreNotInvented(){val r=analyzeUsage(listOf(UsageDay("2026-10-01",100,300),UsageDay("2026-10-03",400,0)));assertEquals(2,r.observedDays);assertEquals(800L,r.totalBytes);assertEquals(400L,r.averageBytes);assertEquals(.625f,r.mobileShare,.001f)}
 @Test fun equalObservedPeriodsCompare(){val r=analyzeUsage(listOf(UsageDay("now",200,100)),listOf(UsageDay("before",100,100)));assertEquals(50.0,r.changePercent!!,.001)}
 @Test fun unmatchedPeriodsDoNotCompare(){val r=analyzeUsage(listOf(UsageDay("now",200,100)),listOf(UsageDay("a",1,1),UsageDay("b",1,1)));assertNull(r.changePercent)}
 @Test fun zeroUsageHasNoArtificialPeak(){assertNull(analyzeUsage(listOf(UsageDay("a",0,0))).peakDate)}
 @Test fun byteTotalsUseLong(){assertEquals(6_000_000_000L,analyzeUsage(listOf(UsageDay("a",3_000_000_000,3_000_000_000))).totalBytes)}
 @Test(expected=IllegalArgumentException::class)fun rejectsNegativeCounters(){analyzeUsage(listOf(UsageDay("a",-1,0)))}
}
