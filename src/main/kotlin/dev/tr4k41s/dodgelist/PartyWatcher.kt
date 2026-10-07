package dev.tr4k41s.dodgelist

import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object PartyWatcher {
    private const val NAME = """(?:\[[^\]]+\] )?(\w{1,16})"""

    private val singleName = listOf(
        Regex("""^Party Finder > $NAME joined the (?:dungeon )?group!.*$"""),
        Regex("""^$NAME joined the party\.$"""),
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

    fun onMessage(text: String) {
        val names = mutableListOf<String>()
        singleName.firstNotNullOfOrNull { it.matchEntire(text) }?.let { names += it.groupValues[1] }
        nameLists.firstNotNullOfOrNull { it.matchEntire(text) }?.let { match ->
            match.groupValues[1].split('●', ',')
                .mapNotNull { listedName.matchEntire(it.trim())?.groupValues?.get(1) }
                .forEach { names += it }
        }

        val self = Minecraft.getInstance().user.name
        names.filterNot { it.equals(self, ignoreCase = true) }.distinct().forEach(::check)
    }

    private fun check(name: String) {
        Profiles.resolve(name).thenAccept { uuid ->
            val id = uuid ?: return@thenAccept
            val entries = ListStore[id]
            if (entries.isEmpty()) return@thenAccept
            val now = System.currentTimeMillis()
            val last = warned[id]
            if (last != null && now - last < REPEAT_AFTER_MS) return@thenAccept
            warned[id] = now
            Minecraft.getInstance().execute { warn(name, entries) }
        }
    }

    private fun warn(name: String, entries: List<Entry>) {
        val mc = Minecraft.getInstance()
        val onlyShares = entries.all { it.share }
        val color = if (onlyShares) ChatFormatting.YELLOW else ChatFormatting.RED
        val lists = entries.joinToString(", ") { if (it.share) "${it.label} (account share)" else it.label }

        val message = Component.empty()
            .append(Component.literal("[DodgeList] ").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD))
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
        mc.gui.chat.addClientSystemMessage(message)

        val subtitle = if (onlyShares) "account share (${entries.joinToString(", ") { it.label }})"
        else "on the dodge list ($lists)"
        mc.gui.setTitle(Component.literal("⚠ $name").withStyle(color))
        mc.gui.setSubtitle(Component.literal(subtitle).withStyle(ChatFormatting.GRAY))
        mc.soundManager.play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING, 0.5f))
    }
}
