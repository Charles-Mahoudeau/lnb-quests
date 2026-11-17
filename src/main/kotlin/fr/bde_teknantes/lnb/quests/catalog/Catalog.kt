package fr.bde_teknantes.lnb.quests.catalog

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fr.bde_teknantes.lnb.quests.LnbQuests
import fr.bde_teknantes.lnb.quests.QuestsConfig
import fr.bde_teknantes.lnb.quests.api.CategoryDef
import fr.bde_teknantes.lnb.quests.api.QuestConfig
import fr.bde_teknantes.lnb.quests.api.QuestDef
import fr.bde_teknantes.lnb.quests.api.QuestsApi
import fr.bde_teknantes.lnb.warden.common.api.ApiClient
import fr.bde_teknantes.lnb.warden.common.api.ApiResult
import net.minecraft.server.MinecraftServer
import net.neoforged.fml.loading.FMLPaths
import java.nio.file.Files

/**
 * What the site says quests are: the catalogue and its categories, today's daily quests, the season, flash and
 * anti-cheat settings, the running community quest. Read at start, every `pollSeconds` and just after midnight. Until
 * the site answers, the local quests.json stands in, so counting starts at once; nothing is paid before the site
 * confirms.
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

    /** The categories in display order, individual chapters and Records groups alike. */
    val categories: List<CategoryDef> get() = config.categories

    /** The quests.json of the server: its categories (optional) and quests. */
    data class Local(val categories: List<CategoryDef>, val quests: List<QuestDef>)

    /** `config/lnbquests/quests.json`, written from the mod's default the first time. */
    fun local(): Local {
        val file = FMLPaths.CONFIGDIR.get().resolve("lnbquests").resolve("quests.json")
        try {
            if (!Files.exists(file)) {
                Files.createDirectories(file.parent)
                val bundled = Catalog::class.java.getResourceAsStream(DEFAULT)?.use { it.readAllBytes() } ?: return Local(emptyList(), emptyList())
                Files.write(file, bundled)
            }
            return parse(Files.readString(file))
        } catch (e: Exception) {
            LnbQuests.LOGGER.error("config/lnbquests/quests.json can't be read: {}", e.toString())
            return Local(emptyList(), emptyList())
        }
    }

    /** A quests.json: `{ "categories": [...], "quests": [...] }`, or just the array of quests. */
    fun parse(text: String): Local {
        val json = JsonParser.parseString(text)
        if (json.isJsonArray) return Local(emptyList(), json.asJsonArray.map { ApiClient.GSON.fromJson(it, QuestDef::class.java) })
        val root = json as JsonObject
        val categories = root.getAsJsonArray("categories")?.map { ApiClient.GSON.fromJson(it, CategoryDef::class.java) }.orEmpty()
        val quests = root.getAsJsonArray("quests")?.map { ApiClient.GSON.fromJson(it, QuestDef::class.java) }.orEmpty()
        return Local(categories, quests)
    }

    /** At start: the local catalogue stands in, then is sent to the site, then the site's config is read. */
    fun start(server: MinecraftServer, onConfig: (QuestConfig) -> Unit) {
        val local = local()
        if (!fromSite) config = config.copy(quests = local.quests, categories = local.categories)
        if (QuestsConfig.SEED_CATALOG.get() && local.quests.isNotEmpty()) {
            QuestsApi.seedCatalog(local.categories, local.quests).thenAccept { result ->
                when (result) {
                    is ApiResult.Ok -> if (result.body.added.isNotEmpty() || result.body.categoriesAdded.isNotEmpty()) {
                        LnbQuests.LOGGER.info(
                            "The site added {} quest(s) and {} category(ies) from quests.json",
                            result.body.added.size,
                            result.body.categoriesAdded.size,
                        )
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
