package com.example.kept.core.domain

/**
 * Petals that fall once (the `.petal` / `petalFall` keyframes of `A5-Recap`, `A16-Celebrate` and
 * `A17-Evolve`), as functions of the milliseconds since the moment started, so a composable only
 * reads a clock. The recap and the two moments use the same shape and colours and differ in
 * count, spread, timing and how far the petals fall; each file's script is one [of] call.
 *
 * A petal is [W_DP] × [H_DP], starts at [TOP_DP], falls [fallDp] and turns [turnDeg] over
 * [durationMs] after its own delay. Its opacity is linear between the keyframes 0% (0),
 * 10% (1), [fadeOutFrom] (1) and 100% (0).
 */
class PetalShower(
    val petals: List<Petal>,
    val durationMs: Int,
    val fallDp: Float,
    val turnDeg: Float,
    val fadeOutFrom: Float,
) {
    data class Petal(val xDp: Float, val colour: Long, val delayMs: Int)

    /** When the last petal has fallen. */
    val endMs: Int = (petals.maxOfOrNull { it.delayMs } ?: 0) + durationMs

    /** A petal's progress through its own fall, 0 to 1, or null before it starts or after it ends. */
    fun progress(p: Petal, ms: Long): Float? {
        val t = (ms - p.delayMs).toFloat() / durationMs
        return if (t < 0f || t > 1f) null else t
    }

    /** `petalFall` opacity at progress [t]: 0 → 1 over the first 10%, 1 until [fadeOutFrom], then back to 0. */
    fun alpha(t: Float): Float = when {
        t <= FADE_IN_TO -> t / FADE_IN_TO
        t <= fadeOutFrom -> 1f
        else -> ((1f - t) / (1f - fadeOutFrom)).coerceAtLeast(0f)
    }

    companion object {
        const val TOP_DP = -24f
        const val W_DP = 10f
        const val H_DP = 16f
        const val FADE_IN_TO = 0.1f

        /** `#FF8FB0 #FFFFFF #8B7CF6 #2FA866 #FFF4C2`, as ARGB; petal `i` takes colour `i % 5`. */
        val COLOURS = longArrayOf(0xFFFF8FB0, 0xFFFFFFFF, 0xFF8B7CF6, 0xFF2FA866, 0xFFFFF4C2)

        /**
         * The design scripts' loop: petal `i` sits at `x0 + (i × xStep) % xMod` and starts after
         * `((i × delayStep) % delayMod) / 10` seconds.
         */
        fun of(
            count: Int, x0: Int, xStep: Int, xMod: Int, delayStep: Int, delayMod: Int,
            durationMs: Int, fallDp: Float, turnDeg: Float, fadeOutFrom: Float,
        ) = PetalShower(
            (0 until count).map { i -> Petal((x0 + (i * xStep) % xMod).toFloat(), COLOURS[i % COLOURS.size], ((i * delayStep) % delayMod) * 100) },
            durationMs, fallDp, turnDeg, fadeOutFrom,
        )
    }
}
