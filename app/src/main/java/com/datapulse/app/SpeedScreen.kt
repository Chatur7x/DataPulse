package com.datapulse.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.datapulse.app.speed.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private fun number(value:Double)=String.format(Locale.ROOT,"%.2f",value)

@Composable fun SpeedScreen(vm:SpeedViewModel=hiltViewModel(),onMeasurementChanged:()->Unit={}) {
 val lifecycle=LocalLifecycleOwner.current.lifecycle
 DisposableEffect(lifecycle){
  val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_STOP)vm.cancel()}
  lifecycle.addObserver(observer)
  onDispose{lifecycle.removeObserver(observer);vm.cancel()}
 }
 val ui by vm.ui.collectAsStateWithLifecycle()
 val history by vm.history.collectAsStateWithLifecycle()
 var large by remember{mutableStateOf(false)}
 var consent by remember{mutableStateOf(false)}
 var method by remember{mutableStateOf(false)}
 var selected by remember{mutableStateOf<String?>(null)}
 var raw by remember{mutableStateOf(false)}
 var exportText by remember{mutableStateOf("")}
 var exportStatus by remember{mutableStateOf<String?>(null)}
 LaunchedEffect(ui.running,ui.result?.id,ui.error,selected){onMeasurementChanged()}
 val ctx=LocalContext.current;val scope=rememberCoroutineScope()
 val save=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){uri->
  if(uri!=null)scope.launch{
   exportStatus=runCatching{withContext(Dispatchers.IO){ctx.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use{it.write(exportText)}?:error("Cannot open destination")};"Measurement JSON saved."}.getOrElse{"Could not save: ${it.message}"}
  }
 }
 Heading("Know your connection.","Real bytes. Clear evidence.")
 if(ui.running)Panel{
  Text(ui.progress.stage,style=MaterialTheme.typography.titleLarge)
  val measuring=ui.progress.stage=="Measuring download"||ui.progress.stage=="Measuring upload"
  Text(if(measuring)number(ui.progress.liveMbps) else "—",fontSize=42.sp,fontWeight=FontWeight.SemiBold)
  Text(if(measuring)"Mbps · live sample, about every 250 ms" else "Preparing real measurements")
  LinearProgressIndicator(progress={ui.progress.fraction},modifier=Modifier.fillMaxWidth())
  SpeedTrace(ui.progress.samples)
  Text("${formatBytes(ui.progress.payloadBytes)} payload handled · four adaptive streams",style=MaterialTheme.typography.bodySmall)
  Text("Final throughput uses completed payload bytes over the entire phase. The live trace may fluctuate.",style=MaterialTheme.typography.bodySmall)
  OutlinedButton(onClick={vm.cancel()},modifier=Modifier.fillMaxWidth()){Text("Cancel test")}
 }
 ui.error?.let{Panel{Text(it,color=MaterialTheme.colorScheme.error)}}
 // Keep the result above setup controls so completion is immediately visible.
 val result=if(ui.running||ui.error!=null)null else selected?.let{id->history.find{it.id==id}}?:ui.result?:history.firstOrNull()
 result?.let{r->Panel{
  Text("Measured result",style=MaterialTheme.typography.titleLarge)
  Text(java.text.DateFormat.getDateTimeInstance().format(java.util.Date(r.timestampMs)),style=MaterialTheme.typography.bodySmall)
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
   Column{Text("Download");Text(number(r.downloadMbps),fontSize=30.sp,fontWeight=FontWeight.SemiBold);Text("Mbps")}
   Column(horizontalAlignment=Alignment.End){Text("Upload");Text(number(r.uploadMbps),fontSize=30.sp,fontWeight=FontWeight.SemiBold);Text("Mbps")}
  }
  Text("${r.server} · ${r.networkType}",fontWeight=FontWeight.SemiBold)
  Text("Idle HTTP latency ${number(r.latencyMs)} ms · jitter ${number(r.jitterMs)} ms")
  val evidence=remember(r.id){r.evidence()}
  evidence?.let{e->
   for((label,key) in listOf("Download loaded HTTP" to "downloadLoadedHttpMs","Upload loaded HTTP" to "uploadLoadedHttpMs")){
    val samples=e.samples(key)
    if(samples.isEmpty())Text("$label: unavailable")
    else Text("$label: ${number(medianSample(samples))} ms median · ${number(sampleJitter(samples))} ms jitter\n${number(samples.min())}–${number(samples.max())} ms range · ${samples.size} samples",style=MaterialTheme.typography.bodySmall)
   }
   Text("Download throughput trace",style=MaterialTheme.typography.labelLarge);SpeedTrace(e.samples("downloadMbpsTrace"))
   Text("Upload throughput trace",style=MaterialTheme.typography.labelLarge);SpeedTrace(e.samples("uploadMbpsTrace"))
   Text("HTTP probe failures: download ${e.optInt("downloadProbeFailures")}, upload ${e.optInt("uploadProbeFailures")}. Packet loss: not measured.",style=MaterialTheme.typography.bodySmall)
  }
  Text("Download ${number(r.downloadMs/1000.0)} s · upload ${number(r.uploadMs/1000.0)} s · ${formatBytes(r.payloadBytes)} payload",style=MaterialTheme.typography.bodySmall)
  Text(r.qualityNote,style=MaterialTheme.typography.bodySmall)
  TextButton(onClick={raw=!raw}){Text(if(raw)"Hide measurement evidence" else "Show measurement evidence")}
  if(raw){
   evidence?.let{e->Text("Acknowledged download: ${e.getLong("downloadBytes")} bytes\nAcknowledged upload: ${e.getLong("uploadAcknowledgedBytes")} bytes\nElapsed download: ${e.getLong("downloadNanos")} ns\nElapsed upload: ${e.getLong("uploadNanos")} ns\nFormula: bytes × 8000 ÷ nanoseconds = Mbps\nMethod v${e.getInt("methodVersion")} · ${e.getInt("streams")} HTTP/1.1 streams",style=MaterialTheme.typography.bodySmall)}?:Text("This older result has no raw samples.")
   OutlinedButton(onClick={exportText=r.exportJson();save.launch("DataPulse-${r.id}.json")},modifier=Modifier.fillMaxWidth()){Text("Save original measurement JSON")}
  }
  exportStatus?.let{Text(it,style=MaterialTheme.typography.bodySmall)}
 }}
 Panel{
  Text("Internet speed test",style=MaterialTheme.typography.titleLarge)
  Text("DataPulse measures real transfers to Cloudflare. Results are specific to this server and route; they may differ from Ookla.",style=MaterialTheme.typography.bodyMedium)
  Row(verticalAlignment=Alignment.CenterVertically){Text("Extended sample · up to 256 MiB",Modifier.weight(1f));Switch(large,{large=it},enabled=!ui.running)}
  Text(if(large)"Up to 256 MiB of payload plus network overhead." else "Up to 64 MiB of payload plus network overhead. Fast connections may hit the cap before a stable sample is collected.",style=MaterialTheme.typography.bodySmall)
  Text("Cloudflare receives your IP and test traffic. Your usage history and app list are not uploaded.",style=MaterialTheme.typography.bodySmall)
  Row(verticalAlignment=Alignment.CenterVertically){Checkbox(consent,{consent=it},enabled=!ui.running);Text("I agree to use data for this test",style=MaterialTheme.typography.bodyMedium)}
  Button(onClick={selected=null;raw=false;vm.start(large)},enabled=consent&&!ui.running,modifier=Modifier.fillMaxWidth()){Text("Start speed test")}
 }
 Panel{
  TextButton(onClick={method=!method}){Text(if(method)"Hide methodology" else "How we measure")}
  AnimatedVisibility(method){Text("A warm-up precedes ten idle HTTP probes. Download and upload each run four parallel HTTP/1.1 streams with adaptive payloads for up to twelve seconds before stopping new requests, or until the data cap. In-flight requests may finish later. Independent HTTP probes measure latency while each phase is busy. Final throughput is completed payload bytes divided by full monotonic phase duration, including request overhead. Upload requests count only after a successful server response. Live samples use bytes read or handed to the HTTP transport and are provisional. Raw samples, byte counts, nanosecond durations, and probe failures are saved locally and exportable as JSON. A network change, failed transfer, timeout, cancellation, or app backgrounding prevents an incomplete result being saved. HTTP probes do not measure packet loss, ICMP ping, ISP billing, or guaranteed line capacity. No invented accuracy score is shown.",style=MaterialTheme.typography.bodySmall)}
 }
 if(history.isNotEmpty())Panel{
  Text("Recent measurements",style=MaterialTheme.typography.titleMedium)
  history.take(10).forEach{r->
   TextButton(onClick={vm.dismissError();selected=r.id;raw=false}){Column(horizontalAlignment=Alignment.Start){
    Text(java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT,java.text.DateFormat.SHORT).format(java.util.Date(r.timestampMs)))
    Text("↓ ${number(r.downloadMbps)}  ↑ ${number(r.uploadMbps)} Mbps · ${r.networkType}")
   }}
  }
 }
}

@Composable private fun SpeedTrace(samples:List<Double>){
 val color=MaterialTheme.colorScheme.primary
 Canvas(Modifier.fillMaxWidth().height(64.dp)){
  drawLine(color.copy(alpha=.15f),Offset(0f,size.height),Offset(size.width,size.height),1.dp.toPx())
  if(samples.size>1){val peak=samples.max().coerceAtLeast(1.0);samples.zipWithNext().forEachIndexed{i,(a,b)->
   drawLine(color,Offset(i*size.width/(samples.size-1),size.height*(1-a/peak).toFloat()),Offset((i+1)*size.width/(samples.size-1),size.height*(1-b/peak).toFloat()),2.dp.toPx())
  }}
 }
}
