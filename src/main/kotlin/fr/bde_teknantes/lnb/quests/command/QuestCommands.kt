package fr.bde_teknantes.lnb.quests.command

import com.google.gson.JsonObject
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import fr.bde_teknantes.lnb.quests.catalog.Catalog
import fr.bde_teknantes.lnb.quests.flash.FlashRaces
import fr.bde_teknantes.lnb.quests.journal.QuestJournal
import fr.bde_teknantes.lnb.quests.network.QuestScreens
import fr.bde_teknantes.lnb.quests.progress.Deliveries
import fr.bde_teknantes.lnb.quests.LnbQuests
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.NbtOps
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.neoforged.neoforge.event.RegisterCommandsEvent

/**
 * `/quests` (and `/quetes`): opens the quest screen. Players: `deliver <quest>`. Operators: `flash start|stop`,
 * `sync`, `status`, and `reward copy`, which copies the held item as a quest reward, ready to paste in the site's
 * quest editor.
 */
object QuestCommands {
    fun register(event: RegisterCommandsEvent) {
        val dispatcher = event.dispatcher
        val root = dispatcher.register(build("quests"))
        dispatcher.register(Commands.literal("quetes").redirect(root))
    }

    private fun build(name: String) = Commands.literal(name)
        .executes { context -> context.source.playerOrException.let(QuestScreens::open); 1 }
        .then(
            Commands.literal("deliver").then(
                Commands.argument("quest", StringArgumentType.word())
                    .suggests { _, builder -> SharedSuggestionProvider.suggest(Catalog.quests.filter { it.objective.type == "deliver" }.map { it.id }, builder) }
                    .executes { context ->
                        Deliveries.deliver(context.source.playerOrException, StringArgumentType.getString(context, "quest")).toInt()
                    },
            ),
        )
        .then(
            Commands.literal("flash").requires { it.hasPermission(2) }
                .then(
                    Commands.literal("start").then(
                        Commands.argument("quest", StringArgumentType.word())
                            .suggests { _, builder -> SharedSuggestionProvider.suggest(Catalog.quests.filter { it.isFlash }.map { it.id }, builder) }
                            .executes { startFlash(it, null) }
                            .then(Commands.argument("quantity", IntegerArgumentType.integer(1)).executes { startFlash(it, IntegerArgumentType.getInteger(it, "quantity")) }),
                    ),
                )
                .then(
                    Commands.literal("stop").executes { context ->
                        val stopped = FlashRaces.stop(context.source.server)
                        context.source.sendSuccess({ Component.translatable(if (stopped) "command.lnbquests.flash.stopped" else "command.lnbquests.flash.none") }, true)
                        if (stopped) 1 else 0
                    },
                ),
        )
        .then(
            Commands.literal("sync").requires { it.hasPermission(2) }.executes { context ->
                LnbQuests.sync(context.source.server)
                context.source.sendSuccess({ Component.translatable("command.lnbquests.sync") }, true)
                1
            },
        )
        .then(
            Commands.literal("status").requires { it.hasPermission(2) }.executes { context ->
                val config = Catalog.config
                context.source.sendSuccess({
                    Component.translatable(
                        "command.lnbquests.status",
                        config.season.number,
                        Catalog.quests.size,
                        if (Catalog.fromSite) Component.translatable("command.lnbquests.status.site") else Component.translatable("command.lnbquests.status.local"),
                        QuestJournal.pending.size,
                        "%.3f".format(config.index.kGlobal),
                    )
                }, false)
                1
            },
        )
        .then(
            Commands.literal("reward").requires { it.hasPermission(2) }.then(
                Commands.literal("copy").executes { context -> copyReward(context) },
            ),
        )

    private fun startFlash(context: CommandContext<CommandSourceStack>, quantity: Int?): Int {
        val id = StringArgumentType.getString(context, "quest")
        val quest = Catalog.quest(id)?.takeIf { it.isFlash }
        if (quest == null) {
            context.source.sendFailure(Component.translatable("command.lnbquests.unknown", id))
            return 0
        }
        if (!FlashRaces.start(context.source.server, quest, quantity)) {
            context.source.sendFailure(Component.translatable("command.lnbquests.flash.running"))
            return 0
        }
        return 1
    }

    /** The held stack as `{ id, snbt, name, count }`, copied to the clipboard by a click. */
    private fun copyReward(context: CommandContext<CommandSourceStack>): Int {
        val player = context.source.playerOrException
        val stack = player.mainHandItem
        if (stack.isEmpty) {
            context.source.sendFailure(Component.translatable("command.lnbquests.reward.empty"))
            return 0
        }
        val tag = stack.save(player.registryAccess())
        val json = JsonObject().apply {
            addProperty("id", BuiltInRegistries.ITEM.getKey(stack.item).toString())
            addProperty("snbt", tag.toString())
            addProperty("name", stack.hoverName.string)
            addProperty("count", stack.count)
        }.toString()
        context.source.sendSuccess({
            Component.translatable("command.lnbquests.reward.copy").withStyle { style ->
                style.withColor(ChatFormatting.AQUA).withUnderlined(true)
                    .withClickEvent(ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, json))
                    .withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(json.take(200))))
            }
        }, false)
        return 1
    }
}
