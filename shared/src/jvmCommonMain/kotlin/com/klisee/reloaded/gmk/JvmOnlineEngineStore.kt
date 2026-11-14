package com.klisee.reloaded.gmk

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

class JvmOnlineEngineStore(private val gameDir: File) : OnlineEngineStore {
    private val file get() = File(gameDir, "reloaded-online.json")
    private val json = Json { prettyPrint = true }

    override fun load(): OnlineEngineConfig {
        val text = runCatching { file.readText() }.getOrNull()
            ?: return OnlineEngineConfig().normalized()
        return runCatching {
            val obj = Json.parseToJsonElement(text).jsonObject
            val bridge = obj["bridge_upstream"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
            val rawProtocol = obj["protocol"]?.jsonPrimitive?.contentOrNull
                ?: obj["mode"]?.jsonPrimitive?.contentOrNull
                ?: if (bridge.isNotBlank()) OnlineEngineConfig.PROTOCOL_ZIBELAN else OnlineEngineConfig.PROTOCOL_RELAY
            val protocol = OnlineEngineConfig.normalizeProtocol(rawProtocol)
            val rawServer = obj["server"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            val server = when {
                protocol == OnlineEngineConfig.PROTOCOL_ZIBELAN &&
                    (rawServer.isBlank() || rawServer == OnlineEngineConfig.DEFAULT_RELAY_SERVER) ->
                    bridge.ifBlank { OnlineEngineConfig.DEFAULT_ZIBELAN_SERVER }
                rawServer.isNotBlank() -> rawServer
                else -> OnlineEngineConfig.defaultServer(protocol)
            }
            OnlineEngineConfig(
                enabled = obj["enabled"]?.jsonPrimitive?.booleanOrNull ?: false,
                protocol = protocol,
                mode = protocol,
                server = server,
                name = obj["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: "Jugador",
                room = obj["room"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: "mundo",
                bridgeUpstream = bridge,
            ).normalized()
        }.getOrElse { OnlineEngineConfig().normalized() }
    }

    override fun save(config: OnlineEngineConfig): Result<Unit> = runCatching {
        val normalized = config.normalized()
        gameDir.mkdirs()
        val existing = runCatching { Json.parseToJsonElement(file.readText()).jsonObject }.getOrNull()
        val data = buildJsonObject {
            existing?.forEach { (key, value) ->
                if (key !in KNOWN_KEYS) put(key, value)
            }
            put("enabled", JsonPrimitive(normalized.enabled))
            put("protocol", JsonPrimitive(normalized.protocol))
            put("mode", JsonPrimitive(normalized.protocol))
            put("server", JsonPrimitive(normalized.server))
            put("name", JsonPrimitive(normalized.name))
            put("room", JsonPrimitive(normalized.room))
            put("bridge_upstream", JsonPrimitive(normalized.bridgeUpstream))
        }
        file.writeText(json.encodeToString(JsonObject.serializer(), data) + "\n")
    }

    private companion object {
        val KNOWN_KEYS = setOf(
            "enabled",
            "protocol",
            "mode",
            "server",
            "name",
            "room",
            "bridge_upstream",
        )
    }
}
