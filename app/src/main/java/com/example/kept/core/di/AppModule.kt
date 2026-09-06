package com.example.kept.core.di

import android.content.Context
import androidx.room.Room
import com.example.kept.core.data.BuddyNotifier
import com.example.kept.core.data.BuddyRepository
import com.example.kept.core.data.LocalStubBuddyRepository
import com.example.kept.core.data.RecapNotifier
import com.example.kept.core.data.db.KeptDatabase
import com.example.kept.core.notify.KeptNotifications
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton fun clock(): Clock = Clock.systemDefaultZone()

    @Provides @Singleton
    fun database(@ApplicationContext ctx: Context): KeptDatabase =
        Room.databaseBuilder(ctx, KeptDatabase::class.java, "kept.db").fallbackToDestructiveMigration().build()

    @Provides fun habitDao(db: KeptDatabase) = db.habitDao()
    @Provides fun habitEntryDao(db: KeptDatabase) = db.habitEntryDao()
    @Provides fun dayRecordDao(db: KeptDatabase) = db.dayRecordDao()
    @Provides fun allowedAppDao(db: KeptDatabase) = db.allowedAppDao()
    @Provides fun lockBreakDao(db: KeptDatabase) = db.lockBreakDao()
    @Provides fun protectionGapDao(db: KeptDatabase) = db.protectionGapDao()
    @Provides fun galleryDao(db: KeptDatabase) = db.galleryDao()
    @Provides fun buddyDao(db: KeptDatabase) = db.buddyDao()

    @Provides @Singleton
    fun buddyNotifier(n: KeptNotifications): BuddyNotifier = BuddyNotifier { title, body -> n.buddy(title, body) }

    @Provides @Singleton
    fun recapNotifier(n: KeptNotifications): RecapNotifier = RecapNotifier { summary, unlocks -> n.recap(summary, unlocks) }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BindsModule {
    @Binds @Singleton abstract fun buddyRepository(impl: LocalStubBuddyRepository): BuddyRepository
}
