package dev.tr4k41s.dodgelist

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Files

object Config {
    private const val DEFAULT_URL = "https://ticket-bot.tr4k41s.workers.dev/dodgelist"

    var url = DEFAULT_URL
        private set

    fun load() {
        val file = FabricLoader.getInstance().configDir.resolve("dodgelist.json")
        try {
            if (Files.exists(file)) {
                JsonParser.parseString(Files.readString(file)).asJsonObject.get("url")?.asString?.let { url = it }
            } else {
                val json = JsonObject().apply { addProperty("url", url) }
                Files.writeString(file, GsonBuilder().setPrettyPrinting().create().toJson(json))
            }
        } catch (e: Exception) {
            DodgeList.log.warn("Couldn't read {}", file, e)
        }
    }
}
