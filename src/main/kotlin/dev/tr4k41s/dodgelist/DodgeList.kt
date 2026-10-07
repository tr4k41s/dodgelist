package dev.tr4k41s.dodgelist

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object DodgeList : ClientModInitializer {
    val log: Logger = LoggerFactory.getLogger("dodgelist")

    override fun onInitializeClient() {
        Config.load()
        ListStore.start()

        ClientPlayConnectionEvents.JOIN.register { _, _, _ ->
            PartyWatcher.reset()
            ListStore.refresh()
        }
        ClientReceiveMessageEvents.GAME.register { message, overlay ->
            if (!overlay) PartyWatcher.onMessage(message.string)
        }
    }
}
