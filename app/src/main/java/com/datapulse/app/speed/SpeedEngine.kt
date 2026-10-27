package com.datapulse.app.speed

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okio.BufferedSink
import java.io.IOException
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class SpeedProgress(val stage:String="Ready",val payloadBytes:Long=0,val liveMbps:Double=0.0)

class SpeedEngine @Inject constructor(@ApplicationContext private val ctx:Context) {
 private val client=OkHttpClient.Builder().connectTimeout(5,TimeUnit.SECONDS).readTimeout(4,TimeUnit.SECONDS)
  .writeTimeout(4,TimeUnit.SECONDS).callTimeout(12,TimeUnit.SECONDS).followRedirects(false).build()
 private val cm=ctx.getSystemService(ConnectivityManager::class.java)
 private data class Transfer(val bytes:Long,val nanos:Long)
 private suspend fun transfer(bytes:Int,upload:Boolean,onBytes:(Long)->Unit):Transfer = suspendCancellableCoroutine { continuation ->
  val url=if(upload)"https://speed.cloudflare.com/__up" else "https://speed.cloudflare.com/__down?bytes=$bytes&nonce=${UUID.randomUUID()}"
  val builder=Request.Builder().url(url).header("Cache-Control","no-store").header("Accept-Encoding","identity").header("User-Agent","DataPulse/0.4 measurement")
   .header("Origin","https://speed.cloudflare.com").header("Referer","https://speed.cloudflare.com/")
  if(upload) {
   val block=ByteArray(minOf(bytes,32768));SecureRandom().nextBytes(block)
   builder.post(object:RequestBody(){
    override fun contentType()="application/octet-stream".toMediaType()
    override fun contentLength()=bytes.toLong()
    override fun writeTo(sink:BufferedSink){var remaining=bytes;while(remaining>0){val n=minOf(remaining,block.size);sink.write(block,0,n);remaining-=n;onBytes(n.toLong())}}
   })
  }
  val call=client.newCall(builder.build());val start=System.nanoTime()
  continuation.invokeOnCancellation{call.cancel()}
  call.enqueue(object:Callback {
   override fun onFailure(call:Call,e:IOException){if(continuation.isActive)continuation.resumeWithException(e)}
   override fun onResponse(call:Call,response:Response){
    try {response.use {
     if(!it.isSuccessful)throw IOException("Measurement server returned HTTP ${it.code}")
     var count=0L
     it.body?.byteStream()?.use{stream->val buffer=ByteArray(32768);while(true){val n=stream.read(buffer);if(n<0)break;count+=n;if(!upload)onBytes(n.toLong())}}
     if(!upload && count!=bytes.toLong())throw IOException("Incomplete download measurement")
     if(continuation.isActive)continuation.resume(Transfer(if(upload)bytes.toLong() else count,System.nanoTime()-start))
    }}catch(e:Exception){if(continuation.isActive)continuation.resumeWithException(e)}
   }
  })
 }
 suspend fun run(large:Boolean,onProgress:(SpeedProgress)->Unit):SpeedResult=coroutineScope {
  val network=cm.activeNetwork?:throw IOException("Connect to Wi-Fi or mobile data first")
  val caps=cm.getNetworkCapabilities(network)
  val type=when {caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN)==true->"VPN";caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)==true->"Wi-Fi";caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)==true->"Mobile";else->"Other"}
  val used=AtomicLong(0)
  fun count(n:Long){used.addAndGet(n)}
  onProgress(SpeedProgress("Warming connection"))
  transfer(262144,false,::count)
  val latency=mutableListOf<Double>()
  repeat(6){ensureActive();onProgress(SpeedProgress("Measuring HTTP latency",used.get()));latency+=transfer(0,false,::count).nanos/1_000_000.0}
  suspend fun phase(upload:Boolean,limit:Long):Pair<Long,Long> {
   val reserved=AtomicLong(0);val completed=AtomicLong(0)
   val started=System.nanoTime();val deadline=started+8_000_000_000L
   val lastProgress=AtomicLong(started)
   val stage=if(upload)"Measuring upload" else "Measuring download"
   (1..3).map { async {
    while(System.nanoTime()<deadline) {
     ensureActive()
     val ticket=reserved.getAndAdd(1_048_576)
     val n=minOf(1_048_576L,limit-ticket).toInt()
     if(n<=0)break
     val result=transfer(n,upload){delta->count(delta);val now=System.nanoTime();val previous=lastProgress.get();if(now-previous>100_000_000L && lastProgress.compareAndSet(previous,now)){val elapsed=(now-started).coerceAtLeast(1);onProgress(SpeedProgress(stage,used.get(),throughputMbps(completed.get(),elapsed)))}}
     completed.addAndGet(result.bytes)
     onProgress(SpeedProgress(stage,used.get(),throughputMbps(completed.get(),(System.nanoTime()-started).coerceAtLeast(1))))
    }
   }}.awaitAll()
   return completed.get() to (System.nanoTime()-started).coerceAtLeast(1)
  }
  // Payload cap includes warmup. HTTP/TLS overhead is additional.
  val total=if(large)268_435_456L else 67_108_864L
  val dl=phase(false,total*3/4-262144)
  val ul=phase(true,total/4)
  if(cm.activeNetwork!=network)throw IOException("Connection changed during testing. Please repeat the test.")
  if(dl.first==0L || ul.first==0L)throw IOException("No complete throughput samples were received")
  val short=dl.second<2_000_000_000L || ul.second<2_000_000_000L
  SpeedResult(UUID.randomUUID().toString(),System.currentTimeMillis(),throughputMbps(dl.first,dl.second),throughputMbps(ul.first,ul.second),medianSample(latency),sampleJitter(latency),dl.second/1_000_000,ul.second/1_000_000,used.get(),type,"Cloudflare public measurement endpoint",if(short)"Short sample: the data cap was reached quickly. A larger test can improve stability." else "Measured HTTP payload throughput to this endpoint. This is not a guaranteed maximum line rate.")
 }
}
