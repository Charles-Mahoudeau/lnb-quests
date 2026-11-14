package fr.bde_teknantes.lnb.quests.network

import fr.bde_teknantes.lnb.quests.LnbQuests
import fr.bde_teknantes.lnb.quests.Texts
import fr.bde_teknantes.lnb.quests.api.ClaimBody
import fr.bde_teknantes.lnb.quests.api.ClaimResult
import fr.bde_teknantes.lnb.quests.api.Localized
import fr.bde_teknantes.lnb.quests.api.PlayerView
import fr.bde_teknantes.lnb.quests.api.Problems
import fr.bde_teknantes.lnb.quests.api.QuestDef
import fr.bde_teknantes.lnb.quests.api.QuestsApi
import fr.bde_teknantes.lnb.quests.catalog.Catalog
import fr.bde_teknantes.lnb.quests.community.CommunityQuest
import fr.bde_teknantes.lnb.quests.flash.FlashRaces
import fr.bde_teknantes.lnb.quests.journal.JournalEntry
import fr.bde_teknantes.lnb.quests.journal.Outcome
import fr.bde_teknantes.lnb.quests.journal.QuestJournal
import fr.bde_teknantes.lnb.quests.progress.Deliveries
import fr.bde_teknantes.lnb.quests.progress.QuestState
import fr.bde_teknantes.lnb.warden.common.api.ApiClient
import fr.bde_teknantes.lnb.warden.common.api.ApiResult
import fr.bde_teknantes.lnb.warden.server.ServerConfig
import java.util.UUID
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.handling.IPayloadContext

/**
 * The quest screen, server side: builds what a player sees (the catalogue, their counters, the site's view of their
 * tiers and ranks, the flash race and the community quest) and handles claims and deliveries from it.
 */
object QuestScreens {
    private const val CLAIM = "claim"

    /** Players who have the screen open, so a tier or a claim refreshes it. */
    private val viewers = HashSet<UUID>()

    fun register() {
        QuestJournal.register(CLAIM, ::finishClaim)
    }

    fun open(player: ServerPlayer) = send(player, open = true)

    fun refresh(server: MinecraftServer, uuid: UUID) {
        if (uuid !in viewers) return
        server.playerList.getPlayer(uuid)?.let { send(it, open = false) }
    }

    fun closed(uuid: UUID) = viewers.remove(uuid)

    private val SECRET_NAME = Localized("???", "???")

    /**
     * A secret server quest stays "???" until someone takes first place: the site's flag, or the game hiding the
     * advancement. Masked here, so the client never gets the name. Without the site's answer, it stays masked.
     */
    private fun secret(server: MinecraftServer, quest: QuestDef, view: PlayerView?): Boolean {
        if (!quest.isServer) return false
        val hidden = quest.hidden == true || quest.advancement?.let(ResourceLocation::tryParse)?.let(server.advancements::get)
            ?.value()?.display()?.map { it.isHidden }?.orElse(false) == true
        return hidden && view?.podiums?.get(quest.id).isNullOrEmpty() && view?.ranks?.containsKey(quest.id) != true
    }

    private fun send(player: ServerPlayer, open: Boolean) {
        val server = player.server
        QuestsApi.player(player.uuid).thenAccept { result ->
            server.execute {
                if (player.hasDisconnected()) return@execute
                val state = QuestState.get(server)
                val run = FlashRaces.current
                val flash = run?.let {
                    val leader = it.leader()
                    FlashView(it.quest.id, it.quantity, it.endsAt.toEpochMilli(), leader?.let { l -> it.names[l.first] }, leader?.second ?: 0, it.progress[player.uuid] ?: 0)
                }
                val data = ScreenData(
                    season = state.season,
                    quests = Catalog.quests.map { quest ->
                        val shown = quest.copy(items = quest.items?.map { it.copy(snbt = "") }, tiers = quest.tiers?.map { t -> t.copy(items = t.items?.map { it.copy(snbt = "") }) })
                        if (secret(server, quest, (result as? ApiResult.Ok)?.body)) shown.copy(name = SECRET_NAME, description = null, icon = "", hidden = true) else shown
                    },
                    counters = state.player(player.uuid).counters.toMap(),
                    view = (result as? ApiResult.Ok)?.body,
                    problem = (result as? ApiResult.Http)?.problem ?: if (result is ApiResult.Network) "offline" else null,
                    community = CommunityQuest.running,
                    communityProgress = CommunityQuest.progress,
                    flash = flash,
                    kGlobal = Catalog.config.index.kGlobal,
                    siteUrl = if (ServerConfig.isApiConfigured) ServerConfig.API_URL.get().trimEnd('/') else "",
                    flashHistory = FlashRaces.history.toList(),
                    nextFlashAt = FlashRaces.nextDrawAt(),
                )
                viewers.add(player.uuid)
                PacketDistributor.sendToPlayer(player, QuestScreenPayload(ApiClient.GSON.toJson(data), open))
            }
        }
    }

