package com.datapulse.app.domain

import org.junit.Assert.*
import org.junit.Test

class AccountingTest {
 @Test fun poolIsNeverMultipliedByValidity() { assertEquals(10000, cycleAllowanceMib(10000, 28, false)) }
 @Test fun dailyNominalCycleAllowance() { assertEquals(42000, cycleAllowanceMib(1500, 28, true)) }
 @Test fun decimalGbIsNotBinaryGiB() { assertEquals(1430, decimalGbToMib(1.5f)) }
 @Test(expected = IllegalArgumentException::class) fun nanIsRejected() { decimalGbToMib(Float.NaN) }
 @Test(expected = IllegalArgumentException::class) fun negativeIsRejected() { decimalGbToMib(-1f) }
 @Test fun veryLargeDailyAllowanceDoesNotWrapNegative() { assertEquals(Int.MAX_VALUE, cycleAllowanceMib(10000000, 366, true)) }
}
