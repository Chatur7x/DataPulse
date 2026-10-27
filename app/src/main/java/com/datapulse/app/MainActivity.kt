package com.datapulse.app

import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.datapulse.app.data.local.PlanType
import com.datapulse.app.data.stats.hasUsageAccess
import com.datapulse.app.data.stats.openUsageAccessSettings
import com.datapulse.app.presentation.*
import com.datapulse.app.sync.SyncWorker
import dagger.hilt.android.AndroidEntryPoint
import java.time.LocalDate
import java.util.Locale

private val Ocean = Color(0xFF103B52)
private val Teal = Color(0xFF087F82)
private val Mint = Color(0xFF62E1CE)

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  setContent {
   val dark = isSystemInDarkTheme()
   SideEffect { androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = !dark }
   val colors = if (dark) darkColorScheme(primary = Mint, primaryContainer = Color(0xFF164C4C), secondary = Color(0xFFA4CFE1), secondaryContainer = Ocean, background = Color(0xFF0D202B), surface = Color(0xFF142E3D))
    else lightColorScheme(primary = Teal, primaryContainer = Color(0xFFD5F4EF), onPrimaryContainer = Ocean, secondary = Ocean, secondaryContainer = Color(0xFFDCECEF), onSecondaryContainer = Ocean, background = Color(0xFFF2F5F7), surface = Color.White, surfaceVariant = Color(0xFFDCECEF))
   MaterialTheme(colorScheme = colors, typography = PulseTypography, shapes = Shapes(medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(28.dp))) {
    Surface(Modifier.fillMaxSize(), color = colors.background) { PulseApp() }
   }
  }
 }
}

