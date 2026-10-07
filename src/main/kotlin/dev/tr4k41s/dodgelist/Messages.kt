package dev.tr4k41s.dodgelist

import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent

object Messages {
    fun prefix(): MutableComponent =
        Component.literal("[DodgeList] ").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)

    fun send(text: String, color: ChatFormatting = ChatFormatting.GRAY) =
        send(Component.literal(text).withStyle(color))

    fun send(component: Component) {
        val mc = Minecraft.getInstance()
        mc.execute { mc.gui.chat.addClientSystemMessage(Component.empty().append(prefix()).append(component)) }
    }
}
