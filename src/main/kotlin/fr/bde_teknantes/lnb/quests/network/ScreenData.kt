package fr.bde_teknantes.lnb.quests.network

import fr.bde_teknantes.lnb.quests.api.CategoryDef
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

/** One of today's daily quests, as a player sees it. */
data class DailyLine(
    val quest: String = "",
    val quantity: Long = 0,
    val progress: Long = 0,
    val done: Boolean = false,
)

/** Today's daily quests, until [endsAt] (epoch millis). */
data class DailyScreen(val endsAt: Long = 0, val lines: List<DailyLine> = emptyList())

/**
 * An advancement's title and description, as component JSON, so they show in the player's language: the server knows
 * every advancement, the client only the ones it was shown.
 */
data class AdvancementText(val title: String = "", val description: String = "")

/** Everything the quest screen shows, sent as JSON in [QuestScreenPayload]. */
data class ScreenData(
    val season: Int = 0,
    val quests: List<QuestDef> = emptyList(),
    /** Individual chapters and Records groups, in display order. Empty from a site that doesn't send them yet. */
    val categories: List<CategoryDef> = emptyList(),
    val counters: Map<String, Long> = emptyMap(),
    val daily: DailyScreen = DailyScreen(),
    /** Quest id -> the text of its advancement (server quests and `advancement` objectives), secret ones left out. */
    val advancements: Map<String, AdvancementText> = emptyMap(),
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
