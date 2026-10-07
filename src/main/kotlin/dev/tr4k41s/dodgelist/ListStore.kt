package dev.tr4k41s.dodgelist

import com.google.gson.JsonParser
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

data class Entry(val uuid: UUID, val name: String, val reason: String)

object ListStore {
    val http: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()

    @Volatile
    private var entries: Map<UUID, Entry> = emptyMap()

    private val scheduler = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "DodgeList refresh").apply { isDaemon = true }
    }

    fun start() {
        scheduler.scheduleAtFixedRate(::refresh, 0, 5, TimeUnit.MINUTES)
    }

    operator fun get(uuid: UUID): Entry? = entries[uuid]

    fun refresh() {
        val request = HttpRequest.newBuilder(URI.create(Config.url))
            .timeout(Duration.ofSeconds(15))
            .header("User-Agent", "dodgelist")
            .GET()
            .build()
        http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenAccept { response ->
                if (response.statusCode() == 200) {
                    entries = parse(response.body())
                } else {
                    DodgeList.log.warn("Dodge list request returned {}", response.statusCode())
                }
            }
            .exceptionally { e ->
                DodgeList.log.warn("Couldn't fetch the dodge list: {}", e.message)
                null
            }
    }

    private fun parse(body: String): Map<UUID, Entry> =
        JsonParser.parseString(body).asJsonObject.getAsJsonArray("players").mapNotNull { element ->
            val player = element.asJsonObject
            val uuid = parseUuid(player.get("uuid")?.asString) ?: return@mapNotNull null
            Entry(uuid, player.get("name")?.asString.orEmpty(), player.get("reason")?.asString.orEmpty())
        }.associateBy { it.uuid }
}

fun parseUuid(raw: String?): UUID? {
    val hex = raw?.replace("-", "") ?: return null
    if (hex.length != 32) return null
    return runCatching {
        UUID(java.lang.Long.parseUnsignedLong(hex.substring(0, 16), 16), java.lang.Long.parseUnsignedLong(hex.substring(16), 16))
    }.getOrNull()
}
