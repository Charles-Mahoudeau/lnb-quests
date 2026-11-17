package fr.bde_teknantes.lnb.quests.client

import fr.bde_teknantes.lnb.quests.api.CategoryDef
import fr.bde_teknantes.lnb.quests.api.PlayerCompletion
import fr.bde_teknantes.lnb.quests.api.QuestDef
import fr.bde_teknantes.lnb.quests.api.SubcategoryDef
import fr.bde_teknantes.lnb.quests.client.QuestUi.NodeState
import fr.bde_teknantes.lnb.quests.network.ClaimPayload
import fr.bde_teknantes.lnb.quests.network.ClosedQuestsPayload
import fr.bde_teknantes.lnb.quests.network.DeliverPayload
import fr.bde_teknantes.lnb.quests.network.ScreenData
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.resources.language.I18n
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.neoforged.fml.ModList
import net.neoforged.neoforge.network.PacketDistributor
import kotlin.math.roundToLong

/**
 * The quest book, laid out like FTB Quests in LNB Economy's casino style: chapters (the site's individual categories)
 * on the left, the chapter's quests as linked round nodes on a canvas under their subcategory headings, details of the
 * chosen quest in a panel on the right. Records (server quests) are a tree per Records category and open in a popup.
 * Daily, Community and Flash have their own pages. The server sends its data ([ScreenData]) when the book opens and
 * after every tier, claim, daily quest or delivery.
 */
class QuestBookScreen(private var data: ScreenData) : Screen(Component.translatable("gui.lnbquests.title")) {
    private companion object {
        const val W = 416
        const val H = 230
        const val SIDEBAR = 112
        const val PANEL = 118
        const val ROW = 19
        const val CHAIN_STEP = 48
        const val CHAIN_ROW = 56
        const val RECORD_STEP = 34

        /** Without categories from the site: Records groups shown first, in this order; other mods follow. */
        val GROUP_ORDER = listOf("overworld", "nether", "end", "create", "stellarity", "cooking", "exploration", "fishing")

        /** Mods gathered under a theme in Records. Stellarity stays on its own: it is the End's content. */
        val MOD_GROUPS = mapOf(
            "farmersdelight" to "cooking",
            "betterdeserttemples" to "exploration",
            "betterdungeons" to "exploration",
            "betterfortresses" to "exploration",
            "betterstrongholds" to "exploration",
            "betterarcheology" to "exploration",
            "lootr" to "exploration",
            "tide" to "fishing",
        )
        const val RECORD_ROW = 44
        const val HEADING = 14
        const val SUB_HEADING = 12
        const val DAILY_ROW = 48

        /** Chapter rows sit closer than the pages', so six fit above the fixed pages. */
        const val CHAPTER_ROW = 18

        /** The chapters before the site sent categories. */
        val CATEGORIES = listOf("mining", "combat", "fishing", "exploration", "crafting", "delivery")
        val CATEGORY_ICONS = mapOf(
            "mining" to Items.DIAMOND_PICKAXE, "combat" to Items.IRON_SWORD, "fishing" to Items.FISHING_ROD,
            "exploration" to Items.COMPASS, "crafting" to Items.CRAFTING_TABLE, "delivery" to Items.BUNDLE,
        )
        const val RECORDS = "records"
        const val COMMUNITY = "community"
        const val FLASH = "flash"
        const val DAILY = "daily"

        /**
         * Quests grouped by subcategory, in the category's order: the ones without a known subcategory first (no
         * heading), then each subcategory that has quests.
         */
        fun bySubcategory(quests: List<QuestDef>, subcategories: List<SubcategoryDef>): List<Pair<SubcategoryDef?, List<QuestDef>>> {
            val known = subcategories.map { it.id }.toSet()
            val groups = ArrayList<Pair<SubcategoryDef?, List<QuestDef>>>()
            val loose = quests.filter { it.subcategory == null || it.subcategory !in known }
            if (loose.isNotEmpty()) groups += null to loose
            for (sub in subcategories) {
                val list = quests.filter { it.subcategory == sub.id }
                if (list.isNotEmpty()) groups += sub to list
            }
            return groups
        }
    }

    /** A chapter of the sidebar: an individual category. */
    private class Chapter(val id: String, val name: Component, val icon: ItemStack, val subcategories: List<SubcategoryDef>)

    /** A clickable area, rebuilt every frame. */
    private class Hit(val x: Int, val y: Int, val w: Int, val h: Int, val tooltip: List<Component> = emptyList(), val action: (() -> Unit)? = null) {
        fun contains(mx: Double, my: Double) = mx >= x && mx < x + w && my >= y && my < y + h
    }

    private var chapter = chapters().firstOrNull()?.id ?: RECORDS
    private var selected: Pair<String, Int>? = null
    private var popup: String? = null
    private var scroll = 0
    private var sideScroll = 0
    private var sideMax = 0

    /** How far the current page scrolls, set as it draws; the wheel never goes past it. */
    private var maxScroll = 0
    private val hits = ArrayList<Hit>()

    private var x0 = 0
    private var y0 = 0
    private val language get() = Minecraft.getInstance().options.languageCode
    private val kPlayer get() = data.view?.kPlayer ?: 1.0

    fun update(next: ScreenData) {
        data = next
    }

    override fun init() {
        x0 = (width - W) / 2
        y0 = (height - H) / 2
    }

    override fun isPauseScreen() = false

    override fun removed() {
        PacketDistributor.sendToServer(ClosedQuestsPayload)
        super.removed()
    }

    // Layout.

    private val sideX get() = x0 + 8
    private val sideY get() = y0 + 24
    private val canvasX get() = sideX + SIDEBAR + 4
    private val canvasY get() = sideY
    private val canvasW get() = W - SIDEBAR - 24
    private val canvasH get() = H - 32

    // Data helpers.

    private fun individuals(category: String) = data.quests.filter { it.isIndividual && it.category == category }
    private val records get() = data.quests.filter { it.isServer }

