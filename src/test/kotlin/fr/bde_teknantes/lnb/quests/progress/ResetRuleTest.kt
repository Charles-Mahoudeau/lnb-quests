package fr.bde_teknantes.lnb.quests.progress

import fr.bde_teknantes.lnb.quests.progress.ResetRule.Action
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ResetRuleTest {
    private val date = "2026-11-18T10:30:00.000Z"
    private val later = "2026-11-19T08:00:00.000Z"

    @Test
    fun `the first value is stored without a reset, even a date`() {
        assertEquals(Action.ADOPT, ResetRule.onConfig(known = false, stored = "", incoming = date))
        assertEquals(Action.ADOPT, ResetRule.onConfig(known = false, stored = "", incoming = ""))
    }

    @Test
    fun `the same value does nothing`() {
        assertEquals(Action.NONE, ResetRule.onConfig(known = true, stored = date, incoming = date))
        assertEquals(Action.NONE, ResetRule.onConfig(known = true, stored = "", incoming = ""))
    }

    @Test
    fun `a new date resets, from null or from an older date`() {
        assertEquals(Action.RESET, ResetRule.onConfig(known = true, stored = "", incoming = date))
        assertEquals(Action.RESET, ResetRule.onConfig(known = true, stored = date, incoming = later))
    }

    @Test
    fun `a player is cleaned when they missed one reset or two`() {
        assertFalse(ResetRule.playerStale(later, later))
        assertTrue(ResetRule.playerStale(date, later))
        assertTrue(ResetRule.playerStale("", later))
        assertTrue(ResetRule.playerStale(null, later))
    }
}
