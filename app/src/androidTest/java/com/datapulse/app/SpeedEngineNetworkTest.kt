package com.datapulse.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.datapulse.app.speed.SpeedEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import com.datapulse.app.speed.evidence
import com.datapulse.app.speed.exportJson
import com.datapulse.app.speed.samples
import com.datapulse.app.speed.throughputMbps

/** Opt-in live integration test. Run explicitly on the dedicated emulator. */
@RunWith(AndroidJUnit4::class)
class SpeedEngineNetworkTest {
 @Test(timeout=120000)fun realEndpointTransfersProduceFiniteResultsWithinPayloadCap()=runBlocking {
  val result=SpeedEngine(ApplicationProvider.getApplicationContext()).run(false){}
  assertTrue(result.downloadMbps.isFinite()&&result.downloadMbps>0)
  assertTrue(result.uploadMbps.isFinite()&&result.uploadMbps>0)
  assertTrue(result.latencyMs>=0)
  assertTrue(result.payloadBytes in 1..67_108_864L)
  val raw=result.evidence()!!
  assertEquals(10,raw.samples("idleHttpMs").size)
  assertTrue(raw.samples("downloadLoadedHttpMs").isNotEmpty())
  assertTrue(raw.samples("uploadLoadedHttpMs").isNotEmpty())
  assertEquals(result.downloadMbps,throughputMbps(raw.getLong("downloadBytes"),raw.getLong("downloadNanos")),.000001)
  assertEquals(result.uploadMbps,throughputMbps(raw.getLong("uploadAcknowledgedBytes"),raw.getLong("uploadNanos")),.000001)
  assertEquals(result.payloadBytes,262144L+raw.getLong("downloadBytes")+raw.getLong("uploadAcknowledgedBytes"))
  for(prefix in listOf("download","upload")){
   val times=raw.getJSONArray(prefix+"TraceElapsedNanos")
   val bytes=raw.getJSONArray(prefix+"TracePayloadBytes")
   val rates=raw.getJSONArray(prefix+"MbpsTrace")
   assertEquals(times.length(),bytes.length());assertEquals(times.length(),rates.length())
   var previousTime=0L;var previousBytes=0L
   for(i in 0 until times.length()){
    assertTrue(times.getLong(i)>previousTime);assertTrue(bytes.getLong(i)>=previousBytes)
    assertEquals(throughputMbps(bytes.getLong(i)-previousBytes,times.getLong(i)-previousTime),rates.getDouble(i),.000001)
    previousTime=times.getLong(i);previousBytes=bytes.getLong(i)
   }
  }
  ApplicationProvider.getApplicationContext<android.content.Context>().filesDir.resolve("speed-engine-evidence.json").writeText(result.exportJson())
  println("DATAPULSE_MEASURED download=${result.downloadMbps} upload=${result.uploadMbps} latency=${result.latencyMs} payload=${result.payloadBytes} note=${result.qualityNote}")
 }
}
