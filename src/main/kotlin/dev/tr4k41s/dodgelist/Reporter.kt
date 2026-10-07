package dev.tr4k41s.dodgelist

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.SecureRandom
import java.time.Duration
import java.util.HexFormat
import java.util.concurrent.CompletableFuture

object Reporter {
    private val random = SecureRandom()

    // The bot verifies who sent the report by asking Mojang whether this account just
    // "joined" serverId, the same check a Minecraft server does when you log in.
    fun report(category: String, ign: String, reason: String) {
        val mc = Minecraft.getInstance()
        val user = mc.user
        val serverId = HexFormat.of().formatHex(ByteArray(20).also(random::nextBytes))

        Messages.send("Sending report for $ign...")
        CompletableFuture
            .runAsync {
                mc.services().sessionService().joinServer(user.profileId, user.accessToken, serverId)
            }
            .thenCompose {
                val body = JsonObject().apply {
                    addProperty("category", category)
                    addProperty("ign", ign)
                    addProperty("reason", reason)
                    addProperty("reporter", user.name)
                    addProperty("serverId", serverId)
                }
                val request = HttpRequest.newBuilder(URI.create(Config.reportUrl))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build()
                ListStore.http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            }
            .thenAccept { response ->
                val message = runCatching {
                    JsonParser.parseString(response.body()).asJsonObject.get("message").asString
                }.getOrDefault("Report failed (${response.statusCode()}).")
                Messages.send(message, if (response.statusCode() == 200) ChatFormatting.GREEN else ChatFormatting.RED)
            }
            .exceptionally { e ->
                DodgeList.log.warn("Report failed", e)
                Messages.send("Report failed: ${e.cause?.message ?: e.message}", ChatFormatting.RED)
                null
            }
    }
}
