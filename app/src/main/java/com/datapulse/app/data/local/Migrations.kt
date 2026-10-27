package com.datapulse.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_2_3 = object : Migration(2,3) {
 override fun migrate(db: SupportSQLiteDatabase) {
  db.execSQL("ALTER TABLE daily_snapshots ADD COLUMN mobileBytes INTEGER NOT NULL DEFAULT 0")
  db.execSQL("ALTER TABLE daily_snapshots ADD COLUMN wifiBytes INTEGER NOT NULL DEFAULT 0")
  db.execSQL("ALTER TABLE daily_snapshots ADD COLUMN collectedAtMs INTEGER NOT NULL DEFAULT 0")
  db.execSQL("ALTER TABLE daily_snapshots ADD COLUMN partialDay INTEGER NOT NULL DEFAULT 0")
  db.execSQL("ALTER TABLE daily_snapshots ADD COLUMN timezoneId TEXT NOT NULL DEFAULT ''")
  db.execSQL("CREATE TABLE IF NOT EXISTS speed_results (id TEXT NOT NULL PRIMARY KEY, timestampMs INTEGER NOT NULL, downloadMbps REAL NOT NULL, uploadMbps REAL NOT NULL, latencyMs REAL NOT NULL, jitterMs REAL NOT NULL, downloadMs INTEGER NOT NULL, uploadMs INTEGER NOT NULL, payloadBytes INTEGER NOT NULL, networkType TEXT NOT NULL, server TEXT NOT NULL, qualityNote TEXT NOT NULL)")
 }
}

val MIGRATION_3_4 = object : Migration(3,4) {
 override fun migrate(db: SupportSQLiteDatabase) {
  db.execSQL("ALTER TABLE speed_results ADD COLUMN measurementJson TEXT NOT NULL DEFAULT ''")
 }
}

val MIGRATION_4_5 = object : Migration(4,5) {
 override fun migrate(db: SupportSQLiteDatabase) {
  db.execSQL("CREATE TABLE IF NOT EXISTS sim_recharges (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, subscriptionId INTEGER NOT NULL, operatorKey TEXT NOT NULL, carrierName TEXT NOT NULL, title TEXT NOT NULL, pricePaise INTEGER, dataBytes INTEGER, daily INTEGER NOT NULL, validFrom TEXT NOT NULL, validThrough TEXT NOT NULL, confirmedAtMs INTEGER NOT NULL, source TEXT NOT NULL)")
 }
}
