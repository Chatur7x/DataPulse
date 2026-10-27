package com.datapulse.app.sim

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.time.LocalDate

@Entity(tableName="sim_recharges")
data class SimRecharge(
 @PrimaryKey(autoGenerate=true) val id:Long=0,
 val subscriptionId:Int,val operatorKey:String,val carrierName:String,val title:String,
 val pricePaise:Long?,val dataBytes:Long?,val daily:Boolean,
 val validFrom:String,val validThrough:String,val confirmedAtMs:Long,val source:String
)
@Dao interface SimRechargeDao{
 @Query("SELECT * FROM sim_recharges ORDER BY confirmedAtMs DESC, id DESC") fun all():Flow<List<SimRecharge>>
 @Insert suspend fun insert(row:SimRecharge)
 @Query("DELETE FROM sim_recharges WHERE id=:id") suspend fun delete(id:Long)
}
fun SimRecharge.dateStatus(today:LocalDate=LocalDate.now()):String=when{
 today<LocalDate.parse(validFrom)->"Upcoming · entered dates"
 today>LocalDate.parse(validThrough)->"Expired · entered dates"
 else->"Within entered dates · not carrier-verified"
}
fun SimRecharge.belongsTo(subId:Int,key:String)=subscriptionId==subId&&operatorKey==key
fun rupeesToPaise(text:String):Long?=text.trim().takeIf{it.isNotEmpty()}?.let{
 val n=it.toBigDecimal();require(n>=BigDecimal.ZERO&&n<=BigDecimal("1000000"))
 n.multiply(BigDecimal(100)).longValueExact()
}
fun gbToBytes(text:String):Long?=text.trim().takeIf{it.isNotEmpty()}?.let{
 val n=it.toBigDecimal();require(n>=BigDecimal.ZERO&&n<=BigDecimal("10000"))
 n.multiply(BigDecimal("1000000000")).longValueExact()
}
data class ReceiptSuggestion(val rupees:String?,val gb:String?,val daily:Boolean?,val days:Int?)
fun simSlotLabel(slot:Int)=if(slot>=0)"SIM ${slot+1}" else "SIM (slot unavailable)"
/** Suggestions only: ambiguous values stay absent and dates always require confirmation. */
fun suggestRecharge(text:String):ReceiptSuggestion{
 require(text.length<=10000)
 fun unique(pattern:String):String?=Regex(pattern,RegexOption.IGNORE_CASE).findAll(text).map{it.groupValues[1]}.distinct().toList().singleOrNull()
 val price=unique("(?:₹|\\bRs\\.?|\\bINR)\\s*((?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)(?:\\.[0-9]{1,2})?)(?![0-9]|[.,][0-9])")?.replace(",","")
 val matches=Regex("(?<![0-9.,])([0-9]+(?:\\.[0-9]+)?)\\s*(GB|MB)\\b\\s*((?:/|per|a)\\s*day|daily|total|(?:/|per)\\s*cycle)?",RegexOption.IGNORE_CASE).findAll(text).map{
  val gb=it.groupValues[1].toBigDecimal().let{n->if(it.groupValues[2].equals("MB",true))n.divide(BigDecimal(1000)) else n}
  val qualifier=it.groupValues[3].lowercase()
  val daily=when{qualifier.isBlank()->null;qualifier.contains("day")||qualifier=="daily"->true;else->false}
  gb.stripTrailingZeros().toPlainString() to daily
 }.distinct().toList().singleOrNull()
 val days=unique("(?:validity\\s*[:=-]?|valid\\s+for)\\s*([0-9]{1,3})\\s*days?\\b")?.toIntOrNull()?.takeIf{it in 1..366}
 return ReceiptSuggestion(price,matches?.first,matches?.second,days)
}
