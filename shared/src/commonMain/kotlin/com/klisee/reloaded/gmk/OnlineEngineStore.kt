package com.klisee.reloaded.gmk

data class OnlineEngineConfig(
    val enabled: Boolean = false,
    val protocol: String = PROTOCOL_RELAY,
    val mode: String = protocol,
    val server: String = defaultServer(protocol),
    val name: String = "Jugador",
    val room: String = "mundo",
    val bridgeUpstream: String = "",
) {
    val isZibelan: Boolean
        get() = normalizeProtocol(protocol) == PROTOCOL_ZIBELAN

    fun withProtocol(value: String): OnlineEngineConfig {
        val next = normalizeProtocol(value)
        val previousDefault = defaultServer(protocol)
        val nextDefault = defaultServer(next)
        val nextServer = when {
            server.isBlank() -> nextDefault
            server.trim() == previousDefault -> nextDefault
            else -> server.trim()
        }
        return copy(
            protocol = next,
            mode = next,
            server = nextServer,
            bridgeUpstream = if (next == PROTOCOL_ZIBELAN) nextServer else "",
        )
    }

    fun normalized(): OnlineEngineConfig {
        val next = normalizeProtocol(protocol)
        val zibelan = next == PROTOCOL_ZIBELAN
        val fallback = defaultServer(next)
        val trimmedServer = server.trim()
        val trimmedBridge = bridgeUpstream.trim()
        val effectiveServer = when {
            zibelan && trimmedBridge.isNotBlank() &&
                (trimmedServer.isBlank() || trimmedServer == DEFAULT_RELAY_SERVER) -> trimmedBridge
            trimmedServer.isNotBlank() -> trimmedServer
            else -> fallback
        }
        return copy(
            protocol = next,
            mode = next,
            server = effectiveServer,
            name = name.trim().ifBlank { "Jugador" },
            room = room.trim().ifBlank { "mundo" },
            bridgeUpstream = if (zibelan) effectiveServer else "",
        )
    }

    companion object {
        const val PROTOCOL_RELAY = "relay"
        const val PROTOCOL_ZIBELAN = "zibelan"
        const val DEFAULT_RELAY_SERVER = "127.0.0.1:7777"
        const val DEFAULT_ZIBELAN_SERVER = "64.177.48.215:5000"

        private val ZIBELAN_ALIASES = setOf("zibelan", "roz", "bridge", "remote", "comunidad")
        private val RELAY_ALIASES = setOf("relay", "own", "local", "server", "directo", "direct")

        fun normalizeProtocol(value: String?): String {
            val key = value?.trim()?.lowercase().orEmpty()
            return when {
                key in ZIBELAN_ALIASES -> PROTOCOL_ZIBELAN
                key in RELAY_ALIASES -> PROTOCOL_RELAY
                else -> PROTOCOL_RELAY
            }
        }

        fun defaultServer(protocol: String): String =
            if (normalizeProtocol(protocol) == PROTOCOL_ZIBELAN) DEFAULT_ZIBELAN_SERVER else DEFAULT_RELAY_SERVER

        fun isValidServer(value: String): Boolean {
            val trimmed = value.trim()
            if (trimmed.isEmpty()) return false
            val host = trimmed.substringBeforeLast(':', missingDelimiterValue = "")
            val port = trimmed.substringAfterLast(':', missingDelimiterValue = "").toIntOrNull() ?: return false
            return host.isNotBlank() && port in 1..65535
        }
    }
}

interface OnlineEngineStore {
    fun load(): OnlineEngineConfig
    fun save(config: OnlineEngineConfig): Result<Unit>
}