@Composable private fun PulseApp(planVm: PlanViewModel = hiltViewModel(), status: StatusViewModel = hiltViewModel()) {
 val ctx = LocalContext.current
 val plan by planVm.activePlan.collectAsStateWithLifecycle()
 val stamp by status.stamp.collectAsStateWithLifecycle()
 var tab by rememberSaveable { mutableIntStateOf(0) }
 var setup by rememberSaveable { mutableStateOf(false) }
 var simScreen by rememberSaveable { mutableStateOf(false) }
 var speedScreen by rememberSaveable { mutableStateOf(false) }
 BackHandler(enabled=setup||speedScreen||simScreen){when{simScreen->simScreen=false;speedScreen->speedScreen=false;else->setup=false}}
 val scrollState = rememberScrollState()
 val pageAlpha = remember { Animatable(1f) }
 var firstPage by remember { mutableStateOf(true) }
 LaunchedEffect(tab, setup, speedScreen, simScreen) {
  scrollState.scrollTo(0)
  if (firstPage) firstPage = false
  else { pageAlpha.snapTo(.45f); pageAlpha.animateTo(1f, tween(180, easing=FastOutSlowInEasing)) }
 }
 val lifecycleScope = rememberCoroutineScope()
 var granted by remember { mutableStateOf(hasUsageAccess(ctx)) }
 val lifecycle = LocalLifecycleOwner.current.lifecycle
 DisposableEffect(lifecycle) {
  val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) {
   granted = hasUsageAccess(ctx)
   if (granted) lifecycleScope.launch {
    delay(500) // Let the first frame draw before initializing background scheduling.
    withContext(Dispatchers.Default) { SyncWorker.schedule(ctx); SyncWorker.syncNow(ctx) }
   }
  } }
  lifecycle.addObserver(observer)
  onDispose { lifecycle.removeObserver(observer) }
 }
 Scaffold(containerColor = MaterialTheme.colorScheme.background, bottomBar = {
  if (!setup) NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
   val labels = listOf("Overview", "Trends", "Apps", "My plan", "Settings")
   val icons = listOf(Glyph.DonutLarge, Glyph.BarChart, Glyph.Apps, Glyph.SimCard, Glyph.Tune)
   labels.forEachIndexed { i, name -> NavigationBarItem(selected = tab == i, onClick = { tab = i; speedScreen = false; simScreen=false }, colors = NavigationBarItemDefaults.colors(indicatorColor = Mint.copy(alpha = .25f), selectedIconColor = MaterialTheme.colorScheme.primary), icon = {
    val scale by animateFloatAsState(if(tab==i) 1.08f else 1f, spring(stiffness=Spring.StiffnessMediumLow), label="Navigation selection")
    Icon(icons[i], name, Modifier.graphicsLayer { scaleX=scale; scaleY=scale })
   }, label = { Text(name, fontSize = 10.sp) }) }
  }
 }) { padding ->
  Column(Modifier.padding(padding).fillMaxSize().verticalScroll(scrollState).graphicsLayer { alpha = pageAlpha.value }.padding(horizontal = 22.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
   Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Image(painterResource(R.drawable.datapulse_logo), "DataPulse logo", Modifier.size(46.dp))
    Spacer(Modifier.width(10.dp))
    Column(Modifier.weight(1f)) { Text("DataPulse", fontWeight = FontWeight.Bold, fontSize = 22.sp); Text("Your connection, considered.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    if (!setup) IconButton(onClick = { SyncWorker.syncNow(ctx) }, enabled = granted) { Icon(Glyph.Refresh, "Refresh usage") }
   }
   if (simScreen) {
    TextButton(onClick={simScreen=false}){Icon(Glyph.ArrowBack,null);Text(" Back to app")}
    SimPlansScreen(onPageChanged={lifecycleScope.launch{scrollState.scrollTo(0)}})
   } else if (speedScreen) {
    TextButton(onClick={speedScreen=false}){Icon(Glyph.ArrowBack,null);Text(" Back to app")}
     SpeedScreen(onMeasurementChanged={lifecycleScope.launch{scrollState.scrollTo(0)}})
   } else if (setup) {
    TextButton(onClick = { setup = false }) { Icon(Glyph.ArrowBack, null); Text(" Back") }
    SetupScreen(onSaved = { setup = false; tab = 0 })
   } else {
    if (!granted) Notice("Connect your usage", "Allow Usage Access to see this phone’s estimates. You can set up a plan first.", Glyph.Shield) {
     OutlinedButton(onClick = { openUsageAccessSettings(ctx) }) { Text("Open usage settings") }
    }
    when (tab) {
     0 -> {
      if (plan == null) {
       Heading("Make room for\nwhat matters.", "Your allowance, your apps, and your next recharge — in one private space.")
       Notice("Start with your plan", "Add the allowance and dates from your carrier. No account needed.", Glyph.SimCard) {
        Button(onClick = { setup = true }) { Text("Set up my plan") }
       }
      } else Overview(granted, stamp, onSetup = { setup = true })
     }
     1 -> HistoryAnalyticsScreen(granted,onDayPicked={lifecycleScope.launch{scrollState.animateScrollTo(0)}})
     2 -> AppsScreen(granted)
     3 -> {
      Heading("Your connection", "Phone-wide allowance, plus separate profiles for your SIMs.")
      OutlinedButton(onClick={simScreen=true},modifier=Modifier.fillMaxWidth()){Icon(Glyph.SimCard,null);Spacer(Modifier.width(8.dp));Text("SIMs & recharges")}
      OutlinedButton(onClick={speedScreen=true},modifier=Modifier.fillMaxWidth()){Icon(Glyph.DonutLarge,null);Spacer(Modifier.width(8.dp));Text("Measure internet speed")}
      if (plan != null) {
       Panel { Text(plan!!.carrierName, style = MaterialTheme.typography.headlineMedium); Text("${plan!!.cycleDays} days from ${plan!!.cycleStartDate}"); Text("₹${plan!!.rechargeAmount} paid"); Text(if (plan!!.planType == PlanType.DAILY) "Daily reset allowance" else "Shared cycle allowance") }
       PlanComparison(plan!!)
      }
      Button(onClick = { setup = true }) { Text(if (plan == null) "Add a plan" else "Log a new recharge") }
      HistoryScreen()
     }
     else -> SettingsScreen(status)
    }
    Text(if (stamp == 0L) "No successful measurement yet" else "Last collected ${java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(stamp))}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text("On-device estimates • No ads • No account", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
   }
  }
 }
}

@Composable fun Heading(title: String, subtitle: String) {
 Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { Text(title, fontSize = 27.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold); Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium) }
}
@Composable fun Panel(content: @Composable ColumnScope.() -> Unit) {
 Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
  Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
 }
}
@Composable private fun Notice(title: String, text: String, icon: Glyph, action: @Composable () -> Unit = {}) {
 Panel { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary); Column { Text(title, fontWeight = FontWeight.SemiBold); Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } }; action() }
}
private fun amount(mib: Int): String = if (mib >= 1024) String.format(Locale.ROOT, "%.2f GiB", mib / 1024.0) else "$mib MiB"

