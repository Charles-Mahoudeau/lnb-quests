package fr.bde_teknantes.lnb.quests.api

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fr.bde_teknantes.lnb.quests.LnbQuests
import fr.bde_teknantes.lnb.quests.network.QuestScreens
import fr.bde_teknantes.lnb.warden.common.api.ApiClient
import net.minecraft.server.MinecraftServer
import net.neoforged.neoforge.event.server.ServerStartedEvent
import net.neoforged.neoforge.event.server.ServerStoppingEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import java.io.BufferedReader
import java.io.IOException
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID

/**
 * Keeps the site's `GET /api/quests/server/events` open, on its own thread, as LNB Warden does with its own stream:
 * a quest, a setting, the season or the community quest changed on the site reaches the game within a second, instead
 * of at the next poll (`sync.pollSeconds`), which still runs in case the stream is down.
 *
 * - `config`: the site's quest config is read again.
 * - `player` `{ uuids }`: those players claimed a reward on the site; their quest screen and badge follow.
 */
object QuestEvents {
    private const val PATH = "/api/quests/server/events"
    private const val MAX_BACKOFF_MS = 30_000L

    /** The site pings after 25 quiet seconds: past this, the connection is presumed dead. */
    private const val SILENCE_MS = 75_000L

    @Volatile
    private var running = false
    private var thread: Thread? = null

    @Volatile
    private var reader: BufferedReader? = null

    @Volatile
    private var lastEventId: String? = null

    @Volatile
    private var lastDataAt = 0L

    @Volatile
    private var connected = false
    private var backoffMs = 1_000L

    fun onServerStarted(event: ServerStartedEvent) {
        if (running) return
        running = true
        val server = event.server
        thread = Thread({ run(server) }, "LNB Quests events").apply {
            isDaemon = true
            start()
        }
    }

    fun onServerStopping(event: ServerStoppingEvent) {
        running = false
        thread?.interrupt()
        thread = null
        closeReader()
    }

    /** Drops a connection the site stopped pinging, so the thread reconnects. */
    fun onServerTick(event: ServerTickEvent.Post) {
        if (connected && System.currentTimeMillis() - lastDataAt > SILENCE_MS) closeReader()
    }

    private fun run(server: MinecraftServer) {
        var lastReason: String? = null
        while (running) {
            val reason = try {
                if (QuestsApi.connection() == null) "api.url and api.token aren't set" else {
                    listen(server)
                    "the stream ended"
                }
            } catch (e: InterruptedException) {
                break
            } catch (e: Exception) {
                e.message ?: e.javaClass.simpleName
            }
            connected = false
            if (!running) break
            // Once per change, not again for each failed reconnection.
            if (reason != lastReason) LnbQuests.LOGGER.warn("LNB's quest event stream is disconnected ({}), reconnecting", reason)
            lastReason = reason
            try {
                Thread.sleep(backoffMs)
            } catch (e: InterruptedException) {
                break
            }
            backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
        }
    }

    private fun listen(server: MinecraftServer) {
        val (url, token) = QuestsApi.connection() ?: return
        val request = ApiClient.request(url, PATH, token)
            .header("Accept", "text/event-stream")
            .timeout(Duration.ofSeconds(30))
            .apply { lastEventId?.let { header("Last-Event-ID", it) } }
            .GET()
            .build()
        val response = ApiClient.HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream())
        if (response.statusCode() != 200) {
            response.body().close()
            throw IOException("HTTP ${response.statusCode()}")
        }
        lastDataAt = System.currentTimeMillis()
        backoffMs = 1_000L
        connected = true
        LnbQuests.LOGGER.info("Connected to LNB's quest event stream")
        // Changes made while the stream was down are lost: read the config again.
        server.execute { LnbQuests.reloadConfig(server) }

        response.body().bufferedReader().use { reader ->
            this.reader = reader
            var type = "message"
            var id: String? = null
            val data = StringBuilder()
            while (running) {
                val line = reader.readLine() ?: break
                lastDataAt = System.currentTimeMillis()
                when {
                    line.isEmpty() -> {
                        if (id != null) lastEventId = id
                        if (data.isNotEmpty()) dispatch(server, type, data.toString().trimEnd('\n'))
                        type = "message"
                        id = null
                        data.clear()
                    }
                    line.startsWith(":") -> Unit
                    else -> {
                        val value = line.substringAfter(':', "").removePrefix(" ")
                        when (line.substringBefore(':')) {
                            "event" -> type = value
                            "data" -> data.append(value).append('\n')
                            "id" -> id = value
                        }
                    }
                }
            }
        }
        reader = null
    }

    private fun dispatch(server: MinecraftServer, type: String, data: String) {
        if (type == "ping") return
        val body = try {
            JsonParser.parseString(data) as? JsonObject
        } catch (e: Exception) {
            null
        } ?: return LnbQuests.LOGGER.warn("Ignoring a malformed LNB quests {} event", type)
        val uuids = body.getAsJsonArray("uuids")?.mapNotNull { runCatching { UUID.fromString(it.asString) }.getOrNull() }.orEmpty()
        when (type) {
            "config" -> server.execute { LnbQuests.reloadConfig(server) }
            "player" -> server.execute {
                for (uuid in uuids) {
                    val player = server.playerList.getPlayer(uuid) ?: continue
                    QuestScreens.refresh(server, uuid)
                    QuestScreens.sendBadge(player)
                }
            }
            else -> LnbQuests.LOGGER.debug("Ignoring LNB quests event {}", type)
        }
    }

    private fun closeReader() {
        try {
            reader?.close()
        } catch (_: IOException) {
        }
    }
}
