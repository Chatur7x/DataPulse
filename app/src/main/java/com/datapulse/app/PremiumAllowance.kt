package com.datapulse.app

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.datapulse.app.data.local.PlanType
import com.datapulse.app.presentation.DashboardUi
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

private fun units(mib: Int) = if (mib >= 1024) String.format(Locale.ROOT,"%.2f GiB",mib/1024.0) else "$mib MiB"

@Composable fun PremiumAllowance(ui: DashboardUi, expired: Boolean) {
 var revealed by remember { mutableStateOf(false) }
 LaunchedEffect(Unit) { revealed = true }
 val fraction by animateFloatAsState(
  if (revealed && !expired) (1f-ui.ringFrac).coerceIn(0f,1f) else 0f,
  animationSpec = tween(700, easing = FastOutSlowInEasing), label="Remaining allowance")
 val mint = Color(0xFF84E5D2)
 Surface(shape=RoundedCornerShape(28.dp), color=Color.Transparent,
  border=BorderStroke(1.dp,Color.White.copy(alpha=.12f)),modifier=Modifier.fillMaxWidth()) {
  Column(Modifier.engravedSurface().padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
   Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
    Text(if(ui.archetype==PlanType.DAILY) "Today’s allowance" else "Cycle allowance",color=Color(0xFFD0E4E7),fontSize=13.sp,fontWeight=FontWeight.Medium)
    Surface(color=Color.White.copy(alpha=.09f),shape=RoundedCornerShape(50)) {
     Row(Modifier.padding(horizontal=10.dp,vertical=5.dp),horizontalArrangement=Arrangement.spacedBy(5.dp),verticalAlignment=Alignment.CenterVertically) {
      Canvas(Modifier.size(5.dp)){drawCircle(mint)}
      Text("On device",color=Color(0xFFD0E4E7),fontSize=10.sp)
     }
    }
   }
   Box(Modifier.fillMaxWidth().height(182.dp),contentAlignment=Alignment.Center) {
    Canvas(Modifier.size(182.dp).padding(9.dp).semantics { contentDescription="${((1-ui.ringFrac)*100).toInt()} percent allowance remaining" }) {
     val width=7.dp.toPx()
     for(i in 0..40) {
      val a=Math.toRadians((135+i*6.75).toDouble())
      val outer=size.minDimension/2
      val inner=outer-3.dp.toPx()
      val center=Offset(size.width/2,size.height/2)
      drawLine(Color.White.copy(alpha=.16f),center+Offset(cos(a).toFloat()*inner,sin(a).toFloat()*inner),center+Offset(cos(a).toFloat()*outer,sin(a).toFloat()*outer),strokeWidth=1.dp.toPx())
     }
     val inset=10.dp.toPx()
     val arcSize=androidx.compose.ui.geometry.Size(size.width-inset*2,size.height-inset*2)
     drawArc(Color.White.copy(alpha=.09f),135f,270f,false,topLeft=Offset(inset,inset),size=arcSize,style=Stroke(width,cap=StrokeCap.Round))
     drawArc(if(ui.overBudget)Color(0xFFFFB7A8) else mint,135f,270*fraction,false,topLeft=Offset(inset,inset),size=arcSize,style=Stroke(width,cap=StrokeCap.Round))
    }
    Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(3.dp)) {
     Text(if(expired)"Ended" else units(ui.remainingMb),color=Color.White,fontSize=30.sp,fontWeight=FontWeight.SemiBold,letterSpacing=(-1).sp)
     Text(if(expired)"Log your next recharge" else "estimated remaining",color=Color(0xFFB9D4D9),fontSize=11.sp)
    }
   }
   HorizontalDivider(color=Color.White.copy(alpha=.12f))
   Row(Modifier.fillMaxWidth().padding(top=4.dp),horizontalArrangement=Arrangement.SpaceBetween) {
    Column { Text("Used",color=Color(0xFFB9D4D9),fontSize=10.sp); Text(units(if(ui.archetype==PlanType.DAILY)ui.todayMb else ui.usedCycleMb),color=Color.White,fontSize=15.sp,fontWeight=FontWeight.SemiBold) }
    Column(horizontalAlignment=Alignment.End) { Text(if(ui.archetype==PlanType.DAILY)"Resets" else "Cycle ends in",color=Color(0xFFB9D4D9),fontSize=10.sp); Text(if(ui.archetype==PlanType.DAILY)"At midnight" else "${ui.daysLeft} days",color=Color.White,fontSize=15.sp,fontWeight=FontWeight.SemiBold) }
   }
  }
 }
}
