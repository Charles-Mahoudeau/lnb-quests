package fr.bde_teknantes.lnb.quests.catalog

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fr.bde_teknantes.lnb.quests.LnbQuests
import fr.bde_teknantes.lnb.quests.QuestsConfig
import fr.bde_teknantes.lnb.quests.api.QuestConfig
import fr.bde_teknantes.lnb.quests.api.QuestDef
import fr.bde_teknantes.lnb.quests.api.QuestsApi
import fr.bde_teknantes.lnb.warden.common.api.ApiClient
import fr.bde_teknantes.lnb.warden.common.api.ApiResult
import net.minecraft.server.MinecraftServer
import net.neoforged.fml.loading.FMLPaths
import java.nio.file.Files

/**
 * What the site says quests are: the catalogue, the season, flash and anti-cheat settings, the running community
 * quest. Read at start and every `pollSeconds`. Until the site answers, the local quests.json stands in, so counting
 * starts at once; nothing is paid before the site confirms.
 */
object Catalog {
    private const val DEFAULT = "/assets/lnbquests/default_quests.json"

    @Volatile
    var config: QuestConfig = QuestConfig()
        private set

    /** True once the site answered at least once. */
    @Volatile
    var fromSite = false
        private set

    val quests: List<QuestDef> get() = config.quests.filter { it.enabled }

    fun quest(id: String): QuestDef? = config.quests.firstOrNull { it.id == id }

    /** `config/lnbquests/quests.json`, written from the mod's default the first time. */
    fun localQuests(): List<QuestDef> {
        val file = FMLPaths.CONFIGDIR.get().resolve("lnbquests").resolve("quests.json")
        try {
            if (!Files.exists(file)) {
                Files.createDirectories(file.parent)
                val bundled = Catalog::class.java.getResourceAsStream(DEFAULT)?.use { it.readAllBytes() } ?: return emptyList()
                Files.write(file, bundled)
            }
            val json = JsonParser.parseString(Files.readString(file))
            val array = if (json.isJsonArray) json.asJsonArray else (json as JsonObject).getAsJsonArray("quests")
            return array.map { ApiClient.GSON.fromJson(it, QuestDef::class.java) }
        } catch (e: Exception) {
            LnbQuests.LOGGER.error("config/lnbquests/quests.json can't be read: {}", e.toString())
            return emptyList()
        }
    }

    /** At start: the local catalogue stands in, then is sent to the site, then the site's config is read. */
    fun start(server: MinecraftServer, onConfig: (QuestConfig) -> Unit) {
        val local = localQuests()
        if (!fromSite) config = config.copy(quests = local)
        if (QuestsConfig.SEED_CATALOG.get() && local.isNotEmpty()) {
            QuestsApi.seedCatalog(local).thenAccept { result ->
                when (result) {
                    is ApiResult.Ok -> if (result.body.added.isNotEmpty()) {
                        LnbQuests.LOGGER.info("The site added {} quest(s) from quests.json", result.body.added.size)
                    }
                    else -> LnbQuests.LOGGER.warn("The quest catalogue couldn't be sent to the site: {}", result.describe())
                }
                refresh(server, onConfig)
            }
        } else {
            refresh(server, onConfig)
        }
    }

    fun refresh(server: MinecraftServer, onConfig: (QuestConfig) -> Unit) {
        QuestsApi.config().thenAccept { result ->
            server.execute {
                when (result) {
                    is ApiResult.Ok -> {
                        config = result.body
                        fromSite = true
                        onConfig(result.body)
                    }
                    else -> LnbQuests.LOGGER.warn("The quest settings couldn't be read from the site: {}", result.describe())
                }
            }
        }
    }

    fun pollSeconds(): Int = if (fromSite) config.pollSeconds else QuestsConfig.POLL_SECONDS.get()
}
