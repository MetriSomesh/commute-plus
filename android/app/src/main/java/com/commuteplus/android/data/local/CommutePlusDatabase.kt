package com.commuteplus.android.data.local

import androidx.room.*

/**
 * Room database for offline caching.
 *
 * Stores:
 * - Recent searches (origin/destination pairs) for quick access
 * - Cached journey results for the last N queries (survives offline)
 */
@Database(
    entities = [RecentSearchEntity::class, CachedJourneyEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class CommutePlusDatabase : RoomDatabase() {
    abstract fun recentSearchDao(): RecentSearchDao
    abstract fun cachedJourneyDao(): CachedJourneyDao
}

// --- Entities ---

@Entity(tableName = "recent_searches")
data class RecentSearchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val originName: String,
    val originLat: Double,
    val originLng: Double,
    val destinationName: String,
    val destinationLat: Double,
    val destinationLng: Double,
    val timestamp: Long, // epoch millis
)

@Entity(tableName = "cached_journeys")
data class CachedJourneyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val originLat: Double,
    val originLng: Double,
    val destinationLat: Double,
    val destinationLng: Double,
    val responseJson: String, // Full JourneyPlanResponse serialized as JSON
    val cachedAt: Long, // epoch millis
)

// --- DAOs ---

@Dao
interface RecentSearchDao {
    @Query("SELECT * FROM recent_searches ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 10): List<RecentSearchEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(search: RecentSearchEntity)

    @Query("DELETE FROM recent_searches WHERE id NOT IN (SELECT id FROM recent_searches ORDER BY timestamp DESC LIMIT 20)")
    suspend fun trimOld()
}

@Dao
interface CachedJourneyDao {
    /**
     * Find a cached result for the given origin/destination (within ~100m tolerance).
     * Used for offline fallback and instant repeat-query results.
     */
    @Query("""
        SELECT * FROM cached_journeys 
        WHERE ABS(originLat - :originLat) < 0.001 
        AND ABS(originLng - :originLng) < 0.001
        AND ABS(destinationLat - :destLat) < 0.001
        AND ABS(destinationLng - :destLng) < 0.001
        ORDER BY cachedAt DESC
        LIMIT 1
    """)
    suspend fun findCached(
        originLat: Double,
        originLng: Double,
        destLat: Double,
        destLng: Double,
    ): CachedJourneyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(journey: CachedJourneyEntity)

    @Query("DELETE FROM cached_journeys WHERE cachedAt < :before")
    suspend fun deleteOlderThan(before: Long)
}
