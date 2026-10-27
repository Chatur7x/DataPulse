package com.datapulse.app.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.datapulse.app.data.local.RechargePlan
import java.util.Locale

/** User-supplied prices avoid presenting a stale bundled carrier catalogue. */
@Composable fun PlanComparison(current: RechargePlan) {
 var expanded by rememberSaveable { mutableStateOf(false) }
 var price by rememberSaveable { mutableStateOf("") }
 var validity by rememberSaveable { mutableStateOf("28") }
 OutlinedCard(Modifier.fillMaxWidth().animateContentSize(tween(220))) {
  Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
   Text("Could a different recharge cost less?", style = MaterialTheme.typography.titleMedium)
   Text("Compare a price you verified with your carrier. Check coverage, allowance and benefits separately.", style = MaterialTheme.typography.bodySmall)
   TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Close comparison" else "Compare a recharge") }
   if (expanded) {
    OutlinedTextField(price, { price = it }, label = { Text("Alternative price (₹)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(validity, { validity = it }, label = { Text("Alternative validity (days)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
    val p = price.toIntOrNull(); val d = validity.toIntOrNull()
    if (p != null && p in 1..1000000 && d != null && d in 1..366) {
     val baseline = current.rechargeAmount.toDouble() / current.cycleDays
     val alternative = p.toDouble() / d
     Text(String.format(Locale.ROOT, "Current: ₹%.2f/day\nAlternative: ₹%.2f/day", baseline, alternative))
     Text(String.format(Locale.ROOT, "Difference over 365 days: ₹%.0f %s", kotlin.math.abs(baseline - alternative) * 365, if (alternative <= baseline) "less" else "more"))
     Text("A normalized cost illustration, assuming continuous renewals. It excludes taxes, add-ons, changing prices and differences in service.", style = MaterialTheme.typography.bodySmall)
    }
   }
  }
 }
}
