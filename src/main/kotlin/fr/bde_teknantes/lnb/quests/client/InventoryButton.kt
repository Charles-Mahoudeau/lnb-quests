package fr.bde_teknantes.lnb.quests.client

import fr.bde_teknantes.lnb.quests.network.OpenQuestsPayload
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractButton
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.neoforged.neoforge.client.event.ScreenEvent
import net.neoforged.neoforge.network.PacketDistributor

/**
 * The quest book's button on the survival inventory: LNB Economy's balance plaque turned on its side, coming 5 px over
 * the inventory's left edge and held by two gold brackets, with a red badge while a reward waits for a claim. Follows
 * the inventory when the recipe book moves it.
 */
object InventoryButton {
    private const val W = 27
    private const val H = 28
    private const val OVER = 5
    private var button: Plaque? = null

    fun onInit(event: ScreenEvent.Init.Post) {
        val screen = event.screen as? InventoryScreen ?: return
        val created = Plaque()
        created.setTooltip(Tooltip.create(Component.translatable("gui.lnbquests.open")))
        place(screen, created)
        event.addListener(created)
        button = created
    }

    fun onRenderPre(event: ScreenEvent.Render.Pre) {
        val screen = event.screen as? InventoryScreen ?: return
        button?.let {
            place(screen, it)
            val tip = if (ClientQuests.unclaimed > 0) {
                Component.translatable("gui.lnbquests.open").append("\n").append(
                    Component.translatable("gui.lnbquests.open.unclaimed", ClientQuests.unclaimed).withColor(QuestUi.GOLD_TEXT),
                )
            } else {
                Component.translatable("gui.lnbquests.open")
            }
            it.setTooltip(Tooltip.create(tip))
        }
    }

    private fun place(screen: InventoryScreen, plaque: Plaque) {
        plaque.inventoryLeft = screen.guiLeft
        plaque.x = screen.guiLeft - W + OVER
        plaque.y = screen.guiTop + 8
    }

    private class Plaque : AbstractButton(0, 0, W, H, Component.translatable("gui.lnbquests.open")) {
        var inventoryLeft = 0
        private val icon = ItemStack(Items.WRITABLE_BOOK)

        override fun onPress() = PacketDistributor.sendToServer(OpenQuestsPayload)

        override fun renderWidget(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
            graphics.blitSprite(QuestUi.PLAQUE_SIDE, x, y, W, H)
            graphics.blitSprite(QuestUi.SECONDARY.get(true, isHoveredOrFocused), x + 4, y + 4, 20, 20)
            graphics.renderFakeItem(icon, x + 6, y + 6)
            graphics.blitSprite(QuestUi.BRACKET_SIDE_TOP, inventoryLeft - 8, y - 6, 13, 7)
            graphics.blitSprite(QuestUi.BRACKET_SIDE_BOTTOM, inventoryLeft - 8, y + H - 1, 13, 7)
            if (ClientQuests.unclaimed > 0) {
                graphics.pose().pushPose()
                graphics.pose().translate(0f, 0f, 200f)
                graphics.blitSprite(QuestUi.BADGE_READY, x - 2, y - 3, 8, 8)
                graphics.pose().popPose()
            }
        }

        override fun updateWidgetNarration(output: NarrationElementOutput) = defaultButtonNarrationText(output)
    }
}
