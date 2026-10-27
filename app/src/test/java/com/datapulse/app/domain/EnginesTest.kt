package com.datapulse.app.domain

import org.junit.Assert.*
import org.junit.Test

// §14 Table 11: domain engines ≥90% branch. JVM tests, no Android deps.
class EnginesTest {
    @Test fun projection_empty_isInsufficient() {
        assertTrue(projectCycle(500f, 10, emptyList()) is Projection.InsufficientData)
    }

    @Test fun projection_overdrawn() {
        assertTrue(projectCycle(0f, 10, listOf(100f)) is Projection.Overdrawn)
        assertTrue(projectCycle(-5f, 10, listOf(100f)) is Projection.Overdrawn)
    }

    @Test fun projection_noBurn() {
        assertTrue(projectCycle(500f, 10, listOf(0f, 0f)) is Projection.NoBurn)
    }

    @Test fun projection_ceil_notTruncation() {
        // 1000/300 = 3.33 → ceil 4 (v1 .toInt() gave 3, optimistic)
        val r = projectCycle(1000f, 10, listOf(300f, 300f, 300f)) as Projection.Ok
        assertEquals(4, r.daysUntilEmpty)
        assertFalse(r.willSurvive)
    }

    @Test fun projection_survives() {
        val r = projectCycle(42000f - 20000f, 13, listOf(1300f, 1400f, 1350f)) as Projection.Ok
        assertTrue(r.willSurvive)
    }

    @Test fun budget_floor_math() {
        val b = smartBudget(42000, 20000, java.time.LocalDate.now().minusDays(15), 28)
        assertEquals(13, b.remainingDays)
        assertEquals(22000 / 13, b.budgetMb) // floor
        assertFalse(b.overBudget)
    }

    @Test fun budget_overdrawn() {
        val b = smartBudget(1000, 1500, java.time.LocalDate.now(), 28)
        assertTrue(b.overBudget)
        assertEquals(0, b.budgetMb)
    }

    @Test fun throttleEta() {
        assertEquals(60f, throttleEtaMinutes(1500f, 1400f, 100f)!!, 0.01f)
        assertNull(throttleEtaMinutes(1500f, 1500f, 100f))
        assertNull(throttleEtaMinutes(1500f, 100f, 0f))
    }

    @Test fun weekday_fallback_onThinData() {
        assertNull(weekdayFactors(mapOf(1 to listOf(100f))))
    }

    @Test fun anomalies_r1_r6() {
        assertTrue(Anomalies.r1AppSpike(400f, 100f))
        assertFalse(Anomalies.r1AppSpike(200f, 100f))
        assertTrue(Anomalies.r2HourSpike(50f, 20f))
        assertTrue(Anomalies.r3BackgroundDrain(0f, 60f))
        assertFalse(Anomalies.r3BackgroundDrain(10f, 60f))
        assertTrue(Anomalies.r4NewHog(true, false))
        assertTrue(Anomalies.r5UploadAnomaly(60, 40, 100f))
        assertFalse(Anomalies.r5UploadAnomaly(10, 90, 100f))
        assertTrue(Anomalies.r6OverLimitDay(1600f, 1536f))
    }
}
