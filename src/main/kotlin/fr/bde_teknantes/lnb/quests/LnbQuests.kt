package fr.bde_teknantes.lnb.quests

import com.mojang.logging.LogUtils
import fr.bde_teknantes.lnb.quests.catalog.Catalog
import fr.bde_teknantes.lnb.quests.command.QuestCommands
import fr.bde_teknantes.lnb.quests.community.CommunityQuest
import fr.bde_teknantes.lnb.quests.flash.FlashRaces
import fr.bde_teknantes.lnb.quests.journal.QuestJournal
import fr.bde_teknantes.lnb.quests.network.QuestNetwork
import fr.bde_teknantes.lnb.quests.network.QuestScreens
import fr.bde_teknantes.lnb.quests.progress.Counting
import fr.bde_teknantes.lnb.quests.progress.PlacedBlocks
import fr.bde_teknantes.lnb.quests.progress.Progress
import fr.bde_teknantes.lnb.quests.progress.Seasons
import fr.bde_teknantes.lnb.quests.progress.ServerQuests
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import net.neoforged.fml.config.ModConfig
import net.neoforged.fml.loading.FMLEnvironment
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.server.ServerStartedEvent
import net.neoforged.neoforge.event.server.ServerStoppingEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import org.slf4j.Logger

/**
 * Common entrypoint. Only the logical server talks to LNB's site; the client shows the quest screen.
 *
 * Quests count on the server and are paid by the site (`docs/quests.md` on the site): personal tiers, server
 * advancements by rank, community goals and flash races.
 */
@Mod(LnbQuests.ID)
class LnbQuests(modBus: IEventBus, container: ModContainer) {
    init {
        container.registerConfig(ModConfig.Type.COMMON, QuestsConfig.SPEC, "$ID-server.toml")
        PlacedBlocks.register(modBus)
        modBus.addListener(QuestNetwork::register)

        Progress.register()
        ServerQuests.register()
        FlashRaces.register()
        CommunityQuest.register()
        QuestScreens.register()

        val bus = NeoForge.EVENT_BUS
        bus.addListener(QuestJournal::onServerStarted)
        bus.addListener(QuestJournal::onServerStopped)
        bus.addListener(QuestJournal::onServerTick)
        bus.addListener(::onServerStarted)
        bus.addListener(::onServerStopping)
        bus.addListener(::onServerTick)
        bus.addListener(::onLoggedIn)
        bus.addListener(::onLoggedOut)
        bus.addListener(QuestCommands::register)

        bus.addListener(Counting::onPlace)
        bus.addListener(Counting::onBreak)
        bus.addListener(Counting::onSpawn)
        bus.addListener(Counting::onDeath)
        bus.addListener(Counting::onFished)
        bus.addListener(Counting::onCrafted)
        bus.addListener(Counting::onSmelted)
        bus.addListener(Counting::onTeleport)
        bus.addListener(Counting::onPlayerTick)
        bus.addListener(ServerQuests::onAdvancement)

        if (FMLEnvironment.dist.isClient) fr.bde_teknantes.lnb.quests.client.LnbQuestsClient.init()
    }

    private var ticks = 0
    private var syncTicks = 0

    private fun onServerStarted(event: ServerStartedEvent) {
        ticks = 0
        syncTicks = 0
        Catalog.start(event.server) { config -> applyConfig(event.server, config) }
    }

    private fun onServerStopping(event: ServerStoppingEvent) {
        FlashRaces.onStopping(event.server)
        CommunityQuest.flush(event.server)
        Progress.pushCounters(event.server)
    }

    private fun onServerTick(event: ServerTickEvent.Post) {
        if (++ticks % 20 != 0) return
        FlashRaces.onSecond(event.server)
        if (++syncTicks >= Catalog.pollSeconds()) {
            syncTicks = 0
            sync(event.server)
        }
    }

    private fun onLoggedIn(event: PlayerEvent.PlayerLoggedInEvent) {
        val player = event.entity as? ServerPlayer ?: return
        Seasons.onLogin(player)
        FlashRaces.onLogin(player)
        CommunityQuest.onLogin(player)
        QuestScreens.sendBadge(player)
    }

    private fun onLoggedOut(event: PlayerEvent.PlayerLoggedOutEvent) {
        val player = event.entity as? ServerPlayer ?: return
        FlashRaces.onLogout(player)
        Counting.onLoggedOut(player)
        QuestScreens.closed(player.uuid)
    }

    companion object {
        const val ID = "lnbquests"
        val LOGGER: Logger = LogUtils.getLogger()

        private fun applyConfig(server: MinecraftServer, config: fr.bde_teknantes.lnb.quests.api.QuestConfig) {
            Seasons.onConfig(server, config.season.number)
            CommunityQuest.onConfig(config.community)
        }

        /** Sends what waits (counters, community contributions) and reads the site's settings again. */
        fun sync(server: MinecraftServer) {
            Progress.pushCounters(server)
            CommunityQuest.flush(server)
            Catalog.refresh(server) { applyConfig(server, it) }
        }
    }
}
