package fr.bde_teknantes.lnb.quests.client

import net.minecraft.client.Minecraft
import net.minecraft.network.chat.contents.TranslatableContents
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent

/**
 * The flash race's boss bar in the quest style: a felt track in a thin frame, a gold fill with a light top edge and
 * a dark bottom one, a notch every tenth, and a gold clasp on each end. Other boss bars stay vanilla.
 */
object FlashBossBar {
    private const val WIDTH = 182

    fun onBossBar(event: CustomizeGuiOverlayEvent.BossEventProgress) {
        val name = event.bossEvent.name
        if ((name.contents as? TranslatableContents)?.key != "bossbar.lnbquests.flash") return
        event.isCanceled = true
        val graphics = event.guiGraphics
        val font = Minecraft.getInstance().font
        val x = event.x
        val y = event.y
        graphics.drawString(font, name, x + (WIDTH - font.width(name)) / 2, y - 9, QuestUi.IVORY, true)
        graphics.blitSprite(QuestUi.TEXT_FIELD, x, y, WIDTH, 9)
        val fill = ((WIDTH - 4) * event.bossEvent.progress).toInt()
        if (fill > 0) {
            graphics.fill(x + 2, y + 2, x + 2 + fill, y + 7, QuestUi.GOLD_MD)
            graphics.fill(x + 2, y + 2, x + 2 + fill, y + 3, QuestUi.GOLD_HI)
            graphics.fill(x + 2, y + 6, x + 2 + fill, y + 7, QuestUi.GOLD_DK)
        }
        for (t in 1 until 10) {
            val nx = x + 2 + (WIDTH - 4) * t / 10
            graphics.fill(nx, y + 3, nx + 1, y + 6, QuestUi.OUTLINE)
        }
        graphics.blitSprite(QuestUi.FLASH_CLASP, x - 3, y - 2, 6, 13)
        graphics.pose().pushPose()
        graphics.pose().translate((x + WIDTH + 3).toFloat(), 0f, 0f)
        graphics.pose().scale(-1f, 1f, 1f) // mirrored clasp on the right
        graphics.blitSprite(QuestUi.FLASH_CLASP, 0, y - 2, 6, 13)
        graphics.pose().popPose()
        event.increment = 22
    }
}
