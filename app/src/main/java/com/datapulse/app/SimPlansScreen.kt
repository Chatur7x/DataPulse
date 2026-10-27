package com.datapulse.app

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.datapulse.app.presentation.SimPlansViewModel
import com.datapulse.app.sim.*
import java.time.LocalDate
import java.util.Locale

@Composable fun SimPlansScreen(vm:SimPlansViewModel=hiltViewModel(),onPageChanged:()->Unit={}){
 val state by vm.state.collectAsStateWithLifecycle();val records by vm.records.collectAsStateWithLifecycle()
 val busy by vm.busy.collectAsStateWithLifecycle();val error by vm.error.collectAsStateWithLifecycle()
 var editing by remember{mutableStateOf<DetectedSim?>(null)}
 BackHandler(enabled=editing!=null){editing=null}
 var removing by remember{mutableStateOf<SimRecharge?>(null)}
 var page by rememberSaveable{mutableIntStateOf(0)}
 var browserError by remember{mutableStateOf<String?>(null)}
 val ctx=LocalContext.current
 fun open(catalogue:CarrierCatalogue){browserError=runCatching{CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(ctx,Uri.parse(catalogue.url));null}.getOrElse{"No browser could open the carrier site. Install or enable a browser and try again."}}
 val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){vm.refresh()}
 val lifecycle=LocalLifecycleOwner.current.lifecycle
 DisposableEffect(lifecycle){val observer=LifecycleEventObserver{_,e->if(e==Lifecycle.Event.ON_RESUME)vm.refresh()};lifecycle.addObserver(observer);onDispose{lifecycle.removeObserver(observer)}}
 LaunchedEffect(Unit){vm.refresh()}
 LaunchedEffect(page,editing?.id){onPageChanged()}
 Heading("Every SIM, in focus.","Current plan records and live recharge catalogues, kept separate.")
 if(editing!=null){
  TextButton(onClick={editing=null}){Text("Back to SIMs")}
  RechargeEditor(editing!!,error,onSave={record->vm.save(record){editing=null}})
  return
 }
 Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
  FilterChip(page==0,{page=0},{Text("My SIMs")})
  FilterChip(page==1,{page=1},{Text("Recharge catalogue")})
 }
 browserError?.let{Text(it,color=MaterialTheme.colorScheme.error)}
 if(page==1){
  Panel{
   Text("Live official offers",style=MaterialTheme.typography.titleLarge)
   Text("Browse the carrier’s current catalogue in a secure browser tab. Prices, eligibility and availability come from the carrier; choose your circle or number on its site when required.")
   Text("Browsing an offer does not mean it is active on your SIM. DataPulse does not receive your carrier login, payment details or phone number.",style=MaterialTheme.typography.bodySmall)
  }
  val preferred=state.sims.mapNotNull{catalogueFor(it.carrier)}.distinct()
  (preferred+carrierCatalogues.filterNot{it in preferred}).forEach{c->Panel{
   Text(c.name,style=MaterialTheme.typography.titleLarge)
   val slots=state.sims.filter{catalogueFor(it.carrier)==c}.map{simSlotLabel(it.slot)}
   if(slots.isNotEmpty())Text("Detected on ${slots.joinToString()}",fontWeight=FontWeight.SemiBold)
   Text(Uri.parse(c.url).host.orEmpty(),style=MaterialTheme.typography.bodySmall)
   Text(if(c.name=="BSNL")"Use the official site to choose your service and circle; plans can vary by region." else "All offers published in this carrier’s live catalogue; the carrier determines eligibility.",style=MaterialTheme.typography.bodySmall)
   OutlinedButton(onClick={open(c)},modifier=Modifier.fillMaxWidth()){Text("Browse ${c.name} official plans")}
  }}
  Text("Jio, Airtel, Vi and BSNL directories are included. Other carriers are not yet supported in the catalogue. No cached price list or universal all-operators coverage is claimed.",style=MaterialTheme.typography.bodySmall)
  return
 }
 if(!state.permission)Panel{
  Text("Detect your SIMs",style=MaterialTheme.typography.titleLarge)
  Text("Optional Phone permission lets Android share active SIM carrier names and slots. We do not read phone numbers, SIM serials, SMS messages or call logs.")
  Button(onClick={permission.launch(Manifest.permission.READ_PHONE_STATE)},modifier=Modifier.fillMaxWidth()){Text("Allow SIM detection")}
  Text("This permission does not unlock restricted carrier billing records. You can browse recharge catalogues without granting it.",style=MaterialTheme.typography.bodySmall)
 }
 OutlinedButton(onClick={vm.refresh()},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text(if(busy)"Reading SIMs…" else "Refresh SIMs")}
 state.message?.let{Text(it,color=MaterialTheme.colorScheme.error)}
 error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
 if(state.permission&&!busy&&state.sims.isEmpty()&&state.message==null)Panel{Text("No active SIMs detected");Text("Disabled eSIM profiles and removed SIMs may not be exposed by Android. Saved recharge records are retained.",style=MaterialTheme.typography.bodySmall)}
 state.sims.forEach{sim->Panel{
  Text("SIM ${sim.slot+1} · ${sim.carrier}",style=MaterialTheme.typography.titleLarge)
  Text(listOfNotNull(if(sim.embedded)"eSIM" else "SIM",if(sim.defaultData)"Default mobile data" else null).joinToString(" · "),style=MaterialTheme.typography.labelLarge)
  if(sim.label.isNotBlank()&&sim.label!=sim.carrier)Text(sim.label)
  Text("Automatic carrier plan",fontWeight=FontWeight.SemiBold)
  Text(sim.planStatus,style=MaterialTheme.typography.bodySmall)
  sim.plans.forEach{plan->
   Text(plan.title,fontWeight=FontWeight.SemiBold)
   Text("Reported cycle allowance: ${if(plan.unlimited)"Unlimited" else plan.allowanceBytes?.let{formatBytes(it)}?:"Not provided"}")
   plan.cycle?.let{Text(it,style=MaterialTheme.typography.bodySmall)}
   if(plan.usageBytes!=null&&plan.usageAtMs!=null)Text("Carrier usage snapshot: ${formatBytes(plan.usageBytes)} at ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(plan.usageAtMs))}",style=MaterialTheme.typography.bodySmall)
   Text("Price and daily-reset rules are not supplied by this Android API.",style=MaterialTheme.typography.bodySmall)
  }
  val mine=records.filter{it.belongsTo(sim.id,sim.operatorKey)}
  Text("Confirmed recharge records",fontWeight=FontWeight.SemiBold)
  if(mine.isEmpty())Text("No recharge confirmed for this SIM. Carrier detection alone cannot identify the plan you bought.",style=MaterialTheme.typography.bodySmall)
  mine.take(8).forEach{r->RechargeRecord(r,onRemove={removing=r})}
  Button(onClick={editing=sim},modifier=Modifier.fillMaxWidth()){Text("Confirm recharge for ${simSlotLabel(sim.slot)}")}
  catalogueFor(sim.carrier)?.let{c->OutlinedButton(onClick={open(c)},modifier=Modifier.fillMaxWidth()){Text("Browse ${c.name} official plans")}}
 }}
 val unmatched=records.filter{r->state.sims.none{r.belongsTo(it.id,it.operatorKey)}}
 if(unmatched.isNotEmpty())Panel{
  Text("Other saved SIM records",style=MaterialTheme.typography.titleMedium)
  Text("These records cannot currently be linked to a detected subscription. SIM detection may be off, a SIM may have been removed, or Android may have assigned a new subscription ID.",style=MaterialTheme.typography.bodySmall)
  unmatched.forEach{r->RechargeRecord(r,onRemove={removing=r})}
 }
 Text("SIM records are kept separately from the phone-wide allowance in Overview. Aggregate measured mobile usage is not assigned to individual SIMs here. Dates and amounts entered by you are not carrier-verified.",style=MaterialTheme.typography.bodySmall)
 removing?.let{r->AlertDialog(onDismissRequest={removing=null},title={Text("Remove recharge record?")},text={Text("This removes only the saved ${r.title} record. It does not cancel a carrier plan or remove measured usage.")},confirmButton={TextButton(onClick={vm.delete(r.id);removing=null}){Text("Remove record")}},dismissButton={TextButton(onClick={removing=null}){Text("Cancel")}})}
}

