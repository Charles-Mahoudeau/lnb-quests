package fr.bde_teknantes.lnb.quests.progress

/**
 * How the site's `resetAt` (an ISO date, or null when the quests were never reset) is compared with the last one the
 * server handled. Kept apart from the game so it can be tested.
 */
object ResetRule {
    enum class Action {
        /** Nothing stored yet: take the site's value as it is, without starting anyone over. */
        ADOPT,
        /** Already handled. */
        NONE,
        /** The site reset the quests since: every player starts over. */
        RESET,
    }

    /** [known] says whether a value was ever stored; [stored] and [incoming] use "" for null. */
    fun onConfig(known: Boolean, stored: String, incoming: String): Action = when {
        !known -> Action.ADOPT
        stored == incoming -> Action.NONE
        else -> Action.RESET
    }

    /** Whether a player last cleaned for [player] must start over now that the server is at [server]. */
    fun playerStale(player: String?, server: String): Boolean = player != server
}
