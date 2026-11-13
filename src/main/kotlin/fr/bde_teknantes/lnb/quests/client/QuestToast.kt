package fr.bde_teknantes.lnb.quests.client

import fr.bde_teknantes.lnb.quests.network.QuestToastPayload
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.toasts.Toast
import net.minecraft.client.gui.components.toasts.ToastComponent
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.network.handling.IPayloadContext

/** A quest notification, drawn like an advancement toast: the frame, the quest's item, a gold title and a line. */
class QuestToast(private val title: Component, private val description: Component, private val icon: ItemStack) : Toast {
    override fun render(graphics: GuiGraphics, toasts: ToastComponent, timeSinceLastVisible: Long): Toast.Visibility {
        val font = toasts.minecraft.font
        graphics.blitSprite(BACKGROUND, 0, 0, width(), height())
        graphics.renderFakeItem(icon, 8, 8)
        graphics.drawString(font, title, 30, 7, GOLD, false)
        font.split(description, width() - 36).firstOrNull()?.let { graphics.drawString(font, it, 30, 18, WHITE, false) }
        return if (timeSinceLastVisible >= DURATION_MS * toasts.notificationDisplayTimeMultiplier) Toast.Visibility.HIDE else Toast.Visibility.SHOW
    }

    companion object {
        private val BACKGROUND = ResourceLocation.withDefaultNamespace("toast/advancement")
        private const val DURATION_MS = 6000.0
        private const val GOLD = 0xFFF5C451.toInt()
        private const val WHITE = 0xFFFFFFFF.toInt()

        fun handle(payload: QuestToastPayload, context: IPayloadContext) {
            context.enqueueWork {
                val minecraft = Minecraft.getInstance()
                minecraft.toasts.addToast(QuestToast(payload.title, payload.description, payload.icon))
                minecraft.soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1f, 0.6f))
            }
        }
    }
}
