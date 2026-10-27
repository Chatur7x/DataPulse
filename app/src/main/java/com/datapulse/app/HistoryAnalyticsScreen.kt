package com.datapulse.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.datapulse.app.presentation.HistoryViewModel
import com.datapulse.app.presentation.toUsageDay
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

fun formatBytes(bytes: Long): String = when {
 bytes>=1_073_741_824L -> String.format(Locale.ROOT,"%.2f GiB",bytes/1_073_741_824.0)
 bytes>=1_048_576L -> String.format(Locale.ROOT,"%.1f MiB",bytes/1_048_576.0)
 bytes>=1024L -> String.format(Locale.ROOT,"%.1f KiB",bytes/1024.0)
 else -> "$bytes B"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun HistoryAnalyticsScreen(granted: Boolean, onDayPicked:()->Unit={}, vm: HistoryViewModel = hiltViewModel()) {
 val date by vm.date.collectAsStateWithLifecycle()
 val day by vm.day.collectAsStateWithLifecycle()
 val apps by vm.apps.collectAsStateWithLifecycle()
 val history by vm.history.collectAsStateWithLifecycle()
 val analysis by vm.analysis.collectAsStateWithLifecycle()
 var picker by remember { mutableStateOf(false) }
 Heading("Find your rhythm.","Every recorded day, with its own story.")
 Panel {
  Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
   IconButton(onClick={vm.select(date.minusDays(1));onDayPicked()}){Icon(Glyph.ArrowBack,"Previous day")}
   TextButton(onClick={picker=true},modifier=Modifier.weight(1f)){Text(date.format(DateTimeFormatter.ofPattern("d MMM yyyy")),fontWeight=FontWeight.SemiBold)}
   IconButton(onClick={vm.select(date.plusDays(1));onDayPicked()},enabled=date<LocalDate.now()){Icon(Glyph.ArrowBack,"Next day",Modifier.graphicsLayer{rotationZ=180f})}
  }
  Text("Tap the date to explore your history.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
  if(!granted) Text("Usage Access is required to collect this phone’s history.")
  else if(day==null) Text("No measurement stored for this day. Missing days are not counted as zero.")
  else {
   val row=day!!; val usage=row.toUsageDay()
   Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
    Column{Text("Mobile",style=MaterialTheme.typography.labelMedium);Text(formatBytes(usage.mobileBytes),style=MaterialTheme.typography.titleLarge)}
    Column(horizontalAlignment=Alignment.End){Text("Wi-Fi · this phone",style=MaterialTheme.typography.labelMedium);Text(formatBytes(usage.wifiBytes),style=MaterialTheme.typography.titleLarge)}
   }
   Text(if(date==LocalDate.now())"Today is still updating" else "Android-reported daily estimate",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
   if(row.collectedAtMs>0)Text("Collected ${java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT,java.text.DateFormat.SHORT).format(java.util.Date(row.collectedAtMs))} · ${row.timezoneId}",style=MaterialTheme.typography.bodySmall)
  }
 }
 if(picker) {
  val state=rememberDatePickerState(initialSelectedDateMillis=date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),selectableDates=object:SelectableDates{
   override fun isSelectableDate(utcTimeMillis:Long)=utcTimeMillis<=LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
  })
  DatePickerDialog(onDismissRequest={picker=false},confirmButton={TextButton(onClick={state.selectedDateMillis?.let{vm.select(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())};picker=false;onDayPicked()}){Text("Choose day")}},dismissButton={TextButton(onClick={picker=false}){Text("Cancel")}}){DatePicker(state=state)}
 }
 if(day!=null && apps.isNotEmpty())Panel {
  Text("Top apps on this day",style=MaterialTheme.typography.titleMedium)
  apps.sortedByDescending{it.rxBytes+it.txBytes}.take(5).forEach { app -> Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(app.appName,Modifier.weight(1f),maxLines=1);Text(formatBytes(app.rxBytes+app.txBytes))} }
 }
 Panel {
  Text("Your last 7 completed days",style=MaterialTheme.typography.titleMedium)
  if(analysis.observedDays==0)Text("Analysis appears once completed daily records are available.")
  else {
   Text("${formatBytes(analysis.totalBytes)} across ${analysis.observedDays} recorded days",style=MaterialTheme.typography.titleLarge)
   Text("Daily average ${formatBytes(analysis.averageBytes)} · ${(analysis.mobileShare*100).toInt()}% mobile")
   analysis.peakDate?.let { Text("Highest usage: $it · ${formatBytes(analysis.peakBytes)}") }
   analysis.changePercent?.let { Text(String.format(Locale.ROOT,"%.1f%% %s than the preceding matched period",kotlin.math.abs(it),if(it>=0)"more" else "less")) }
  }
 }
 val ordered=history.take(14).sortedBy{it.date}
 if(ordered.isNotEmpty())Panel {
  Text("Daily usage pattern",style=MaterialTheme.typography.titleMedium)
  val totals=ordered.map{it.toUsageDay().let{u->u.mobileBytes+u.wifiBytes}}
  val maximum=(totals.maxOrNull()?:0).coerceAtLeast(1)
  Canvas(Modifier.fillMaxWidth().height(130.dp).semantics{contentDescription="Fourteen-day total usage chart, with daily values below"}){
   val step=size.width/ordered.size
   totals.forEachIndexed{i,total-> val x=step*(i+.5f);drawLine(Color(0xFF087F82),Offset(x,size.height),Offset(x,size.height*(1-total.toFloat()/maximum)),step*.5f,StrokeCap.Round)}
  }
 }
 Panel {
  Text("Recorded days",style=MaterialTheme.typography.titleMedium)
  if(history.isEmpty())Text("Grant Usage Access and enable daily collection. Android may provide recent history; older records cannot always be recovered.")
  history.take(60).forEach { row ->
   val u=row.toUsageDay()
   Row(Modifier.fillMaxWidth().clickable{vm.select(LocalDate.parse(row.date));onDayPicked()}.padding(vertical=8.dp),horizontalArrangement=Arrangement.SpaceBetween){Text(row.date);Text(formatBytes(u.mobileBytes+u.wifiBytes),fontWeight=FontWeight.SemiBold)}
  }
  if(history.size>60)Text("Use the date picker to open older recorded days.",style=MaterialTheme.typography.bodySmall)
 }
 Text("Collection is best effort in the background and refreshes when you open the app. Recent days are reconciled when Android revises counters. Uninstalling or clearing app data removes this local history.",style=MaterialTheme.typography.bodySmall)
}