@Composable private fun Overview(granted: Boolean, stamp: Long, onSetup: () -> Unit, vm: DashboardViewModel = hiltViewModel()) {
 val ui by vm.ui.collectAsStateWithLifecycle()
 Heading("Your data, in balance.", "${ui.carrier} • ${ui.dayOf}")
 if (!granted || stamp == 0L) { Notice("Waiting for a measurement", "Usage will appear after access is enabled and collection completes. Missing measurements are never shown as zero.", Glyph.HourglassEmpty); return }
 if (java.time.Instant.ofEpochMilli(stamp).atZone(java.time.ZoneId.systemDefault()).toLocalDate() < LocalDate.now()) {
  Notice("Refresh your measurements", "Your last collection was on an earlier day. Refresh before interpreting today’s remaining allowance.", Glyph.Update)
  return
 }
 val expired = ui.daysLeft == 0
 if (!ui.completeCycle && ui.archetype != PlanType.DAILY) {
  Notice("Incomplete cycle history", "Some dates in this cycle were not collected. A remaining pool balance cannot be calculated reliably. Trends shows the days that are available.", Glyph.Info)
  OutlinedButton(onClick = onSetup) { Text("Review my plan") }
  return
 }
 PremiumAllowance(ui, expired)

 Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
  Surface(Modifier.weight(1f), shape = RoundedCornerShape(20.dp)) { Column(Modifier.padding(18.dp)) { Text("Today", style = MaterialTheme.typography.labelMedium); Text(amount(ui.todayMb), fontSize = 20.sp, fontWeight = FontWeight.Bold) } }
  Surface(Modifier.weight(1f), shape = RoundedCornerShape(20.dp)) { Column(Modifier.padding(18.dp)) { Text("Cycle used", style = MaterialTheme.typography.labelMedium); Text(amount(ui.usedCycleMb), fontSize = 20.sp, fontWeight = FontWeight.Bold) } }
 }
 Notice("A useful estimate", "Android statistics can arrive late and differ from carrier billing. Alerts cannot guarantee protection from overages.", Glyph.Info)
 OutlinedButton(onClick = onSetup, modifier = Modifier.fillMaxWidth()) { Text("Log a new recharge") }
}

