package fr.bde_teknantes.lnb.quests.api

import com.google.gson.JsonParser
import fr.bde_teknantes.lnb.warden.common.api.ApiClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QuestModelsTest {
    @Test
    fun `the bundled catalogue reads as quests of the four families, in categories it lists`() {
        val text = javaClass.getResourceAsStream("/assets/lnbquests/default_quests.json")!!.readAllBytes().decodeToString()
        val root = JsonParser.parseString(text).asJsonObject
        val quests = root.getAsJsonArray("quests").map { ApiClient.GSON.fromJson(it, QuestDef::class.java) }
        val categories = root.getAsJsonArray("categories").map { ApiClient.GSON.fromJson(it, CategoryDef::class.java) }
        assertEquals(setOf("individual", "server", "flash", "daily"), quests.map { it.family }.toSet())
        val known = categories.map { it.family to it.id }.toSet()
        assertEquals(categories.size, known.size)
        assertTrue(quests.filter { it.isIndividual || it.isServer }.all { (it.family to it.category) in known })
        assertTrue(quests.filter { it.isDaily }.all { it.range!!.max >= it.range!!.min && it.objective.type in setOf("kill", "mine", "fish", "craft", "travel") })
        assertTrue(quests.filter { it.isIndividual }.all { q -> q.tiers!!.zipWithNext().all { (a, b) -> b.amount > a.amount } })
        assertTrue(quests.filter { it.isServer }.all { Regex("[a-z0-9_.-]+:[a-z0-9_./-]+").matches(it.advancement!!) })
        assertEquals(quests.size, quests.map { it.id }.toSet().size)
    }

    @Test
    fun `a config with missing parts keeps its defaults`() {
        val config = ApiClient.GSON.fromJson("""{"resetAt":"2026-11-18T10:30:00.000Z","quests":[],"community":null}""", QuestConfig::class.java)
        assertEquals("2026-11-18T10:30:00.000Z", config.resetAt)
        assertEquals(300, config.pollSeconds)
        assertTrue(config.antiCheat.ignorePlacedBlocks)
    }

    @Test
    fun `a config from an older site has no categories and no daily quests`() {
        val config = ApiClient.GSON.fromJson("""{"season":{"id":1,"number":3},"quests":[]}""", QuestConfig::class.java)
        assertTrue(config.categories.isEmpty())
        assertTrue(config.daily.quests.isEmpty())
        assertEquals(null, config.resetAt)
    }
}
