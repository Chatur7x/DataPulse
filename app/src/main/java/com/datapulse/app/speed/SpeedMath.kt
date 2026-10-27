package com.datapulse.app.speed

import java.util.concurrent.atomic.AtomicLong

/** Target ~750 ms per request while keeping four streams active on fast links. */
fun adaptiveChunkBytes(bytes:Long,nanos:Long):Int {
 require(bytes>0 && nanos>0)
 return (bytes.toDouble()*750_000_000.0/nanos).coerceIn(65_536.0,8_388_608.0).toInt()
}

/** Atomic reservation: concurrent streams together can never exceed the consented cap. */
fun reservePayload(reserved:AtomicLong,limit:Long,requested:Int):Int {
 require(limit>=0 && requested>0)
 while(true){
  val before=reserved.get();val amount=minOf(requested.toLong(),(limit-before).coerceAtLeast(0))
  if(amount==0L)return 0
  if(reserved.compareAndSet(before,before+amount))return amount.toInt()
 }
}

fun throughputMbps(bytes:Long,nanoseconds:Long):Double {
 require(bytes>=0 && nanoseconds>0)
 return bytes*8000.0/nanoseconds
}
fun medianSample(samples:List<Double>):Double {
 require(samples.isNotEmpty() && samples.all { it.isFinite() && it>=0 })
 val s=samples.sorted();return if(s.size%2==0)(s[s.size/2-1]+s[s.size/2])/2 else s[s.size/2]
}
fun sampleJitter(samples:List<Double>):Double = if(samples.size<2)0.0 else samples.zipWithNext().map{kotlin.math.abs(it.second-it.first)}.average()
