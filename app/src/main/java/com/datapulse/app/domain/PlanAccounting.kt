package com.datapulse.app.domain

/** Entered carrier GB are decimal; stored presentation counters are MiB. */
fun decimalGbToMib(gb: Float): Int {
 require(gb.isFinite() && gb > 0f && gb <= 10000f)
 return (gb.toDouble() * 1_000_000_000L / 1_048_576L).toInt().coerceAtLeast(1)
}

fun cycleAllowanceMib(allowanceMib: Int, cycleDays: Int, dailyReset: Boolean): Int {
 require(allowanceMib >= 0 && cycleDays > 0)
 return (allowanceMib.toLong() * if (dailyReset) cycleDays else 1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}
