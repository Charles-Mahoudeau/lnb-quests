package fr.bde_teknantes.lnb.quests.progress

import fr.bde_teknantes.lnb.quests.LnbQuests
import fr.bde_teknantes.lnb.quests.api.CompletionBody
import fr.bde_teknantes.lnb.quests.api.CompletionResult
import fr.bde_teknantes.lnb.quests.api.Problems
import fr.bde_teknantes.lnb.quests.api.ProgressItem
import fr.bde_teknantes.lnb.quests.api.QuestDef
import fr.bde_teknantes.lnb.quests.api.QuestsApi
import fr.bde_teknantes.lnb.quests.catalog.Catalog
import fr.bde_teknantes.lnb.quests.community.CommunityQuest
import fr.bde_teknantes.lnb.quests.flash.FlashRaces
import fr.bde_teknantes.lnb.quests.journal.JournalEntry
import fr.bde_teknantes.lnb.quests.journal.Outcome
import fr.bde_teknantes.lnb.quests.journal.QuestJournal
import fr.bde_teknantes.lnb.quests.Texts
import fr.bde_teknantes.lnb.quests.network.QuestScreens
import fr.bde_teknantes.lnb.quests.network.Toasts
import fr.bde_teknantes.lnb.warden.common.api.ApiResult
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.GameType
import java.util.UUID

/**
 * Where every counted action ends up: a block mined, a mob killed, a catch, a craft, a distance, a delivery or an
 * advancement. It moves the player's individual counters (and reports tiers to the site), the flash race and the
 * community quest.
 */
object Progress {
    private const val PURPOSE = "completion"

    fun register() {
        QuestJournal.register(PURPOSE, ::finishCompletion)
    }

    /** Whether this player's actions count: survival or adventure, unless the anti-cheat setting is off. */
    fun counts(player: ServerPlayer): Boolean {
        if (player is net.neoforged.neoforge.common.util.FakePlayer) return false
        if (!Catalog.config.antiCheat.excludeCreative) return true
        val mode = player.gameMode.gameModeForPlayer
        return mode == GameType.SURVIVAL || mode == GameType.ADVENTURE
    }

    /**
     * [amount] more of an action of [type] (`mine`, `kill`...) by [player]. [matches] says whether a quest's target
     * covers what was done.
     */
    fun add(player: ServerPlayer, type: String, amount: Long, matches: (target: String?) -> Boolean) {
        if (amount <= 0 || !counts(player)) return
        val server = player.server
        val state = QuestState.get(server)
        val mine = state.player(player.uuid)
        if (mine.season != state.season) Seasons.resetPlayer(player, mine, state.season)
        var changed = false
        for (quest in Catalog.quests) {
            if (quest.objective.type != type || !matches(quest.objective.target)) continue
            when {
                quest.isIndividual -> {
                    mine.counters.merge(quest.id, amount, Long::plus)
                    mine.dirty.add(quest.id)
                    changed = true
                    checkTiers(player, quest, mine)
                }
                quest.isFlash -> FlashRaces.onProgress(player, quest, amount)
            }
        }
        CommunityQuest.onProgress(player, type, amount, matches)
        if (changed) state.setDirty()
    }

    /** Sets an individual counter to at least [value] (advancement objectives: 1). */
    fun reach(player: ServerPlayer, quest: QuestDef, value: Long) {
        if (!counts(player)) return
        val state = QuestState.get(player.server)
        val mine = state.player(player.uuid)
        if ((mine.counters[quest.id] ?: 0) >= value) return
        mine.counters[quest.id] = value
        mine.dirty.add(quest.id)
        checkTiers(player, quest, mine)
        state.setDirty()
    }

    /** Reports the next tier the counter reached, once the tier before it is confirmed. */
    private fun checkTiers(player: ServerPlayer, quest: QuestDef, mine: PlayerQuests) {
        val tiers = quest.tiers ?: return
        val value = mine.counters[quest.id] ?: 0
        for ((index, tier) in tiers.withIndex()) {
            val number = index + 1
            val key = "${quest.id}:$number"
            if (key in mine.reported) {
                if (key !in mine.confirmed) return
                continue
            }
            if (value < tier.amount) return
            mine.reported.add(key)
            QuestJournal.submit("/completions", PURPOSE, mapOf("player" to player.uuid.toString(), "quest" to quest.id, "tier" to "$number")) { k ->
                CompletionBody(k, player.uuid.toString(), quest.id, number)
            }
            return
        }
    }

    private fun finishCompletion(server: MinecraftServer, entry: JournalEntry, outcome: Outcome) {
        val uuid = UUID.fromString(entry.data["player"] ?: return)
        val questId = entry.data["quest"] ?: return
        val tier = entry.data["tier"]?.toIntOrNull() ?: return
        val state = QuestState.get(server)
        val mine = state.player(uuid)
        val key = "$questId:$tier"
        val player = server.playerList.getPlayer(uuid)
        when (outcome) {
            is Outcome.Delayed -> Unit
            is Outcome.Done -> {
                val result = outcome.decode<CompletionResult>()
                mine.confirmed.add(key)
                state.setDirty()
                val quest = Catalog.quest(questId)
                if (player != null && quest != null) {
                    player.sendSystemMessage(
                        Component.translatable("chat.lnbquests.tier_reached", Texts.name(quest, player), tier, Texts.amount(result.amount, player))
                            .withStyle(ChatFormatting.GOLD)
                            .append(" ")
                            .append(Texts.openLink(player)),
                    )
                    Toasts.send(
                        player,
                        Component.translatable("toast.lnbquests.tier", tier),
                        Component.translatable("toast.lnbquests.tier.description", quest.name.get(Texts.language(player)), Texts.number(result.amount, player)),
                        Toasts.icon(quest),
                        tier,
                    )
                    checkTiers(player, quest, mine)
                }
                QuestScreens.refresh(server, uuid)
                player?.let(QuestScreens::sendBadge)
            }
            is Outcome.Refused -> {
                val problem = (outcome.result as? ApiResult.Http)?.problem
                when (problem) {
                    // Already recorded (the other Minecraft account of the same person, or a lost answer).
                    Problems.ALREADY_COMPLETED -> mine.confirmed.add(key)
                    // The tier before wasn't recorded: send it again from the start.
                    Problems.TIER_LOCKED -> { mine.reported.removeIf { it.startsWith("$questId:") && it !in mine.confirmed } }
                    else -> {
                        mine.reported.remove(key)
                        if (player != null && problem == Problems.NOT_LINKED) player.sendSystemMessage(Texts.notLinked())
                        LnbQuests.LOGGER.warn("The site refused tier {} of {} for {}: {}", tier, questId, uuid, outcome.result.describe())
                    }
                }
                state.setDirty()
            }
        }
    }

    /** Sends the counters that changed, for the progress bars on the site. Not journaled: the next push fixes a loss. */
    fun pushCounters(server: MinecraftServer) {
        val state = QuestState.get(server)
        val items = state.players.flatMap { (uuid, mine) ->
            mine.dirty.mapNotNull { id -> mine.counters[id]?.let { ProgressItem(uuid.toString(), id, it) } }
        }
        if (items.isEmpty()) return
        state.players.values.forEach { it.dirty.clear() }
        state.setDirty()
        items.chunked(500).forEach { batch ->
            QuestsApi.progress(batch).thenAccept { result ->
                if (result !is ApiResult.Ok) LnbQuests.LOGGER.debug("Quest counters not sent: {}", result.describe())
            }
        }
    }
}
