package fr.bde_teknantes.lnb.quests.network

import fr.bde_teknantes.lnb.quests.client.ClientQuests
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent

/**
 * The mod is required on both sides, so every payload is required too. Bump [PROTOCOL_VERSION] whenever a payload
 * changes, so mismatched client and server versions refuse to connect.
 */
object QuestNetwork {
    private const val PROTOCOL_VERSION = "1"

    fun register(event: RegisterPayloadHandlersEvent) {
        val registrar = event.registrar(PROTOCOL_VERSION)
        registrar.playToServer(OpenQuestsPayload.TYPE, OpenQuestsPayload.STREAM_CODEC, QuestScreens::handleOpen)
        registrar.playToServer(ClaimPayload.TYPE, ClaimPayload.STREAM_CODEC, QuestScreens::handleClaim)
        registrar.playToServer(DeliverPayload.TYPE, DeliverPayload.STREAM_CODEC, QuestScreens::handleDeliver)
        registrar.playToServer(ClosedQuestsPayload.TYPE, ClosedQuestsPayload.STREAM_CODEC) { _, context -> QuestScreens.closed(context.player().uuid) }
        // Wrapped in a lambda so the client-only class only loads when a packet arrives.
        registrar.playToClient(QuestScreenPayload.TYPE, QuestScreenPayload.STREAM_CODEC) { payload, context ->
            ClientQuests.handleScreen(payload, context)
        }
    }
}
