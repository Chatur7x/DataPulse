package com.datapulse.app.domain

import com.datapulse.app.data.csvCell
import org.junit.Assert.assertEquals
import org.junit.Test

class ExportTest {
 @Test fun quotedNamesDoNotBreakColumns() {
  assertEquals("\"An \"\"app\"\", name\"", csvCell("An \"app\", name"))
 }
 @Test fun formulasAreNeutralized() {
  assertEquals("\"'=SUM(1,2)\"", csvCell("=SUM(1,2)"))
  assertEquals("\"'  @formula\"", csvCell("  @formula"))
 }
 @Test fun plainNamesRemainReadable() { assertEquals("\"Maps\"", csvCell("Maps")) }
}
