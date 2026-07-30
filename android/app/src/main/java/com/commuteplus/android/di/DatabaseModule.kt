package com.commuteplus.android.di

import android.content.Context
import androidx.room.Room
import com.commuteplus.android.data.local.CachedJourneyDao
import com.commuteplus.android.data.local.CommutePlusDatabase
import com.commuteplus.android.data.local.RecentSearchDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): CommutePlusDatabase {
        return Room.databaseBuilder(
            context,
            CommutePlusDatabase::class.java,
            "commuteplus.db"
        ).build()
    }

    @Provides
    fun provideRecentSearchDao(db: CommutePlusDatabase): RecentSearchDao = db.recentSearchDao()

    @Provides
    fun provideCachedJourneyDao(db: CommutePlusDatabase): CachedJourneyDao = db.cachedJourneyDao()
}
