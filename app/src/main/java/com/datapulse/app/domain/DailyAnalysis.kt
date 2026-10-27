package com.datapulse.app.domain

data class UsageDay(val date: String, val mobileBytes: Long, val wifiBytes: Long)
data class UsageAnalysis(val observedDays: Int, val totalBytes: Long, val averageBytes: Long,
 val peakDate: String?, val peakBytes: Long, val mobileShare: Float, val changePercent: Double?)

fun analyzeUsage(days: List<UsageDay>, previous: List<UsageDay> = emptyList()): UsageAnalysis {
 require(days.all { it.mobileBytes >= 0 && it.wifiBytes >= 0 })
 val mobile=days.sumOf { it.mobileBytes }
 val total=mobile+days.sumOf { it.wifiBytes }
 val peak=days.maxByOrNull { it.mobileBytes+it.wifiBytes }
 val old=previous.sumOf { it.mobileBytes+it.wifiBytes }
 // Compare averages only when both periods contain the same number of observed days.
 val change=if(old>0 && previous.size==days.size) (total-old)*100.0/old else null
 return UsageAnalysis(days.size,total,if(days.isEmpty())0 else total/days.size,
  if(total>0)peak?.date else null,peak?.let { it.mobileBytes+it.wifiBytes }?:0,if(total==0L)0f else mobile.toFloat()/total,change)
}
