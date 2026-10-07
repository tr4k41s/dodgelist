package dev.tr4k41s.dodgelist

import com.mojang.brigadier.arguments.StringArgumentType
import net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument
import net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import java.util.concurrent.CompletableFuture

object Commands {
    private val CATEGORIES = listOf("f7", "m7")

    fun register() {
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            val root = literal("dodgelist").executes { help(); 1 }

            for (category in CATEGORIES) {
                root.then(literal(category).executes { showList(category); 1 })
            }
            root.then(literal("reload").executes { reload(); 1 })
            root.then(
                literal("link").then(argument("code", StringArgumentType.word()).executes { ctx ->
                    Config.code = StringArgumentType.getString(ctx, "code")
                    Messages.send("Linked. Your reports will be filed under your Discord account.", ChatFormatting.GREEN)
                    1
                })
            )
            root.then(
                literal("autokick")
                    .executes {
                        Config.autokick = !Config.autokick
                        Messages.send("Autokick ${onOff(Config.autokick)}.")
                        1
                    }
                    .then(literal("share").executes {
                        Config.autokickShares = !Config.autokickShares
                        Messages.send("Autokicking account shares ${onOff(Config.autokickShares)}.")
                        1
                    })
            )

            val report = literal("report")
            for (category in CATEGORIES) {
                report.then(
                    literal(category).then(
                        argument("ign", StringArgumentType.word()).then(
                            argument("reason", StringArgumentType.greedyString()).executes { ctx ->
                                val ign = StringArgumentType.getString(ctx, "ign")
                                if (!Regex("""\w{1,16}""").matches(ign)) {
                                    Messages.send("$ign isn't a valid username.", ChatFormatting.RED)
                                } else if (Config.code.isBlank()) {
                                    Messages.send("Link the mod first: run /link in the Discord.", ChatFormatting.RED)
                                } else {
                                    Reporter.report(category, ign, StringArgumentType.getString(ctx, "reason"))
                                }
                                1
                            }
                        )
                    )
                )
            }
            root.then(report)

            dispatcher.register(root)
        }
    }

    private fun onOff(value: Boolean) = if (value) "on" else "off"

    private fun help() {
        val lines = listOf(
            "/dodgelist f7 | m7" to "show a list",
            "/dodgelist reload" to "download the list again",
            "/dodgelist autokick" to "kick listed players who join your party (${onOff(Config.autokick)})",
            "/dodgelist autokick share" to "also kick account shares (${onOff(Config.autokickShares)})",
            "/dodgelist report <f7|m7> <ign> <reason>" to "report a player",
            "/dodgelist link <code>" to "link your Discord (get a code with /link in the Discord)",
        )
        val message = Component.literal("Commands:").withStyle(ChatFormatting.GRAY)
        for ((command, description) in lines) {
            message.append(Component.literal("\n$command").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" - $description").withStyle(ChatFormatting.GRAY))
        }
        Messages.send(message)
    }

    private fun reload() {
        ListStore.refresh().thenAccept { ok ->
            if (ok) Messages.send("Dodge list reloaded.", ChatFormatting.GREEN)
            else Messages.send("Couldn't download the dodge list.", ChatFormatting.RED)
        }
    }

    private fun showList(category: String) {
        val label = category.uppercase()
        val entries = ListStore.inCategory(category)
        if (entries.isEmpty()) {
            Messages.send("The $label list is empty.")
            return
        }
        val names = entries.map { Profiles.currentName(it.uuid, it.name) }
        CompletableFuture.allOf(*names.toTypedArray()).thenRun {
            val rows = entries.zip(names.map { it.join() }).sortedBy { it.second.lowercase() }
            val message = Component.literal("$label dodge list (${rows.size}):").withStyle(ChatFormatting.GRAY)
            for ((entry, name) in rows) {
                message.append(Component.literal("\n• ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal(name).withStyle(if (entry.share) ChatFormatting.YELLOW else ChatFormatting.RED))
                if (entry.share) message.append(Component.literal(" (account share)").withStyle(ChatFormatting.YELLOW))
                if (entry.reason.isNotBlank()) {
                    message.append(Component.literal(" - ${entry.reason.take(80)}").withStyle(ChatFormatting.GRAY))
                }
            }
            Messages.send(message)
        }
    }
}
