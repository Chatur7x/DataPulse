package com.datapulse.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.datapulse.app.speed.SpeedViewModel
import java.util.Locale

@Composable fun SpeedScreen(vm:SpeedViewModel=hiltViewModel()) {
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
 Heading("Know your connection.","A real test, measured by DataPulse.")
 Panel {
  Text("Internet speed test",style=MaterialTheme.typography.titleLarge)
  Text("Our Kotlin measurement engine exchanges test bytes with Cloudflare’s public endpoint. No embedded speed-test website or third-party measurement SDK.",style=MaterialTheme.typography.bodyMedium)
  Row(verticalAlignment=Alignment.CenterVertically){Text("Extended sample · up to 256 MiB",Modifier.weight(1f));Switch(large,{large=it},enabled=!ui.running)}
  Text(if(large)"Up to 256 MiB of payload, plus network overhead." else "Up to 64 MiB of payload, plus network overhead. A fast link may finish with a short sample.",style=MaterialTheme.typography.bodySmall)
  Text("This uses your current connection and can consume your allowance. Cloudflare receives your IP address; usage history and app lists are not uploaded.",style=MaterialTheme.typography.bodySmall)
  Row(verticalAlignment=Alignment.CenterVertically){Checkbox(consent,{consent=it},enabled=!ui.running);Text("I agree to use data for this test",style=MaterialTheme.typography.bodyMedium)}
  Button(onClick={vm.start(large)},enabled=consent&&!ui.running,modifier=Modifier.fillMaxWidth()){Text("Start speed test")}
  if(ui.running){
   LinearProgressIndicator(Modifier.fillMaxWidth())
   Text(ui.progress.stage,fontWeight=FontWeight.SemiBold)
   Text(String.format(Locale.ROOT,"%.2f Mbps · %s transferred",ui.progress.liveMbps,formatBytes(ui.progress.payloadBytes)))
   OutlinedButton(onClick={vm.cancel()},modifier=Modifier.fillMaxWidth()){Text("Cancel test")}
  }
  ui.error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
 }
 ui.result?.let{r->Panel {
  Text("Measured result",style=MaterialTheme.typography.titleMedium)
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
   Column{Text("Download");Text(String.format(Locale.ROOT,"%.2f",r.downloadMbps),fontSize=28.sp,fontWeight=FontWeight.SemiBold);Text("Mbps")}
   Column(horizontalAlignment=Alignment.End){Text("Upload");Text(String.format(Locale.ROOT,"%.2f",r.uploadMbps),fontSize=28.sp,fontWeight=FontWeight.SemiBold);Text("Mbps")}
  }
  Text(String.format(Locale.ROOT,"HTTP latency %.1f ms · jitter %.1f ms",r.latencyMs,r.jitterMs))
  Text("${r.networkType} · ${formatBytes(r.payloadBytes)} test payload")
  Text(r.qualityNote,style=MaterialTheme.typography.bodySmall)
 }}
 Panel {
  TextButton(onClick={method=!method}){Text(if(method)"Hide methodology" else "How we measure")}
  AnimatedVisibility(method){Text("A warm-up request is followed by six HTTP latency samples. Download and upload each use three concurrent streams, monotonic elapsed time, and actual transferred bytes. Upload bytes count toward the result only after the server acknowledges the request. Each phase stops launching requests after eight seconds or its payload cap. A request already in flight may finish later. Results include request overhead, are specific to this server and route, and are not a guarantee of your ISP’s maximum rate. HTTP latency is not ICMP ping. Tests are never scheduled in the background.",style=MaterialTheme.typography.bodySmall)}
 }
 if(history.isNotEmpty())Panel {
  Text("Recent measurements",style=MaterialTheme.typography.titleMedium)
  history.take(10).forEach{r->
   Text(java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT,java.text.DateFormat.SHORT).format(java.util.Date(r.timestampMs)),fontWeight=FontWeight.SemiBold)
   Text(String.format(Locale.ROOT,"↓ %.2f  ↑ %.2f Mbps · %s",r.downloadMbps,r.uploadMbps,r.networkType))
  }
 }
}