@Composable private fun SetupScreen(onSaved: () -> Unit, vm: PlanViewModel = hiltViewModel()) {
 var carrier by rememberSaveable { mutableStateOf("") }
 var price by rememberSaveable { mutableStateOf("") }
 var gb by rememberSaveable { mutableStateOf("") }
 var days by rememberSaveable { mutableStateOf("28") }
 var date by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
 var daily by rememberSaveable { mutableStateOf(true) }
 var error by rememberSaveable { mutableStateOf<String?>(null) }
 var saving by remember { mutableStateOf(false) }
 Heading("A plan that fits you.", "Copy the details from your recharge receipt or carrier app.")
 Panel {
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(daily, { daily = true }, { Text("Daily reset") }); FilterChip(!daily, { daily = false }, { Text("Cycle pool") }) }
  Field("Carrier or connection name", carrier, { carrier = it })
  Field(if (daily) "Data per day (GB)" else "Data per cycle (GB)", gb, { gb = it }, KeyboardType.Decimal)
  Field("Price paid (₹)", price, { price = it }, KeyboardType.Number)
  Field("Validity (days)", days, { days = it }, KeyboardType.Number)
  Field("Start date (YYYY-MM-DD)", date, { date = it })
  Text("1 GB = 1,000,000,000 bytes. Reports use MiB and GiB. Carrier reset time is assumed to be local midnight.", style = MaterialTheme.typography.bodySmall)
  error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
  Button(enabled = !saving, modifier = Modifier.fillMaxWidth(), onClick = {
   val start = runCatching { LocalDate.parse(date) }.getOrNull()
   val data = gb.toFloatOrNull(); val validity = days.toIntOrNull(); val cost = price.toIntOrNull()
   if (carrier.isBlank() || carrier.length > 80 || data == null || !data.isFinite() || data !in 0.001f..10000f || validity == null || validity !in 1..366 || cost == null || cost !in 0..1000000 || start == null || start > LocalDate.now()) error = "Check all fields. Use a valid past or current date, positive allowance and 1–366 days."
   else { saving = true; vm.savePlan(carrier.trim(), cost, data, validity, start, if (daily) PlanType.DAILY else PlanType.TOTAL) { onSaved() } }
  }) { Text(if (saving) "Saving…" else "Save my plan") }
 }
 Text("A new recharge archives the previous plan. Only one plan is active. Same-day recharges cannot split historical daily measurements.", style = MaterialTheme.typography.bodySmall)
}
@Composable private fun Field(label: String, value: String, change: (String) -> Unit, keyboard: KeyboardType = KeyboardType.Text) {
 OutlinedTextField(value = value, onValueChange = change, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = keyboard), shape = RoundedCornerShape(14.dp))
}

@Composable private fun TrendsScreen(granted: Boolean, vm: DailyViewModel = hiltViewModel()) {
 val days by vm.days.collectAsStateWithLifecycle()
 Heading("Find your rhythm.", "Your last 14 measured days on this phone.")
 if (!granted || days.isEmpty()) { Notice("No measurements to chart", "Grant access and collect usage to build your history.", Glyph.BarChart); return }
 val ordered = days.sortedBy { it.date }
 Panel {
  Text("Mobile usage", style = MaterialTheme.typography.titleMedium)
  val max = ordered.maxOf { it.mobileMbUsed }.coerceAtLeast(1)
  Canvas(Modifier.fillMaxWidth().height(160.dp).semantics { contentDescription = "Mobile usage chart. Daily values listed below." }) {
   val step = size.width / ordered.size
   ordered.forEachIndexed { i, d ->
    val h = size.height * d.mobileMbUsed / max
    drawLine(Teal, Offset(step * i + step / 2, size.height), Offset(step * i + step / 2, size.height - h), strokeWidth = step * .55f, cap = StrokeCap.Round)
   }
  }
  ordered.reversed().forEach { d -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(d.date); Text(amount(d.mobileMbUsed), fontWeight = FontWeight.SemiBold) } }
 }
 Notice("About Wi-Fi", "Wi-Fi totals include this phone’s traffic only. Other household devices are not measured.", Glyph.Wifi)
 Panel { ordered.reversed().forEach { d -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(d.date); Text(amount(d.wifiMbUsed)) } } }
}

@Composable private fun AppsScreen(granted: Boolean, vm: PerAppViewModel = hiltViewModel()) {
 val rows by vm.rows.collectAsStateWithLifecycle()
 var filter by rememberSaveable { mutableIntStateOf(0) }
 Heading("Where it goes.", "Your top 100 apps over the last 31 days.")
 Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("Mobile", "Wi-Fi", "Both").forEachIndexed { i, s -> FilterChip(filter == i, { filter = i; vm.setFilter(NetFilter.entries[i]) }, { Text(s) }) } }
 if (!granted || rows.isEmpty()) { Notice("No app measurements yet", "After a successful collection, apps with recorded traffic appear here.", Glyph.Apps); return }
 Panel { rows.take(100).forEach { row ->
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
   Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp)) { Text(row.name.take(1).uppercase(), Modifier.padding(12.dp), fontWeight = FontWeight.Bold) }
   Column(Modifier.weight(1f)) { Text(row.name, fontWeight = FontWeight.SemiBold); Text(row.pkg, style = MaterialTheme.typography.labelSmall, maxLines = 1); LinearProgressIndicator(progress = { row.share }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) }
   Text(amount(row.shown), style = MaterialTheme.typography.labelMedium)
  }
 } }
 Text("Shared UIDs and unresolved packages remain grouped. VPN and system attribution may differ from carrier totals.", style = MaterialTheme.typography.bodySmall)
}

