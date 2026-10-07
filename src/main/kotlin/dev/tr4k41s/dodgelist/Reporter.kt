package dev.tr4k41s.dodgelist

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.ChatFormatting
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

object Reporter {
    // The code from /link tells the bot which Discord account the report is from.
    fun report(category: String, ign: String, reason: String) {
        val body = JsonObject().apply {
            addProperty("category", category)
            addProperty("ign", ign)
            addProperty("reason", reason)
        }
        val request = HttpRequest.newBuilder(URI.create(Config.reportUrl))
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer ${Config.code}")
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
