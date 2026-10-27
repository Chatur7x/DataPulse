package com.datapulse.app.domain

import com.datapulse.app.sim.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class SimRechargeTest{
 @Test fun receiptExtractsOnlyExplicitFields(){val s=suggestRecharge("Recharge successful Rs.299. Validity: 28 days. 1.5 GB/day and 100 SMS/day.");assertEquals("299",s.rupees);assertEquals("1.5",s.gb);assertEquals(true,s.daily);assertEquals(28,s.days)}
 @Test fun ambiguousMoneyIsNotGuessed(){assertNull(suggestRecharge("Recharge ₹299 with cashback ₹20").rupees)}
 @Test fun ambiguousDataIsNotGuessed(){assertNull(suggestRecharge("2 GB/day plus 5 GB bonus").gb)}
 @Test fun unrelatedNumbersAreNotPlanFields(){val s=suggestRecharge("Call 18001234567; 100 SMS/day; ref 1234");assertNull(s.rupees);assertNull(s.gb);assertNull(s.days)}
 @Test fun decimalMbConvertsToDecimalGb(){assertEquals("0.5",suggestRecharge("500 MB valid for 7 days").gb)}
 @Test fun omittedValuesRemainUnknown(){assertNull(rupeesToPaise(""));assertNull(gbToBytes(""))}
 @Test fun exactRupeesKeepPaise(){assertEquals(29950L,rupeesToPaise("299.50"))}
 @Test(expected=ArithmeticException::class)fun excessMoneyPrecisionRejected(){rupeesToPaise("299.999")}
 @Test(expected=IllegalArgumentException::class)fun negativePriceRejected(){rupeesToPaise("-1")}
 @Test fun gbUsesDecimalBytes(){assertEquals(1500000000L,gbToBytes("1.5"))}
 @Test fun explicitVoiceOnlyAllowanceCanBeZero(){assertEquals(0L,gbToBytes("0"));assertNull(gbToBytes(""))}
 private fun record()=SimRecharge(subscriptionId=1,operatorKey="404:10",carrierName="Test carrier",title="Fixture",pricePaise=null,dataBytes=null,daily=true,validFrom="2026-10-01",validThrough="2026-10-28",confirmedAtMs=0,source="Test")
 @Test fun bothEnteredBoundaryDaysAreIncluded(){val r=record();assertTrue(r.dateStatus(LocalDate.parse("2026-10-01")).startsWith("Within"));assertTrue(r.dateStatus(LocalDate.parse("2026-10-28")).startsWith("Within"))}
 @Test fun futureAndExpiredRecordsAreLabelled(){val r=record();assertTrue(r.dateStatus(LocalDate.parse("2026-09-30")).startsWith("Upcoming"));assertTrue(r.dateStatus(LocalDate.parse("2026-10-29")).startsWith("Expired"))}
 @Test fun recordsNeverMergeDifferentSubscriptions(){assertFalse(record().belongsTo(2,"404:10"));assertTrue(record().belongsTo(1,"404:10"))}
 @Test fun changedOperatorDoesNotInheritOldPlan(){assertFalse(record().belongsTo(1,"404:20"))}
 @Test fun carrierMatchingUsesNamesNotSubstrings(){assertEquals("Vi",catalogueFor("Vodafone IN")?.name);assertNull(catalogueFor("Viva"));assertEquals("Jio",catalogueFor("JIO 4G")?.name)}
 @Test fun unknownSlotIsNotInventedAsSimZero(){assertEquals("SIM 1",simSlotLabel(0));assertEquals("SIM (slot unavailable)",simSlotLabel(-1))}
 @Test fun rsInsideAnotherWordIsNotMoney(){assertNull(suggestRecharge("numbers299").rupees)}
 @Test fun groupedPriceIsNotTruncated(){assertEquals("2999",suggestRecharge("Paid Rs.2,999.").rupees);assertNull(suggestRecharge("Paid Rs.2,99.").rupees)}
 @Test fun unsupportedDecimalCommaIsNotMisreadAsLargerAllowance(){assertNull(suggestRecharge("1,5 GB").gb)}
 @Test fun missingDataPeriodIsNotGuessed(){assertNull(suggestRecharge("2 GB").daily);assertEquals(false,suggestRecharge("2 GB total").daily)}
}
