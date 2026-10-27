package com.datapulse.app

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.datapulse.app.data.local.*
import com.datapulse.app.sim.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SimStorageTest{
 @Test fun v4UpgradePreservesRawSpeedEvidenceAndSeparatesTwoSimRecords()=runBlocking{
  val instrumentation=InstrumentationRegistry.getInstrumentation();val ctx=instrumentation.targetContext
  val name="sim-migration-v4-fixture-only.db";ctx.deleteDatabase(name)
  val file=ctx.getDatabasePath(name);file.parentFile!!.mkdirs()
  val schema=JSONObject(instrumentation.context.assets.open("com.datapulse.app.data.local.AppDatabase/4.json").bufferedReader().use{it.readText()}).getJSONObject("database")
  SQLiteDatabase.openOrCreateDatabase(file,null).use{db->
   val entities=schema.getJSONArray("entities")
   for(i in 0 until entities.length()){
    val entity=entities.getJSONObject(i);val table=entity.getString("tableName")
    db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}",table))
    val indices=entity.optJSONArray("indices")?:org.json.JSONArray()
    for(j in 0 until indices.length())db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",table))
   }
   val setup=schema.getJSONArray("setupQueries");for(i in 0 until setup.length())db.execSQL(setup.getString(i))
   db.execSQL("INSERT INTO speed_results VALUES ('legacy4',123,10.0,5.0,20.0,2.0,8000,8000,1000000,'Wi-Fi','Cloudflare','Fixture','{\"methodVersion\":2}')")
   db.version=4
  }
  val db=Room.databaseBuilder(ctx,AppDatabase::class.java,name).addMigrations(MIGRATION_4_5).build()
  try{
   assertEquals("{\"methodVersion\":2}",db.speed().findOnce("legacy4")!!.measurementJson)
   fun record(sim:Int)=SimRecharge(subscriptionId=sim,operatorKey="fixture",carrierName="Test carrier",title="Test recharge",pricePaise=null,dataBytes=null,daily=true,validFrom="2026-10-01",validThrough="2026-10-28",confirmedAtMs=sim.toLong(),source="Instrumented fixture")
   db.sims().insert(record(1));db.sims().insert(record(2))
   val rows=db.sims().all().first();assertEquals(2,rows.size)
   assertTrue(rows.all{it.pricePaise==null&&it.dataBytes==null})
   val first=rows.first{it.subscriptionId==1};db.sims().delete(first.id)
   assertEquals(2,db.sims().all().first().single().subscriptionId)
  }finally{db.close();ctx.deleteDatabase(name)}
 }
}
