package com.example.mesh

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.ByteBuffer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class MeshMessage(
    val id: String,
    val senderName: String,
    val senderClass: String = "",
    val channel: String = "#ogólny",
    val text: String,
    val timestampMillis: Long,
    val hopCount: Int = 0,
    val ttl: Int = DEFAULT_TTL,
    val originNodeId: String,
    val relayTrail: List<String> = emptyList(),
    val isMine: Boolean = false,
    val targetRecipient: String = "",
    val isProbe: Boolean = false,
    val isProbeAck: Boolean = false
) {
    companion object {
        const val DEFAULT_TTL = 5
        const val DIRECT_CHANNEL = "@prywatne"
    }
}

data class MeshNickAvailability(
    val nick: String,
    val isAvailable: Boolean,
    val viaMeshHops: Int = 0,
    val rssi: Int? = null,
    val nodeId: String? = null,
    val checkedAtMillis: Long = System.currentTimeMillis()
)

data class MeshPeer(
    val deviceAddress: String,
    val nodeId: String,
    val displayName: String,
    val rssi: Int,
    val lastSeenMillis: Long
)

enum class MeshRadioState(val label: String) {
    IDLE("Wyłączona"),
    STARTING("Uruchamianie sieci BLE..."),
    ACTIVE("Aktywna sieć kratowa"),
    NO_HARDWARE("Brak modułu BLE"),
    BLUETOOTH_DISABLED("Bluetooth wyłączony"),
    MISSING_PERMISSIONS("Wymagane uprawnienia Bluetooth"),
    LOCATION_DISABLED("Lokalizacja wyłączona (wymagana dla BLE < Android 12)"),
    ERROR("Błąd radia BLE")
}

data class MeshStats(
    val sentCount: Int = 0,
    val receivedCount: Int = 0,
    val relayedCount: Int = 0
)

/**
 * Encodes/decodes compact peer identity in BLE ScanResponse Manufacturer Specific Data
 * (strictly <= 24 payload bytes so Flags/Manufacturer framing always fits within the 31-byte BLE limit).
 */
object MeshAdvertCodec {
    const val MANUFACTURER_ID: Int = 0x5A53 // 'Z','S'
    private const val MAX_TOTAL_PAYLOAD_BYTES = 24
    private const val NODE_ID_LEN = 4

    data class AdvertMeta(
        val nodeId: String,
        val displayLabel: String
    )

    fun encodeScanResponsePayload(nodeId: String, label: String): ByteArray {
        val cleanId = nodeId.trim().uppercase().take(NODE_ID_LEN).padEnd(NODE_ID_LEN, '0')
        val cleanLabel = label.trim().replace("|", " ")
        val prefix = "$cleanId|"
        val maxLabelBytes = MAX_TOTAL_PAYLOAD_BYTES - prefix.toByteArray(Charsets.UTF_8).size
        val trimmedLabel = truncateUtf8(cleanLabel, maxLabelBytes.coerceAtLeast(0))
        return (prefix + trimmedLabel).toByteArray(Charsets.UTF_8)
    }

    fun decodeScanResponsePayload(bytes: ByteArray?): AdvertMeta? {
        if (bytes == null || bytes.isEmpty()) return null
        val text = runCatching { String(bytes, Charsets.UTF_8) }.getOrNull()?.trim() ?: return null
        if (text.isBlank()) return null
        val sep = text.indexOf('|')
        return if (sep > 0) {
            val id = text.substring(0, sep).trim()
            val label = text.substring(sep + 1).trim()
            if (id.isBlank()) null else AdvertMeta(nodeId = id, displayLabel = label)
        } else {
            AdvertMeta(nodeId = text.take(8), displayLabel = "")
        }
    }

    private fun truncateUtf8(text: String, maxBytes: Int): String {
        if (maxBytes <= 0 || text.isEmpty()) return ""
        val sb = StringBuilder()
        var used = 0
        for (ch in text) {
            val bytes = ch.toString().toByteArray(Charsets.UTF_8).size
            if (used + bytes > maxBytes) break
            sb.append(ch)
            used += bytes
        }
        return sb.toString()
    }
}

/**
 * Splits compact MeshMessage JSON payloads into 20-byte BLE ATT MTU safe frames
 * and reassembles incoming frames from neighbor GATT writes.
 *
 * Frame layout (20 bytes max):
 * - bytes 0..1: magic 'Z', 'M' (0x5A, 0x4D)
 * - bytes 2..5: 32-bit packet tag
 * - byte 6: chunk index (0..254)
 * - byte 7: total chunks (1..255)
 * - bytes 8..19: UTF-8 JSON payload slice (up to 12 bytes per frame)
 */
