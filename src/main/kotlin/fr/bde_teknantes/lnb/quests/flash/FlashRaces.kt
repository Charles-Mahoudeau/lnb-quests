package fr.bde_teknantes.lnb.quests.flash

import fr.bde_teknantes.lnb.quests.LnbQuests
import fr.bde_teknantes.lnb.quests.QuestsConfig
import fr.bde_teknantes.lnb.quests.Texts
import fr.bde_teknantes.lnb.quests.api.FlashBody
import fr.bde_teknantes.lnb.quests.api.FlashResult
import fr.bde_teknantes.lnb.quests.api.Hours
import fr.bde_teknantes.lnb.quests.api.QuestDef
import fr.bde_teknantes.lnb.quests.catalog.Catalog
import fr.bde_teknantes.lnb.quests.journal.JournalEntry
import fr.bde_teknantes.lnb.quests.journal.Outcome
import fr.bde_teknantes.lnb.quests.journal.QuestJournal
import fr.bde_teknantes.lnb.quests.progress.Progress
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerBossEvent
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.BossEvent
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import kotlin.random.Random

/**
 * Flash races: every `intervalMinutes`, with enough players online and inside the active hours, a quest of the flash
 * family is drawn (by weight) with a goal in its range. The first to reach it wins; after `durationMinutes` it expires.
 * A boss bar shows the goal, the leader and the time left. The site pays the winner.
 */
object FlashRaces {
    private const val PURPOSE = "flash"
    private val PARIS: ZoneId = ZoneId.of("Europe/Paris")

    class Run(
        val quest: QuestDef,
        val quantity: Int,
        val startedAt: Instant,
        val endsAt: Instant,
        val bar: ServerBossEvent,
    ) {
        val progress = HashMap<UUID, Long>()
        val names = HashMap<UUID, String>()

        fun leader(): Pair<UUID, Long>? = progress.maxByOrNull { it.value }?.toPair()
    }

    var current: Run? = null
        private set
    private var nextDraw: Instant? = null
    private var ticks = 0

    fun register() {
        QuestJournal.register(PURPOSE, ::finish)
    }

    private fun intervalMinutes() = if (Catalog.fromSite) Catalog.config.flash.intervalMinutes else QuestsConfig.FLASH_INTERVAL_MINUTES.get()
    private fun minPlayers() = if (Catalog.fromSite) Catalog.config.flash.minPlayers else QuestsConfig.FLASH_MIN_PLAYERS.get()
    private fun durationMinutes() = if (Catalog.fromSite) Catalog.config.flash.durationMinutes else QuestsConfig.FLASH_DURATION_MINUTES.get()

    fun within(hours: Hours?, now: Instant = Instant.now()): Boolean {
        if (hours == null || hours.from == hours.to) return true
        val time = LocalTime.ofInstant(now, PARIS).toString().take(5)
        return if (hours.from < hours.to) time >= hours.from && time < hours.to else time >= hours.from || time < hours.to
    }

    /** Once a second. */
    fun onSecond(server: MinecraftServer) {
        val run = current
        val now = Instant.now()
        if (run != null) {
            if (!now.isBefore(run.endsAt)) end(server, run, null) else updateBar(run, now)
            return
        }
        if (!QuestsConfig.FLASH_ENABLED.get()) return
        val due = nextDraw ?: now.plusSeconds(intervalMinutes() * 60L).also { nextDraw = it }
        if (now.isBefore(due)) return
        nextDraw = now.plusSeconds(intervalMinutes() * 60L)
        val online = server.playerList.players.count(Progress::counts)
        if (online < minPlayers() || !within(Catalog.config.flash.hours, now)) return
        draw(online)?.let { start(server, it, null) }
    }

    private fun draw(online: Int): QuestDef? {
        val candidates = Catalog.quests.filter { it.isFlash && (it.minPlayers ?: 1) <= online && within(it.hours) }
        val total = candidates.sumOf { it.weight ?: 1 }
        if (total <= 0) return null
        var pick = Random.nextInt(total)
        return candidates.firstOrNull { quest -> pick -= quest.weight ?: 1; pick < 0 }
    }

    /** Starts a race now. [quantity] defaults to a draw in the quest's range. Returns false while one runs. */
    fun start(server: MinecraftServer, quest: QuestDef, quantity: Int?): Boolean {
        if (current != null) return false
        val range = quest.range ?: return false
        val goal = quantity ?: Random.nextInt(range.min, range.max + 1)
        val now = Instant.now()
        val bar = ServerBossEvent(Component.empty(), BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.NOTCHED_10)
        val run = Run(quest, goal, now, now.plusSeconds(durationMinutes() * 60L), bar)
        current = run
        server.playerList.players.forEach { player ->
            bar.addPlayer(player)
            player.sendSystemMessage(
                Component.translatable("chat.lnbquests.flash.started", goalText(run, player), durationMinutes()).withStyle(ChatFormatting.LIGHT_PURPLE),
            )
            player.playNotifySound(SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 1f, 1.4f)
        }
        updateBar(run, now)
        return true
    }

