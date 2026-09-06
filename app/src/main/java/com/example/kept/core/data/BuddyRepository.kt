package com.example.kept.core.data

import com.example.kept.core.data.db.BuddyDao
import com.example.kept.core.data.db.BuddyEntity
import com.example.kept.core.data.prefs.KeptPreferences
import com.example.kept.core.domain.SprigForm
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/** What a buddy is allowed to see about you. Nothing else ever leaves the device. */
data class BuddyStatus(
    val streakDays: Int,
    val doneToday: Boolean,
    val form: SprigForm,
    val unprotectedToday: Boolean,
)

data class Buddy(
    val displayName: String,
    val initials: String,
    val streakDays: Int,
    val doneToday: Boolean,
    val form: SprigForm,
    /** Oldest first, 7 entries: 1 done, 0 missed, u unprotected, - unknown. */
    val lastSevenDays: String,
    val pairedAt: Long,
    val lastCheerAt: Long?,
    val lastNudgeAt: Long?,
)

/**
 * Pairing contract. v1 ships [LocalStubBuddyRepository]; a synced implementation later replaces it
 * behind this interface without touching the UI.
 */
interface BuddyRepository {
    fun observeBuddy(): Flow<Buddy?>
    suspend fun myInviteCode(): String
    /** Returns false when the code is malformed. Any well-formed code pairs in the stub. */
    suspend fun pair(code: String): Boolean
    suspend fun unpair()
    suspend fun nudge()
    suspend fun cheer()
    /** Called by the app whenever my own status changes; the stub ignores it. */
    suspend fun publishMyStatus(status: BuddyStatus)
    /** True if the buddy completed their habits on [date]; null when no buddy. */
    suspend fun buddyDoneOn(date: LocalDate): Boolean?
    /** Stub-only hook that makes the buddy react (cheer) after an event. */
    suspend fun simulateBuddyReaction(reason: String)
}

fun interface BuddyNotifier {
    fun notify(title: String, body: String)
}

@Singleton
class LocalStubBuddyRepository @Inject constructor(
    private val dao: BuddyDao,
    private val prefs: KeptPreferences,
    private val time: TimeSource,
    private val notifier: BuddyNotifier,
) : BuddyRepository {

    override fun observeBuddy(): Flow<Buddy?> = dao.observe().map { it?.toModel() }

    private fun BuddyEntity.toModel() = Buddy(
        displayName, initials, streakDays, doneToday, SprigForm.fromId(formId), lastSevenDays, pairedAt, lastCheerAt, lastNudgeAt,
    )

    override suspend fun myInviteCode(): String {
        val existing = prefs.currentSettings().myInviteCode
        if (existing.isNotBlank()) return existing
        val code = generateCode()
        prefs.updateSettings { it.copy(myInviteCode = code) }
        return code
    }

    private fun generateCode(): String {
        val alphabet = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
        fun part() = (1..3).map { alphabet[Random.nextInt(alphabet.length)] }.joinToString("")
        return "${part()}-${part()}"
    }

    override suspend fun pair(code: String): Boolean {
        val normalized = code.trim().uppercase().replace(" ", "")
        val ok = Regex("^[A-Z0-9]{3}-?[A-Z0-9]{3}$").matches(normalized)
        if (!ok) return false
        dao.upsert(
            BuddyEntity(
                displayName = "Maya", initials = "MK", streakDays = 9, doneToday = false,
                formId = SprigForm.BLOOM.id, lastSevenDays = "1101110", pairedAt = time.nowMillis(),
            ),
        )
        return true
    }

    override suspend fun unpair() = dao.clear()

    override suspend fun nudge() {
        val b = dao.get() ?: return
        dao.upsert(b.copy(lastNudgeAt = time.nowMillis()))
        notifier.notify("Nudge sent to ${b.displayName}", "They'll see your streak is waiting on them.")
    }

    override suspend fun cheer() {
        val b = dao.get() ?: return
        dao.upsert(b.copy(lastCheerAt = time.nowMillis()))
        notifier.notify("Cheer sent to ${b.displayName}", "A little boost goes a long way.")
    }

    override suspend fun publishMyStatus(status: BuddyStatus) { /* no backend in v1 */ }

    override suspend fun buddyDoneOn(date: LocalDate): Boolean? {
        dao.get() ?: return null
        // Deterministic pseudo-history: Maya keeps ~85% of her days.
        val h = date.toEpochDay() * 31L
        return (h % 7L) != 3L
    }

    override suspend fun simulateBuddyReaction(reason: String) {
        val b = dao.get() ?: return
        when (reason) {
            "break" -> notifier.notify("${b.displayName} cheered you on", "\"Rough one. Tomorrow's yours.\" Sprig perked up.")
            "done" -> notifier.notify("${b.displayName} saw you finish", "Both of you kept today. Pair streak grows.")
        }
    }

    /** Called by the day ticker so the stub buddy "does her habits" at some point each day. */
    suspend fun tickDaily() {
        val b = dao.get() ?: return
        val minute = time.minuteOfDay()
        val doneAt = 9 * 60 + ((time.today().toEpochDay() * 37) % 600).toInt() // between 9:00 and 19:00
        val done = minute >= doneAt && (buddyDoneOn(time.today()) ?: false)
        if (done != b.doneToday) dao.upsert(b.copy(doneToday = done))
    }

    suspend fun current(): Buddy? = observeBuddy().first()
}
