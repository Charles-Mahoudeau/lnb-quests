package fr.bde_teknantes.lnb.quests

import net.neoforged.neoforge.common.ModConfigSpec

/**
 * `config/lnbquests-server.toml`. The site's settings (`/admin/economy/quests`) replace the flash and anti-cheat values
 * once it answers; these are what the server uses before that, or when the site can't be reached.
 */
object QuestsConfig {
    private val BUILDER = ModConfigSpec.Builder()

    init {
        BUILDER.push("flash")
    }

    val FLASH_ENABLED: ModConfigSpec.BooleanValue = BUILDER
        .comment("Start flash races at random. Admins can still start one with /quests flash start.")
        .define("enabled", true)
    val FLASH_INTERVAL_MINUTES: ModConfigSpec.IntValue = BUILDER
        .comment("Minutes between two draws, until the site's setting is known.")
        .defineInRange("intervalMinutes", 30, 1, 24 * 60)
    val FLASH_MIN_PLAYERS: ModConfigSpec.IntValue = BUILDER
        .comment("Players online needed for a draw, until the site's setting is known.")
        .defineInRange("minPlayers", 2, 1, 200)
    val FLASH_DURATION_MINUTES: ModConfigSpec.IntValue = BUILDER
        .comment("How long a race lasts before it expires, until the site's setting is known.")
        .defineInRange("durationMinutes", 15, 1, 240)

    init {
        BUILDER.pop().push("sync")
    }

    val POLL_SECONDS: ModConfigSpec.IntValue = BUILDER
        .comment("How often the server reads the site's quest settings and sends counters, until the site's setting is known.")
        .defineInRange("pollSeconds", 300, 30, 3600)
    val SEED_CATALOG: ModConfigSpec.BooleanValue = BUILDER
        .comment("Send config/lnbquests/quests.json to the site at start, so it adds the quests it doesn't know.")
        .define("seedCatalog", true)

    val SPEC: ModConfigSpec = BUILDER.pop().build()
}
