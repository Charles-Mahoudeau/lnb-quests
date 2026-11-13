package fr.bde_teknantes.lnb.quests.client

import fr.bde_teknantes.lnb.quests.network.OpenQuestsPayload
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.components.WidgetSprites
import net.minecraft.resources.ResourceLocation
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.neoforged.neoforge.client.event.ScreenEvent
import net.neoforged.neoforge.network.PacketDistributor

/**
 * A book button on the left of the survival inventory that opens the quest screen. Follows the inventory when the
 * recipe book moves it.
 */
object InventoryButton {
    private const val SIZE = 20
    private var button: Button? = null
    private val ICON = ItemStack(Items.WRITABLE_BOOK)

    fun onInit(event: ScreenEvent.Init.Post) {
        val screen = event.screen as? InventoryScreen ?: return
        val created = BookButton()
        created.setTooltip(Tooltip.create(Component.translatable("gui.lnbquests.open")))
        place(screen, created)
        event.addListener(created)
        button = created
    }

    fun onRenderPre(event: ScreenEvent.Render.Pre) {
        val screen = event.screen as? InventoryScreen ?: return
        button?.let { place(screen, it) }
    }

    /**
     * Vanilla's button with the book drawn on it. Uses its own sprite set and narration rather than Button's
     * protected statics, which a mod class can't reach at runtime.
     */
    private class BookButton : Button(
        0, 0, SIZE, SIZE, Component.translatable("gui.lnbquests.open"),
        { PacketDistributor.sendToServer(OpenQuestsPayload) },
        { narration -> narration.get() },
    ) {
        override fun renderWidget(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
            graphics.blitSprite(BUTTON.get(active, isHoveredOrFocused), x, y, width, height)
            graphics.renderItem(ICON, x + 2, y + 2)
        }
    }

    private val BUTTON = WidgetSprites(
        ResourceLocation.withDefaultNamespace("widget/button"),
        ResourceLocation.withDefaultNamespace("widget/button_disabled"),
        ResourceLocation.withDefaultNamespace("widget/button_highlighted"),
    )

    private fun place(screen: InventoryScreen, button: Button) {
        button.x = screen.guiLeft - SIZE - 2
        button.y = screen.guiTop + 4
    }
}
