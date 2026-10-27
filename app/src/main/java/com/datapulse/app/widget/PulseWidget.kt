package com.datapulse.app.widget

import android.content.ComponentName
import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.glance.background
import androidx.glance.unit.ColorProvider
import androidx.glance.text.TextStyle
import androidx.glance.GlanceId
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.*
import androidx.glance.text.Text
import androidx.room.Room
import com.datapulse.app.MainActivity
import com.datapulse.app.data.local.AppDatabase
import java.time.LocalDate
import java.time.temporal.ChronoUnit

// S8: 2×2 remaining+days, 4×2 full summary. Room-direct read (same SoT, no dual store).
// Refresh: hourly worker + app-open sync + alert fire (§11). Tap deep-links to S1.
class PulseWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(SmallRectangle, BigRectangle)
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snap = readSnapshot(context)
        provideContent {
            Column(
                    modifier = androidx.glance.GlanceModifier.fillMaxSize().background(Color(0xFF103B52)).padding(12.dp)
                        .clickable(actionStartActivity(ComponentName(context, MainActivity::class.java)))
                ) {
                    Text("DataPulse · ${snap.carrier}", style = TextStyle(color = ColorProvider(Color(0xFF62E1CE)), fontSize = 16.sp))
                    Text(snap.line1, style = TextStyle(color = ColorProvider(Color.White)))
                    Text(snap.line2, style = TextStyle(color = ColorProvider(Color(0xFFC8DEE8))))
                }
        }
    }

    private data class Snap(val carrier: String, val line1: String, val line2: String)

    private suspend fun readSnapshot(context: Context): Snap = runCatching {
        val db = Room.databaseBuilder(context, AppDatabase::class.java, "datapulse.db").addMigrations(com.datapulse.app.data.local.MIGRATION_2_3).build()
        try {
            val plan = db.plans().activePlanOnce() ?: return Snap("—", "No plan", "Set up a plan")
            val stamp = db.meta().syncStateOnce()?.lastSuccessMs ?: 0L
            if (stamp == 0L) return Snap(plan.carrierName, "Not measured yet", "Open app to collect")
            val today = LocalDate.now().toString()
            if (java.time.Instant.ofEpochMilli(stamp).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString() < today)
                return Snap(plan.carrierName, "Measurements are stale", "Open app to refresh")
            val rows = db.daily().daysSince(plan.cycleStartDate)
                .filter { it.date <= today && it.date < LocalDate.parse(plan.cycleStartDate).plusDays(plan.cycleDays.toLong()).toString() }
            val used = rows.sumOf { it.effectiveMobile() }
            val total = if (plan.planType == com.datapulse.app.data.local.PlanType.DAILY) plan.dailyDataMb else plan.dailyDataMb
            val passed = ChronoUnit.DAYS.between(LocalDate.parse(plan.cycleStartDate), LocalDate.now()).toInt().coerceAtLeast(0)
            val leftDays = (plan.cycleDays - passed).coerceAtLeast(0)
            if (plan.planType != com.datapulse.app.data.local.PlanType.DAILY && rows.size < minOf(passed + 1, plan.cycleDays))
                return Snap(plan.carrierName, "Incomplete cycle history", "Open app for details")
            val todayMb = db.daily().dayOnce(today)?.effectiveMobile() ?: 0
            Snap(plan.carrierName, "${(total - if (plan.planType == com.datapulse.app.data.local.PlanType.DAILY) todayMb else used).coerceAtLeast(0)} MiB · $leftDays d left",
                if (plan.planType == com.datapulse.app.data.local.PlanType.DAILY) "Today $todayMb / ${plan.dailyDataMb} MiB" else "Cycle $used / $total MiB")
        } finally {
            db.close()
        }
    }.getOrElse { Snap("—", "Sync pending", "Open app") }

    companion object {
        val SmallRectangle = DpSize(110.dp, 110.dp)
        val BigRectangle = DpSize(220.dp, 110.dp)
    }
}

class PulseWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PulseWidget()
}

suspend fun updatePulseWidgets(context: Context) = runCatching {
    val manager = GlanceAppWidgetManager(context)
    manager.getGlanceIds(PulseWidget::class.java).forEach { id -> PulseWidget().update(context, id) }
}
