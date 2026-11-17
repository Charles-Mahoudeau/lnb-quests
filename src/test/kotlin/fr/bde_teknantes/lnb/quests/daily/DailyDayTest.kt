package fr.bde_teknantes.lnb.quests.daily

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class DailyDayTest {
    @Test
    fun `days are Paris days, like the site's`() {
        assertEquals("2026-11-17", DailyQuests.day(Instant.parse("2026-11-17T22:59:59Z")))
        assertEquals("2026-11-18", DailyQuests.day(Instant.parse("2026-11-17T23:00:00Z")))
        assertEquals("2026-07-02", DailyQuests.day(Instant.parse("2026-07-01T22:00:00Z")))
    }

    @Test
    fun `a day ends at the next Paris midnight, clock changes included`() {
        assertEquals(Instant.parse("2026-11-17T23:00:00Z"), DailyQuests.endOf("2026-11-17"))
        assertEquals(Instant.parse("2026-03-29T22:00:00Z"), DailyQuests.endOf("2026-03-29"))
        assertEquals(Instant.parse("2026-10-25T23:00:00Z"), DailyQuests.endOf("2026-10-25"))
    }
}