    /** Tells the client how many rewards wait, for the badge on the inventory button. */
    fun sendBadge(player: ServerPlayer) {
        val server = player.server
        QuestsApi.player(player.uuid).thenAccept { result ->
            val count = (result as? ApiResult.Ok)?.body?.completions?.count { it.claimedAt == null } ?: return@thenAccept
            server.execute { if (!player.hasDisconnected()) PacketDistributor.sendToPlayer(player, QuestBadgePayload(count)) }
        }
    }

    fun handleOpen(payload: OpenQuestsPayload, context: IPayloadContext) {
        (context.player() as? ServerPlayer)?.let(::open)
    }

    fun handleDeliver(payload: DeliverPayload, context: IPayloadContext) {
        val player = context.player() as? ServerPlayer ?: return
        Deliveries.deliver(player, payload.questId)
        send(player, open = false)
    }

    private val claiming = HashSet<String>()

    fun handleClaim(payload: ClaimPayload, context: IPayloadContext) {
        val player = context.player() as? ServerPlayer ?: return
        if (!claiming.add(payload.completionId)) return
        QuestJournal.submit("/completions/${payload.completionId}/claim", CLAIM, mapOf("player" to player.uuid.toString(), "completion" to payload.completionId, "quest" to payload.questId)) { key ->
            ClaimBody(key, player.uuid.toString())
        }
    }

    private fun finishClaim(server: MinecraftServer, entry: JournalEntry, outcome: Outcome) {
        val uuid = UUID.fromString(entry.data["player"] ?: return)
        entry.data["completion"]?.let(claiming::remove)
        val player = server.playerList.getPlayer(uuid)
        when (outcome) {
            is Outcome.Delayed -> player?.sendSystemMessage(Component.translatable("chat.lnbquests.claim.delayed").withStyle(ChatFormatting.GRAY))
            is Outcome.Done -> {
                val result = outcome.decode<ClaimResult>()
                if (player != null) {
                    player.sendSystemMessage(Component.translatable("chat.lnbquests.claim.done", Texts.amount(result.amount, player)).withStyle(ChatFormatting.GOLD))
                    Toasts.send(
                        player,
                        Component.translatable("toast.lnbquests.claim"),
                        Component.translatable("toast.lnbquests.claim.description", Texts.number(result.amount, player)),
                        Toasts.icon(Catalog.quest(entry.data["quest"] ?: "")),
                    )
                    if (result.mailboxItems > 0) {
                        player.sendSystemMessage(Component.translatable("chat.lnbquests.mailbox", result.mailboxItems).withStyle(ChatFormatting.GRAY))
                    }
                }
                refresh(server, uuid)
                player?.let(::sendBadge)
            }
            is Outcome.Refused -> {
                val problem = (outcome.result as? ApiResult.Http)?.problem
                val key = when (problem) {
                    Problems.ALREADY_CLAIMED -> "chat.lnbquests.claim.already"
                    Problems.FROZEN -> "chat.lnbquests.claim.frozen"
                    else -> "chat.lnbquests.claim.failed"
                }
                if (problem != Problems.ALREADY_CLAIMED) LnbQuests.LOGGER.warn("The site refused a claim for {}: {}", uuid, outcome.result.describe())
                player?.sendSystemMessage(Component.translatable(key).withStyle(ChatFormatting.RED))
                refresh(server, uuid)
            }
        }
    }
}
