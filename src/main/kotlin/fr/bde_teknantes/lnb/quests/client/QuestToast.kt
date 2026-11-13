package fr.bde_teknantes.lnb.quests.client

import fr.bde_teknantes.lnb.quests.client.QuestUi.NodeState
import fr.bde_teknantes.lnb.quests.network.QuestToastPayload
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.toasts.Toast
import net.minecraft.client.gui.components.toasts.ToastComponent
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.network.handling.IPayloadContext

/** A quest notification in the casino style: a felt panel, the quest's round node and its level, a gold title, a line. */
class QuestToast(
    private val title: Component,
    private val description: Component,
    private val icon: ItemStack,
    private val level: Int,
) : Toast {
    override fun width() = 210

    override fun height() = 40

    override fun render(graphics: GuiGraphics, toasts: ToastComponent, timeSinceLastVisible: Long): Toast.Visibility {
        val font = toasts.minecraft.font
        graphics.blitSprite(QuestUi.PANEL, 0, 0, width(), height())
        QuestUi.node(graphics, 22, 20, icon, NodeState.CLAIMED, level, glow = false)
        graphics.drawString(font, QuestUi.fit(font, title.string, width() - 52), 42, 10, QuestUi.GOLD_TEXT, true)
        font.split(description, width() - 52).firstOrNull()?.let { graphics.drawString(font, it, 42, 22, QuestUi.IVORY, true) }
        return if (timeSinceLastVisible >= DURATION_MS * toasts.notificationDisplayTimeMultiplier) Toast.Visibility.HIDE else Toast.Visibility.SHOW
    }

    companion object {
        private const val DURATION_MS = 6000.0

        fun handle(payload: QuestToastPayload, context: IPayloadContext) {
            context.enqueueWork {
                val minecraft = Minecraft.getInstance()
                minecraft.toasts.addToast(QuestToast(payload.title, payload.description, payload.icon, payload.level))
                minecraft.soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1f, 0.6f))
            }
        }
    }
}
