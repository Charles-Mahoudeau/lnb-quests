package fr.bde_teknantes.lnb.quests.journal

import fr.bde_teknantes.lnb.quests.LnbQuests
import fr.bde_teknantes.lnb.quests.api.QuestsApi
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.storage.LevelResource
import net.neoforged.neoforge.event.server.ServerStartedEvent
import net.neoforged.neoforge.event.server.ServerStoppedEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import java.util.UUID

/**
 * Every call that pays (a tier, a claim, a rank, a flash race, community contributions) goes through here: written to
 * `<world>/data/lnbquests_journal.json` before it is sent, sent again with the same idempotency key until the site
 * confirms or refuses it (after a restart too), then finished by the [Handler] registered for its purpose.
 */
object QuestJournal {
    /** Finishes an entry on the server thread. Must not throw. */
    fun interface Handler {
        fun onOutcome(server: MinecraftServer, entry: JournalEntry, outcome: Outcome)
    }

    private const val FILE = "lnbquests_journal.json"
    private const val TICKS_BETWEEN_CHECKS = 20

    private val handlers = HashMap<String, Handler>()
    private var core: JournalCore? = null
    private var server: MinecraftServer? = null
    private var ticks = 0

    val pending: List<JournalEntry> get() = core?.pending.orEmpty()

    fun register(purpose: String, handler: Handler) {
        check(handlers.putIfAbsent(purpose, handler) == null) { "Journal purpose $purpose is already registered" }
    }

    /**
     * Records and sends a POST to [route] (under `/api/quests/server`). [body] gets a new idempotency key, which it
     * must put in its `idempotencyKey` field. Returns the key.
     */
    fun submit(route: String, purpose: String, data: Map<String, String> = emptyMap(), body: (key: String) -> Any): String {
        val core = checkNotNull(core) { "The quest journal only runs on a started server" }
        check(purpose in handlers) { "No journal handler for $purpose" }
        val entry = JournalEntry.of(route, body(UUID.randomUUID().toString()), purpose, data)
        core.submit(entry)
        return entry.key
    }

    fun retryNow(key: String): Boolean = core?.retryNow(key) ?: false

    fun onServerStarted(event: ServerStartedEvent) {
        val server = event.server
        this.server = server
        val file = server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(FILE)
        val core = JournalCore(
            store = JournalStore(file),
            send = { entry -> QuestsApi.post(entry.route, entry.body) },
            clock = System::currentTimeMillis,
            executor = server::execute,
            onOutcome = ::finish,
        )
        core.load()
        this.core = core
        if (core.pending.isNotEmpty()) {
            LnbQuests.LOGGER.info("{} quest call(s) from a previous run are waiting for LNB's site, sending them again", core.pending.size)
        }
    }

    fun onServerStopped(event: ServerStoppedEvent) {
        core = null
        server = null
    }

    fun onServerTick(event: ServerTickEvent.Post) {
        if (++ticks < TICKS_BETWEEN_CHECKS) return
        ticks = 0
        core?.tick()
    }

    private fun finish(entry: JournalEntry, outcome: Outcome) {
        val server = server ?: return
        val handler = handlers[entry.purpose]
        if (handler == null) {
            LnbQuests.LOGGER.error("No journal handler for {}: entry {} ended with {}", entry.purpose, entry.key, outcome)
            return
        }
        try {
            handler.onOutcome(server, entry, outcome)
        } catch (e: Exception) {
            LnbQuests.LOGGER.error("Journal handler {} failed for entry {}", entry.purpose, entry.key, e)
        }
    }
}
