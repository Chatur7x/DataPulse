package com.datapulse.app.domain
import com.datapulse.app.speed.*
import org.junit.Assert.*
import org.junit.Test
class SpeedMathTest {
 @Test fun oneMegabytePerSecondIsEightMegabits(){assertEquals(8.0,throughputMbps(1_000_000,1_000_000_000),.0001)}
 @Test fun gigabitConversion(){assertEquals(1000.0,throughputMbps(125_000_000,1_000_000_000),.0001)}
 @Test fun medianResistsOutlier(){assertEquals(20.0,medianSample(listOf(10.0,20.0,999.0)),.001)}
 @Test fun evenMedian(){assertEquals(25.0,medianSample(listOf(10.0,20.0,30.0,40.0)),.001)}
 @Test fun jitterIsSuccessiveVariation(){assertEquals(15.0,sampleJitter(listOf(10.0,20.0,40.0)),.001)}
 @Test(expected=IllegalArgumentException::class)fun zeroElapsedRejected(){throughputMbps(100,0)}
 @Test(expected=IllegalArgumentException::class)fun emptyLatencyRejected(){medianSample(emptyList())}
}
