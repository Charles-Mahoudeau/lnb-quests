package fr.bde_teknantes.lnb.quests.journal

import fr.bde_teknantes.lnb.quests.api.Problems
import fr.bde_teknantes.lnb.warden.common.api.ApiResult

/** What an answer means for the journal. */
enum class Verdict {
    /** The site recorded it (or already had, `replayed`). */
    DONE,

    /** The site said no, and sending it again won't change that. */
    REFUSED,

    /** No answer, or a broken one: the site may or may not have recorded it. Send the same request again. */
    RETRY,
}

object RetryPolicy {
    /** Delays before each new attempt; the last one repeats until the site answers. */
    private val DELAYS_MS = longArrayOf(2_000, 5_000, 15_000, 60_000, 300_000)

    fun verdict(result: ApiResult<*>): Verdict = when (result) {
        is ApiResult.Ok -> Verdict.DONE
        is ApiResult.Network -> Verdict.RETRY
        is ApiResult.Http -> when {
            // A switched-off flag answers before anything moves.
            result.problem == Problems.DISABLED -> Verdict.REFUSED
            result.status == 429 || result.status >= 500 -> Verdict.RETRY
            // 401: the server's token is wrong. Nothing happened, but the call must not be lost: keep it for later.
            result.status == 401 -> Verdict.RETRY
            else -> Verdict.REFUSED
        }
    }

    /** How long to wait after the [attempts]-th failed attempt (1 for the first). */
    fun delayAfter(attempts: Int): Long = DELAYS_MS[(attempts - 1).coerceIn(0, DELAYS_MS.size - 1)]
}