    /**
     * The chapters with quests, in the site's order. Before the site sent categories, the six the book always had; a
     * category the list doesn't know still gets a chapter, at the end, titled with its id.
     */
    private fun chapters(): List<Chapter> {
        val defined = data.categories.filter { it.family == QuestDef.INDIVIDUAL }
        val list = if (defined.isNotEmpty()) {
            defined.map { Chapter(it.id, Component.literal(it.name.get(language)), QuestUi.stack(it.icon), it.subcategories.orEmpty()) }
        } else {
            CATEGORIES.map { Chapter(it, Component.translatable("gui.lnbquests.category.$it"), ItemStack(CATEGORY_ICONS[it] ?: Items.PAPER), emptyList()) }
        }
        val known = list.map { it.id }.toSet()
        val extra = data.quests.mapNotNull { q -> q.category?.takeIf { q.isIndividual && it !in known } }.distinct()
            .map { Chapter(it, Component.literal(it), ItemStack(Items.PAPER), emptyList()) }
        return (list + extra).filter { individuals(it.id).isNotEmpty() }
    }

    /**
     * The title and description of the advancement a quest is about: from the server, which knows them all, else from
     * the advancements the client was shown. Null for a secret one, or a quest without an advancement.
     */
    private fun advancementText(quest: QuestDef): Pair<Component, Component>? {
        val registries = Minecraft.getInstance().connection?.registryAccess() ?: return null
        data.advancements[quest.id]?.let { text ->
            return runCatching {
                (Component.Serializer.fromJson(text.title, registries) ?: Component.empty()) to
                    (Component.Serializer.fromJson(text.description, registries) ?: Component.empty())
            }.getOrNull()
        }
        if (quest.hidden == true) return null
        val id = (if (quest.isServer) quest.advancement else quest.objective.target.takeIf { quest.objective.type == "advancement" })
            ?.let(ResourceLocation::tryParse) ?: return null
        val display = Minecraft.getInstance().connection?.advancements?.get(id)?.value()?.display()?.orElse(null) ?: return null
        return display.title to display.description
    }

    /** [text] cut into lines of [width], at most [max], in [color]: for tooltips. */
    private fun tipLines(text: Component, width: Int, max: Int, color: Int): List<Component> =
        font.splitter.splitLines(text, width, net.minecraft.network.chat.Style.EMPTY).take(max).map { Component.literal(it.string).withColor(color) }

    private fun serverCategory(id: String): CategoryDef? = data.categories.firstOrNull { it.family == QuestDef.SERVER && it.id == id }

    private fun completion(quest: String, tier: Int): PlayerCompletion? =
        data.view?.completions?.firstOrNull { it.quest == quest && it.tier == tier }

    private fun tierState(quest: QuestDef, tier: Int): NodeState {
        val done = completion(quest.id, tier)
        return when {
            done != null -> if (done.claimedAt == null) NodeState.READY else NodeState.CLAIMED
            tier > 1 && completion(quest.id, tier - 1) == null -> NodeState.LOCKED
            else -> NodeState.PROGRESS
        }
    }

    private fun estimate(base: Double): Long = (base * data.kGlobal * kPlayer).roundToLong()

    /**
     * The Records group of a server quest: its category, else a guess from its advancement: vanilla advancements by
     * dimension, modded ones by mod, with a few mods gathered under one theme. A mod without a theme gets its own
     * group, titled with its name.
     */
    private fun recordGroup(quest: QuestDef): String {
        quest.category?.let { return it }
        val id = quest.advancement?.let(ResourceLocation::tryParse) ?: return "overworld"
        if (id.namespace != "minecraft") return MOD_GROUPS[id.namespace] ?: id.namespace
        return when {
            id.path.startsWith("nether/") -> "nether"
            id.path.startsWith("end/") -> "end"
            else -> "overworld"
        }
    }

    private fun recordGroupTitle(group: String): Component {
        serverCategory(group)?.let { return Component.literal(it.name.get(language)) }
        val key = "gui.lnbquests.group.$group"
        if (I18n.exists(key)) return Component.translatable(key)
        val name = ModList.get().getModContainerById(group).map { it.modInfo.displayName }.orElse(group)
        return Component.literal(name)
    }

    /** "Mine 10 diamond ore": the objective in words, from its type, amount and target. */
    private fun objective(quest: QuestDef, amount: Long): Component {
        val target = quest.objective.target
        val name: Component = when {
            target.isNullOrBlank() -> return Component.translatable("gui.lnbquests.objective.${quest.objective.type}.any", QuestUi.number(amount, language))
            target.startsWith("#") -> tagName(quest, target.substring(1))
            quest.objective.type == "advancement" -> ResourceLocation.tryParse(target)?.let { id ->
                Minecraft.getInstance().connection?.advancements?.get(id)?.value()?.display()?.map { it.title }?.orElse(null)
            } ?: Component.literal(quest.name.get(language))
            else -> {
                val id = ResourceLocation.tryParse(target)
                when (quest.objective.type) {
                    "kill" -> id?.let { BuiltInRegistries.ENTITY_TYPE.getOptional(it).orElse(null)?.description }
                    "mine" -> id?.let { BuiltInRegistries.BLOCK.getOptional(it).orElse(null)?.name }
                    else -> id?.let { BuiltInRegistries.ITEM.getOptional(it).orElse(null)?.description }
                } ?: Component.literal(target)
            }
        }
        return Component.translatable("gui.lnbquests.objective.${quest.objective.type}", QuestUi.number(amount, language), name)
    }

    // Drawing.

