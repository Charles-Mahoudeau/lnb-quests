package fr.bde_teknantes.lnb.quests.progress

import fr.bde_teknantes.lnb.quests.Texts
import fr.bde_teknantes.lnb.quests.catalog.Catalog
import fr.bde_teknantes.lnb.quests.catalog.Targets
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer

/**
 * Delivery quests: the player hands items over from their inventory, and they are used up. Only what the last tier
 * still needs is taken.
 */
object Deliveries {
    fun deliver(player: ServerPlayer, questId: String): Long {
        val quest = Catalog.quest(questId)?.takeIf { it.isIndividual && it.objective.type == "deliver" && it.enabled } ?: return 0
        if (!Progress.counts(player)) return 0
        val state = QuestState.get(player.server)
        val goal = quest.tiers?.lastOrNull()?.amount ?: return 0
        var wanted = goal - (state.player(player.uuid).counters[quest.id] ?: 0)
        var taken = 0L
        val inventory = player.inventory
        for (slot in 0 until inventory.containerSize) {
            if (wanted <= 0) break
            val stack = inventory.getItem(slot)
            if (stack.isEmpty || !Targets.matches(quest.objective.target, stack.itemHolder, Targets.ITEMS)) continue
            // Named, enchanted or otherwise changed items are kept: only plain ones are delivered.
            if (!stack.componentsPatch.isEmpty) continue
            val count = minOf(stack.count.toLong(), wanted).toInt()
            stack.shrink(count)
            wanted -= count
            taken += count
        }
        if (taken > 0) {
            inventory.setChanged()
            Progress.add(player, "deliver", taken) { target -> target == quest.objective.target }
            player.sendSystemMessage(
                Component.translatable("chat.lnbquests.delivered", Texts.number(taken, player), Texts.name(quest, player)).withStyle(ChatFormatting.GREEN),
            )
        } else {
            player.sendSystemMessage(Component.translatable("chat.lnbquests.nothing_to_deliver", Texts.name(quest, player)).withStyle(ChatFormatting.GRAY))
        }
        return taken
    }
}
