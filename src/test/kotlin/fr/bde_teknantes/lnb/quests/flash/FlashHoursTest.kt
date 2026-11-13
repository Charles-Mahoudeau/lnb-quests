package fr.bde_teknantes.lnb.quests.flash

import fr.bde_teknantes.lnb.quests.api.Hours
import java.time.ZonedDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FlashHoursTest {
    private fun at(hour: Int, minute: Int = 0) =
        ZonedDateTime.of(2026, 11, 13, hour, minute, 0, 0, ZoneId.of("Europe/Paris")).toInstant()

    @Test
    fun `no hours means always`() {
        assertTrue(FlashRaces.within(null, at(4)))
    }

    @Test
    fun `hours may cross midnight, in Paris time`() {
        val evening = Hours("18:00", "01:00")
        assertTrue(FlashRaces.within(evening, at(23)))
        assertTrue(FlashRaces.within(evening, at(0, 30)))
        assertFalse(FlashRaces.within(evening, at(12)))
        assertFalse(FlashRaces.within(evening, at(1)))
    }
}
