package com.datapulse.app.speed

fun throughputMbps(bytes:Long,nanoseconds:Long):Double {
 require(bytes>=0 && nanoseconds>0)
 return bytes*8000.0/nanoseconds
}
fun medianSample(samples:List<Double>):Double {
 require(samples.isNotEmpty() && samples.all { it.isFinite() && it>=0 })
 val s=samples.sorted();return if(s.size%2==0)(s[s.size/2-1]+s[s.size/2])/2 else s[s.size/2]
}
fun sampleJitter(samples:List<Double>):Double = if(samples.size<2)0.0 else samples.zipWithNext().map{kotlin.math.abs(it.second-it.first)}.average()
