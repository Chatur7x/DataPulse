package com.datapulse.app.speed

import org.json.JSONObject

fun SpeedResult.evidence():JSONObject? = if(measurementJson.isBlank())null else runCatching{JSONObject(measurementJson)}.getOrNull()
fun JSONObject.samples(key:String):List<Double> {
 val array=optJSONArray(key)?:return emptyList()
 return (0 until array.length()).map{array.getDouble(it)}
}
fun SpeedResult.exportJson():String=JSONObject()
 .put("id",id).put("completedAtMs",timestampMs).put("network",networkType).put("server",server)
 .put("downloadMbps",downloadMbps).put("uploadMbps",uploadMbps).put("idleHttpMedianMs",latencyMs)
 .put("idleSuccessiveJitterMs",jitterMs).put("payloadBytes",payloadBytes).put("qualityNote",qualityNote)
 .put("measurement",evidence()?:JSONObject.NULL).put("packetLoss",JSONObject.NULL)
 .put("limitations","HTTP application payload, not ISP billing or a certified line-rate measurement; packet loss is not measured.").toString(2)
