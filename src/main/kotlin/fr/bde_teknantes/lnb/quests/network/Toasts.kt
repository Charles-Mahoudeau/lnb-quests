package fr.bde_teknantes.lnb.quests.network

import fr.bde_teknantes.lnb.quests.api.QuestDef
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.neoforged.neoforge.network.PacketDistributor

/** Corner notifications for quest moments: a tier reached, a rank, a flash race won, a reward claimed. */
object Toasts {
    fun icon(quest: QuestDef?): ItemStack {
        val item = quest?.icon?.let(ResourceLocation::tryParse)?.let { BuiltInRegistries.ITEM.getOptional(it).orElse(null) }
        return ItemStack(item ?: Items.WRITABLE_BOOK)
    }

    fun send(player: ServerPlayer, title: Component, description: Component, icon: ItemStack, level: Int = 1) {
        PacketDistributor.sendToPlayer(player, QuestToastPayload(title, description, icon, level))
    }
}
