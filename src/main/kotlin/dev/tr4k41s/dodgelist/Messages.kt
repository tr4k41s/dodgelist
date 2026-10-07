package dev.tr4k41s.dodgelist

import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.MutableComponent
import kotlin.math.PI
import kotlin.math.sin

object Messages {
    // Text carrying this exact hover event is recoloured every frame by PreparedTextBuilderMixin.
    @JvmField
    val WAVE: HoverEvent = HoverEvent.ShowText(Component.literal("DodgeList"))

    private const val DARK = 0x555555
    private const val LIGHT = 0xDDDDDD

    @JvmStatic
    fun waveColor(index: Int): Int {
        val time = System.currentTimeMillis() / 1000.0
        val t = (sin(index * 0.5 - time * 2 * PI * 0.6) + 1) / 2
        fun channel(shift: Int): Int {
            val a = (DARK shr shift) and 0xFF
            val b = (LIGHT shr shift) and 0xFF
            return (a + (b - a) * t).toInt()
        }
        return (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    fun prefix(): MutableComponent =
        Component.literal("[DodgeList] ").withStyle { it.withColor(ChatFormatting.DARK_GRAY).withBold(true).withHoverEvent(WAVE) }

    fun send(text: String, color: ChatFormatting = ChatFormatting.GRAY) =
        send(Component.literal(text).withStyle(color))

    fun send(component: Component) {
        val mc = Minecraft.getInstance()
        mc.execute { mc.gui.chat.addClientSystemMessage(Component.empty().append(prefix()).append(component)) }
    }
}
