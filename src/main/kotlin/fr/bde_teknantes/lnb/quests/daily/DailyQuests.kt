package fr.bde_teknantes.lnb.quests.daily

import fr.bde_teknantes.lnb.quests.LnbQuests
import fr.bde_teknantes.lnb.quests.Texts
import fr.bde_teknantes.lnb.quests.api.DailyBody
import fr.bde_teknantes.lnb.quests.api.DailyEntry
import fr.bde_teknantes.lnb.quests.api.DailyResult
import fr.bde_teknantes.lnb.quests.api.Problems
import fr.bde_teknantes.lnb.quests.api.QuestDef
import fr.bde_teknantes.lnb.quests.catalog.Catalog
import fr.bde_teknantes.lnb.quests.journal.JournalEntry
import fr.bde_teknantes.lnb.quests.journal.Outcome
import fr.bde_teknantes.lnb.quests.journal.QuestJournal
import fr.bde_teknantes.lnb.quests.network.QuestScreens
import fr.bde_teknantes.lnb.quests.network.Toasts
import fr.bde_teknantes.lnb.quests.progress.PlayerQuests
import fr.bde_teknantes.lnb.quests.progress.QuestState
import fr.bde_teknantes.lnb.warden.common.api.ApiResult
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * Daily quests: the site draws them for each Paris day, the same for everyone (`daily` in its config). Each player's
 * progress on today's quests counts from midnight; a player who reaches a quest's quantity is reported once, and the
 * site pays them at once. Just after midnight the config is read again, for the new draw.
 */
object DailyQuests {
    private const val PURPOSE = "daily"
    private val PARIS: ZoneId = ZoneId.of("Europe/Paris")

    /** The Paris day of an instant, "YYYY-MM-DD", as the site writes it. */
    fun day(at: Instant = Instant.now()): String = LocalDate.ofInstant(at, PARIS).toString()

    /** When a Paris day ends: its next midnight. */
    fun endOf(day: String): Instant = LocalDate.parse(day).plusDays(1).atStartOfDay(PARIS).toInstant()

    private var lastDay = ""

    fun register() {
        QuestJournal.register(PURPOSE, ::finish)
    }

    /** Today's draw, or nothing while the config the server holds is for another day. */
    fun today(): List<Pair<QuestDef, DailyEntry>> {
        val daily = Catalog.config.daily
        if (daily.day != day()) return emptyList()
        return daily.quests.mapNotNull { entry -> Catalog.quest(entry.quest)?.takeIf { it.isDaily && it.enabled }?.let { it to entry } }
    }

    /** Once a second: the day changed, so the site has a new draw to give. */
    fun onSecond(server: MinecraftServer) {
        val now = day()
        if (now == lastDay) return
        val first = lastDay.isEmpty()
        lastDay = now
        if (!first) LnbQuests.reloadConfig(server)
    }

    /** [amount] more on a daily quest [quest], which matched what [player] did. */
    fun onProgress(player: ServerPlayer, quest: QuestDef, amount: Long, mine: PlayerQuests) {
        val today = day()
        val entry = Catalog.config.daily.takeIf { it.day == today }?.quests?.firstOrNull { it.quest == quest.id } ?: return
        mine.dailyFor(today)
        if (quest.id in mine.dailyReported || quest.id in mine.dailyDone) return
        val value = (mine.dailyCounters[quest.id] ?: 0) + amount
        mine.dailyCounters[quest.id] = value
        player.displayClientMessage(
            Component.translatable("actionbar.lnbquests.daily", quest.name.get(Texts.language(player)), minOf(value, entry.quantity), entry.quantity)
                .withStyle(ChatFormatting.AQUA),
            true,
        )
        if (value < entry.quantity) return
        mine.dailyReported.add(quest.id)
        QuestJournal.submit("/daily", PURPOSE, mapOf("player" to player.uuid.toString(), "quest" to quest.id, "day" to today)) { key ->
            DailyBody(key, player.uuid.toString(), today, quest.id)
        }
    }

    private fun finish(server: MinecraftServer, entry: JournalEntry, outcome: Outcome) {
        val uuid = UUID.fromString(entry.data["player"] ?: return)
        val questId = entry.data["quest"] ?: return
        val day = entry.data["day"] ?: return
        val state = QuestState.get(server)
        val mine = state.player(uuid)
        val player = server.playerList.getPlayer(uuid)
        // A report for a day that's over only matters to the site: the counters moved on.
        val current = mine.dailyDay == day
        when (outcome) {
            is Outcome.Delayed -> Unit
            is Outcome.Done -> {
                val result = outcome.decode<DailyResult>()
                if (current) mine.dailyDone.add(questId)
                val quest = Catalog.quest(questId)
                if (player != null && quest != null && !result.replayed) {
                    player.sendSystemMessage(
                        Component.translatable("chat.lnbquests.daily.done", Texts.name(quest, player), Texts.amount(result.amount, player))
                            .withStyle(ChatFormatting.AQUA),
                    )
                    Toasts.send(
                        player,
                        Component.translatable("toast.lnbquests.daily"),
                        Component.translatable("toast.lnbquests.daily.description", quest.name.get(Texts.language(player)), Texts.number(result.amount, player)),
                        Toasts.icon(quest),
                    )
                }
                QuestScreens.refresh(server, uuid)
            }
            is Outcome.Refused -> {
                val problem = (outcome.result as? ApiResult.Http)?.problem
                when (problem) {
                    // Paid already (another Minecraft account of the same person, or a lost answer).
                    Problems.ALREADY_COMPLETED -> if (current) mine.dailyDone.add(questId)
                    // The day is over, or the quest wasn't drawn: nothing to pay.
                    Problems.EXPIRED, Problems.NOT_DRAWN, Problems.UNKNOWN_QUEST -> Unit
                    else -> {
                        if (current) mine.dailyReported.remove(questId)
                        if (player != null && problem == Problems.NOT_LINKED) player.sendSystemMessage(Texts.notLinked())
                        LnbQuests.LOGGER.warn("The site refused daily quest {} for {}: {}", questId, uuid, outcome.result.describe())
                    }
                }
                QuestScreens.refresh(server, uuid)
            }
        }
        state.setDirty()
    }
}
