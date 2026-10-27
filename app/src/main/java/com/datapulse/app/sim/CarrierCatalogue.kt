package com.datapulse.app.sim

data class CarrierCatalogue(val name:String,val url:String,val matches:List<String>)
/** Live carrier-owned catalogues; no unaudited prices or fabricated availability. */
val carrierCatalogues=listOf(
 CarrierCatalogue("Jio","https://www.jio.com/selfcare/plans/",listOf("jio","jio4g","jio5g","reliancejio")),
 CarrierCatalogue("Airtel","https://www.airtel.in/recharge/prepaid/",listOf("airtel","airtel4g","airtel5g")),
 CarrierCatalogue("Vi","https://www.myvi.in/prepaid",listOf("vodafone","idea","vi","viindia")),
 CarrierCatalogue("BSNL","https://www.bsnl.co.in/",listOf("bsnl"))
)
fun catalogueFor(carrier:String):CarrierCatalogue?{
 val words=carrier.lowercase().split(Regex("[^a-z0-9]+"))
 return carrierCatalogues.firstOrNull{c->c.matches.any{it in words}}
}
