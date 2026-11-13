package fr.bde_teknantes.lnb.quests.catalog

import net.minecraft.core.Holder
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.TagKey

/**
 * Whether something counts for an objective's `target`: no target counts everything, `ns:path` one thing, and
 * `#ns:path` a tag.
 */
object Targets {
    fun <T> matches(target: String?, holder: Holder<T>, registry: ResourceKey<out net.minecraft.core.Registry<T>>): Boolean {
        if (target.isNullOrBlank()) return true
        if (target.startsWith("#")) {
            val id = ResourceLocation.tryParse(target.substring(1)) ?: return false
            return holder.`is`(TagKey.create(registry, id))
        }
        val id = ResourceLocation.tryParse(target) ?: return false
        return holder.`is`(id)
    }

    val BLOCKS = Registries.BLOCK
    val ENTITIES = Registries.ENTITY_TYPE
    val ITEMS = Registries.ITEM
}