    override fun renderBackground(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick)
        graphics.blitSprite(QuestUi.PANEL, x0, y0, W, H)
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(graphics, mouseX, mouseY, partialTick)
        hits.clear()
        QuestUi.title(graphics, font, title, x0 + 12, y0 + 10)
        val now = System.currentTimeMillis()
        val right: Component = when {
            chapter == FLASH && data.flash == null && data.nextFlashAt > now ->
                Component.translatable("gui.lnbquests.flash.next", ((data.nextFlashAt - now) / 60_000).coerceAtLeast(1))
            chapter == DAILY && data.daily.endsAt > now -> {
                val left = (data.daily.endsAt - now) / 60_000
                Component.translatable("gui.lnbquests.daily.ends", left / 60, left % 60)
            }
            chapter == COMMUNITY && data.community != null -> runCatching {
                val left = java.time.Duration.between(java.time.Instant.now(), java.time.Instant.parse(data.community!!.endsAt))
                Component.translatable("gui.lnbquests.community.ends", left.toDays(), left.toHours() % 24)
            }.getOrElse { Component.translatable("gui.lnbquests.season", data.season) }
            else -> Component.translatable("gui.lnbquests.season", data.season)
        }
        graphics.drawString(font, right, x0 + W - 12 - font.width(right), y0 + 10, QuestUi.DIM, true)
        sidebar(graphics, mouseX, mouseY)
        graphics.blitSprite(QuestUi.PANEL_INSET, canvasX, canvasY, canvasW, canvasH)
        for (gy in canvasY + 8 until canvasY + canvasH - 4 step 12) {
            for (gx in canvasX + 8 until canvasX + canvasW - 4 step 12) graphics.fill(gx, gy, gx + 1, gy + 1, QuestUi.GRID)
        }
        maxScroll = 0
        when (chapter) {
            RECORDS -> records(graphics)
            DAILY -> daily(graphics)
            COMMUNITY -> community(graphics)
            FLASH -> flash(graphics)
            else -> category(graphics, chapter)
        }
        if (data.view == null && data.problem != null) {
            val key = if (data.problem == "notLinked") "gui.lnbquests.not_linked" else "gui.lnbquests.offline"
            val text = Component.translatable(key)
            graphics.drawString(font, text, x0 + (W - font.width(text)) / 2, y0 + H - 4, QuestUi.GOLD_TEXT, true)
        }
        popup?.let { id -> records.firstOrNull { it.id == id }?.let { recordPopup(graphics, it) } }
        val hover = hits.lastOrNull { it.contains(mouseX.toDouble(), mouseY.toDouble()) && it.tooltip.isNotEmpty() }
        if (hover != null && popup == null) graphics.renderComponentTooltip(font, hover.tooltip, mouseX, mouseY)
    }

    private fun sidebar(graphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        graphics.blitSprite(QuestUi.PANEL_INSET, sideX, sideY, SIDEBAR, H - 32)
        val chapters = chapters()
        // The chapters fill the space above the fixed pages and scroll with the wheel; the pages below don't.
        val top = sideY + 3
        val bottom = chapterBottom
        sideMax = maxOf(0, chapters.size * CHAPTER_ROW - (bottom - top))
        sideScroll = sideScroll.coerceIn(0, sideMax)
        graphics.enableScissor(sideX + 2, top, sideX + SIDEBAR - 2, bottom)
        var y = top - sideScroll
        fun entry(id: String, icon: ItemStack, name: Component, count: String, clipTop: Int, clipBottom: Int, step: Int = ROW) {
            val active = id == chapter
            val visible = y + 18 > clipTop && y < clipBottom
            val hovered = visible && mouseX in sideX + 3 until sideX + SIDEBAR - 3 && mouseY in maxOf(y, clipTop) until minOf(y + 18, clipBottom)
            if (active || hovered) graphics.blitSprite(QuestUi.SECONDARY.get(true, true), sideX + 3, y, SIDEBAR - 6, 18)
            // 12 px icons, so they stay inside the 18 px highlight with its frame.
            graphics.pose().pushPose()
            graphics.pose().translate((sideX + 7).toFloat(), (y + 3).toFloat(), 0f)
            graphics.pose().scale(0.75f, 0.75f, 1f)
            graphics.renderFakeItem(icon, 0, 0)
            graphics.pose().popPose()
            val countWidth = if (count.isEmpty()) 0 else font.width(count) + 4
            graphics.drawString(font, QuestUi.fit(font, name.string, SIDEBAR - 30 - countWidth), sideX + 23, y + 5, if (active) QuestUi.IVORY else QuestUi.DIM, true)
            if (count.isNotEmpty()) {
                graphics.drawString(font, count, sideX + SIDEBAR - 7 - font.width(count), y + 5, if (active) QuestUi.GOLD_TEXT else QuestUi.MUTED, true)
            }
            if (visible) {
                hits += Hit(sideX + 3, maxOf(y, clipTop), SIDEBAR - 6, minOf(y + 18, clipBottom) - maxOf(y, clipTop)) { chapter = id; selected = null; scroll = 0 }
            }
            y += step
        }
        for (c in chapters) {
            val quests = individuals(c.id)
            val total = quests.sumOf { it.tiers.orEmpty().size }
            val done = quests.sumOf { q -> data.view?.completions?.count { it.quest == q.id } ?: 0 }
            entry(c.id, c.icon, c.name, "$done/$total", top, bottom, CHAPTER_ROW)
        }
        graphics.disableScissor()
        if (sideMax > 0) {
            // A thin gold thumb on the right edge shows where the list is.
            val track = bottom - top
            val thumb = maxOf(8, track * track / (track + sideMax))
            val ty = top + (track - thumb) * sideScroll / sideMax
            graphics.fill(sideX + SIDEBAR - 3, top, sideX + SIDEBAR - 2, bottom, QuestUi.GOLD_DK)
            graphics.fill(sideX + SIDEBAR - 3, ty, sideX + SIDEBAR - 2, ty + thumb, QuestUi.GOLD_LT)
        }
        // The fixed pages sit at the foot of the sidebar, leaving some room under the chapters.
        val end = sideY + H - 36
        y = pagesTop
        graphics.fill(sideX + 6, y - 4, sideX + SIDEBAR - 6, y - 3, QuestUi.GOLD_DK)
        val daily = data.daily.lines
        entry(DAILY, ItemStack(Items.SUNFLOWER), Component.translatable("gui.lnbquests.tab.daily"), if (daily.isEmpty()) "" else "${daily.count { it.done }}/${daily.size}", y, end)
        entry(RECORDS, ItemStack(Items.NETHER_STAR), Component.translatable("gui.lnbquests.tab.server"), "${data.view?.ranks?.size ?: 0}/${records.size}", bottom, end)
        entry(COMMUNITY, ItemStack(Items.BELL), Component.translatable("gui.lnbquests.tab.community"), "", bottom, end)
        entry(FLASH, ItemStack(Items.CLOCK), Component.translatable("gui.lnbquests.tab.flash"), "", bottom, end)
    }

    /** Where the fixed pages start, at the foot of the sidebar. */
    private val pagesTop get() = sideY + H - 36 - 4 * ROW - 2

    /** Where the scrolling chapter list ends: just above the gold rule over the fixed pages (six rows). */
    private val chapterBottom get() = pagesTop - 5

    private fun heading(graphics: GuiGraphics, text: Component, progress: String, width: Int) {
        QuestUi.title(graphics, font, text, canvasX + 8, canvasY + 6)
        graphics.drawString(font, progress, canvasX + 12 + font.width(text), canvasY + 6, QuestUi.DIM, true)
        graphics.fill(canvasX + 6, canvasY + 17, canvasX + width - 6, canvasY + 18, QuestUi.GOLD_DK)
    }

    // A category: chains of tiers under their subcategory headings, and the chosen tier in the panel on the right.

    private fun category(graphics: GuiGraphics, category: String) {
        val current = chapters().firstOrNull { it.id == category }
        val quests = individuals(category)
        val groups = bySubcategory(quests, current?.subcategories.orEmpty())
        val total = quests.sumOf { it.tiers.orEmpty().size }
        val done = quests.sumOf { q -> data.view?.completions?.count { it.quest == q.id } ?: 0 }
        val areaW = canvasW - PANEL - 3
        heading(graphics, current?.name ?: Component.literal(category), "$done / $total", areaW)
        if (selected == null || quests.none { it.id == selected?.first }) {
            selected = quests.firstNotNullOfOrNull { q ->
                q.tiers.orEmpty().indices.firstOrNull { tierState(q, it + 1) in setOf(NodeState.READY, NodeState.PROGRESS) }?.let { q.id to it + 1 }
            } ?: quests.firstOrNull()?.let { it.id to 1 }
        }
        val top = canvasY + 22
        val bottom = canvasY + canvasH - 4
        val height = groups.sumOf { (sub, list) -> (if (sub != null) HEADING else 0) + list.size * CHAIN_ROW }
        maxScroll = maxOf(0, height + 20 - (bottom - top))
        scroll = scroll.coerceIn(0, maxScroll)
        graphics.enableScissor(canvasX + 2, top, canvasX + areaW, bottom)
        var cursor = 0
        for ((sub, list) in groups) {
            if (sub != null) {
                val hy = canvasY + 26 + cursor - scroll
                val name = Component.literal(sub.name.get(language))
                graphics.drawString(font, name, canvasX + 10, hy, QuestUi.GOLD_TEXT, true)
                graphics.fill(canvasX + 14 + font.width(name), hy + 4, canvasX + areaW - 8, hy + 5, QuestUi.GOLD_DK)
                cursor += HEADING
            }
            for (quest in list) {
                chain(graphics, quest, canvasY + 52 + cursor - scroll, top, bottom)
                cursor += CHAIN_ROW
            }
        }
        graphics.disableScissor()
        if (quests.isEmpty()) graphics.drawString(font, Component.translatable("gui.lnbquests.empty"), canvasX + 10, canvasY + 26, QuestUi.MUTED, true)
        selected?.let { (id, tier) -> quests.firstOrNull { it.id == id }?.let { tierPanel(graphics, it, tier) } }
    }

    /** One quest's chain of tiers, its nodes on the line [y]. */
    private fun chain(graphics: GuiGraphics, quest: QuestDef, y: Int, top: Int, bottom: Int) {
        val ox = canvasX + 34
        graphics.drawString(font, quest.name.get(language), ox - 12, y - 27, QuestUi.IVORY, true)
        val tiers = quest.tiers.orEmpty()
        for (i in 0 until tiers.size - 1) {
            val from = tierState(quest, i + 1)
            val to = tierState(quest, i + 2)
            val color = when {
                from == NodeState.LOCKED || from == NodeState.PROGRESS -> QuestUi.LOCKED_LINK
                to == NodeState.LOCKED -> QuestUi.GOLD_MD
                else -> QuestUi.GOLD_LT
            }
            QuestUi.link(graphics, ox + i * CHAIN_STEP + QuestUi.RING / 2 + 1, ox + (i + 1) * CHAIN_STEP - QuestUi.RING / 2 - 1, y, color)
        }
        tiers.forEachIndexed { i, tier ->
            val x = ox + i * CHAIN_STEP
            val state = tierState(quest, i + 1)
            QuestUi.node(graphics, x, y, QuestUi.stack(quest.icon), state, i + 1)
            if (state == NodeState.PROGRESS) {
                val counter = data.counters[quest.id] ?: 0
                QuestUi.bar(graphics, x - 10, y + QuestUi.RING / 2 + 4, 20, 2, counter.toDouble() / tier.amount)
            }
            if (selected == quest.id to i + 1) graphics.renderOutline(x - 15, y - 15, 30, 30, QuestUi.GOLD_HI)
            if (y in top..bottom) {
                val counter = minOf(data.counters[quest.id] ?: 0, tier.amount)
                // An advancement quest: its description says more than a 0 / 1 counter.
                val middle = advancementText(quest)?.takeIf { quest.objective.type == "advancement" }
                    ?.let { tipLines(it.second, 150, 3, QuestUi.DIM) }
                    ?: listOf(Component.literal("${QuestUi.number(counter, language)} / ${QuestUi.number(tier.amount, language)}").withColor(QuestUi.DIM))
                val tip = listOf<Component>(Component.translatable("gui.lnbquests.tier_of", quest.name.get(language), i + 1)) + middle + listOf(
                    QuestUi.amount(completion(quest.id, i + 1)?.amount ?: estimate(tier.reward), language, completion(quest.id, i + 1) == null).withColor(QuestUi.GOLD_TEXT),
                )
                hits += Hit(x - 13, y - 13, 26, 26, tip) { selected = quest.id to i + 1 }
            }
        }
    }

    private fun tierPanel(graphics: GuiGraphics, quest: QuestDef, tier: Int) {
        val dx = canvasX + canvasW - PANEL - 3
        val dy = canvasY
        val pad = 12
        val inner = PANEL - 2 * pad
        graphics.blitSprite(QuestUi.PANEL, dx, dy, PANEL, canvasH)
        val tiers = quest.tiers.orEmpty()
        val def = tiers.getOrNull(tier - 1) ?: return
        val state = tierState(quest, tier)
        QuestUi.node(graphics, dx + pad + 14, dy + pad + 16, QuestUi.stack(quest.icon), state, tier, glow = false)
        QuestUi.title(graphics, font, Component.literal(QuestUi.fit(font, quest.name.get(language), inner - 33)), dx + pad + 33, dy + pad + 6)
        val counter = minOf(data.counters[quest.id] ?: 0, def.amount)
        val reached = completion(quest.id, tier) != null
        val advancement = advancementText(quest)?.takeIf { quest.objective.type == "advancement" && tiers.size == 1 }
        if (advancement != null) {
            // An advancement: its status instead of "Tier 1/1", its title and description instead of a 0 / 1 bar.
            val got = reached || counter >= def.amount
            graphics.drawString(font, Component.translatable(if (got) "gui.lnbquests.advancement.done" else "gui.lnbquests.advancement.todo"), dx + pad + 33, dy + pad + 17, if (got) QuestUi.GREEN else QuestUi.DIM, true)
            graphics.fill(dx + pad, dy + 50, dx + PANEL - pad, dy + 51, QuestUi.GOLD_DK)
            val title = font.split(advancement.first, inner).take(2)
            val description = font.split(advancement.second, inner).take(4 - title.size)
            title.forEachIndexed { i, line -> graphics.drawString(font, line, dx + pad, dy + 56 + i * 10, QuestUi.IVORY, true) }
            description.forEachIndexed { i, line -> graphics.drawString(font, line, dx + pad, dy + 56 + (title.size + i) * 10, QuestUi.DIM, true) }
        } else {
            graphics.drawString(font, Component.translatable("gui.lnbquests.tier_short", tier, tiers.size), dx + pad + 33, dy + pad + 17, QuestUi.DIM, true)
            graphics.fill(dx + pad, dy + 50, dx + PANEL - pad, dy + 51, QuestUi.GOLD_DK)
            val lines = font.split(objective(quest, def.amount), inner).take(2)
            lines.forEachIndexed { i, line -> graphics.drawString(font, line, dx + pad, dy + 56 + i * 10, QuestUi.IVORY, true) }
            QuestUi.bar(graphics, dx + pad, dy + 79, inner, 3, if (reached) 1.0 else counter.toDouble() / def.amount)
            val shown = if (reached) def.amount else counter
            graphics.drawString(font, "${QuestUi.number(shown, language)} / ${QuestUi.number(def.amount, language)}", dx + pad, dy + 86, if (reached) QuestUi.GREEN else QuestUi.DIM, true)
        }
        graphics.fill(dx + pad, dy + 99, dx + PANEL - pad, dy + 100, QuestUi.GOLD_DK)
        graphics.drawString(font, Component.translatable("gui.lnbquests.reward"), dx + pad, dy + 105, QuestUi.IVORY, true)
        val done = completion(quest.id, tier)
        graphics.drawString(font, QuestUi.amount(done?.amount ?: estimate(def.reward), language, done == null), dx + pad, dy + 119, QuestUi.GOLD_TEXT, true)
        def.items.orEmpty().take(2).forEachIndexed { i, item ->
            val sx = dx + PANEL - pad - 18 - i * 20
            graphics.blitSprite(QuestUi.SLOT, sx, dy + 113, 18, 18)
            val stack = QuestUi.stack(item.id).copyWithCount(item.count)
            graphics.renderFakeItem(stack, sx + 1, dy + 114)
            graphics.renderItemDecorations(font, stack, sx + 1, dy + 114)
            hits += Hit(sx, dy + 113, 18, 18, listOf(Component.literal("${item.count} × ${item.name}")))
        }
        val bx = dx + pad
        val by = dy + canvasH - 34
        val mouse = Minecraft.getInstance().mouseHandler
        val mx = mouse.xpos() * width / Minecraft.getInstance().window.screenWidth
        val my = mouse.ypos() * height / Minecraft.getInstance().window.screenHeight
        when {
            state == NodeState.READY && done != null -> {
                val hovered = mx >= bx && mx < bx + inner && my >= by && my < by + 20
                QuestUi.button(graphics, font, QuestUi.PRIMARY, bx, by, inner, 20, Component.translatable("gui.lnbquests.claim"), true, hovered)
                hits += Hit(bx, by, inner, 20) { PacketDistributor.sendToServer(ClaimPayload(done.id, quest.id)) }
            }
            quest.objective.type == "deliver" && state == NodeState.PROGRESS -> {
                val hovered = mx >= bx && mx < bx + inner && my >= by && my < by + 20
                QuestUi.button(graphics, font, QuestUi.SECONDARY, bx, by, inner, 20, Component.translatable("gui.lnbquests.deliver"), true, hovered)
                hits += Hit(bx, by, inner, 20) { PacketDistributor.sendToServer(DeliverPayload(quest.id)) }
            }
            state == NodeState.LOCKED -> graphics.drawString(font, Component.translatable("gui.lnbquests.locked_after", tier - 1), bx, by + 6, QuestUi.MUTED, true)
            state == NodeState.CLAIMED -> graphics.drawString(font, Component.translatable("gui.lnbquests.claimed_label"), bx, by + 6, QuestUi.GREEN, true)
        }
    }

    // Records: one tree per Records category (or guessed group), nodes opening a popup with the podium.

    private fun records(graphics: GuiGraphics) {
        heading(graphics, Component.translatable("gui.lnbquests.tab.server"), "${data.view?.ranks?.size ?: 0} / ${records.size}", canvasW)
        val perRow = maxOf(1, (canvasW - 40) / RECORD_STEP)
        val top = canvasY + 22
        val bottom = canvasY + canvasH - 4
        val byGroup = records.groupBy(::recordGroup)
        val order = data.categories.filter { it.family == QuestDef.SERVER }.map { it.id }.ifEmpty { GROUP_ORDER }
        val groups = (order + byGroup.keys.filterNot(order::contains)).mapNotNull { key ->
            byGroup[key]?.let { key to bySubcategory(it, serverCategory(key)?.subcategories.orEmpty()) }
        }
        val contentHeight = groups.sumOf { (_, subs) ->
            18 + subs.sumOf { (sub, list) -> (if (sub != null) SUB_HEADING else 0) + ((list.size + perRow - 1) / perRow) * RECORD_ROW }
        }
        maxScroll = maxOf(0, contentHeight + 30 - (bottom - top))
        scroll = scroll.coerceIn(0, maxScroll)
        var y = canvasY + 46 - scroll
        graphics.enableScissor(canvasX + 2, top, canvasX + canvasW - 2, bottom)
        for ((group, subs) in groups) {
            graphics.drawString(font, recordGroupTitle(group), canvasX + 10, y - 24, QuestUi.IVORY, true)
            for ((sub, list) in subs) {
                if (sub != null) {
                    y += SUB_HEADING
                    graphics.drawString(font, Component.literal(sub.name.get(language)), canvasX + 16, y - 24, QuestUi.GOLD_TEXT, true)
                }
                list.chunked(perRow).forEach { row ->
                    recordRow(graphics, row, y, top, bottom)
                    y += RECORD_ROW
                }
            }
            y += 18
        }
        graphics.disableScissor()
    }

    private fun recordRow(graphics: GuiGraphics, row: List<QuestDef>, y: Int, top: Int, bottom: Int) {
        row.forEachIndexed { i, _ ->
            val x = canvasX + 26 + i * RECORD_STEP
            if (i > 0) {
                val previous = row[i - 1]
                val color = if (data.view?.ranks?.containsKey(previous.id) == true) QuestUi.GOLD_LT else QuestUi.LOCKED_LINK
                QuestUi.link(graphics, x - RECORD_STEP + QuestUi.RING / 2 + 1, x - QuestUi.RING / 2 - 1, y, color)
            }
        }
        row.forEachIndexed { i, quest ->
            val x = canvasX + 26 + i * RECORD_STEP
            val rank = data.view?.ranks?.get(quest.id)
            val podium = data.view?.podiums?.get(quest.id).orEmpty()
            val state = when {
                rank != null -> NodeState.CLAIMED
                podium.size >= (quest.ranks?.size ?: 3) -> NodeState.LOCKED
                else -> NodeState.PROGRESS
            }
            secretNode(graphics, x, y, quest, state)
            if (y in top..bottom) {
                val tip = mutableListOf<Component>(Component.literal(quest.name.get(language)))
                advancementText(quest)?.let { tip += tipLines(it.second, 150, 3, QuestUi.DIM) }
                podium.take(3).forEach { tip += Component.literal("${it.rank}. ${it.username}").withColor(if (it.rank == rank) QuestUi.GOLD_TEXT else QuestUi.DIM) }
                hits += Hit(x - 13, y - 13, 26, 26, tip) { popup = quest.id }
            }
        }
    }

    // Daily: today's quests, the same for everyone, each with the player's progress.

    private fun daily(graphics: GuiGraphics) {
        val lines = data.daily.lines
        heading(graphics, Component.translatable("gui.lnbquests.tab.daily"), "${lines.count { it.done }} / ${lines.size}", canvasW)
        if (lines.isEmpty()) {
            graphics.drawWordWrap(font, Component.translatable("gui.lnbquests.daily.none"), canvasX + 10, canvasY + 26, canvasW - 20, QuestUi.MUTED)
            return
        }
        val top = canvasY + 22
        val bottom = canvasY + canvasH - 4
        maxScroll = maxOf(0, lines.size * DAILY_ROW + 4 - (bottom - top))
        scroll = scroll.coerceIn(0, maxScroll)
        graphics.enableScissor(canvasX + 2, top, canvasX + canvasW - 2, bottom)
        lines.forEachIndexed { i, line ->
            val quest = data.quests.firstOrNull { it.id == line.quest } ?: return@forEachIndexed
            val y = top + 2 + i * DAILY_ROW - scroll
            graphics.blitSprite(QuestUi.PANEL, canvasX + 6, y, canvasW - 12, DAILY_ROW - 4)
            QuestUi.node(graphics, canvasX + 28, y + 22, QuestUi.stack(quest.icon), if (line.done) NodeState.CLAIMED else NodeState.PROGRESS, 1, glow = false)
            val reward: Component = if (line.done) {
                Component.translatable("gui.lnbquests.daily.done").withColor(QuestUi.GREEN)
            } else {
                QuestUi.amount(estimate((quest.rewardPerUnit ?: 0.0) * line.quantity), language, true).withColor(QuestUi.GOLD_TEXT)
            }
            val right = canvasX + canvasW - 16
            graphics.drawString(font, reward, right - font.width(reward), y + 9, QuestUi.GOLD_TEXT, true)
            QuestUi.title(graphics, font, Component.literal(QuestUi.fit(font, quest.name.get(language), right - font.width(reward) - canvasX - 54)), canvasX + 48, y + 9)
            val count = "${QuestUi.number(line.progress, language)} / ${QuestUi.number(line.quantity, language)}"
            graphics.drawString(font, count, right - font.width(count), y + 20, if (line.done) QuestUi.GREEN else QuestUi.DIM, true)
            val goal = font.split(objective(quest, line.quantity), right - font.width(count) - canvasX - 54).firstOrNull() ?: Component.empty().visualOrderText
            graphics.drawString(font, goal, canvasX + 48, y + 20, QuestUi.DIM, true)
            QuestUi.bar(graphics, canvasX + 48, y + 32, right - canvasX - 48, 3, if (line.done) 1.0 else line.progress.toDouble() / line.quantity)
        }
        graphics.disableScissor()
    }

    /** A record's node; a secret one (masked by the server) shows a gold "?" sprite instead of its item. */
    private fun secretNode(graphics: GuiGraphics, x: Int, y: Int, quest: QuestDef, state: NodeState, glow: Boolean = true) {
        if (quest.hidden != true) return QuestUi.node(graphics, x, y, QuestUi.stack(quest.icon), state, 1, glow)
        QuestUi.node(graphics, x, y, ItemStack.EMPTY, state, 1, glow)
        graphics.blitSprite(QuestUi.SECRET, x - 6, y - 6, 12, 12)
    }

    private fun recordPopup(graphics: GuiGraphics, quest: QuestDef) {
        hits.clear()
        graphics.pose().pushPose()
        graphics.pose().translate(0f, 0f, 300f)
        graphics.fill(0, 0, width, height, 0x78000000)
        val dw = 236
        // The advancement's description in full, in its own inset between the title and the podium.
        val description = advancementText(quest)?.second?.let { font.split(it, dw - 32).take(3) }.orEmpty()
        val box = if (description.isEmpty()) 0 else 10 + 10 * description.size
        val shift = if (box == 0) 0 else box + 4
        val dh = 170 + shift
        val dx = (width - dw) / 2
        val dy = (height - dh) / 2
        graphics.blitSprite(QuestUi.PANEL, dx, dy, dw, dh)
        val rank = data.view?.ranks?.get(quest.id)
        secretNode(graphics, dx + 26, dy + 26, quest, if (rank != null) NodeState.CLAIMED else NodeState.PROGRESS, glow = false)
        QuestUi.title(graphics, font, Component.literal(QuestUi.fit(font, quest.name.get(language), dw - 60)), dx + 48, dy + 16)
        val rarity = Component.translatable("gui.lnbquests.rarity.${quest.tier}")
        val subtitle = if (quest.hidden == true) rarity.copy().append(", ").append(Component.translatable("gui.lnbquests.secret")) else rarity
        graphics.drawString(font, subtitle, dx + 48, dy + 28, QuestUi.DIM, true)
        if (box > 0) {
            graphics.blitSprite(QuestUi.PANEL_INSET, dx + 10, dy + 42, dw - 20, box)
            description.forEachIndexed { i, line -> graphics.drawString(font, line, dx + 16, dy + 47 + i * 10, QuestUi.IVORY, true) }
        }
        graphics.pose().pushPose()
        graphics.pose().translate(0f, shift.toFloat(), 0f)
        graphics.blitSprite(QuestUi.PANEL_INSET, dx + 10, dy + 42, dw - 20, 84)
        graphics.drawString(font, Component.translatable("gui.lnbquests.podium"), dx + 16, dy + 47, QuestUi.IVORY, true)
        val podium = data.view?.podiums?.get(quest.id).orEmpty()
        val shares = quest.ranks.orEmpty().ifEmpty { listOf(100.0, 60.0, 20.0) }
        shares.take(3).forEachIndexed { i, share ->
            val yy = dy + 60 + i * 21
            val entry = podium.firstOrNull { it.rank == i + 1 }
            graphics.blitSprite(QuestUi.SECONDARY.get(entry != null, entry?.rank == rank), dx + 16, yy, dw - 32, 18)
            graphics.drawString(font, "${i + 1}", dx + 22, yy + 5, QuestUi.GOLD_TEXT, true)
            if (entry != null) {
                graphics.drawString(font, entry.username, dx + 34, yy + 5, QuestUi.IVORY, true)
                val text = QuestUi.amount(entry.amount, language)
                graphics.drawString(font, text, dx + dw - 22 - font.width(text), yy + 5, QuestUi.GOLD_TEXT, true)
            } else {
                graphics.drawString(font, Component.translatable("gui.lnbquests.free_place"), dx + 34, yy + 5, QuestUi.MUTED, true)
                val text = QuestUi.amount(((quest.reward ?: 0.0) * share / 100 * data.kGlobal).roundToLong(), language, true)
                graphics.drawString(font, text, dx + dw - 22 - font.width(text), yy + 5, QuestUi.MUTED, true)
            }
        }
        val rest = ((quest.reward ?: 0.0) * (quest.restShare ?: 2.0) / 100 * data.kGlobal).roundToLong()
        graphics.drawString(font, Component.translatable("gui.lnbquests.rest", QuestUi.amount(rest, language)), dx + 16, dy + 132, QuestUi.MUTED, true)
        if (rank != null) graphics.drawString(font, Component.translatable("gui.lnbquests.your_rank", rank), dx + 16, dy + 146, QuestUi.GREEN, true)
        graphics.pose().popPose()
        val bx = dx + dw - 100
        val by = dy + dh - 26
        val mouse = Minecraft.getInstance().mouseHandler
        val mx = mouse.xpos() * width / Minecraft.getInstance().window.screenWidth
        val my = mouse.ypos() * height / Minecraft.getInstance().window.screenHeight
        QuestUi.button(graphics, font, QuestUi.SECONDARY, bx, by, 90, 18, Component.translatable("gui.lnbquests.close"), true, mx >= bx && mx < bx + 90 && my >= by && my < by + 18)
        hits += Hit(bx, by, 90, 18) { popup = null }
        hits += Hit(0, 0, width, height) // swallows clicks outside
        graphics.pose().popPose()
    }

    // Community: the goal with its tiers as nodes on the track.

    private fun community(graphics: GuiGraphics) {
        val community = data.community
        if (community == null) {
            graphics.drawWordWrap(font, Component.translatable("gui.lnbquests.community.none"), canvasX + 12, canvasY + 14, canvasW - 24, QuestUi.MUTED)
            return
        }
        val bx = canvasX + 16
        val by = canvasY + 16
        graphics.blitSprite(QuestUi.PANEL, canvasX + 6, canvasY + 6, canvasW - 12, 112)
        graphics.renderFakeItem(QuestUi.stack(objectiveIcon(community.objective.type)), bx, by)
        QuestUi.title(graphics, font, Component.literal(community.title.get(language)), bx + 22, by + 1)
        graphics.drawString(font, Component.translatable("gui.lnbquests.community.goal_${community.objective.type}", QuestUi.number(community.goal, language)), bx + 22, by + 12, QuestUi.DIM, true)
        val progress = maxOf(data.communityProgress, community.progress)
        val percent = String.format(if (language.startsWith("fr")) java.util.Locale.FRANCE else java.util.Locale.UK, "%.1f", progress * 100.0 / community.goal)
        val count = QuestUi.number(progress, language)
        graphics.drawString(font, count, bx, by + 28, QuestUi.IVORY, true)
        graphics.drawString(font, " / ${QuestUi.number(community.goal, language)}, $percent %", bx + font.width(count), by + 28, QuestUi.DIM, true)
        val maxAt = maxOf(100.0, community.tiers.maxOfOrNull { it.at } ?: 100.0)
        val tx = bx + 4
        val tw = canvasW - 60
        val ty = by + 54
        QuestUi.bar(graphics, tx, ty - 2, tw, 5, progress * 100.0 / community.goal / maxAt)
        community.tiers.forEachIndexed { i, tier ->
            val x = minOf(tx + (tw * tier.at / maxAt).toInt(), tx + tw - 18)
            val reached = progress * 100.0 / community.goal >= tier.at
            val state = if (reached) NodeState.CLAIMED else if (i == 0 || progress * 100.0 / community.goal >= community.tiers[i - 1].at) NodeState.PROGRESS else NodeState.LOCKED
            QuestUi.node(graphics, x, ty, QuestUi.stack(listOf("minecraft:copper_ingot", "minecraft:iron_ingot", "minecraft:gold_ingot").getOrElse(i) { "minecraft:diamond" }), state, i + 1)
            val name = Component.literal(tier.name)
            graphics.drawString(font, name, x - font.width(name) / 2, ty + 16, if (state != NodeState.LOCKED) QuestUi.IVORY else QuestUi.MUTED, true)
            val reward = QuestUi.amount((tier.rewardToday * kPlayer).roundToLong(), language)
            graphics.drawString(font, reward, x - font.width(reward) / 2, ty + 26, if (state != NodeState.LOCKED) QuestUi.GOLD_TEXT else QuestUi.MUTED, true)
        }
        val py = canvasY + 122
        graphics.blitSprite(QuestUi.PANEL_INSET, canvasX + 6, py, 130, canvasH - 124)
        graphics.drawString(font, Component.translatable("gui.lnbquests.community.your_part"), canvasX + 12, py + 6, QuestUi.IVORY, true)
        val part = data.view?.community
        val threshold = (community.goal * community.thresholdPercent / 100).toLong()
        graphics.drawString(font, QuestUi.number(part?.contribution ?: 0, language), canvasX + 12, py + 18, QuestUi.GOLD_TEXT, true)
        if (part?.qualified == true) {
            graphics.drawString(font, Component.translatable("gui.lnbquests.community.qualified_short"), canvasX + 12, py + 30, QuestUi.GREEN, true)
        }
        graphics.drawString(font, Component.translatable("gui.lnbquests.community.threshold", QuestUi.number(threshold, language)), canvasX + 12, py + 42, QuestUi.MUTED, true)
        val rx = canvasX + 142
        graphics.blitSprite(QuestUi.PANEL_INSET, rx, py, canvasW - 148, canvasH - 124)
        graphics.drawString(font, Component.translatable("gui.lnbquests.community.contributors", community.contributors), rx + 6, py + 6, QuestUi.IVORY, true)
        graphics.drawWordWrap(font, Component.translatable("gui.lnbquests.community.site", community.topBonusPercent.toInt()), rx + 6, py + 18, canvasW - 160, QuestUi.DIM)
    }

    /** A tag's name, "Coal ores": its `tag.<registry>.<namespace>.<path>` translation, else the quest icon's name. */
    private fun tagName(quest: QuestDef, tag: String): Component {
        val registry = when (quest.objective.type) {
            "kill" -> "entity_type"
            "mine" -> "block"
            else -> "item"
        }
        val path = tag.replace(':', '.').replace('/', '.')
        return listOf("tag.$registry.$path", "tag.item.$path").firstOrNull(I18n::exists)?.let { Component.translatable(it) }
            ?: QuestUi.stack(quest.icon).hoverName
    }

    private fun objectiveIcon(type: String) = when (type) {
        "kill" -> "minecraft:iron_sword"
        "fish" -> "minecraft:fishing_rod"
        "craft" -> "minecraft:crafting_table"
        "travel" -> "minecraft:compass"
        else -> "minecraft:iron_pickaxe"
    }

    // Flash: the race running, then the latest ones.

    private fun flash(graphics: GuiGraphics) {
        val now = System.currentTimeMillis()
        val run = data.flash
        val bx = canvasX + 16
        val by = canvasY + 16
        graphics.blitSprite(QuestUi.PANEL, canvasX + 6, canvasY + 6, canvasW - 12, 84)
        if (run == null) {
            graphics.drawWordWrap(font, Component.translatable("gui.lnbquests.flash.none"), bx, by + 4, canvasW - 44, QuestUi.MUTED)
        } else {
            val quest = data.quests.firstOrNull { it.id == run.quest }
            QuestUi.node(graphics, bx + 12, by + 12, QuestUi.stack(quest?.icon), NodeState.PROGRESS, 1)
            QuestUi.title(graphics, font, Component.literal(quest?.name?.get(language) ?: run.quest), bx + 30, by + 2)
            graphics.drawString(font, objective(quest ?: return, run.quantity.toLong()), bx + 30, by + 13, QuestUi.DIM, true)
            val left = ((run.endsAt - now) / 1000).coerceAtLeast(0)
            val clock = "%d:%02d".format(left / 60, left % 60)
            QuestUi.title(graphics, font, Component.literal(clock), canvasX + canvasW - 26 - font.width(clock), by + 2)
            val bw = canvasW - 138
            listOf(Triple(Component.translatable("gui.lnbquests.flash.you"), run.mine, QuestUi.IVORY), Triple(Component.literal(run.leader ?: "-"), run.leaderValue, QuestUi.GOLD_TEXT))
                .forEachIndexed { i, (who, value, color) ->
                    val yy = by + 34 + i * 13
                    graphics.drawString(font, QuestUi.fit(font, who.string, 44), bx + 4, yy, color, true)
                    QuestUi.bar(graphics, bx + 50, yy + 2, bw, 4, value.toDouble() / run.quantity, if (i == 0) 0xFF96C8A0.toInt() else QuestUi.GOLD_LT)
                    graphics.drawString(font, "${minOf(value, run.quantity.toLong())} / ${run.quantity}", bx + 56 + bw, yy, color, true)
                }
        }
        val hy = canvasY + 94
        graphics.drawString(font, Component.translatable("gui.lnbquests.flash.history"), canvasX + 10, hy, QuestUi.IVORY, true)
        data.flashHistory.take(4).forEachIndexed { i, entry ->
            val yy = hy + 11 + i * 21
            val mine = entry.winner == Minecraft.getInstance().player?.gameProfile?.name
            graphics.blitSprite(QuestUi.SECONDARY.get(true, mine), canvasX + 6, yy, canvasW - 12, 20)
            val quest = data.quests.firstOrNull { it.id == entry.quest }
            graphics.renderFakeItem(QuestUi.stack(quest?.icon), canvasX + 9, yy + 2)
            graphics.drawString(font, QuestUi.fit(font, "${quest?.name?.get(language) ?: entry.quest} : ${entry.quantity}", 130), canvasX + 29, yy + 6, QuestUi.IVORY, true)
            graphics.drawString(font, entry.winner ?: Component.translatable("gui.lnbquests.flash.nobody").string, canvasX + 170, yy + 6, if (entry.winner == null) QuestUi.MUTED else QuestUi.DIM, true)
            if (entry.amount > 0) {
                val text = QuestUi.amount(entry.amount, language)
                graphics.drawString(font, text, canvasX + canvasW - 12 - font.width(text), yy + 6, QuestUi.GOLD_TEXT, true)
            }
        }
    }

    // Input.

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0) {
            hits.lastOrNull { it.contains(mouseX, mouseY) && it.action != null }?.let {
                it.action?.invoke()
                Minecraft.getInstance().soundManager.play(
                    net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1f),
                )
                return true
            }
            if (popup != null) return true
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (popup != null) return true
        if (mouseX >= sideX && mouseX < sideX + SIDEBAR && mouseY >= sideY && mouseY < sideY + H - 32) {
            // Only the chapter list scrolls; the pages below it stay put.
            if (mouseY < chapterBottom) sideScroll = (sideScroll - (scrollY * 12).toInt()).coerceIn(0, sideMax)
        } else {
            scroll = (scroll - (scrollY * 12).toInt()).coerceIn(0, maxScroll)
        }
        return true
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (popup != null && keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            popup = null
            return true
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }
}
