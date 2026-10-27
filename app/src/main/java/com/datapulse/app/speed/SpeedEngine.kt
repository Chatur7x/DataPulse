package com.datapulse.app.speed

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okio.BufferedSink
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class SpeedProgress(val stage:String="Ready", val payloadBytes:Long=0,
 val liveMbps:Double=0.0, val fraction:Float=0f, val samples:List<Double> = emptyList())

/** Application-payload measurement to one disclosed endpoint, never a synthetic score. */
class SpeedEngine @Inject constructor(@ApplicationContext private val ctx:Context) {
 private val cm=ctx.getSystemService(ConnectivityManager::class.java)
 private val random=SecureRandom()
 private data class Transfer(val bytes:Long,val nanos:Long,val edge:String?)
 private data class Phase(val bytes:Long,val nanos:Long,val latencies:List<Double>,val trace:List<Double>,val traceNanos:List<Long>,val traceBytes:List<Long>,val probeFailures:Int)

 private suspend fun transfer(client:OkHttpClient,bytes:Int,upload:Boolean,onBytes:(Long)->Unit):Transfer = suspendCancellableCoroutine { continuation ->
  val url=if(upload)"https://speed.cloudflare.com/__up" else "https://speed.cloudflare.com/__down?bytes=$bytes&nonce=${UUID.randomUUID()}"
  val builder=Request.Builder().url(url).header("Cache-Control","no-store").header("Accept-Encoding","identity")
   .header("User-Agent","DataPulse/0.5 measurement").header("Origin","https://speed.cloudflare.com").header("Referer","https://speed.cloudflare.com/")
  if(upload){
   val block=ByteArray(minOf(bytes,65536));random.nextBytes(block)
   builder.post(object:RequestBody(){
    override fun contentType()="application/octet-stream".toMediaType()
    override fun contentLength()=bytes.toLong()
    override fun writeTo(sink:BufferedSink){
     var remaining=bytes
     while(remaining>0){val n=minOf(remaining,block.size);sink.write(block,0,n);remaining-=n;onBytes(n.toLong())}
    }
   })
  }
  val call=client.newCall(builder.build());val start=System.nanoTime()
  continuation.invokeOnCancellation{call.cancel()}
  call.enqueue(object:Callback{
   override fun onFailure(call:Call,e:IOException){if(continuation.isActive)continuation.resumeWithException(e)}
   override fun onResponse(call:Call,response:Response){
    try{response.use{
     if(!it.isSuccessful)throw IOException("Measurement server returned HTTP ${it.code}; no result saved")
     if(!upload && it.header("Content-Encoding")?.let { encoding -> encoding!="identity" }==true)throw IOException("Compressed measurement response rejected")
     var count=0L
     it.body?.byteStream()?.use{stream->val buffer=ByteArray(65536);while(true){val n=stream.read(buffer);if(n<0)break;count+=n;if(!upload)onBytes(n.toLong())}}
     if(!upload && count!=bytes.toLong())throw IOException("Incomplete download; no result saved")
     if(continuation.isActive)continuation.resume(Transfer(if(upload)bytes.toLong() else count,System.nanoTime()-start,it.header("CF-Ray")?.substringAfterLast('-')))
    }}catch(e:Exception){if(continuation.isActive)continuation.resumeWithException(e)}
   }
  })
 }