object MeshPacketChunker {
    private const val MAGIC_0: Byte = 0x5A
    private const val MAGIC_1: Byte = 0x4D
    private const val HEADER_SIZE = 8
    private const val MAX_PAYLOAD_PER_FRAME = 12

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    fun encodeToFrames(packet: MeshMessage): List<ByteArray> {
        val raw = json.encodeToString(packet.copy(isMine = false)).toByteArray(Charsets.UTF_8)
        val totalChunks = ((raw.size + MAX_PAYLOAD_PER_FRAME - 1) / MAX_PAYLOAD_PER_FRAME).coerceAtLeast(1)
        require(totalChunks <= 250) { "Wiadomość przekracza limit rozmiaru BLE mesh" }
        val tag = packet.id.hashCode()

        return (0 until totalChunks).map { idx ->
            val start = idx * MAX_PAYLOAD_PER_FRAME
            val end = minOf(start + MAX_PAYLOAD_PER_FRAME, raw.size)
            val sliceLen = end - start
            val frame = ByteArray(HEADER_SIZE + sliceLen)
            frame[0] = MAGIC_0
            frame[1] = MAGIC_1
            ByteBuffer.allocate(4).putInt(tag).array().copyInto(frame, destinationOffset = 2)
            frame[6] = idx.toByte()
            frame[7] = totalChunks.toByte()
            raw.copyInto(frame, destinationOffset = HEADER_SIZE, startIndex = start, endIndex = end)
            frame
        }
    }

    class FrameAssembler {
        private data class PartialPacket(
            val totalChunks: Int,
            val chunks: MutableMap<Int, ByteArray> = mutableMapOf(),
            val createdAtMillis: Long = System.currentTimeMillis()
        )

        private val buffers = ConcurrentHashMap<String, PartialPacket>()

        fun acceptFrame(sourceKey: String, frame: ByteArray): MeshMessage? {
            if (frame.size < HEADER_SIZE) return null
            if (frame[0] != MAGIC_0 || frame[1] != MAGIC_1) return null

            val tag = ByteBuffer.wrap(frame, 2, 4).int
            val chunkIdx = frame[6].toInt() and 0xFF
            val totalChunks = frame[7].toInt() and 0xFF
            if (totalChunks <= 0 || chunkIdx >= totalChunks) return null

            val payloadSlice = frame.copyOfRange(HEADER_SIZE, frame.size)
            val bufferKey = "$sourceKey:$tag"
            val partial = buffers.getOrPut(bufferKey) { PartialPacket(totalChunks) }
            synchronized(partial) {
                partial.chunks[chunkIdx] = payloadSlice
                if (partial.chunks.size == partial.totalChunks) {
                    buffers.remove(bufferKey)
                    val totalSize = (0 until partial.totalChunks).sumOf { partial.chunks[it]?.size ?: 0 }
                    val assembled = ByteArray(totalSize)
                    var offset = 0
                    for (i in 0 until partial.totalChunks) {
                        val part = partial.chunks[i] ?: return null
                        part.copyInto(assembled, offset)
                        offset += part.size
                    }
                    return runCatching {
                        json.decodeFromString<MeshMessage>(String(assembled, Charsets.UTF_8))
                    }.getOrNull()
                }
            }

            // Prune stale partial packets older than 30s
            val cutoff = System.currentTimeMillis() - 30_000L
            buffers.entries.removeIf { it.value.createdAtMillis < cutoff }
            return null
        }
    }
}

/**
 * Controlled-flooding multi-hop mesh router with loop deduplication and store-and-carry sync.
 */
