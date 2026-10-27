package com.datapulse.app.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

// §9: pure Kotlin, no Android deps. Sealed results — callers handle every state.

// §9.2 Listing 11: EWMA projection, TOTAL archetype. ceil, not truncation.
sealed interface Projection {
    data object InsufficientData : Projection
    data object NoBurn : Projection
    data object Overdrawn : Projection
    data class Ok(val avgDailyMb: Float, val daysUntilEmpty: Int, val willSurvive: Boolean) : Projection
}

fun ewma(values: List<Float>, alpha: Float = 0.35f): Float {
    var acc = values.first()
    for (v in values.drop(1)) acc = alpha * v + (1 - alpha) * acc
    return acc
}

fun projectCycle(
    remainingMb: Float,
    remainingDays: Int,
    dailyMb: List<Float>,
    alpha: Float = 0.35f
): Projection = when {
    dailyMb.isEmpty() -> Projection.InsufficientData
    remainingMb <= 0f -> Projection.Overdrawn
    else -> {
        val avg = ewma(dailyMb, alpha) // recent-weighted
        if (avg < 1f) Projection.NoBurn
        else {
            val rawDays = remainingMb / avg
            Projection.Ok(
                avgDailyMb = avg,
                daysUntilEmpty = ceil(rawDays).toInt(), // not truncation
                willSurvive = rawDays >= remainingDays
            )
        }
    }
}

// §9.3 smart daily budget: floor Rt/max(Drem,1). TOTAL/FUP only; DAILY budget = plan limit.
data class SmartBudget(val budgetMb: Int, val remainingMb: Int, val remainingDays: Int, val overBudget: Boolean)

fun smartBudget(totalPlanMb: Int, usedSoFarMb: Int, cycleStartDate: LocalDate, cycleDays: Int, today: LocalDate = LocalDate.now()): SmartBudget {
    val passed = ChronoUnit.DAYS.between(cycleStartDate, today).toInt().coerceAtLeast(0)
    val remDays = (cycleDays - passed).coerceAtLeast(1)
    val rem = totalPlanMb - usedSoFarMb
    return SmartBudget(
        budgetMb = if (rem < 0) 0 else rem / remDays, // floor
        remainingMb = rem.coerceAtLeast(0), remainingDays = remDays, overBudget = rem < 0
    )
}

// §9.4 DAILY-archetype throttle ETA: t ≈ t0 + (L − u_today) / h̄, h̄ = hourly EWMA.
fun throttleEtaMinutes(limitMb: Float, usedTodayMb: Float, hourlyEwmaMb: Float): Float? {
    if (hourlyEwmaMb <= 0f || usedTodayMb >= limitMb) return null
    return (limitMb - usedTodayMb) / hourlyEwmaMb * 60f
}

// §9.5 weekday-factor prediction: γ=0.6, W≤4 cycles; median samples/weekday < 3 → EWMA fallback.
fun weekdayFactors(history: Map<Int, List<Float>>, gamma: Float = 0.6f, maxCycles: Int = 4): Map<Int, Float>? {
    if (history.values.any { it.size < 3 }) return null // median < 3 → fallback
    var denom = 0f
    for (w in 1..maxCycles) denom += Math.pow(gamma.toDouble(), w.toDouble()).toFloat()
    return history.mapValues { (_, samples) ->
        val recent = samples.takeLast(maxCycles)
        var num = 0f
        recent.forEachIndexed { i, v -> num += Math.pow(gamma.toDouble(), (recent.size - i).toDouble()).toFloat() * v }
        num / denom
    }
}

fun predictedCycleTotal(usedSoFarMb: Float, remainingWeekdays: List<Int>, factors: Map<Int, Float>?): Float =
    usedSoFarMb + (factors?.let { f -> remainingWeekdays.sumOf { (f[it] ?: 0f).toDouble() }.toFloat() } ?: 0f)

// §9.6 Table 6: anomaly rules, pure predicates. Cooldown/dedupe via alert_log (§8 repo).
object Anomalies {
    fun r1AppSpike(todayMb: Float, ewma7: Float): Boolean = ewma7 > 0 && todayMb > 3 * ewma7
    fun r2HourSpike(hourMb: Float, sameHourMean7: Float): Boolean = sameHourMean7 > 0 && hourMb > 2 * sameHourMean7
    fun r3BackgroundDrain(fgMb: Float, bgMb: Float): Boolean = fgMb < 1f && bgMb > 50f
    fun r4NewHog(inTop3: Boolean, inPriorTop5: Boolean): Boolean = inTop3 && !inPriorTop5
    fun r5UploadAnomaly(txBytes: Long, rxBytes: Long, mobileMb: Float): Boolean {
        val total = txBytes + rxBytes
        return total > 0 && txBytes.toFloat() / total > 0.4f && mobileMb > 50f
    }
    fun r6OverLimitDay(dayTotalMb: Float, dailyCapMb: Float): Boolean = dayTotalMb > dailyCapMb
}
