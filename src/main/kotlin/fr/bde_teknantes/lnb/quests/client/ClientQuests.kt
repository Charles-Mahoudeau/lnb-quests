package fr.bde_teknantes.lnb.quests.client

import fr.bde_teknantes.lnb.quests.network.QuestBadgePayload
import fr.bde_teknantes.lnb.quests.network.QuestScreenPayload
import fr.bde_teknantes.lnb.quests.network.ScreenData
import fr.bde_teknantes.lnb.warden.common.api.ApiClient
import net.minecraft.client.Minecraft
import net.neoforged.neoforge.network.handling.IPayloadContext

/** Client side of the quest screen's data, and the count behind the inventory button's badge. */
object ClientQuests {
    @Volatile
    var unclaimed = 0
        private set

    fun handleScreen(payload: QuestScreenPayload, context: IPayloadContext) {
        context.enqueueWork {
            val data = runCatching { ApiClient.GSON.fromJson(payload.json, ScreenData::class.java) }.getOrNull() ?: return@enqueueWork
            unclaimed = data.view?.completions?.count { it.claimedAt == null } ?: unclaimed
            val minecraft = Minecraft.getInstance()
            val open = minecraft.screen as? QuestBookScreen
            when {
                open != null -> open.update(data)
                payload.open -> minecraft.setScreen(QuestBookScreen(data))
            }
        }
    }

    fun handleBadge(payload: QuestBadgePayload, context: IPayloadContext) {
        context.enqueueWork { unclaimed = payload.unclaimed }
    }
}
