package com.example.kept.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.example.kept.core.domain.ProofType

class Converters {
    @TypeConverter fun proofToString(p: ProofType): String = p.name
    @TypeConverter fun stringToProof(s: String): ProofType = ProofType.valueOf(s)
}

@Database(
    entities = [
        HabitEntity::class,
        HabitEntryEntity::class,
        DayRecordEntity::class,
        AllowedAppEntity::class,
        LockBreakEntity::class,
        ProtectionGapEntity::class,
        GalleryEntryEntity::class,
        BuddyEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class KeptDatabase : RoomDatabase() {
    abstract fun habitDao(): HabitDao
    abstract fun habitEntryDao(): HabitEntryDao
    abstract fun dayRecordDao(): DayRecordDao
    abstract fun allowedAppDao(): AllowedAppDao
    abstract fun lockBreakDao(): LockBreakDao
    abstract fun protectionGapDao(): ProtectionGapDao
    abstract fun galleryDao(): GalleryDao
    abstract fun buddyDao(): BuddyDao
}