@Composable private fun RechargeRecord(r:SimRecharge,onRemove:()->Unit){
 HorizontalDivider()
 Text(r.title,fontWeight=FontWeight.SemiBold)
 Text(r.dateStatus(),style=MaterialTheme.typography.labelMedium)
 Text("${r.validFrom} through ${r.validThrough}")
 Text("Price: ${r.pricePaise?.let{String.format(Locale.ROOT,"₹%.2f",it/100.0)}?:"Not entered"} · Data: ${r.dataBytes?.let{formatBytes(it)+(if(r.daily)" per day" else " per cycle") }?:"Not entered"}",style=MaterialTheme.typography.bodySmall)
 Text("${r.carrierName} · ${r.source}",style=MaterialTheme.typography.bodySmall)
 TextButton(onClick=onRemove){Text("Remove saved record")}
}

@Composable private fun RechargeEditor(sim:DetectedSim,saveError:String?,onSave:(SimRecharge)->Unit){
 var receipt by remember{mutableStateOf("")};var suggested by remember{mutableStateOf(false)}
 var name by rememberSaveable(sim.id){mutableStateOf("")};var price by rememberSaveable(sim.id){mutableStateOf("")}
 var gb by rememberSaveable(sim.id){mutableStateOf("")};var days by rememberSaveable(sim.id){mutableStateOf("")}
 var start by rememberSaveable(sim.id){mutableStateOf("")};var daily by rememberSaveable(sim.id){mutableStateOf<Boolean?>(null)}
 var error by remember{mutableStateOf<String?>(null)};var saving by remember{mutableStateOf(false)}
 LaunchedEffect(saveError){if(saveError!=null)saving=false}
 Panel{
  Text("Confirm ${simSlotLabel(sim.slot)} recharge",style=MaterialTheme.typography.titleLarge)
  Text(sim.carrier)
  Text("Optional: paste a carrier recharge confirmation. Suggestions stay on-device and require your review. Ambiguous fields are left blank.",style=MaterialTheme.typography.bodySmall)
  OutlinedTextField(receipt,{receipt=it.take(10000)},label={Text("Paste recharge confirmation")},modifier=Modifier.fillMaxWidth(),maxLines=5)
  OutlinedButton(onClick={val s=suggestRecharge(receipt);price=s.rupees.orEmpty();gb=s.gb.orEmpty();days=s.days?.toString().orEmpty();daily=s.daily;suggested=true;error="Review every suggested field and enter the actual start date."},enabled=receipt.isNotBlank()){Text("Extract suggestions")}
  OutlinedTextField(name,{name=it},label={Text("Plan name")},modifier=Modifier.fillMaxWidth())
  OutlinedTextField(price,{price=it},label={Text("Price paid in rupees · optional")},modifier=Modifier.fillMaxWidth())
  Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(daily==true,{daily=true},{Text("Daily reset")});FilterChip(daily==false,{daily=false},{Text("Cycle pool")})}
  OutlinedTextField(gb,{gb=it},label={Text("Data allowance in GB · optional")},modifier=Modifier.fillMaxWidth())
  OutlinedTextField(days,{days=it},label={Text("Validity in days")},modifier=Modifier.fillMaxWidth())
  OutlinedTextField(start,{start=it},label={Text("Actual start date · YYYY-MM-DD")},modifier=Modifier.fillMaxWidth())
  Text("Dates are inclusive; queued recharges may start later than the purchase date. Blank price/data means unknown, not zero. 1 GB = 1,000,000,000 bytes.",style=MaterialTheme.typography.bodySmall)
  (saveError?:error)?.let{Text(it,color=MaterialTheme.colorScheme.error)}
  Button(enabled=!saving,onClick={
   val row=runCatching{
    require(name.isNotBlank()&&name.length<=100)
    val from=LocalDate.parse(start);val count=days.toInt();require(count in 1..366)
    val bytes=gbToBytes(gb);require(bytes==null||bytes==0L||daily!=null)
    SimRecharge(subscriptionId=sim.id,operatorKey=sim.operatorKey,carrierName=sim.carrier,title=name.trim(),pricePaise=rupeesToPaise(price),dataBytes=bytes,daily=daily?:false,
     validFrom=from.toString(),validThrough=from.plusDays(count-1L).toString(),confirmedAtMs=System.currentTimeMillis(),source=if(suggested)"Receipt suggestions reviewed by you" else "Entered and confirmed by you")
   }.getOrNull()
   if(row==null)error="Check the plan name, exact price/data, a valid date, and validity from 1 to 366 days. Select Daily reset or Cycle pool when entering a nonzero allowance."
   else{saving=true;error=null;onSave(row)}
  },modifier=Modifier.fillMaxWidth()){Text(if(saving)"Saving…" else "Confirm and save recharge")}
 }
}
