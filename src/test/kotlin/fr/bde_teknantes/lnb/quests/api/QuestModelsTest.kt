package fr.bde_teknantes.lnb.quests.api

import com.google.gson.JsonParser
import fr.bde_teknantes.lnb.warden.common.api.ApiClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QuestModelsTest {
    @Test
    fun `the bundled catalogue reads as quests of the three families`() {
        val text = javaClass.getResourceAsStream("/assets/lnbquests/default_quests.json")!!.readAllBytes().decodeToString()
        val quests = JsonParser.parseString(text).asJsonObject.getAsJsonArray("quests").map { ApiClient.GSON.fromJson(it, QuestDef::class.java) }
        assertEquals(setOf("individual", "server", "flash"), quests.map { it.family }.toSet())
        assertTrue(quests.filter { it.isIndividual }.all { q -> q.tiers!!.zipWithNext().all { (a, b) -> b.amount > a.amount } })
        assertTrue(quests.filter { it.isServer }.all { Regex("[a-z0-9_.-]+:[a-z0-9_./-]+").matches(it.advancement!!) })
        assertEquals(quests.size, quests.map { it.id }.toSet().size)
    }

    @Test
    fun `a config with missing parts keeps its defaults`() {
        val config = ApiClient.GSON.fromJson("""{"season":{"id":1,"number":3},"quests":[],"community":null}""", QuestConfig::class.java)
        assertEquals(3, config.season.number)
        assertEquals(300, config.pollSeconds)
        assertTrue(config.antiCheat.ignorePlacedBlocks)
    }
}
