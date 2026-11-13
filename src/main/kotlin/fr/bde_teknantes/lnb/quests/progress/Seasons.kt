package fr.bde_teknantes.lnb.quests.progress

import fr.bde_teknantes.lnb.quests.LnbQuests
import fr.bde_teknantes.lnb.quests.catalog.Catalog
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

/**
 * Seasons come from the site. When its number goes up, every player starts over: counters, tiers, and the advancements
 * of the catalogue's server quests, taken back so they can be won again. Players online now are reset at once, the
 * others at their next login. Recipes and every other advancement stay.
 */
object Seasons {
    /** The site's season, seen in its config. */
    fun onConfig(server: MinecraftServer, number: Int) {
        val state = QuestState.get(server)
        if (number <= 0 || number == state.season) return
        val first = state.season == 0
        state.season = number
        state.setDirty()
        if (first) {
            // The first season the server sees: nothing to take back.
            state.players.values.forEach { it.season = number }
            return
        }
        LnbQuests.LOGGER.info("Quest season {} started: every player starts over", number)
        server.playerList.players.forEach { player ->
            resetPlayer(player, state.player(player.uuid), number)
            player.sendSystemMessage(Component.translatable("chat.lnbquests.new_season", number).withStyle(ChatFormatting.GOLD))
        }
        state.setDirty()
    }

    fun onLogin(player: ServerPlayer) {
        val state = QuestState.get(player.server)
        val mine = state.player(player.uuid)
        if (state.season > 0 && mine.season != state.season) {
            resetPlayer(player, mine, state.season)
            state.setDirty()
        }
    }

    fun resetPlayer(player: ServerPlayer, mine: PlayerQuests, season: Int) {
        mine.resetFor(season)
        val advancements = player.server.advancements
        Catalog.config.quests.filter { it.isServer }.forEach { quest ->
            val id = quest.advancement?.let(ResourceLocation::tryParse) ?: return@forEach
            val holder = advancements.get(id) ?: return@forEach
            val progress = player.advancements.getOrStartProgress(holder)
            progress.completedCriteria.toList().forEach { criterion -> player.advancements.revoke(holder, criterion) }
        }
    }
}
