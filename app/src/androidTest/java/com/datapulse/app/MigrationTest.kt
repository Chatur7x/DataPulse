package com.datapulse.app

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.datapulse.app.data.local.AppDatabase
import com.datapulse.app.data.local.MIGRATION_2_3
import com.datapulse.app.data.local.MIGRATION_3_4
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {
 @Test fun upgradeV3PreservesExistingSpeedResultWithoutInventingSamples()=runBlocking {
  val instrumentation=InstrumentationRegistry.getInstrumentation();val ctx=instrumentation.targetContext
  val name="migration-v3-fixture-only.db";ctx.deleteDatabase(name)
  val file=ctx.getDatabasePath(name);file.parentFile!!.mkdirs()
  val schema=JSONObject(instrumentation.context.assets.open("com.datapulse.app.data.local.AppDatabase/3.json").bufferedReader().use{it.readText()}).getJSONObject("database")
  SQLiteDatabase.openOrCreateDatabase(file,null).use{db->
   val entities=schema.getJSONArray("entities")
   for(i in 0 until entities.length()){
    val entity=entities.getJSONObject(i);val table=entity.getString("tableName")
    db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}",table))
    val indices=entity.optJSONArray("indices")?:org.json.JSONArray()
    for(j in 0 until indices.length())db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",table))
   }
   val setup=schema.getJSONArray("setupQueries");for(i in 0 until setup.length())db.execSQL(setup.getString(i))
   db.execSQL("INSERT INTO speed_results VALUES ('legacy',123,10.0,5.0,20.0,2.0,8000,8000,1000000,'Wi-Fi','Cloudflare','Legacy measurement')")
   db.version=3
  }
  val db=Room.databaseBuilder(ctx,AppDatabase::class.java,name).addMigrations(MIGRATION_3_4).build()
  try{
   val row=db.speed().findOnce("legacy")!!
   assertEquals(10.0,row.downloadMbps,0.0);assertEquals(5.0,row.uploadMbps,0.0)
   assertEquals("",row.measurementJson)
  }finally{db.close();ctx.deleteDatabase(name)}
 }
 @Test fun upgradePreservesDailyHistoryAndAddsSpeedStorage()=runBlocking {
  val instrumentation=InstrumentationRegistry.getInstrumentation()
  val ctx=instrumentation.targetContext
  val name="migration-fixture-only.db"
  ctx.deleteDatabase(name)
  val file=ctx.getDatabasePath(name);file.parentFile!!.mkdirs()
  val schema=JSONObject(instrumentation.context.assets.open("com.datapulse.app.data.local.AppDatabase/2.json").bufferedReader().use{it.readText()}).getJSONObject("database")
  SQLiteDatabase.openOrCreateDatabase(file,null).use{db->
   val entities=schema.getJSONArray("entities")
   for(i in 0 until entities.length()){
    val entity=entities.getJSONObject(i);val table=entity.getString("tableName")
    db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}",table))
    val indices=entity.optJSONArray("indices")?:org.json.JSONArray()
    for(j in 0 until indices.length())db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",table))
   }
   val setup=schema.getJSONArray("setupQueries")
   for(i in 0 until setup.length())db.execSQL(setup.getString(i))
   db.execSQL("INSERT INTO daily_snapshots (date,totalMbUsed,wifiMbUsed,mobileMbUsed,unattributedMb) VALUES ('2026-10-25',300,200,100,0)")
   db.version=2
  }
  val db=Room.databaseBuilder(ctx,AppDatabase::class.java,name).addMigrations(MIGRATION_2_3, MIGRATION_3_4).build()
  try{
   val row=db.daily().dayOnce("2026-10-25")!!
   assertEquals(100,row.mobileMbUsed);assertEquals(200,row.wifiMbUsed);assertEquals(0L,row.mobileBytes)
   db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM speed_results").use{assertTrue(it.moveToFirst());assertEquals(0,it.getInt(0))}
  }finally{db.close();ctx.deleteDatabase(name)}
 }
}
