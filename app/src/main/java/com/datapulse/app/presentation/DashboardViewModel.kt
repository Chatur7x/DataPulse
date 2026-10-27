package com.datapulse.app.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.datapulse.app.data.PulseRepository
import com.datapulse.app.data.local.PlanType
import com.datapulse.app.domain.Projection
import com.datapulse.app.domain.projectCycle
import com.datapulse.app.domain.smartBudget
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

data class DashboardUi(
    val carrier: String = "—",
    val archetype: PlanType = PlanType.DAILY,
    val dayOf: String = "—",
    val ringFrac: Float = 0f, // ring fill 0..1 (archetype-aware denominator)
    val ringLabel: String = "—",
    val todayMb: Int = 0,
    val todayLimitMb: Int = 0,
    val usedCycleMb: Int = 0,
    val totalPlanMb: Int = 0,
    val remainingMb: Int = 0,
    val chip: String = "—", // projection (TOTAL/FUP) or budget (DAILY*) or throttle risk
    val overBudget: Boolean = false,
    val daysLeft: Int = 0,
    val completeCycle: Boolean = false
)

// S1: archetype-aware ring + today bar + 4 cards + chip + recharge CTA signal.
@HiltViewModel
class DashboardViewModel @Inject constructor(repo: PulseRepository) : ViewModel() {
    val ui: StateFlow<DashboardUi> =
        combine(repo.activePlan(), repo.recentDays(366)) { plan, days ->
            if (plan == null) return@combine DashboardUi()
            val today = LocalDate.now().toString()
            val start = runCatching { LocalDate.parse(plan.cycleStartDate) }.getOrElse { LocalDate.now() }
            val passed = ChronoUnit.DAYS.between(start, LocalDate.now()).toInt().coerceAtLeast(0)
            val left = (plan.cycleDays - passed).coerceAtLeast(0)
            val inCycle = days.filter { it.date >= plan.cycleStartDate && it.date < start.plusDays(plan.cycleDays.toLong()).toString() }
            val used = inCycle.sumOf { it.effectiveMobile() }
            val todayMb = inCycle.firstOrNull { it.date == today }?.effectiveMobile() ?: 0
            val total = com.datapulse.app.domain.cycleAllowanceMib(plan.dailyDataMb, plan.cycleDays, plan.planType == PlanType.DAILY || plan.planType == PlanType.DAILY_NIGHT)

            when (plan.planType) {
                PlanType.DAILY, PlanType.DAILY_NIGHT -> DashboardUi(
                    carrier = plan.carrierName, archetype = plan.planType,
                    dayOf = "Day ${passed + 1} of ${plan.cycleDays}",
                    ringFrac = (todayMb.toFloat() / plan.dailyDataMb.coerceAtLeast(1)).coerceIn(0f, 1f),
                    ringLabel = "$todayMb / ${plan.dailyDataMb} MiB",
                    todayMb = todayMb, todayLimitMb = plan.dailyDataMb,
                    usedCycleMb = used, totalPlanMb = total,
                    remainingMb = (plan.dailyDataMb - todayMb).coerceAtLeast(0),
                    chip = if (todayMb > plan.dailyDataMb) "Estimated usage exceeds allowance"
                    else "${plan.dailyDataMb - todayMb} MiB left today",
                    overBudget = todayMb > plan.dailyDataMb, daysLeft = left,
                    completeCycle = inCycle.size >= minOf(passed + 1, plan.cycleDays)
                )
                PlanType.TOTAL, PlanType.FUP -> {
                    val b = smartBudget(total, used, start, plan.cycleDays)
                    val proj = projectCycle(
                        b.remainingMb.toFloat(), b.remainingDays,
                        inCycle.filter { it.date < today }.sortedBy { it.date }.takeLast(7).map { it.effectiveMobile().toFloat() }
                    )
                    DashboardUi(
                        carrier = plan.carrierName, archetype = plan.planType,
                        dayOf = "Day ${passed + 1} of ${plan.cycleDays}",
                        ringFrac = (used.toFloat() / total.coerceAtLeast(1)).coerceIn(0f, 1f),
                        ringLabel = "${used / 1024}.${(used % 1024) / 103} / ${total / 1024} GiB",
                        todayMb = todayMb, todayLimitMb = b.budgetMb,
                        usedCycleMb = used, totalPlanMb = total,
                        remainingMb = b.remainingMb,
                        chip = when (proj) {
                            is Projection.Ok -> if (proj.willSurvive) "On track ✓ · ~${proj.daysUntilEmpty}d of data"
                            else "Runs out in ~${proj.daysUntilEmpty}d"
                            Projection.InsufficientData -> "Smart budget: ${b.budgetMb} MiB/day"
                            Projection.NoBurn -> "No burn yet"
                            Projection.Overdrawn -> "Over budget — throttle risk"
                        },
                        overBudget = b.overBudget, daysLeft = left,
                        completeCycle = inCycle.size >= minOf(passed + 1, plan.cycleDays)
                    )
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUi())
}