class MeshRouter(
    val myNodeId: String = UUID.randomUUID().toString().take(8).uppercase()
) {
    private val seenOrder = ArrayDeque<String>()
    private val seenIds = HashSet<String>()
    private val activeRelayCache = ArrayDeque<MeshMessage>()
    private val deliveredToPeer = ConcurrentHashMap<String, MutableSet<String>>()

    data class IncomingOutcome(
        val acceptedMessage: MeshMessage?,
        val forwardPacket: MeshMessage?,
        val probeReplyPacket: MeshMessage? = null,
        val probeAckFrom: MeshMessage? = null
    )

    @Synchronized
    fun createLocalMessage(
        senderName: String,
        senderClass: String,
        channel: String,
        text: String,
        ttl: Int = MeshMessage.DEFAULT_TTL,
        targetRecipient: String = "",
        isProbe: Boolean = false,
        isProbeAck: Boolean = false
    ): MeshMessage {
        val cleanTarget = targetRecipient.trim()
        val effectiveChannel = if (cleanTarget.isNotBlank() && !isProbe && !isProbeAck) {
            MeshMessage.DIRECT_CHANNEL
        } else {
            channel
        }
        val msg = MeshMessage(
            id = UUID.randomUUID().toString().take(12),
            senderName = senderName.trim().ifBlank { "Uczeń ZSME" },
            senderClass = senderClass.trim(),
            channel = effectiveChannel,
            text = text.trim(),
            timestampMillis = System.currentTimeMillis(),
            hopCount = 0,
            ttl = ttl,
            originNodeId = myNodeId,
            relayTrail = emptyList(),
            isMine = !isProbe && !isProbeAck,
            targetRecipient = cleanTarget,
            isProbe = isProbe,
            isProbeAck = isProbeAck
        )
        markSeen(msg.id)
        cacheForRelay(msg)
        return msg
    }

    @Synchronized
    fun onIncomingPacket(
        packet: MeshMessage,
        fromPeerAddress: String,
        myRelayTag: String
    ): IncomingOutcome {
        if (packet.originNodeId == myNodeId || seenIds.contains(packet.id)) {
            markDeliveredToPeer(fromPeerAddress, packet.id)
            return IncomingOutcome(acceptedMessage = null, forwardPacket = null)
        }

        markSeen(packet.id)
        markDeliveredToPeer(fromPeerAddress, packet.id)

        val cleanTarget = packet.targetRecipient.trim()
        val isBroadcast = cleanTarget.isBlank()
        val isForMe = !isBroadcast && (
            cleanTarget.equals(myRelayTag.trim(), ignoreCase = true) ||
                cleanTarget.equals(myNodeId, ignoreCase = true)
            )

        fun buildForward(): MeshMessage? = if (packet.ttl > 1) {
            packet.copy(
                ttl = packet.ttl - 1,
                hopCount = packet.hopCount + 1,
                relayTrail = (packet.relayTrail + myRelayTag).takeLast(6),
                isMine = false
            ).also { cacheForRelay(it) }
        } else {
            null
        }

        if (packet.isProbe) {
            if (isForMe) {
                val ack = createLocalMessage(
                    senderName = myRelayTag,
                    senderClass = "",
                    channel = MeshMessage.DIRECT_CHANNEL,
                    text = "ACK",
                    ttl = MeshMessage.DEFAULT_TTL,
                    targetRecipient = packet.senderName,
                    isProbe = false,
                    isProbeAck = true
                )
                return IncomingOutcome(
                    acceptedMessage = null,
                    forwardPacket = null,
                    probeReplyPacket = ack
                )
            }
            return IncomingOutcome(acceptedMessage = null, forwardPacket = buildForward())
        }

        if (packet.isProbeAck) {
            if (isForMe) {
                return IncomingOutcome(
                    acceptedMessage = null,
                    forwardPacket = null,
                    probeAckFrom = packet
                )
            }
            return IncomingOutcome(acceptedMessage = null, forwardPacket = buildForward())
        }

        if (!isBroadcast) {
            // Private/direct message: only accept if addressed to me; otherwise relay transparently
            return if (isForMe) {
                IncomingOutcome(
                    acceptedMessage = packet.copy(isMine = false),
                    forwardPacket = null
                )
            } else {
                IncomingOutcome(
                    acceptedMessage = null,
                    forwardPacket = buildForward()
                )
            }
        }

        val displayMessage = packet.copy(isMine = false)
        return IncomingOutcome(
            acceptedMessage = displayMessage,
            forwardPacket = buildForward()
        )
    }

    @Synchronized
    fun pendingPacketsForPeer(peerAddress: String): List<MeshMessage> {
        val sentSet = deliveredToPeer.getOrPut(peerAddress) { mutableSetOf() }
        val cutoff = System.currentTimeMillis() - 15 * 60_000L
        return activeRelayCache.filter { pkt ->
            pkt.timestampMillis >= cutoff && pkt.ttl > 0 && !sentSet.contains(pkt.id)
        }
    }

    @Synchronized
    fun markDeliveredToPeer(peerAddress: String, packetId: String) {
        deliveredToPeer.getOrPut(peerAddress) { mutableSetOf() }.add(packetId)
    }

    private fun markSeen(id: String) {
        if (seenIds.add(id)) {
            seenOrder.addLast(id)
            while (seenOrder.size > 500) {
                val oldest = seenOrder.removeFirst()
                seenIds.remove(oldest)
            }
        }
    }

    private fun cacheForRelay(packet: MeshMessage) {
        activeRelayCache.addLast(packet)
        while (activeRelayCache.size > 80) {
            activeRelayCache.removeFirst()
        }
    }
}
