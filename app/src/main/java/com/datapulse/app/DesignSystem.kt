package com.datapulse.app

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.*

val Manrope = FontFamily(
 Font(R.font.manrope_400, FontWeight.Normal),
 Font(R.font.manrope_500, FontWeight.Medium),
 Font(R.font.manrope_600, FontWeight.SemiBold),
 Font(R.font.manrope_700, FontWeight.Bold)
)
private fun TextStyle.premium() = copy(fontFamily = Manrope, fontFeatureSettings = "tnum")
private val baseType = Typography()
val PulseTypography = with(baseType) { copy(
 displayLarge=displayLarge.premium(), displayMedium=displayMedium.premium(), displaySmall=displaySmall.premium(),
 headlineLarge=headlineLarge.premium(), headlineMedium=headlineMedium.premium(), headlineSmall=headlineSmall.premium(),
 titleLarge=titleLarge.premium(), titleMedium=titleMedium.premium(), titleSmall=titleSmall.premium(),
 bodyLarge=bodyLarge.premium(), bodyMedium=bodyMedium.premium(), bodySmall=bodySmall.premium(),
 labelLarge=labelLarge.premium(), labelMedium=labelMedium.premium(), labelSmall=labelSmall.premium()
) }

enum class Glyph(val resource: Int) {
 DonutLarge(R.drawable.glyph_donutlarge), BarChart(R.drawable.glyph_barchart), Apps(R.drawable.glyph_apps),
 SimCard(R.drawable.glyph_simcard), Tune(R.drawable.glyph_tune), GraphicEq(R.drawable.glyph_graphiceq),
 Refresh(R.drawable.glyph_refresh), ArrowBack(R.drawable.glyph_arrowback), Shield(R.drawable.glyph_shield),
 HourglassEmpty(R.drawable.glyph_hourglassempty), Update(R.drawable.glyph_update), Info(R.drawable.glyph_info),
 Wifi(R.drawable.glyph_wifi), Lock(R.drawable.glyph_lock)
}

@Composable fun Icon(glyph: Glyph, contentDescription: String?, modifier: Modifier = Modifier,
 tint: Color = LocalContentColor.current) {
 androidx.compose.material3.Icon(painterResource(glyph.resource), contentDescription, modifier, tint)
}

/** Fine engraved lines, cached with the size. No bitmap, random noise or blur pass. */
fun Modifier.engravedSurface() = drawWithCache {
 val base = Brush.linearGradient(listOf(Color(0xFF12374B),Color(0xFF1B5260)), Offset.Zero, Offset(size.width,size.height))
 val spacing = 14f * density
 onDrawBehind {
  drawRect(base)
  var x = -size.height
  while (x < size.width) {
   drawLine(Color.White.copy(alpha=.025f), Offset(x,0f), Offset(x+size.height,size.height), strokeWidth=.6f*density)
   x += spacing
  }
  drawCircle(Color.White.copy(alpha=.035f), size.width*.7f, Offset(size.width*1.1f,-size.height*.15f), style=androidx.compose.ui.graphics.drawscope.Stroke(1f*density))
 }
}
