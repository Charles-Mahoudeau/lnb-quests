package fr.bde_teknantes.lnb.quests.progress

import fr.bde_teknantes.lnb.quests.catalog.Catalog
import fr.bde_teknantes.lnb.quests.catalog.Targets
import net.minecraft.core.Holder
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.MobSpawnType
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.common.util.FakePlayer
import net.neoforged.neoforge.event.entity.EntityTeleportEvent
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.level.BlockEvent
import net.neoforged.neoforge.event.tick.PlayerTickEvent
import java.util.UUID

/**
 * Turns game events into quest progress, with the anti-cheat rules from the site: blocks placed by players and mobs
 * from spawners don't count, nor do blocks broken by machines (fake players). Blocks made by fluids count.
 */
object Counting {
    private const val FROM_SPAWNER = "lnbquests_from_spawner"

    /** Distance moves faster than this per second (blocks) are teleports, not travel. */
    private const val MAX_SPEED = 80.0

    private val lastPosition = HashMap<UUID, Pair<String, Vec3>>()
    private val travelled = HashMap<UUID, Double>()

    fun onPlace(event: BlockEvent.EntityPlaceEvent) {
        if (event.entity !is ServerPlayer) return
        if (Catalog.config.antiCheat.ignorePlacedBlocks) PlacedBlocks.mark(event.level, event.pos)
    }

    fun onBreak(event: BlockEvent.BreakEvent) {
        val player = event.player as? ServerPlayer ?: return
        val placed = PlacedBlocks.take(event.level, event.pos)
        if (player is FakePlayer) return
        if (placed && Catalog.config.antiCheat.ignorePlacedBlocks) return
        val holder = event.state.blockHolder
        Progress.add(player, "mine", 1) { target -> Targets.matches(target, holder, Targets.BLOCKS) }
    }

    fun onSpawn(event: FinalizeSpawnEvent) {
        if (event.spawnType == MobSpawnType.SPAWNER || event.spawnType == MobSpawnType.TRIAL_SPAWNER) {
            event.entity.persistentData.putBoolean(FROM_SPAWNER, true)
        }
    }

    fun onDeath(event: LivingDeathEvent) {
        val player = event.source.entity as? ServerPlayer ?: return
        val mob = event.entity
        if (mob is ServerPlayer) return
        if (Catalog.config.antiCheat.ignoreSpawnerMobs && mob.persistentData.getBoolean(FROM_SPAWNER)) return
        val holder = mob.type.builtInRegistryHolder()
        Progress.add(player, "kill", 1) { target -> Targets.matches(target, holder, Targets.ENTITIES) }
    }

    fun onFished(event: ItemFishedEvent) {
        val player = event.entity as? ServerPlayer ?: return
        event.drops.forEach { stack -> countItem(player, "fish", stack) }
    }

    fun onCrafted(event: PlayerEvent.ItemCraftedEvent) {
        val player = event.entity as? ServerPlayer ?: return
        countItem(player, "craft", event.crafting)
    }

    fun onSmelted(event: PlayerEvent.ItemSmeltedEvent) {
        val player = event.entity as? ServerPlayer ?: return
        countItem(player, "craft", event.smelting)
    }

    private fun countItem(player: ServerPlayer, type: String, stack: ItemStack) {
        if (stack.isEmpty) return
        val holder: Holder<Item> = stack.itemHolder
        Progress.add(player, type, stack.count.toLong()) { target -> Targets.matches(target, holder, Targets.ITEMS) }
    }

    /** Teleports (commands, ender pearls, chorus fruit, portals...) don't count as travel. */
    fun onTeleport(event: EntityTeleportEvent) {
        if (event.entity is ServerPlayer) lastPosition.remove(event.entity.uuid)
    }

    /** Every second: the distance since the last second, in whole blocks once a block is covered. */
    fun onPlayerTick(event: PlayerTickEvent.Post) {
        val player = event.entity as? ServerPlayer ?: return
        if (player.tickCount % 20 != 0) return
        val dimension = player.level().dimension().location().toString()
        val here = player.position()
        val last = lastPosition.put(player.uuid, dimension to here)
        if (last == null || last.first != dimension || player.isSpectator) return
        val distance = last.second.distanceTo(here)
        if (distance <= 0.01 || distance > MAX_SPEED) return
        val total = (travelled[player.uuid] ?: 0.0) + distance
        val whole = total.toLong()
        travelled[player.uuid] = total - whole
        if (whole > 0) Progress.add(player, "travel", whole) { true }
    }

    fun onLoggedOut(player: ServerPlayer) {
        lastPosition.remove(player.uuid)
        travelled.remove(player.uuid)
    }
}
