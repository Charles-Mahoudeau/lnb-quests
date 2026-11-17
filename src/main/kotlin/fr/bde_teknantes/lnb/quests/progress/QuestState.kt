package fr.bde_teknantes.lnb.quests.progress

import com.google.gson.reflect.TypeToken
import fr.bde_teknantes.lnb.warden.common.api.ApiClient
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.saveddata.SavedData
import java.util.UUID

/** One player's quests this season, as the server counts them. */
data class PlayerQuests(
    var season: Int = 0,
    /** Individual quest id -> its counter this season. */
    val counters: MutableMap<String, Long> = HashMap(),
    /** "quest:tier" sent to the site and not refused. */
    val reported: MutableSet<String> = HashSet(),
    /** "quest:tier" the site confirmed. A tier is only sent once the one before it is confirmed. */
    val confirmed: MutableSet<String> = HashSet(),
    /** Server quests whose advancement was sent to the site. */
    val ranked: MutableSet<String> = HashSet(),
    /** Individual quests whose counter changed since the last push to the site. */
    val dirty: MutableSet<String> = HashSet(),
    /** The Paris day the daily counters below are for ("YYYY-MM-DD"). */
    var dailyDay: String = "",
    /** Daily quest id -> its counter today. */
    val dailyCounters: MutableMap<String, Long> = HashMap(),
    /** Daily quests sent to the site today and not refused. */
    val dailyReported: MutableSet<String> = HashSet(),
    /** Daily quests the site paid today (or said were already paid). */
    val dailyDone: MutableSet<String> = HashSet(),
) {
    /** Starts the daily counters over when [day] isn't theirs. */
    fun dailyFor(day: String) {
        if (dailyDay == day) return
        dailyDay = day
        dailyCounters.clear()
        dailyReported.clear()
        dailyDone.clear()
    }

    fun resetFor(season: Int) {
        this.season = season
        counters.clear()
        reported.clear()
        confirmed.clear()
        ranked.clear()
        dirty.clear()
    }
}

/**
 * Every player's counters, in the overworld's saved data (`data/lnbquests.dat`), as JSON inside NBT: small, and
 * readable when an admin needs to look. Also keeps the community contributions not sent yet.
 */
class QuestState : SavedData() {
    var season: Int = 0
    val players: MutableMap<UUID, PlayerQuests> = HashMap()
    /** Community quest id -> player -> amount not sent yet. */
    val pendingCommunity: MutableMap<String, MutableMap<UUID, Long>> = HashMap()

    fun player(uuid: UUID): PlayerQuests = players.getOrPut(uuid) { PlayerQuests(season = season) }

    override fun save(tag: CompoundTag, registries: HolderLookup.Provider): CompoundTag {
        tag.putInt("season", season)
        tag.putString("players", ApiClient.GSON.toJson(players.mapKeys { it.key.toString() }))
        tag.putString("community", ApiClient.GSON.toJson(pendingCommunity.mapValues { (_, v) -> v.mapKeys { it.key.toString() } }))
        return tag
    }

    companion object {
        private const val NAME = "lnbquests"
        private val playersType = object : TypeToken<Map<String, PlayerQuests>>() {}.type
        private val communityType = object : TypeToken<Map<String, Map<String, Long>>>() {}.type

        private fun load(tag: CompoundTag, registries: HolderLookup.Provider): QuestState {
            val state = QuestState()
            state.season = tag.getInt("season")
            runCatching {
                val players: Map<String, PlayerQuests> = ApiClient.GSON.fromJson(tag.getString("players"), playersType) ?: emptyMap()
                players.forEach { (key, value) -> state.players[UUID.fromString(key)] = normalize(value) }
            }
            runCatching {
                val community: Map<String, Map<String, Long>> = ApiClient.GSON.fromJson(tag.getString("community"), communityType) ?: emptyMap()
                community.forEach { (id, map) ->
                    state.pendingCommunity[id] = map.mapKeys { UUID.fromString(it.key) }.toMutableMap()
                }
            }
            return state
        }

        /** Gson builds the collections as it likes: make them the mutable ones the code expects. */
        private fun normalize(value: PlayerQuests) = PlayerQuests(
            season = value.season,
            counters = HashMap(value.counters ?: emptyMap()),
            reported = HashSet(value.reported ?: emptySet()),
            confirmed = HashSet(value.confirmed ?: emptySet()),
            ranked = HashSet(value.ranked ?: emptySet()),
            dirty = HashSet(value.dirty ?: emptySet()),
            dailyDay = value.dailyDay ?: "",
            dailyCounters = HashMap(value.dailyCounters ?: emptyMap()),
            dailyReported = HashSet(value.dailyReported ?: emptySet()),
            dailyDone = HashSet(value.dailyDone ?: emptySet()),
        )

        fun get(server: MinecraftServer): QuestState =
            server.overworld().dataStorage.computeIfAbsent(Factory(::QuestState, ::load), NAME)
    }
}
