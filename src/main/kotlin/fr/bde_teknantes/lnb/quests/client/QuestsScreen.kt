package fr.bde_teknantes.lnb.quests.client

import fr.bde_teknantes.lnb.quests.api.QuestDef
import fr.bde_teknantes.lnb.quests.network.ClaimPayload
import fr.bde_teknantes.lnb.quests.network.ClosedQuestsPayload
import fr.bde_teknantes.lnb.quests.network.DeliverPayload
import fr.bde_teknantes.lnb.quests.network.ScreenData
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.neoforged.neoforge.network.PacketDistributor
import java.text.NumberFormat
import java.util.Locale

/**
 * The quest screen: four tabs (personal, server, community, flash), drawn with vanilla widgets until the visual pass.
 * The server sends its data ([ScreenData]) when it opens and after every tier, claim or delivery.
 */
class QuestsScreen(private var data: ScreenData) : Screen(Component.translatable("gui.lnbquests.title")) {
    private enum class Tab(val key: String) { INDIVIDUAL("individual"), SERVER("server"), COMMUNITY("community"), FLASH("flash") }

    private companion object {
        const val WIDTH = 340
        const val HEIGHT = 224
        const val ROW = 44
        const val SERVER_ROW = 30
        const val PANEL = 0xE0101330.toInt()
        const val CARD = 0xFF1C2150.toInt()
        const val LINE = 0xFF282F66.toInt()
        const val TEXT = 0xFFE9EBF8.toInt()
        const val MUTED = 0xFFA0A6D0.toInt()
        const val ACCENT = 0xFFA99BFF.toInt()
        const val GOLD = 0xFFF5C451.toInt()
        const val GREEN = 0xFF5CCF8A.toInt()
        val CATEGORIES = listOf("mining", "combat", "fishing", "exploration", "crafting", "delivery")
    }

    private var tab = Tab.INDIVIDUAL
    private var category = "mining"
    private var scroll = 0
    private var left = 0
    private var top = 0

    private val language get() = Minecraft.getInstance().options.languageCode
    private fun format(value: Long): String =
        NumberFormat.getIntegerInstance(if (language.startsWith("fr")) Locale.FRANCE else Locale.UK).format(value)

    fun update(next: ScreenData) {
        data = next
        rebuild()
    }

    override fun init() {
        left = (width - WIDTH) / 2
        top = (height - HEIGHT) / 2
        if (data.quests.none { it.isIndividual && it.category == category }) {
            category = CATEGORIES.firstOrNull { c -> data.quests.any { it.isIndividual && it.category == c } } ?: category
        }
        rebuild()
    }

    private fun rebuild() {
        clearWidgets()
        Tab.entries.forEachIndexed { index, value ->
            val button = Button.builder(Component.translatable("gui.lnbquests.tab.${value.key}")) {
                tab = value
                scroll = 0
                rebuild()
            }.bounds(left + 8 + index * 82, top + 22, 80, 16).build()
            button.active = tab != value
            addRenderableWidget(button)
        }
        when (tab) {
            Tab.INDIVIDUAL -> buildIndividual()
            Tab.SERVER, Tab.COMMUNITY, Tab.FLASH -> Unit
        }
    }

    // Personal quests.

    private val individuals get() = data.quests.filter { it.isIndividual && it.category == category }

    private fun buildIndividual() {
        CATEGORIES.forEachIndexed { index, c ->
            val count = data.quests.count { it.isIndividual && it.category == c }
            val button = Button.builder(Component.translatable("gui.lnbquests.category.$c").append(" ($count)")) {
                category = c
                scroll = 0
                rebuild()
            }.bounds(left + 8, top + 44 + index * 20, 76, 18).build()
            button.active = c != category
            addRenderableWidget(button)
        }
        visibleRows(individuals.size, ROW).forEach { index ->
            val quest = individuals[index]
            val y = top + 44 + (index - scroll) * ROW
            val tiers = quest.tiers.orEmpty()
            tiers.forEachIndexed { tierIndex, _ ->
                val completion = completion(quest.id, tierIndex + 1)
                if (completion != null && completion.claimedAt == null) {
                    addRenderableWidget(
                        Button.builder(Component.translatable("gui.lnbquests.claim")) {
                            it.active = false
                            PacketDistributor.sendToServer(ClaimPayload(completion.id))
                        }.bounds(tierX(tierIndex) + 44, y + 26, 34, 14).build(),
                    )
                }
            }
            if (quest.objective.type == "deliver") {
                addRenderableWidget(
                    Button.builder(Component.translatable("gui.lnbquests.deliver")) {
                        PacketDistributor.sendToServer(DeliverPayload(quest.id))
                    }.bounds(left + WIDTH - 60, y + 2, 50, 14).build(),
                )
            }
        }
    }

