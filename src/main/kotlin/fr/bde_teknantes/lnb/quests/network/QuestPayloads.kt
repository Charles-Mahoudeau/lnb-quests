package fr.bde_teknantes.lnb.quests.network

import fr.bde_teknantes.lnb.quests.LnbQuests
import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation

private fun <T : CustomPacketPayload> type(name: String) = CustomPacketPayload.Type<T>(ResourceLocation.fromNamespaceAndPath(LnbQuests.ID, name))

/**
 * Client asks for the quest screen. A single instance: [StreamCodec.unit] only encodes the instance it was given.
 */
object OpenQuestsPayload : CustomPacketPayload {
    val TYPE = type<OpenQuestsPayload>("open")
    val STREAM_CODEC: StreamCodec<ByteBuf, OpenQuestsPayload> = StreamCodec.unit(this)

    override fun type() = TYPE
}

/** Client claims a tier. */
data class ClaimPayload(val completionId: String) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE = type<ClaimPayload>("claim")
        val STREAM_CODEC: StreamCodec<ByteBuf, ClaimPayload> = ByteBufCodecs.STRING_UTF8.map(::ClaimPayload, ClaimPayload::completionId)
    }
}

/** Client hands items over for a delivery quest. */
data class DeliverPayload(val questId: String) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE = type<DeliverPayload>("deliver")
        val STREAM_CODEC: StreamCodec<ByteBuf, DeliverPayload> = ByteBufCodecs.STRING_UTF8.map(::DeliverPayload, DeliverPayload::questId)
    }
}

/**
 * The quest screen's data, as JSON ([ScreenData]). [open] opens the screen; otherwise it only refreshes one that is
 * already open.
 */
data class QuestScreenPayload(val json: String, val open: Boolean) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        val TYPE = type<QuestScreenPayload>("screen")
        val STREAM_CODEC: StreamCodec<ByteBuf, QuestScreenPayload> = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(1 shl 20), QuestScreenPayload::json,
            ByteBufCodecs.BOOL, QuestScreenPayload::open,
            ::QuestScreenPayload,
        )
    }
}

/** Client closed the quest screen: no more refreshes. A single instance, like [OpenQuestsPayload]. */
object ClosedQuestsPayload : CustomPacketPayload {
    val TYPE = type<ClosedQuestsPayload>("closed")
    val STREAM_CODEC: StreamCodec<ByteBuf, ClosedQuestsPayload> = StreamCodec.unit(this)

    override fun type() = TYPE
}
