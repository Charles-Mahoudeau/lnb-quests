package fr.bde_teknantes.lnb.quests.network

import fr.bde_teknantes.lnb.quests.api.Community
import fr.bde_teknantes.lnb.quests.api.PlayerView
import fr.bde_teknantes.lnb.quests.api.QuestDef

/** The flash race running, as a player sees it. */
data class FlashView(
    val quest: String = "",
    val quantity: Int = 0,
    val endsAt: Long = 0,
    val leader: String? = null,
    val leaderValue: Long = 0,
    val mine: Long = 0,
)

/** A flash race that ended, for the history. */
data class FlashEntry(
    val quest: String = "",
    val quantity: Int = 0,
    val winner: String? = null,
    val amount: Long = 0,
    val endedAt: Long = 0,
)

/** Everything the quest screen shows, sent as JSON in [QuestScreenPayload]. */
data class ScreenData(
    val season: Int = 0,
    val quests: List<QuestDef> = emptyList(),
    val counters: Map<String, Long> = emptyMap(),
    /** The site's view of the player, or null when it couldn't be read (not linked, site down). */
    val view: PlayerView? = null,
    val problem: String? = null,
    val community: Community? = null,
    val communityProgress: Long = 0,
    val flash: FlashView? = null,
    val kGlobal: Double = 1.0,
    val siteUrl: String = "",
    val flashHistory: List<FlashEntry> = emptyList(),
    /** When the next flash race may be drawn, in epoch millis, or 0 when unknown. */
    val nextFlashAt: Long = 0,
)