    private fun tierX(index: Int) = left + 92 + index * 80

    private fun completion(quest: String, tier: Int) = data.view?.completions?.firstOrNull { it.quest == quest && it.tier == tier }

    private fun visibleRows(count: Int, rowHeight: Int): IntRange {
        val fits = (HEIGHT - 52) / rowHeight
        scroll = scroll.coerceIn(0, maxOf(0, count - fits))
        return scroll until minOf(count, scroll + fits)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        scroll -= scrollY.toInt().coerceIn(-1, 1)
        rebuild()
        return true
    }

    // Drawing.

    override fun renderBackground(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick)
        graphics.fill(left, top, left + WIDTH, top + HEIGHT, PANEL)
        graphics.renderOutline(left, top, WIDTH, HEIGHT, LINE)
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(graphics, mouseX, mouseY, partialTick)
        graphics.drawString(font, title, left + 8, top + 8, TEXT, false)
        val season = Component.translatable("gui.lnbquests.season", data.season)
        graphics.drawString(font, season, left + WIDTH - 8 - font.width(season), top + 8, MUTED, false)
        when (tab) {
            Tab.INDIVIDUAL -> drawIndividual(graphics)
            Tab.SERVER -> drawServer(graphics)
            Tab.COMMUNITY -> drawCommunity(graphics)
            Tab.FLASH -> drawFlash(graphics)
        }
        if (data.problem != null && data.view == null) {
            val text = Component.translatable(if (data.problem == "notLinked") "gui.lnbquests.not_linked" else "gui.lnbquests.offline")
            graphics.drawCenteredString(font, text, left + WIDTH / 2, top + HEIGHT - 12, GOLD)
        }
    }

    private fun icon(graphics: GuiGraphics, id: String, x: Int, y: Int) {
        val item = ResourceLocation.tryParse(id)?.let { BuiltInRegistries.ITEM.get(it) } ?: Items.PAPER
        graphics.renderItem(ItemStack(item), x, y)
    }

    private fun drawIndividual(graphics: GuiGraphics) {
        val quests = individuals
        if (quests.isEmpty()) {
            graphics.drawString(font, Component.translatable("gui.lnbquests.empty"), left + 92, top + 48, MUTED, false)
            return
        }
        visibleRows(quests.size, ROW).forEach { index ->
            val quest = quests[index]
            val y = top + 44 + (index - scroll) * ROW
            graphics.fill(left + 88, y, left + WIDTH - 6, y + ROW - 3, CARD)
            icon(graphics, quest.icon, left + 92, y + 3)
            graphics.drawString(font, quest.name.get(language), left + 112, y + 4, TEXT, false)
            val counter = data.counters[quest.id] ?: 0
            quest.tiers.orEmpty().forEachIndexed { tierIndex, tier ->
                val x = tierX(tierIndex)
                val completion = completion(quest.id, tierIndex + 1)
                val previousDone = tierIndex == 0 || completion(quest.id, tierIndex) != null
                graphics.drawString(font, Component.translatable("gui.lnbquests.tier", tierIndex + 1, format(tier.amount)), x, y + 16, MUTED, false)
                when {
                    completion?.claimedAt != null ->
                        graphics.drawString(font, Component.translatable("gui.lnbquests.claimed", format(completion.amount)), x, y + 29, GREEN, false)
                    completion != null ->
                        graphics.drawString(font, format(completion.amount), x, y + 29, GOLD, false)
                    !previousDone -> graphics.drawString(font, Component.translatable("gui.lnbquests.locked"), x, y + 29, MUTED, false)
                    else -> {
                        val shown = minOf(counter, tier.amount)
                        graphics.fill(x, y + 27, x + 74, y + 29, LINE)
                        graphics.fill(x, y + 27, x + (74 * shown / tier.amount.coerceAtLeast(1)).toInt(), y + 29, ACCENT)
                        val estimate = (tier.reward * data.kGlobal * (data.view?.kPlayer ?: 1.0)).toLong()
                        graphics.drawString(font, "${format(shown)} · ≈${format(estimate)}", x, y + 31, MUTED, false)
                    }
                }
            }
        }
    }

    private val servers get() = data.quests.filter { it.isServer }

    private fun drawServer(graphics: GuiGraphics) {
        val quests = servers
        visibleRows(quests.size, SERVER_ROW).forEach { index ->
            val quest = quests[index]
            val y = top + 44 + (index - scroll) * SERVER_ROW
            graphics.fill(left + 6, y, left + WIDTH - 6, y + SERVER_ROW - 3, if (data.view?.ranks?.containsKey(quest.id) == true) 0xFF2A2463.toInt() else CARD)
            icon(graphics, quest.icon, left + 10, y + 5)
            graphics.drawString(font, quest.name.get(language), left + 30, y + 4, TEXT, false)
            graphics.drawString(font, Component.translatable("gui.lnbquests.rarity.${quest.tier}"), left + 30, y + 15, ACCENT, false)
            val podium = data.view?.podiums?.get(quest.id).orEmpty()
            val shares = quest.ranks.orEmpty()
            shares.forEachIndexed { rank, share ->
                val x = left + 150 + rank * 62
                val entry = podium.firstOrNull { it.rank == rank + 1 }
                graphics.drawString(font, "${rank + 1}.", x, y + 4, MUTED, false)
                if (entry != null) {
                    graphics.drawString(font, font.plainSubstrByWidth(entry.username, 48), x + 10, y + 4, TEXT, false)
                    graphics.drawString(font, format(entry.amount), x + 10, y + 15, GOLD, false)
                } else {
                    val estimate = ((quest.reward ?: 0.0) * share / 100 * data.kGlobal).toLong()
                    graphics.drawString(font, Component.translatable("gui.lnbquests.free"), x + 10, y + 4, MUTED, false)
                    graphics.drawString(font, "≈${format(estimate)}", x + 10, y + 15, MUTED, false)
                }
            }
        }
    }

    private fun drawCommunity(graphics: GuiGraphics) {
        val community = data.community
        val x = left + 12
        var y = top + 50
        if (community == null) {
            graphics.drawWordWrap(font, Component.translatable("gui.lnbquests.community.none"), x, y, WIDTH - 24, MUTED)
            return
        }
        graphics.drawString(font, community.title.get(language), x, y, GOLD, false)
        y += 14
        val progress = maxOf(data.communityProgress, community.progress)
        graphics.drawString(font, Component.translatable("gui.lnbquests.community.progress", format(progress), format(community.goal)), x, y, TEXT, false)
        y += 14
        val maxAt = maxOf(100.0, community.tiers.maxOfOrNull { it.at } ?: 100.0)
        val barWidth = WIDTH - 24
        graphics.fill(x, y, x + barWidth, y + 6, LINE)
        val fill = (barWidth * minOf(1.0, progress * 100.0 / community.goal / maxAt)).toInt()
        graphics.fill(x, y, x + fill, y + 6, ACCENT)
        community.tiers.forEach { tier ->
            val tx = x + (barWidth * tier.at / maxAt).toInt().coerceAtMost(barWidth - 1)
            graphics.fill(tx, y - 2, tx + 1, y + 8, TEXT)
        }
        y += 12
        community.tiers.forEach { tier ->
            graphics.drawString(font, Component.translatable("gui.lnbquests.community.tier", tier.name, tier.at.toInt(), format(tier.rewardToday)), x, y, MUTED, false)
            y += 11
        }
        y += 4
        val part = data.view?.community
        if (part != null) {
            val threshold = (community.goal * community.thresholdPercent / 100).toLong()
            graphics.drawWordWrap(
                font,
                Component.translatable(
                    if (part.qualified) "gui.lnbquests.community.qualified" else "gui.lnbquests.community.not_qualified",
                    format(part.contribution),
                    format(threshold),
                ),
                x, y, WIDTH - 24, if (part.qualified) GREEN else MUTED,
            )
        }
    }

    private fun drawFlash(graphics: GuiGraphics) {
        val x = left + 12
        var y = top + 50
        val flash = data.flash
        if (flash == null) {
            graphics.drawWordWrap(font, Component.translatable("gui.lnbquests.flash.none"), x, y, WIDTH - 24, MUTED)
            return
        }
        val quest: QuestDef? = data.quests.firstOrNull { it.id == flash.quest }
        graphics.drawString(font, Component.translatable("gui.lnbquests.flash.goal", quest?.name?.get(language) ?: flash.quest, flash.quantity), x, y, GOLD, false)
        y += 14
        val left = ((flash.endsAt - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
        graphics.drawString(font, Component.translatable("gui.lnbquests.flash.left", "%d:%02d".format(left / 60, left % 60)), x, y, TEXT, false)
        y += 14
        graphics.drawString(font, Component.translatable("gui.lnbquests.flash.mine", flash.mine, flash.quantity), x, y, TEXT, false)
        y += 12
        flash.leader?.let {
            graphics.drawString(font, Component.translatable("gui.lnbquests.flash.leader", it, flash.leaderValue), x, y, MUTED, false)
        }
    }

    override fun isPauseScreen() = false

    override fun removed() {
        PacketDistributor.sendToServer(ClosedQuestsPayload())
        super.removed()
    }
}
