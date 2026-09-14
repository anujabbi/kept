package com.example.kept.core.di

import android.content.Context
import android.util.Log
import androidx.room.Room
import com.example.kept.core.AppForeground
import com.example.kept.core.data.BuddyNotifier
import com.example.kept.core.data.BuddyRepository
import com.example.kept.core.data.DayCompletionReactor
import com.example.kept.core.data.ForegroundSignal
import com.example.kept.core.data.LocalStubBuddyRepository
import com.example.kept.core.data.RecapNotifier
import com.example.kept.core.data.db.KeptDatabase
import com.example.kept.core.data.db.MIGRATION_1_2
import com.example.kept.core.data.db.MIGRATION_2_3
import com.example.kept.core.notify.KeptNotifications
import com.example.kept.core.notify.NotificationDayCompletion
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.time.Clock
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * A scope that outlives any one screen (issue #9).
 *
 * `LockActivity` calls `finishAndRemoveTask()` the instant the lock lifts, which kills its
 * ViewModel and cancels `viewModelScope` — so a tick taken there had its follow-up work (the
 * level grant, the celebration flag, the shade tidy-up) cancelled halfway, exactly the way the
 * old buddy-cheer coroutine was. Work that must finish once started belongs here instead.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton fun clock(): Clock = Clock.systemDefaultZone()

    /**
     * The handler is not optional. `SupervisorJob` keeps one failed child from cancelling its
     * siblings, but it does nothing about the exception itself: with no handler the throw reaches
     * the thread's default handler and takes the process down. A tick taken on the lock screen
     * runs here, so a failure in the follow-up work would crash the app out from under a user who
     * had just kept their promise.
     */
    @Provides @Singleton @ApplicationScope
    fun applicationScope(): CoroutineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { ctx, t ->
            Log.e("KeptAppScope", "uncaught failure in application-scope work ($ctx)", t)
        },
    )

    @Provides @Singleton
    fun database(@ApplicationContext ctx: Context): KeptDatabase =
        Room.databaseBuilder(ctx, KeptDatabase::class.java, "kept.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()

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

    /** The data layer raises a finished day; `core/notify` decides what the shade does (issue #9). */
    @Binds @Singleton abstract fun dayCompletionReactor(impl: NotificationDayCompletion): DayCompletionReactor

    @Binds @Singleton abstract fun foregroundSignal(impl: AppForeground): ForegroundSignal
}
