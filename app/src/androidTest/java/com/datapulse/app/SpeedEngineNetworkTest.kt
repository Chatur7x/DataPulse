package com.datapulse.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.datapulse.app.speed.SpeedEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in live integration test. Run explicitly on the dedicated emulator. */
@RunWith(AndroidJUnit4::class)
class SpeedEngineNetworkTest {
 @Test(timeout=120000)fun realEndpointTransfersProduceFiniteResultsWithinPayloadCap()=runBlocking {
  val result=SpeedEngine(ApplicationProvider.getApplicationContext()).run(false){}
  assertTrue(result.downloadMbps.isFinite()&&result.downloadMbps>0)
  assertTrue(result.uploadMbps.isFinite()&&result.uploadMbps>0)
  assertTrue(result.latencyMs>=0)
  assertTrue(result.payloadBytes in 1..67_108_864L)
  println("DATAPULSE_MEASURED download=${result.downloadMbps} upload=${result.uploadMbps} latency=${result.latencyMs} payload=${result.payloadBytes} note=${result.qualityNote}")
 }
}