    fun stop(server: MinecraftServer): Boolean {
        val run = current ?: return false
        end(server, run, null)
        return true
    }

    fun goalText(run: Run, player: ServerPlayer): Component =
        Component.translatable("chat.lnbquests.flash.goal", Texts.name(run.quest, player), Texts.number(run.quantity.toLong(), player))

    private fun updateBar(run: Run, now: Instant) {
        val left = run.endsAt.epochSecond - now.epochSecond
        val clock = "%d:%02d".format(left / 60, left % 60)
        val leader = run.leader()
        val title = Component.literal("⚡ ").append(Component.literal(run.quest.name.fr)).append(" : ${run.quantity}  ")
            .append(
                if (leader == null) Component.translatable("bossbar.lnbquests.flash.nobody")
                else Component.translatable("bossbar.lnbquests.flash.leader", run.names[leader.first] ?: "?", leader.second, run.quantity),
            )
            .append("  $clock")
        run.bar.name = title
        val total = (run.endsAt.epochSecond - run.startedAt.epochSecond).coerceAtLeast(1)
        run.bar.progress = (left.toFloat() / total).coerceIn(0f, 1f)
    }

    fun onProgress(player: ServerPlayer, quest: QuestDef, amount: Long) {
        val run = current ?: return
        if (run.quest.id != quest.id) return
        val value = (run.progress[player.uuid] ?: 0) + amount
        run.progress[player.uuid] = value
        run.names[player.uuid] = player.gameProfile.name
        player.displayClientMessage(
            Component.translatable("actionbar.lnbquests.flash", minOf(value, run.quantity.toLong()), run.quantity).withStyle(ChatFormatting.LIGHT_PURPLE), true,
        )
        if (value >= run.quantity) end(player.server, run, player)
    }

    fun onLogin(player: ServerPlayer) {
        current?.bar?.addPlayer(player)
    }

    fun onLogout(player: ServerPlayer) {
        current?.bar?.removePlayer(player)
    }

    private fun end(server: MinecraftServer, run: Run, winner: ServerPlayer?) {
        if (current !== run) return
        current = null
        run.bar.removeAllPlayers()
        val ended = Instant.now()
        server.playerList.players.forEach { viewer ->
            val text = if (winner != null) {
                Component.translatable("chat.lnbquests.flash.won", Component.literal(winner.gameProfile.name).withStyle(ChatFormatting.AQUA), goalText(run, viewer))
            } else {
                Component.translatable("chat.lnbquests.flash.expired", goalText(run, viewer))
            }
            viewer.sendSystemMessage(text.withStyle(ChatFormatting.LIGHT_PURPLE))
        }
        QuestJournal.submit("/flash", PURPOSE, mapOf("winner" to (winner?.uuid?.toString() ?: ""), "quest" to run.quest.id)) { key ->
            FlashBody(key, run.quest.id, run.quantity, run.startedAt.toString(), ended.toString(), winner?.uuid?.toString())
        }
    }

    private fun finish(server: MinecraftServer, entry: JournalEntry, outcome: Outcome) {
        val winner = entry.data["winner"]?.takeIf { it.isNotEmpty() }?.let(UUID::fromString) ?: return
        val player = server.playerList.getPlayer(winner)
        when (outcome) {
            is Outcome.Done -> {
                val result = outcome.decode<FlashResult>()
                if (player != null && !result.replayed) {
                    player.sendSystemMessage(Component.translatable("chat.lnbquests.flash.paid", Texts.amount(result.amount, player)).withStyle(ChatFormatting.GOLD))
                    val quest = Catalog.quest(entry.data["quest"] ?: "")
                    fr.bde_teknantes.lnb.quests.network.Toasts.send(
                        player,
                        Component.translatable("toast.lnbquests.flash"),
                        Component.translatable("toast.lnbquests.flash.description", Texts.number(result.amount, player)),
                        fr.bde_teknantes.lnb.quests.network.Toasts.icon(quest),
                    )
                }
            }
            is Outcome.Refused -> {
                LnbQuests.LOGGER.warn("The site refused the flash race won by {}: {}", winner, outcome.result.describe())
                val problem = (outcome.result as? fr.bde_teknantes.lnb.warden.common.api.ApiResult.Http)?.problem
                if (player != null && problem == fr.bde_teknantes.lnb.quests.api.Problems.NOT_LINKED) player.sendSystemMessage(Texts.notLinked())
            }
            is Outcome.Delayed -> Unit
        }
    }

    /** A race running when the server stops is reported as expired. */
    fun onStopping(server: MinecraftServer) {
        current?.let { end(server, it, null) }
        nextDraw = null
    }
}
