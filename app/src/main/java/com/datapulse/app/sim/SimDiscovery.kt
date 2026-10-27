package com.datapulse.app.sim

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class CarrierPlan(val title:String,val allowanceBytes:Long?,val unlimited:Boolean,val usageBytes:Long?,val usageAtMs:Long?,val cycle:String?)
data class DetectedSim(val id:Int,val slot:Int,val carrier:String,val label:String,val operatorKey:String,val embedded:Boolean,val defaultData:Boolean,val plans:List<CarrierPlan>,val planStatus:String)
data class SimDiscoveryState(val permission:Boolean=false,val sims:List<DetectedSim> = emptyList(),val message:String?=null)

class SimDiscovery @Inject constructor(@ApplicationContext private val ctx:Context){
 suspend fun read():SimDiscoveryState=withContext(Dispatchers.IO){
  if(ContextCompat.checkSelfPermission(ctx,Manifest.permission.READ_PHONE_STATE)!=PackageManager.PERMISSION_GRANTED)return@withContext SimDiscoveryState()
  try{
   val sm=ctx.getSystemService(SubscriptionManager::class.java)?:return@withContext SimDiscoveryState(true,message="SIM services are unavailable on this device.")
   val defaultId=if(Build.VERSION.SDK_INT>=24)SubscriptionManager.getDefaultDataSubscriptionId() else SubscriptionManager.INVALID_SUBSCRIPTION_ID
   val sims=(sm.activeSubscriptionInfoList?:emptyList()).map{info->
    var status="Android does not expose carrier plan information on this version."
    val plans=if(Build.VERSION.SDK_INT>=28)try{
     val published=sm.getSubscriptionPlans(info.subscriptionId)?:emptyList()
     status=if(published.isEmpty())"No carrier plan was published to Android." else "Carrier-published Android records · availability and freshness depend on your carrier."
     published.map{plan->
      val iterator=plan.cycleIterator();val range=if(iterator.hasNext())iterator.next() else null
      CarrierPlan(plan.title?.toString()?.takeIf{it.isNotBlank()}?:"Carrier plan",plan.dataLimitBytes.takeIf{it>=0&&it!=Long.MAX_VALUE},plan.dataLimitBytes==Long.MAX_VALUE,
       plan.dataUsageBytes.takeIf{it>=0&&it!=Long.MAX_VALUE},plan.dataUsageTime.takeIf{it>0},range?.let{"${it.lower} to ${it.upper} (end exclusive)"})
     }
    }catch(_:SecurityException){status="Android restricts automatic plan access to carrier-authorized apps. Check your carrier account or confirm a recharge below.";emptyList()}
     catch(_:UnsupportedOperationException){status="This device does not provide carrier plan records.";emptyList()}
     catch(_:Exception){status="Carrier plan records are temporarily unavailable. SIM detection is still available.";emptyList()} else emptyList()
    val operator=if(Build.VERSION.SDK_INT>=29)"${info.mccString.orEmpty()}:${info.mncString.orEmpty()}" else "${info.mcc}:${info.mnc}"
    DetectedSim(info.subscriptionId,info.simSlotIndex,info.carrierName?.toString()?.ifBlank{"Unknown carrier"}?:"Unknown carrier",info.displayName?.toString().orEmpty(),operator,
     Build.VERSION.SDK_INT>=28&&info.isEmbedded,info.subscriptionId==defaultId,plans,status)
   }
   SimDiscoveryState(true,sims)
  }catch(_:SecurityException){SimDiscoveryState(message="Phone permission was revoked. Grant access to detect active SIMs again.")}
   catch(_:UnsupportedOperationException){SimDiscoveryState(true,message="This device does not support active SIM discovery.")}
   catch(_:Exception){SimDiscoveryState(true,message="SIM information is temporarily unavailable. Try Refresh SIMs.")}
 }
}
