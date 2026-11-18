package fr.bde_teknantes.lnb.quests.progress

import fr.bde_teknantes.lnb.quests.LnbQuests
import fr.bde_teknantes.lnb.quests.catalog.Catalog
import fr.bde_teknantes.lnb.quests.flash.FlashRaces
import fr.bde_teknantes.lnb.quests.network.QuestScreens
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

/**
 * An admin can reset the quests on the site; its config then carries a new `resetAt`. Every player starts over:
 * counters (individual, daily, flash), cached tiers and ranks, and the advancements of the catalogue's server quests,
 * taken back so they can be won again. Players online now are cleaned at once, the others at their next login (each
 * player keeps the `resetAt` they were last cleaned for, so a second reset before they come back still works).
 * Recipes and every other advancement stay.
 *
 * The server's own value is stored last, so a stop in the middle runs the job again; running it twice is harmless.
 */
object Resets {
    fun onConfig(server: MinecraftServer, resetAt: String?) {
        val state = QuestState.get(server)
        val incoming = resetAt.orEmpty()
        when (ResetRule.onConfig(state.resetKnown, state.resetAt, incoming)) {
            ResetRule.Action.NONE -> return
            ResetRule.Action.ADOPT -> {
                // First run (or first run since seasons went away): the players already match the site.
                state.players.values.forEach { it.resetAt = incoming }
                state.resetAt = incoming
                state.resetKnown = true
                state.setDirty()
                LnbQuests.LOGGER.info("Quest reset date stored without resetting: {}", resetAt ?: "never reset")
                return
            }
            ResetRule.Action.RESET -> Unit
        }
        LnbQuests.LOGGER.info("The quests were reset on the site ({}): every player starts over", resetAt ?: "null")
        // The community quest they were for no longer exists on the site.
        state.pendingCommunity.clear()
        FlashRaces.current?.let { run ->
            run.progress.clear()
            run.names.clear()
        }
        server.playerList.players.forEach { player ->
            resetPlayer(player, state.player(player.uuid), incoming)
            player.sendSystemMessage(Component.translatable("chat.lnbquests.reset").withStyle(ChatFormatting.GOLD))
            QuestScreens.refresh(server, player.uuid)
            QuestScreens.sendBadge(player)
        }
        state.resetAt = incoming
        state.setDirty()
    }

    fun onLogin(player: ServerPlayer) {
        val state = QuestState.get(player.server)
        val mine = state.player(player.uuid)
        if (stale(state, mine)) {
            resetPlayer(player, mine, state.resetAt)
            state.setDirty()
        }
    }

    fun stale(state: QuestState, mine: PlayerQuests): Boolean =
        state.resetKnown && ResetRule.playerStale(mine.resetAt, state.resetAt)

    fun resetPlayer(player: ServerPlayer, mine: PlayerQuests, resetAt: String) {
        mine.resetFor(resetAt)
        val advancements = player.server.advancements
        Catalog.config.quests.filter { it.isServer }.forEach { quest ->
            val id = quest.advancement?.let(ResourceLocation::tryParse) ?: return@forEach
            val holder = advancements.get(id) ?: return@forEach
            val progress = player.advancements.getOrStartProgress(holder)
            progress.completedCriteria.toList().forEach { criterion -> player.advancements.revoke(holder, criterion) }
        }
    }
}
