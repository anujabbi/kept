package com.example.kept.core.data

import com.example.kept.core.data.db.DayRecordDao
import com.example.kept.core.data.db.GalleryDao
import com.example.kept.core.data.db.GalleryEntryEntity
import com.example.kept.core.data.prefs.KeptPreferences
import com.example.kept.core.domain.LevelRules
import com.example.kept.core.domain.PointsRules
import com.example.kept.core.domain.SprigForm
import com.example.kept.core.domain.SprigState
import com.example.kept.core.domain.Variants
import com.example.kept.core.domain.WeekVariant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

data class GalleryCard(
    val id: Long,
    val form: SprigForm,
    val variant: WeekVariant,
    val unlockedDate: LocalDate,
    val streakAtUnlock: Int,
    val seen: Boolean,
)

@Singleton
class SprigRepository @Inject constructor(
    private val prefs: KeptPreferences,
    private val galleryDao: GalleryDao,
    private val dayDao: DayRecordDao,
    private val time: TimeSource,
) {
    val state: Flow<SprigState> = prefs.sprigState
    suspend fun current(): SprigState = prefs.currentSprig()

    val gallery: Flow<List<GalleryCard>> = galleryDao.observeAll().map { list -> list.map { it.toCard() } }
    val unseenUnlocks: Flow<List<GalleryCard>> = galleryDao.observeUnseen().map { list -> list.map { it.toCard() } }

    private fun GalleryEntryEntity.toCard() = GalleryCard(
        id, SprigForm.fromId(formId), Variants.byId(variantId), LocalDate.parse(unlockedDate), streakAtUnlock, seen,
    )

    suspend fun markSeen(id: Long) = galleryDao.markSeen(id)
    suspend fun markAllSeen() = galleryDao.markAllSeen()

    suspend fun onHabitEvent(event: HabitEvent) {
        val date = time.todayKey()
        when (event) {
            is HabitEvent.Completed -> {
                dayDao.addPoints(date, PointsRules.HABIT_BONUS)
                prefs.updateSprig { s ->
                    s.copy(
                        points = s.points + PointsRules.HABIT_BONUS,
                        level = if (event.allDoneNow) LevelRules.up(s.level) else s.level,
                    )
                }
            }
            is HabitEvent.Undone -> {
                dayDao.addPoints(date, -PointsRules.HABIT_BONUS)
                prefs.updateSprig { s ->
                    s.copy(
                        points = maxOf(0, s.points - PointsRules.HABIT_BONUS),
                        level = if (event.wasAllDone) LevelRules.down(s.level) else s.level,
                    )
                }
            }
        }
    }

    suspend fun onLockBreak() {
        prefs.updateSprig { s -> s.copy(level = LevelRules.down(s.level), wilted = true) }
    }

    suspend fun revive() {
        prefs.updateSprig { s -> s.copy(wilted = false) }
    }

    suspend fun addLockedTime(millis: Long) {
        val pts = PointsRules.forLockedMillis(millis)
        dayDao.addLockedTime(time.todayKey(), millis, pts)
        if (pts > 0) prefs.updateSprig { s -> s.copy(points = s.points + pts) }
    }

    suspend fun write(state: SprigState) = prefs.updateSprig { state }
    suspend fun update(block: (SprigState) -> SprigState) = prefs.updateSprig(block)

    suspend fun insertUnlock(form: SprigForm, variant: WeekVariant, date: LocalDate, streak: Int, seen: Boolean = false) {
        galleryDao.insert(GalleryEntryEntity(formId = form.id, variantId = variant.id, unlockedDate = date.toString(), streakAtUnlock = streak, seen = seen))
    }
}
