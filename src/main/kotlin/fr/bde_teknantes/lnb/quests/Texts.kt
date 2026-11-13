package fr.bde_teknantes.lnb.quests

import fr.bde_teknantes.lnb.quests.api.Localized
import fr.bde_teknantes.lnb.quests.api.QuestDef
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.MutableComponent
import net.minecraft.server.level.ServerPlayer
import java.text.NumberFormat
import java.util.Locale

/** Chat texts shared by every part of the mod. */
object Texts {
    fun language(player: ServerPlayer): String = player.clientInformation().language()

    fun name(quest: QuestDef, player: ServerPlayer): Component = Component.literal(quest.name.get(language(player))).withStyle(ChatFormatting.YELLOW)

    fun name(text: Localized, player: ServerPlayer): Component = Component.literal(text.get(language(player))).withStyle(ChatFormatting.YELLOW)

    fun number(value: Long, player: ServerPlayer): String {
        val locale = if (language(player).startsWith("fr")) Locale.FRANCE else Locale.UK
        return NumberFormat.getIntegerInstance(locale).format(value)
    }

    fun amount(value: Long, player: ServerPlayer): Component =
        Component.translatable("chat.lnbquests.lnbits", number(value, player)).withStyle(ChatFormatting.GOLD)

    /** "[Quests]": opens the quest screen. */
    fun openLink(player: ServerPlayer): MutableComponent =
        Component.translatable("chat.lnbquests.open").withStyle { style ->
            style.withColor(ChatFormatting.AQUA)
                .withClickEvent(ClickEvent(ClickEvent.Action.RUN_COMMAND, "/quests"))
                .withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("chat.lnbquests.open.hint")))
        }

    fun notLinked(): Component = Component.translatable("chat.lnbquests.not_linked").withStyle(ChatFormatting.RED)
}