@Composable private fun HistoryScreen(vm: CyclesViewModel = hiltViewModel()) {
 val cycles by vm.cycles.collectAsStateWithLifecycle()
 if (cycles.isNotEmpty()) Panel { Text("Recharge history", style = MaterialTheme.typography.titleMedium); cycles.forEach { c -> Text("${c.plan.carrierName} • ₹${c.plan.rechargeAmount} • ${c.plan.cycleStartDate}"); Text("${amount(c.usedMb)} observed in date range", style = MaterialTheme.typography.bodySmall) } }
}

@Composable private fun SettingsScreen(status: StatusViewModel, alerts: AlertsViewModel = hiltViewModel(), exports: ExportViewModel = hiltViewModel()) {
 val ctx = LocalContext.current
 val prefs by alerts.prefs.collectAsStateWithLifecycle()
 var confirm by remember { mutableStateOf(false) }
 val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
 Heading("Private by design.", "Your records stay on this phone unless you choose to share them.")
 Panel {
  Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("Daily collection",style=MaterialTheme.typography.titleMedium);Text("Works independently of a recharge plan.",style=MaterialTheme.typography.bodySmall)};Switch(prefs["tracking"]!=false,{alerts.setTracking(it)})}
  Text("Usage Access is required. Background collection is best effort; app-open refresh reconciles recent days. Deleting all records pauses collection until you enable it or add a new plan.",style=MaterialTheme.typography.bodySmall)
 }
 Panel {
  Text("Allowance alerts", style = MaterialTheme.typography.titleMedium)
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(75, 90, 100).forEach { p -> val selected = (prefs["thresholds"] as? Set<*>)?.contains(p.toString()) ?: true; FilterChip(selected, { alerts.toggleThreshold(p, !selected) }, { Text("$p%") }) } }
  Text("Daily plans only. Delivery depends on system scheduling and notification settings.", style = MaterialTheme.typography.bodySmall)
  OutlinedButton(onClick = { if (Build.VERSION.SDK_INT >= 33) launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("Allow notifications") }
  Row(verticalAlignment = Alignment.CenterVertically) { Text("Quiet hours (23:00–07:00)", Modifier.weight(1f)); Switch(prefs["quiet"] == true, { alerts.setQuiet(it) }) }
 }
 Panel {
  Text("Take your data with you", style = MaterialTheme.typography.titleMedium)
  Text("CSV files contain usage history and app names. Anyone you share them with can retain a copy.", style = MaterialTheme.typography.bodySmall)
  OutlinedButton(onClick = { exports.shareDaily() }, modifier = Modifier.fillMaxWidth()) { Text("Share daily report") }
  OutlinedButton(onClick = { exports.shareApps() }, modifier = Modifier.fillMaxWidth()) { Text("Share app report") }
 }
 Notice("Your history stays here", "Daily usage and app lists stay on this phone. Only an explicitly started speed test connects to Cloudflare, which receives your IP address. No accounts, ads or analytics. System backups are excluded.", Glyph.Lock)
 OutlinedButton(onClick = { confirm = true }, colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error), modifier = Modifier.fillMaxWidth()) { Text("Delete all local records") }
 if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Delete local records?") }, text = { Text("This removes plans, measurements, alert settings and temporary exports. Previously shared files remain with their recipients.") }, confirmButton = { TextButton(onClick = { confirm = false; status.deleteAll() }) { Text("Delete records") } }, dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } })
}
