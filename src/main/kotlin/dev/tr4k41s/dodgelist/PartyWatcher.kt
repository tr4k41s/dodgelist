package dev.tr4k41s.dodgelist

import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object PartyWatcher {
    private const val NAME = """(?:\[[^\]]+\] )?(\w{1,16})"""

    // Someone joined the party you're in, so they can be kicked.
    private val joined = listOf(
        Regex("""^Party Finder > $NAME joined the (?:dungeon )?group!.*$"""),
        Regex("""^$NAME joined the party\.$"""),
    )
    // You joined someone else's party, or listed its members.
    private val alreadyIn = listOf(
        Regex("""^You have joined $NAME's party!$"""),
    )
    private val nameLists = listOf(
        Regex("""^You'll be partying with: (.+)$"""),
        Regex("""^Party (?:Leader|Moderators|Members): (.+)$"""),
    )
    private val listedName = Regex("""^$NAME$""")

    private const val REPEAT_AFTER_MS = 10 * 60 * 1000L
    private val warned = ConcurrentHashMap<UUID, Long>()

    fun reset() = warned.clear()

    // Hypixel wraps party messages in separator lines, all inside one chat message.
    fun onMessage(text: String) {
        for (line in text.lines()) {
            val trimmed = line.trim()
            if (trimmed.isNotEmpty()) onLine(trimmed)
        }
    }

    private fun onLine(line: String) {
        joined.firstNotNullOfOrNull { it.matchEntire(line) }?.let {
            check(it.groupValues[1], canKick = true)
            return
        }
        alreadyIn.firstNotNullOfOrNull { it.matchEntire(line) }?.let {
            check(it.groupValues[1], canKick = false)
            return
        }
        nameLists.firstNotNullOfOrNull { it.matchEntire(line) }?.let { match ->
            match.groupValues[1].split('●', ',')
                .mapNotNull { listedName.matchEntire(it.trim())?.groupValues?.get(1) }
                .forEach { check(it, canKick = false) }
        }
    }

    private fun check(name: String, canKick: Boolean) {
        if (name.equals(Minecraft.getInstance().user.name, ignoreCase = true)) return
        Profiles.resolve(name).thenAccept { uuid ->
            val id = uuid ?: return@thenAccept
            val entries = ListStore[id]
            if (entries.isEmpty()) return@thenAccept

            val kick = canKick && Config.autokick && (Config.autokickShares || entries.any { !it.share })
            if (kick) {
                ListStore.scheduler.schedule({
                    Minecraft.getInstance().execute { Minecraft.getInstance().connection?.sendCommand("p kick $name") }
                }, 500, TimeUnit.MILLISECONDS)
            }

            val now = System.currentTimeMillis()
            val last = warned[id]
            if (!kick && last != null && now - last < REPEAT_AFTER_MS) return@thenAccept
            warned[id] = now
            Minecraft.getInstance().execute { warn(name, entries, kick) }
        }
    }

    private fun warn(name: String, entries: List<Entry>, kicked: Boolean) {
        val mc = Minecraft.getInstance()
        val onlyShares = entries.all { it.share }
        val color = if (onlyShares) ChatFormatting.YELLOW else ChatFormatting.RED
        val lists = entries.joinToString(", ") { if (it.share) "${it.label} (account share)" else it.label }

        val message = Component.empty()
            .append(Messages.prefix())
            .append(Component.literal(name).withStyle(color, ChatFormatting.BOLD))
            .append(Component.literal(" is on the dodge list: $lists").withStyle(color))
        if (entries.any { it.share }) {
            message.append(
                Component.literal("\nAccount share: the person playing may not be the one who was reported.")
                    .withStyle(ChatFormatting.GOLD)
            )
        }
        for (entry in entries.filter { it.reason.isNotBlank() }) {
            val prefix = if (entries.size > 1) entry.label else "Reason"
            message.append(Component.literal("\n$prefix: ${entry.reason}").withStyle(ChatFormatting.GRAY))
        }
        if (kicked) message.append(Component.literal("\nKicking them from the party.").withStyle(ChatFormatting.GREEN))
        mc.gui.chat.addClientSystemMessage(message)

        val subtitle = if (onlyShares) "account share (${entries.joinToString(", ") { it.label }})"
        else "on the dodge list ($lists)"
        mc.gui.setTitle(Component.literal("⚠ $name").withStyle(color))
        mc.gui.setSubtitle(Component.literal(subtitle).withStyle(ChatFormatting.GRAY))
        mc.soundManager.play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING, 0.5f))
    }
}
