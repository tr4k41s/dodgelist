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

    private val invited = Regex("""^$NAME invited $NAME to the party! They have 60 seconds to accept\.$""")
    private val joinedParty = Regex("""^$NAME joined the party\.$""")
    private val joinedPartyFinder = Regex("""^Party Finder > $NAME joined the (?:dungeon )?group!.*$""")
    private val youJoined = Regex("""^You have joined $NAME's party!$""")
    private val nameLists = listOf(
        Regex("""^You'll be partying with: (.+)$"""),
        Regex("""^Party (?:Leader|Moderators|Members): (.+)$"""),
    )
    private val listedName = Regex("""^$NAME$""")
    // One member per line in /p list: "● [MVP+] Name (Leader)"
    private val memberLine = Regex("""^[●•] $NAME(?: \((?:Leader|Moderator)\))?$""")
    private val partyEnded = listOf(
        Regex("""^You left the party\.$"""),
        Regex("""^You have been kicked from the party by .+$"""),
        Regex("""^You are not currently in a party\.$"""),
        Regex("""^.*disbanded the party!$"""),
        Regex("""^The party was disbanded.*$"""),
    )

    private const val INVITE_VALID_MS = 70 * 1000L
    private val invites = ConcurrentHashMap<String, Long>()

    // Dodged players already announced for the current party.
    private val warned = ConcurrentHashMap.newKeySet<UUID>()

    fun reset() {
        warned.clear()
        invites.clear()
    }

    // Hypixel wraps party messages in separator lines, all inside one chat message.
    fun onMessage(text: String) {
        for (line in text.lines()) {
            val trimmed = line.trim()
            if (trimmed.isNotEmpty()) onLine(trimmed)
        }
    }

    private fun onLine(line: String) {
        if (partyEnded.any { it.matches(line) }) {
            reset()
            return
        }
        invited.matchEntire(line)?.let {
            invites[it.groupValues[2].lowercase()] = System.currentTimeMillis()
            return
        }
        joinedPartyFinder.matchEntire(line)?.let {
            if (isSelf(it.groupValues[1])) joinedNewParty() else check(it.groupValues[1], uninvited = true)
            return
        }
        joinedParty.matchEntire(line)?.let {
            val name = it.groupValues[1]
            val invitedAt = invites.remove(name.lowercase())
            check(name, uninvited = invitedAt == null || System.currentTimeMillis() - invitedAt > INVITE_VALID_MS)
            return
        }
        youJoined.matchEntire(line)?.let {
            joinedNewParty()
            check(it.groupValues[1], uninvited = false)
            return
        }
        memberLine.matchEntire(line)?.let {
            check(it.groupValues[1], uninvited = false)
            return
        }
        nameLists.firstNotNullOfOrNull { it.matchEntire(line) }?.let { match ->
            match.groupValues[1].split('●', ',')
                .mapNotNull { listedName.matchEntire(it.trim())?.groupValues?.get(1) }
                .forEach { check(it, uninvited = false) }
        }
    }

    private fun isSelf(name: String) = name.equals(Minecraft.getInstance().user.name, ignoreCase = true)

    // Joining someone else's party doesn't announce who's already in it, so ask for the list.
    private fun joinedNewParty() {
        reset()
        ListStore.scheduler.schedule({
            Minecraft.getInstance().execute { Minecraft.getInstance().connection?.sendCommand("p list") }
        }, 1, TimeUnit.SECONDS)
    }

    // Only players who got in without an invite (Party Finder, open party) are auto-kicked.
    private fun check(name: String, uninvited: Boolean) {
        if (isSelf(name)) return
        Profiles.resolve(name).thenAccept { uuid ->
            val id = uuid ?: return@thenAccept
            val entries = ListStore[id]
            if (entries.isEmpty()) return@thenAccept

            val kick = uninvited && Config.autokick && (Config.autokickShares || entries.any { !it.share })
            if (kick) {
                ListStore.scheduler.schedule({
                    Minecraft.getInstance().execute { Minecraft.getInstance().connection?.sendCommand("p kick $name") }
                }, 500, TimeUnit.MILLISECONDS)
            }

            val firstTime = warned.add(id)
            Minecraft.getInstance().execute {
                when {
                    firstTime -> warn(name, entries, kick, invitedWhileAutokick = !uninvited && Config.autokick)
                    kick -> Messages.send("Kicked $name again (dodge list).", ChatFormatting.GREEN)
                }
            }
        }
    }

    private fun warn(name: String, entries: List<Entry>, kicked: Boolean, invitedWhileAutokick: Boolean) {
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
        when {
            kicked -> message.append(Component.literal("\nKicking them from the party.").withStyle(ChatFormatting.GREEN))
            invitedWhileAutokick -> message.append(
                Component.literal("\nThey were invited, so they weren't kicked.").withStyle(ChatFormatting.GRAY)
            )
        }
        mc.gui.chat.addClientSystemMessage(message)

        val subtitle = if (onlyShares) "account share (${entries.joinToString(", ") { it.label }})"
        else "on the dodge list ($lists)"
        mc.gui.setTitle(Component.literal("⚠ $name").withStyle(color))
        mc.gui.setSubtitle(Component.literal(subtitle).withStyle(ChatFormatting.GRAY))
        mc.soundManager.play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING, 0.5f))
    }
}
