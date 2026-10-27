package com.datapulse.app.speed

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName="speed_results")
data class SpeedResult(
 @PrimaryKey val id:String,
 val timestampMs:Long,
 val downloadMbps:Double,
 val uploadMbps:Double,
 val latencyMs:Double,
 val jitterMs:Double,
 val downloadMs:Long,
 val uploadMs:Long,
 val payloadBytes:Long,
 val networkType:String,
 val server:String,
 val qualityNote:String
)
@Dao interface SpeedDao {
 @Insert suspend fun insert(result:SpeedResult)
 @Query("SELECT * FROM speed_results ORDER BY timestampMs DESC LIMIT 30")
 fun recent():Flow<List<SpeedResult>>
}
