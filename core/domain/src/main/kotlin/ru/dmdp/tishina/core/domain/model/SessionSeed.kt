package ru.dmdp.tishina.core.domain.model

/**
 * Pre-existing state injected into a fresh [StartMeasurementUseCase] invocation
 * so a Pause→Resume cycle continues the session's running min / max / avg /
 * duration / 60-second tail instead of restarting from zero.
 *
 * The use-case's accumulator is otherwise stateless: each `samples()` collection
 * folds from `empty`. To preserve aggregates across the `cancel()` + `relaunch`
 * that Pause → Resume performs, the ViewModel hands back the last-observed
 * scalars plus the side-band `sumDb` and `count` (which the public snapshot does
 * not expose) as a [SessionSeed].
 *
 * [durationOffsetMs] is added to every emitted `durationMs` so the UI clock
 * appears continuous across pauses — the underlying `AudioRecord` restarts its
 * own zero, so without the offset the duration would visibly reset.
 *
 * `Float.POSITIVE_INFINITY` / `Float.NEGATIVE_INFINITY` are monoid identities
 * for `min` / `max` — `min(POS_INF, x) == x` and `max(NEG_INF, x) == x` — so a
 * `count == 0L` seed behaves identically to a fresh `Accumulator.Empty`.
 */
data class SessionSeed(
    val minDb: Float,
    val maxDb: Float,
    val sumDb: Double,
    val count: Long,
    val durationOffsetMs: Long,
    val recent: List<SoundSample>,
) {
    companion object {
        val empty: SessionSeed = SessionSeed(
            minDb = Float.POSITIVE_INFINITY,
            maxDb = Float.NEGATIVE_INFINITY,
            sumDb = 0.0,
            count = 0L,
            durationOffsetMs = 0L,
            recent = emptyList(),
        )
    }
}
