package fr.bde_teknantes.lnb.quests.progress

import fr.bde_teknantes.lnb.quests.LnbQuests
import fr.bde_teknantes.lnb.quests.Texts
import fr.bde_teknantes.lnb.quests.api.AdvancementBody
import fr.bde_teknantes.lnb.quests.api.AdvancementResult
import fr.bde_teknantes.lnb.quests.api.Problems
import fr.bde_teknantes.lnb.quests.catalog.Catalog
import fr.bde_teknantes.lnb.quests.journal.JournalEntry
import fr.bde_teknantes.lnb.quests.journal.Outcome
import fr.bde_teknantes.lnb.quests.journal.QuestJournal
import fr.bde_teknantes.lnb.quests.network.QuestScreens
import fr.bde_teknantes.lnb.warden.common.api.ApiResult
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.neoforged.neoforge.event.entity.player.AdvancementEvent
import java.util.UUID

/**
 * Server quests: an advancement of the catalogue, paid by rank across the whole network. The site gives the rank; the
 * podium is announced to everyone, the rest to the player only.
 */
object ServerQuests {
    private const val PURPOSE = "advancement"

    fun register() {
        QuestJournal.register(PURPOSE, ::finish)
    }

    fun onAdvancement(event: AdvancementEvent.AdvancementEarnEvent) {
        val player = event.entity as? ServerPlayer ?: return
        val id = event.advancement.id().toString()
        // Individual quests that ask for an advancement.
        Catalog.quests.filter { it.isIndividual && it.objective.type == "advancement" && it.objective.target == id }
            .forEach { Progress.reach(player, it, 1) }
        if (!Progress.counts(player)) return
        val state = QuestState.get(player.server)
        val mine = state.player(player.uuid)
        Catalog.quests.filter { it.isServer && it.advancement == id }.forEach { quest ->
            if (!mine.ranked.add(quest.id)) return@forEach
            state.setDirty()
            QuestJournal.submit("/advancements", PURPOSE, mapOf("player" to player.uuid.toString(), "quest" to quest.id)) { key ->
                AdvancementBody(key, player.uuid.toString(), quest.id)
            }
        }
    }

    private fun finish(server: MinecraftServer, entry: JournalEntry, outcome: Outcome) {
        val uuid = UUID.fromString(entry.data["player"] ?: return)
        val quest = Catalog.quest(entry.data["quest"] ?: return) ?: return
        val player = server.playerList.getPlayer(uuid)
        when (outcome) {
            is Outcome.Delayed -> Unit
            is Outcome.Done -> {
                val result = outcome.decode<AdvancementResult>()
                if (result.replayed) return
                val name = player?.gameProfile?.name ?: result.podium.firstOrNull { it.rank == result.rank }?.username ?: "?"
                if (result.rank <= (quest.ranks?.size ?: 3)) {
                    server.playerList.players.forEach { viewer ->
                        viewer.sendSystemMessage(
                            Component.translatable(
                                "chat.lnbquests.ranked.public",
                                Component.literal(name).withStyle(ChatFormatting.AQUA),
                                rankText(result.rank, viewer),
                                Texts.name(quest, viewer),
                                Texts.amount(result.amount, viewer),
                            ).withStyle(ChatFormatting.GOLD),
                        )
                    }
                    player?.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8f, 1f)
                } else if (player != null) {
                    player.sendSystemMessage(
                        Component.translatable("chat.lnbquests.ranked.private", rankText(result.rank, player), Texts.name(quest, player), Texts.amount(result.amount, player))
                            .withStyle(ChatFormatting.GOLD),
                    )
                }
                if (player != null && result.mailboxItems > 0) {
                    player.sendSystemMessage(Component.translatable("chat.lnbquests.mailbox", result.mailboxItems).withStyle(ChatFormatting.GRAY))
                }
                QuestScreens.refresh(server, uuid)
            }
            is Outcome.Refused -> {
                val problem = (outcome.result as? ApiResult.Http)?.problem
                if (problem == Problems.ALREADY_RANKED) {
                    player?.sendSystemMessage(Component.translatable("chat.lnbquests.already_ranked", Texts.name(quest, player)).withStyle(ChatFormatting.GRAY))
                    return
                }
                if (problem == Problems.NOT_LINKED) player?.sendSystemMessage(Texts.notLinked())
                // Let the player try again after linking, or when the site is back.
                QuestState.get(server).apply { player(uuid).ranked.remove(quest.id); setDirty() }
                LnbQuests.LOGGER.warn("The site refused {}'s advancement {}: {}", uuid, quest.id, outcome.result.describe())
            }
        }
    }

    fun rankText(rank: Int, player: ServerPlayer): Component =
        if (Texts.language(player).startsWith("fr")) {
            Component.literal(if (rank == 1) "1er" else "${rank}e")
        } else {
            val suffix = if (rank % 100 in 11..13) "th" else when (rank % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }
            Component.literal("$rank$suffix")
        }
}
