package dev.tr4k41s.dodgelist

import com.google.gson.JsonParser
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

data class Entry(val uuid: UUID, val name: String, val reason: String, val category: String, val share: Boolean) {
    val label: String get() = category.uppercase()
}

object ListStore {
    val http: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()

    @Volatile
    private var entries: Map<UUID, List<Entry>> = emptyMap()

    val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "DodgeList").apply { isDaemon = true }
    }

    fun start() {
        scheduler.scheduleAtFixedRate({ refresh() }, 0, 5, TimeUnit.MINUTES)
    }

    operator fun get(uuid: UUID): List<Entry> = entries[uuid].orEmpty()

    fun inCategory(category: String): List<Entry> = entries.values.flatten().filter { it.category == category }

    fun refresh(): CompletableFuture<Boolean> {
        val request = HttpRequest.newBuilder(URI.create(Config.listUrl))
            .timeout(Duration.ofSeconds(15))
            .header("User-Agent", "dodgelist")
            .GET()
            .build()
        return http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply { response ->
                if (response.statusCode() == 200) {
                    entries = parse(response.body())
                    true
                } else {
                    DodgeList.log.warn("Dodge list request returned {}", response.statusCode())
                    false
                }
            }
            .exceptionally { e ->
                DodgeList.log.warn("Couldn't fetch the dodge list: {}", e.message)
                false
            }
    }

    private fun parse(body: String): Map<UUID, List<Entry>> =
        JsonParser.parseString(body).asJsonObject.getAsJsonArray("players").mapNotNull { element ->
            val player = element.asJsonObject
            val uuid = parseUuid(player.get("uuid")?.asString) ?: return@mapNotNull null
            Entry(
                uuid,
                player.get("name")?.asString.orEmpty(),
                player.get("reason")?.asString.orEmpty(),
                player.get("category")?.asString ?: "f7",
                player.get("share")?.asBoolean ?: false,
            )
        }.groupBy { it.uuid }
}

fun parseUuid(raw: String?): UUID? {
    val hex = raw?.replace("-", "") ?: return null
    if (hex.length != 32) return null
    return runCatching {
        UUID(java.lang.Long.parseUnsignedLong(hex.substring(0, 16), 16), java.lang.Long.parseUnsignedLong(hex.substring(16), 16))
    }.getOrNull()
}
