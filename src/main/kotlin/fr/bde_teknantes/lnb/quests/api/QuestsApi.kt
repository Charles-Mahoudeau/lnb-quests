package fr.bde_teknantes.lnb.quests.api

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import fr.bde_teknantes.lnb.warden.common.api.ApiClient
import fr.bde_teknantes.lnb.warden.common.api.ApiResult
import fr.bde_teknantes.lnb.warden.server.ServerConfig
import fr.bde_teknantes.lnb.warden.server.api.ApiHealth
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * The `/api/quests/server` routes of LNB's site, called with the server's token from LNB Warden's config
 * (`api.url` and `api.token` in `lnbwarden-server.toml`), so the secret lives in one place.
 *
 * Every call is asynchronous and never throws: callers hop back to the server thread before touching the game.
 * Calls that pay go through [fr.bde_teknantes.lnb.quests.journal.QuestJournal], which uses [post].
 */
object QuestsApi {
    private const val BASE = "/api/quests/server"

    /** The site's base URL and the server's token, or null when they aren't set. */
    @Volatile
    var connection: () -> Pair<String, String>? = {
        if (ServerConfig.isApiConfigured) ServerConfig.API_URL.get() to ServerConfig.API_TOKEN.get() else null
    }

    fun config(): CompletableFuture<ApiResult<QuestConfig>> = call("quests config", "GET", "$BASE/config")

    fun seedCatalog(categories: List<CategoryDef>, quests: List<QuestDef>): CompletableFuture<ApiResult<CatalogResult>> =
        call("quests catalogue", "POST", "$BASE/catalog", CatalogBody(categories, quests))

    fun progress(items: List<ProgressItem>): CompletableFuture<ApiResult<Unit>> =
        call("quests progress", "POST", "$BASE/progress", ProgressBody(items))

    fun player(uuid: UUID): CompletableFuture<ApiResult<PlayerView>> = call("quests player", "GET", "$BASE/players/$uuid")

    /** A POST to [route] (under `/api/quests/server`), with an answer left as JSON. Used by the journal. */
    fun post(route: String, body: JsonObject): CompletableFuture<ApiResult<JsonElement>> =
        call("quests ${route.trimStart('/').substringBefore('/')}", "POST", BASE + route, body)

    private inline fun <reified T> call(what: String, method: String, path: String, body: Any? = null): CompletableFuture<ApiResult<T>> {
        val (url, token) = connection() ?: run {
            val result = ApiResult.Network(IllegalStateException("api.url and api.token aren't set in lnbwarden-server.toml"))
            ApiHealth.record(what, result)
            return CompletableFuture.completedFuture(result)
        }
        return ApiClient.send<T>(url, method, path, token, body, requireSecure = false)
            .thenApply { result -> result.also { ApiHealth.record(what, it) } }
    }
}
