package fr.bde_teknantes.lnb.quests.client

import net.neoforged.neoforge.common.NeoForge

/** Client-only listeners. Loaded only on the physical client. */
object LnbQuestsClient {
    fun init() {
        NeoForge.EVENT_BUS.addListener(InventoryButton::onInit)
        NeoForge.EVENT_BUS.addListener(InventoryButton::onRenderPre)
        NeoForge.EVENT_BUS.addListener(FlashBossBar::onBossBar)
    }
}