 suspend fun run(large:Boolean,onProgress:(SpeedProgress)->Unit):SpeedResult=withContext(Dispatchers.IO){withTimeout(90_000){coroutineScope{
  val network=cm.activeNetwork?:throw IOException("Connect to Wi-Fi or mobile data first")
  val caps=cm.getNetworkCapabilities(network)
  val type=when{caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN)==true->"VPN";caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)==true->"Wi-Fi";caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)==true->"Mobile";else->"Other"}
  // Bind DNS and sockets to the starting network, and reject a default-network switch.
  val client=OkHttpClient.Builder().socketFactory(network.socketFactory).dns(object:Dns{
   override fun lookup(hostname:String)=network.getAllByName(hostname).toList()
  })
   .protocols(listOf(Protocol.HTTP_1_1)).retryOnConnectionFailure(false).followRedirects(false)
   .connectTimeout(5,TimeUnit.SECONDS).readTimeout(6,TimeUnit.SECONDS).writeTimeout(6,TimeUnit.SECONDS)
   .callTimeout(10,TimeUnit.SECONDS).build()
  // Separate connection pool prevents a latency probe waiting behind a transfer.
  val probe=client.newBuilder().connectionPool(ConnectionPool()).build()
  val used=AtomicLong(0)
  fun count(n:Long){used.addAndGet(n)}
  fun checkNetwork(){if(cm.activeNetwork!=network)throw IOException("Connection changed during the test; repeat on a stable connection")}
  try{
   val startedAt=System.currentTimeMillis()
   onProgress(SpeedProgress("Warming connection"))
   val warm=(1..4).map{async{transfer(client,65536,false,::count)}}.awaitAll().first()
   transfer(probe,0,false,{})
   val idle=mutableListOf<Double>()
   repeat(10){ensureActive();checkNetwork();onProgress(SpeedProgress("Measuring idle HTTP latency",used.get(),fraction=it/10f*.12f));idle+=transfer(probe,0,false,{}).nanos/1_000_000.0;delay(60)}

   suspend fun phase(upload:Boolean,limit:Long):Phase=coroutineScope{
    val reserved=AtomicLong(0);val completed=AtomicLong(0);val streamed=AtomicLong(0)
    val start=System.nanoTime();val deadline=start+12_000_000_000L
    val latency=mutableListOf<Double>();val trace=mutableListOf<Double>()
    val traceNanos=mutableListOf<Long>();val traceBytes=mutableListOf<Long>();var failures=0
    val stage=if(upload)"Measuring upload" else "Measuring download"
    val monitor=launch{
     var previous=0L;var previousTime=start
     while(isActive){
      delay(250);checkNetwork()
      val now=System.nanoTime();val current=streamed.get()
      val live=throughputMbps((current-previous).coerceAtLeast(0),now-previousTime)
      trace.add(live);traceNanos.add(now-start);traceBytes.add(current);previous=current;previousTime=now
      onProgress(SpeedProgress(stage,used.get(),live,(if(upload).56f else .12f)+.44f*((now-start)/12_000_000_000f).coerceIn(0f,1f),trace.takeLast(100)))
     }
    }
    val loaded=launch{
     while(isActive){
      try{latency.add(transfer(probe,0,false,{}).nanos/1_000_000.0)}catch(e:CancellationException){throw e}catch(_:IOException){failures++}
      delay(250)
     }
    }
    try{
     (1..4).map{async{
      var chunk=262144
      while(System.nanoTime()<deadline){
       ensureActive();checkNetwork()
       val n=reservePayload(reserved,limit,chunk)
       if(n==0)break
       val result=transfer(client,n,upload){delta->count(delta);streamed.addAndGet(delta)}
       completed.addAndGet(result.bytes)
       chunk=adaptiveChunkBytes(result.bytes,result.nanos)
      }
     }}.awaitAll()
    }finally{monitor.cancelAndJoin();loaded.cancelAndJoin()}
    Phase(completed.get(),(System.nanoTime()-start).coerceAtLeast(1),latency.toList(),trace.toList(),traceNanos.toList(),traceBytes.toList(),failures)
   }
   val cap=if(large)268_435_456L else 67_108_864L
   val dl=phase(false,cap*3/4-262144);val ul=phase(true,cap/4)
   checkNetwork()
   if(dl.bytes==0L||ul.bytes==0L)throw IOException("No complete throughput samples; no result saved")
   val notes=mutableListOf("Real HTTP payload throughput to Cloudflare; not a certified maximum line rate.")
   if(dl.nanos<5_000_000_000L||ul.nanos<5_000_000_000L)notes.add("Short sample: data cap reached before five seconds; repeat in extended mode for more evidence.")
   if(dl.probeFailures+ul.probeFailures>0)notes.add("Some loaded HTTP probes failed; this is not a packet-loss measurement.")
   val evidence=JSONObject().put("methodVersion",2).put("startedAtMs",startedAt).put("streams",4).put("sampleIntervalMs",250)
    .put("endpoint","https://speed.cloudflare.com").put("edge",warm.edge?:JSONObject.NULL)
    .put("warmupBytes",262144).put("downloadBytes",dl.bytes).put("uploadAcknowledgedBytes",ul.bytes)
    .put("downloadNanos",dl.nanos).put("uploadNanos",ul.nanos).put("payloadCapBytes",cap)
    .put("idleHttpMs",JSONArray(idle)).put("downloadLoadedHttpMs",JSONArray(dl.latencies)).put("uploadLoadedHttpMs",JSONArray(ul.latencies))
    .put("downloadMbpsTrace",JSONArray(dl.trace)).put("uploadMbpsTrace",JSONArray(ul.trace))
    .put("downloadTraceElapsedNanos",JSONArray(dl.traceNanos)).put("uploadTraceElapsedNanos",JSONArray(ul.traceNanos))
    .put("downloadTracePayloadBytes",JSONArray(dl.traceBytes)).put("uploadTracePayloadBytes",JSONArray(ul.traceBytes))
    .put("downloadProbeFailures",dl.probeFailures).put("uploadProbeFailures",ul.probeFailures)
   SpeedResult(UUID.randomUUID().toString(),System.currentTimeMillis(),throughputMbps(dl.bytes,dl.nanos),throughputMbps(ul.bytes,ul.nanos),medianSample(idle),sampleJitter(idle),dl.nanos/1_000_000,ul.nanos/1_000_000,used.get(),type,"Cloudflare${warm.edge?.let{" · $it"}?:""}",notes.joinToString(" "),evidence.toString())
  }finally{client.dispatcher.cancelAll();probe.dispatcher.cancelAll();client.connectionPool.evictAll();probe.connectionPool.evictAll()}
 }}}
}
