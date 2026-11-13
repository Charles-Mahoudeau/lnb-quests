package fr.bde_teknantes.lnb.quests.progress

import com.mojang.serialization.Codec
import fr.bde_teknantes.lnb.quests.LnbQuests
import net.minecraft.core.BlockPos
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.chunk.ChunkAccess
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.attachment.AttachmentType
import net.neoforged.neoforge.registries.DeferredRegister
import net.neoforged.neoforge.registries.NeoForgeRegistries
import java.util.function.Supplier

/**
 * The blocks players placed, per chunk, so mining them back doesn't count (anti-cheat `ignorePlacedBlocks`). Blocks
 * made by fluids (stone and cobblestone generators) are never marked, so they count. A mark goes when its block
 * breaks.
 */
object PlacedBlocks {
    private val TYPES = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, LnbQuests.ID)

    private val PLACED: Supplier<AttachmentType<MutableSet<Long>>> = TYPES.register("placed_blocks", Supplier {
        AttachmentType.builder(Supplier<MutableSet<Long>> { HashSet() })
            .serialize(Codec.LONG.listOf().xmap({ it.toMutableSet() }, { it.toList() }))
            .build()
    })

    fun register(modBus: IEventBus) = TYPES.register(modBus)

    private fun chunk(level: LevelAccessor, pos: BlockPos): ChunkAccess? =
        level.getChunk(pos.x shr 4, pos.z shr 4, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false)

    fun mark(level: LevelAccessor, pos: BlockPos) {
        val chunk = chunk(level, pos) ?: return
        chunk.getData(PLACED).add(pos.asLong())
        chunk.isUnsaved = true
    }

    /** Whether a player placed the block at [pos]; forgets it, since the block is going. */
    fun take(level: LevelAccessor, pos: BlockPos): Boolean {
        val chunk = chunk(level, pos) ?: return false
        if (!chunk.hasData(PLACED)) return false
        val removed = chunk.getData(PLACED).remove(pos.asLong())
        if (removed) chunk.isUnsaved = true
        return removed
    }
}
