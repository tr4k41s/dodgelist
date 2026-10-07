package dev.tr4k41s.dodgelist

import com.google.gson.JsonParser
import net.minecraft.client.Minecraft
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Optional
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

object Profiles {
    private val cache = ConcurrentHashMap<String, Optional<UUID>>()
    private val names = ConcurrentHashMap<UUID, String>()

    fun currentName(uuid: UUID, fallback: String): CompletableFuture<String> {
        names[uuid]?.let { return CompletableFuture.completedFuture(it) }
        val id = uuid.toString().replace("-", "")
        val request = HttpRequest.newBuilder(URI.create("https://sessionserver.mojang.com/session/minecraft/profile/$id"))
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build()
        return ListStore.http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply { response ->
                if (response.statusCode() != 200) return@thenApply fallback
                val name = JsonParser.parseString(response.body()).asJsonObject.get("name")?.asString ?: fallback
                names[uuid] = name
                name
            }
            .exceptionally { fallback }
    }

    // Must be called on the client thread, since it reads the tab list.
    fun resolve(name: String): CompletableFuture<UUID?> {
        val key = name.lowercase()
        cache[key]?.let { return CompletableFuture.completedFuture(it.orElse(null)) }

        Minecraft.getInstance().connection?.getPlayerInfo(name)?.profile?.id()?.let { uuid ->
            if (uuid.version() == 4) {
                cache[key] = Optional.of(uuid)
                return CompletableFuture.completedFuture(uuid)
            }
        }

        val request = HttpRequest.newBuilder(URI.create("https://api.mojang.com/users/profiles/minecraft/$name"))
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build()
        return ListStore.http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply { response ->
                when (response.statusCode()) {
                    200 -> parseUuid(JsonParser.parseString(response.body()).asJsonObject.get("id")?.asString)
                        .also { cache[key] = Optional.ofNullable(it) }
                    204, 404 -> null.also { cache[key] = Optional.empty() }
                    else -> null
                }
            }
            .exceptionally { e ->
                DodgeList.log.warn("Couldn't look up {}: {}", name, e.message)
                null
            }
    }
}
