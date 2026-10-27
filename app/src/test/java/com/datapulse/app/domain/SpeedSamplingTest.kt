package com.datapulse.app.domain

import com.datapulse.app.speed.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import org.junit.Assert.*
import org.junit.Test

class SpeedSamplingTest {
 @Test fun adaptivePayloadTracksMeasuredRate(){assertEquals(750000,adaptiveChunkBytes(1000000,1000000000))}
 @Test fun slowLinkKeepsBoundedMinimum(){assertEquals(65536,adaptiveChunkBytes(100,1000000000))}
 @Test fun fastLinkKeepsBoundedMaximum(){assertEquals(8388608,adaptiveChunkBytes(100000000,1000))}
 @Test(expected=IllegalArgumentException::class)fun invalidTimingRejected(){adaptiveChunkBytes(1,0)}
 @Test fun partialLastRequestUsesOnlyRemainingBudget(){val a=AtomicLong(95);assertEquals(5,reservePayload(a,100,32));assertEquals(0,reservePayload(a,100,32));assertEquals(100,a.get())}
 @Test fun fourConcurrentStreamsCannotExceedConsentBudget(){
  val pool=Executors.newFixedThreadPool(4);val used=AtomicLong(0)
  try{
   val sums=(1..4).map{pool.submit<Long>{var total=0L;while(true){val n=reservePayload(used,67108864,333333);if(n==0)break;total+=n};total}}.sumOf{it.get()}
   assertEquals(67108864L,sums);assertEquals(sums,used.get())
  }finally{pool.shutdownNow()}
 }
}
