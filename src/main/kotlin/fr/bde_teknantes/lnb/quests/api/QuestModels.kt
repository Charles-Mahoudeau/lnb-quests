package fr.bde_teknantes.lnb.quests.api

// The site's quest contracts (`docs/quests.md` on the site), as Gson reads and writes them. Fields a family doesn't use
// are null. Kept close to `shared/schemas/quests.ts`.

data class Localized(val fr: String = "", val en: String = "") {
    fun get(language: String): String = if (language.startsWith("fr")) fr.ifBlank { en } else en.ifBlank { fr }
}

data class Objective(val type: String = "", val target: String? = null)

data class ItemReward(val id: String = "", val snbt: String = "", val name: String = "", val count: Int = 1)

data class Tier(val amount: Long = 0, val reward: Double = 0.0, val items: List<ItemReward>? = null)

data class Range(val min: Int = 1, val max: Int = 1)

data class Hours(val from: String = "00:00", val to: String = "00:00")

/** A quest of the catalogue: `individual`, `server` or `flash`. */
data class QuestDef(
    val id: String = "",
    val family: String = "",
    val name: Localized = Localized(),
    val description: Localized? = null,
    val icon: String = "minecraft:paper",
    val objective: Objective = Objective(),
    val enabled: Boolean = true,
    // individual
    val category: String? = null,
    val tiers: List<Tier>? = null,
    // server
    val advancement: String? = null,
    val tier: String? = null,
    val reward: Double? = null,
    val ranks: List<Double>? = null,
    val restShare: Double? = null,
    /** A secret advancement: "???" until someone takes first place. Hidden advancements of the game count too. */
    val hidden: Boolean? = null,
    val items: List<ItemReward>? = null,
    // flash
    val range: Range? = null,
    val rewardPerUnit: Double? = null,
    val weight: Int? = null,
    val minPlayers: Int? = null,
    val hours: Hours? = null,
) {
    val isIndividual get() = family == INDIVIDUAL
    val isServer get() = family == SERVER
    val isFlash get() = family == FLASH

    companion object {
        const val INDIVIDUAL = "individual"
        const val SERVER = "server"
        const val FLASH = "flash"
    }
}

data class Season(val id: Int = 0, val number: Int = 0, val startedAt: String = "")

data class FlashSettings(
    val intervalMinutes: Int = 30,
    val minPlayers: Int = 2,
    val durationMinutes: Int = 15,
    val hours: Hours? = null,
)

data class AntiCheat(
    val ignorePlacedBlocks: Boolean = true,
    val ignoreSpawnerMobs: Boolean = true,
    val ignoreMachineBreaks: Boolean = true,
    val countFluidBlocks: Boolean = true,
    val excludeCreative: Boolean = true,
)

data class CommunityTier(val name: String = "", val at: Double = 0.0, val reward: Double = 0.0, val rewardToday: Long = 0)

data class Community(
    val id: String = "",
    val title: Localized = Localized(),
    val objective: Objective = Objective(),
    val goal: Long = 1,
    val tiers: List<CommunityTier> = emptyList(),
    val thresholdPercent: Double = 0.0,
    val topBonusPercent: Double = 0.0,
    val startsAt: String = "",
    val endsAt: String = "",
    val status: String = "",
    val progress: Long = 0,
    val contributors: Int = 0,
)

data class IndexView(val index: Double = 1000.0, val kGlobal: Double = 1.0)

/** `GET /config`. */
data class QuestConfig(
    val season: Season = Season(),
    val quests: List<QuestDef> = emptyList(),
    val flash: FlashSettings = FlashSettings(),
    val antiCheat: AntiCheat = AntiCheat(),
    val community: Community? = null,
    val index: IndexView = IndexView(),
    val pollSeconds: Int = 300,
)

data class CatalogBody(val quests: List<QuestDef>)
data class CatalogResult(val added: List<String> = emptyList(), val kept: List<String> = emptyList())

data class ProgressItem(val player: String, val quest: String, val value: Long)
data class ProgressBody(val items: List<ProgressItem>)

data class CompletionBody(val idempotencyKey: String, val player: String, val quest: String, val tier: Int)
data class CompletionResult(
    val completionId: String = "",
    val replayed: Boolean = false,
    val quest: String = "",
    val tier: Int = 0,
    val amount: Long = 0,
    val capped: Boolean = false,
    val claimable: Boolean = true,
)

data class ClaimBody(val idempotencyKey: String, val player: String)
data class PaidTo(val accountId: String = "", val balance: Long = 0)
data class ClaimResult(
    val transactionId: Long? = null,
    val replayed: Boolean = false,
    val amount: Long = 0,
    val to: PaidTo? = null,
    val mailboxItems: Int = 0,
)

data class AdvancementBody(val idempotencyKey: String, val player: String, val quest: String)
data class PodiumEntry(val rank: Int = 0, val username: String = "", val amount: Long = 0)
data class AdvancementResult(
    val replayed: Boolean = false,
    val rank: Int = 0,
    val amount: Long = 0,
    val capped: Boolean = false,
    val transactionId: Long? = null,
    val mailboxItems: Int = 0,
    val podium: List<PodiumEntry> = emptyList(),
)

data class FlashBody(
    val idempotencyKey: String,
    val quest: String,
    val quantity: Int,
    val startedAt: String,
    val endedAt: String,
    val winner: String?,
)
data class FlashResult(val replayed: Boolean = false, val flashId: String = "", val amount: Long = 0, val capped: Boolean = false)

data class ContributionItem(val player: String, val amount: Long)
data class ContributionBody(val idempotencyKey: String, val items: List<ContributionItem>)
data class ContributionResult(val replayed: Boolean = false, val progress: Long = 0, val goal: Long = 0)

data class PlayerCompletion(
    val id: String = "",
    val quest: String = "",
    val tier: Int = 0,
    val amount: Long = 0,
    val capped: Boolean = false,
    val completedAt: String = "",
    val claimedAt: String? = null,
)
data class CommunityPart(val contribution: Long = 0, val qualified: Boolean = false)

/** `GET /players/:uuid`. */
data class PlayerView(
    val completions: List<PlayerCompletion> = emptyList(),
    val podiums: Map<String, List<PodiumEntry>> = emptyMap(),
    val ranks: Map<String, Int> = emptyMap(),
    val kPlayer: Double = 1.0,
    val community: CommunityPart? = null,
)
