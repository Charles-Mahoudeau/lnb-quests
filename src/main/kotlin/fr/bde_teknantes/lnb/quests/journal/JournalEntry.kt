package fr.bde_teknantes.lnb.quests.journal

import com.google.gson.JsonObject
import fr.bde_teknantes.lnb.warden.common.api.ApiClient

/**
 * A call that pays someone, which the site hasn't confirmed or refused yet: a POST to [route], under
 * `/api/quests/server`, with [body], which carries its `idempotencyKey`. [purpose] names the [QuestJournal.Handler]
 * that finishes the job (tells the player, refreshes their screen...), and [data] holds what it needs, so the job can
 * finish after a restart.
 */
data class JournalEntry(
    val body: JsonObject,
    val purpose: String,
    val route: String,
    val data: Map<String, String> = emptyMap(),
    val attempts: Int = 0,
    val nextAttemptAt: Long = 0,
    val createdAt: Long = 0,
    val lastError: String? = null,
) {
    val key: String get() = body.get(KEY).asString

    companion object {
        const val KEY = "idempotencyKey"

        /** An entry for a POST of [body] to [route]. [body] is turned into JSON as it will be sent. */
        fun of(route: String, body: Any, purpose: String, data: Map<String, String> = emptyMap()): JournalEntry {
            val json = ApiClient.GSON.toJsonTree(body).asJsonObject
            require(json.has(KEY)) { "A journal entry's body needs an $KEY" }
            return JournalEntry(json, purpose, route, data)
        }
    }
}
