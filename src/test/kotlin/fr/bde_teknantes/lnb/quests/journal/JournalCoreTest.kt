package fr.bde_teknantes.lnb.quests.journal

import com.google.gson.JsonElement
import fr.bde_teknantes.lnb.quests.api.CompletionBody
import fr.bde_teknantes.lnb.quests.api.CompletionResult
import fr.bde_teknantes.lnb.warden.common.api.ApiClient
import fr.bde_teknantes.lnb.warden.common.api.ApiResult
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JournalCoreTest {
    @TempDir
    lateinit var dir: Path

    private var now = 1_000L
    private val sent = mutableListOf<JournalEntry>()
    private val answers = ArrayDeque<ApiResult<JsonElement>>()
    private val outcomes = mutableListOf<Pair<JournalEntry, Outcome>>()

    private fun core() = JournalCore(
        store = JournalStore(dir.resolve("journal.json")),
        send = { entry ->
            sent.add(entry)
            CompletableFuture.completedFuture(answers.removeFirst())
        },
        clock = { now },
        executor = { it.run() },
        onOutcome = { entry, outcome -> outcomes.add(entry to outcome) },
    )

    private val body = CompletionBody("7b0f4a3e-6d1c-4b8e-9f2a-1c3d5e7f9a0b", "player", "mine_stone", 1)
    private fun entry() = JournalEntry.of("/completions", body, "test", mapOf("tier" to "1"))
    private fun ok(): ApiResult<JsonElement> = ApiResult.Ok(ApiClient.GSON.toJsonTree(CompletionResult("c1", false, "mine_stone", 1, 61)))

    @Test
    fun `a confirmed call leaves the journal`() {
        val core = core()
        answers.add(ok())
        core.submit(entry())
        assertTrue(core.pending.isEmpty())
        val done = assertInstanceOf(Outcome.Done::class.java, outcomes.single().second)
        assertEquals(61, done.decode<CompletionResult>().amount)
    }

    @Test
    fun `a call without an answer is sent again with the same key, after a restart too`() {
        answers.add(ApiResult.Network(IllegalStateException("down")))
        core().submit(entry())
        assertInstanceOf(Outcome.Delayed::class.java, outcomes.single().second)

        val restarted = core()
        restarted.load()
        assertEquals(1, restarted.pending.size)
        answers.add(ok())
        restarted.tick()
        assertEquals(sent[0].key, sent[1].key)
        assertEquals("/completions", sent[1].route)
        assertTrue(restarted.pending.isEmpty())
    }

    @Test
    fun `a refusal ends the call and keeps its problem`() {
        val core = core()
        answers.add(ApiResult.Http(409, "tierLocked"))
        core.submit(entry())
        val refused = assertInstanceOf(Outcome.Refused::class.java, outcomes.single().second)
        assertEquals("tierLocked", (refused.result as ApiResult.Http).problem)
        assertTrue(core.pending.isEmpty())
    }
}
