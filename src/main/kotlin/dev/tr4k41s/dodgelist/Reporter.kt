package dev.tr4k41s.dodgelist

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

object Reporter {
    // The bot files the report under the Discord account verified with this username.
    fun report(category: String, ign: String, reason: String) {
        val body = JsonObject().apply {
            addProperty("category", category)
            addProperty("ign", ign)
            addProperty("reason", reason)
            addProperty("reporter", Minecraft.getInstance().user.name)
        }
        val request = HttpRequest.newBuilder(URI.create(Config.reportUrl))
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
            .build()

        Messages.send("Sending report for $ign...")
        ListStore.http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
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
