package fr.bde_teknantes.lnb.quests.community

import fr.bde_teknantes.lnb.quests.LnbQuests
import fr.bde_teknantes.lnb.quests.Texts
import fr.bde_teknantes.lnb.quests.api.Community
import fr.bde_teknantes.lnb.quests.api.ContributionBody
import fr.bde_teknantes.lnb.quests.api.ContributionItem
import fr.bde_teknantes.lnb.quests.api.ContributionResult
import fr.bde_teknantes.lnb.quests.api.Problems
import fr.bde_teknantes.lnb.quests.catalog.Catalog
import fr.bde_teknantes.lnb.quests.journal.JournalEntry
import fr.bde_teknantes.lnb.quests.journal.Outcome
import fr.bde_teknantes.lnb.quests.journal.QuestJournal
import fr.bde_teknantes.lnb.quests.progress.QuestState
import fr.bde_teknantes.lnb.warden.common.api.ApiResult
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

/**
 * The running community quest: what players bring is added up here and sent every `pollSeconds`, through the
 * journal so a batch is never counted twice. The site pays when the quest ends. Players hear about it when they log in.
 */
object CommunityQuest {
    private const val PURPOSE = "community"

    /** The site's progress, from the last config or contribution answer. */
    @Volatile
    var progress: Long = 0
        private set

    fun register() {
        QuestJournal.register(PURPOSE, ::finish)
    }

    val running: Community? get() = Catalog.config.community?.takeIf { it.status == "running" }

    fun onConfig(community: Community?) {
        if (community != null) progress = maxOf(progress, community.progress)
        if (community == null || community.id != lastId) progress = community?.progress ?: 0
        lastId = community?.id
    }

    private var lastId: String? = null

    fun onProgress(player: ServerPlayer, type: String, amount: Long, matches: (String?) -> Boolean) {
        val community = running ?: return
        if (community.objective.type != type || !matches(community.objective.target)) return
        val state = QuestState.get(player.server)
        state.pendingCommunity.getOrPut(community.id) { HashMap() }.merge(player.uuid, amount, Long::plus)
        state.setDirty()
    }

    fun flush(server: MinecraftServer) {
        val state = QuestState.get(server)
        if (state.pendingCommunity.isEmpty()) return
        val batches = state.pendingCommunity.toMap()
        state.pendingCommunity.clear()
        state.setDirty()
        batches.forEach { (id, amounts) ->
            amounts.entries.filter { it.value > 0 }.chunked(500).forEach { chunk ->
                QuestJournal.submit("/community/$id/contributions", PURPOSE, mapOf("community" to id)) { key ->
                    ContributionBody(key, chunk.map { ContributionItem(it.key.toString(), it.value) })
                }
            }
        }
    }

    private fun finish(server: MinecraftServer, entry: JournalEntry, outcome: Outcome) {
        when (outcome) {
            is Outcome.Done -> progress = maxOf(progress, outcome.decode<ContributionResult>().progress)
            is Outcome.Refused -> {
                val problem = (outcome.result as? ApiResult.Http)?.problem
                if (problem != Problems.COMMUNITY_CLOSED) {
                    LnbQuests.LOGGER.warn("The site refused community contributions for {}: {}", entry.data["community"], outcome.result.describe())
                }
            }
            is Outcome.Delayed -> Unit
        }
    }

    fun onLogin(player: ServerPlayer) {
        val community = running ?: return
        player.sendSystemMessage(
            Component.translatable(
                "chat.lnbquests.community.reminder",
                Texts.name(community.title, player),
                Texts.number(progress, player),
                Texts.number(community.goal, player),
            ).withStyle(ChatFormatting.GOLD).append(" ").append(Texts.openLink(player)),
        )
    }
}
