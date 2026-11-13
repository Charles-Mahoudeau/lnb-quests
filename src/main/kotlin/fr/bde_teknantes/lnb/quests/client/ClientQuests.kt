package fr.bde_teknantes.lnb.quests.client

import fr.bde_teknantes.lnb.quests.network.QuestScreenPayload
import fr.bde_teknantes.lnb.quests.network.ScreenData
import fr.bde_teknantes.lnb.warden.common.api.ApiClient
import net.minecraft.client.Minecraft
import net.neoforged.neoforge.network.handling.IPayloadContext

/** Client side of the quest screen's data. */
object ClientQuests {
    fun handleScreen(payload: QuestScreenPayload, context: IPayloadContext) {
        context.enqueueWork {
            val data = runCatching { ApiClient.GSON.fromJson(payload.json, ScreenData::class.java) }.getOrNull() ?: return@enqueueWork
            val minecraft = Minecraft.getInstance()
            val open = minecraft.screen as? QuestsScreen
            when {
                open != null -> open.update(data)
                payload.open -> minecraft.setScreen(QuestsScreen(data))
            }
        }
    }
}
