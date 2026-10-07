package dev.tr4k41s.dodgelist

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Files

object Config {
    private const val DEFAULT_URL = "https://ticket-bot.tr4k41s.workers.dev"
    private val file = FabricLoader.getInstance().configDir.resolve("dodgelist.json")

    var url = DEFAULT_URL
        private set
    var autokick = false
        set(value) { field = value; save() }
    var autokickShares = false
        set(value) { field = value; save() }
    var code = ""
        set(value) { field = value; save() }

    val listUrl get() = "$url/dodgelist"
    val reportUrl get() = "$url/report"

    fun load() {
        try {
            if (Files.exists(file)) {
                val json = JsonParser.parseString(Files.readString(file)).asJsonObject
                json.get("url")?.asString?.let { url = it.removeSuffix("/").removeSuffix("/dodgelist") }
                json.get("autokick")?.asBoolean?.let { autokick = it }
                json.get("autokickShares")?.asBoolean?.let { autokickShares = it }
                json.get("code")?.asString?.let { code = it }
            }
            save()
        } catch (e: Exception) {
            DodgeList.log.warn("Couldn't read {}", file, e)
        }
    }

    private fun save() {
        val json = JsonObject().apply {
            addProperty("url", url)
            addProperty("autokick", autokick)
            addProperty("autokickShares", autokickShares)
            addProperty("code", code)
        }
        try {
            Files.writeString(file, GsonBuilder().setPrettyPrinting().create().toJson(json))
        } catch (e: Exception) {
            DodgeList.log.warn("Couldn't save {}", file, e)
        }
    }
}
