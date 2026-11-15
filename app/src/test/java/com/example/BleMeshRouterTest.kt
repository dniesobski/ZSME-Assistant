package com.example

import com.example.mesh.MeshPacketChunker
import com.example.mesh.MeshRouter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BleMeshRouterTest {

    @Test
    fun `chunker splits Polish UTF8 message into 20 byte frames and reassembles cleanly`() {
        val router = MeshRouter(myNodeId = "NODEA1")
        val msg = router.createLocalMessage(
            senderName = "Kacper",
            senderClass = "2TI",
            channel = "#ogólny",
            text = "Cześć! Zastępstwo z fizyki przeniesione do sali 204 na 4 lekcji."
        )

        val frames = MeshPacketChunker.encodeToFrames(msg)
        assertTrue(frames.size > 3)
        assertTrue(frames.all { it.size <= 20 })

        val assembler = MeshPacketChunker.FrameAssembler()
        var decoded = null as com.example.mesh.MeshMessage?
        for (frame in frames) {
            decoded = assembler.acceptFrame("AA:BB:CC:DD:EE:FF", frame)
        }

        assertNotNull(decoded)
        assertEquals(msg.id, decoded?.id)
        assertEquals(msg.text, decoded?.text)
        assertEquals("Kacper", decoded?.senderName)
        assertEquals("2TI", decoded?.senderClass)
        assertFalseOrNotMine(decoded!!.isMine)
    }

    private fun assertFalseOrNotMine(isMine: Boolean) {
        assertEquals(false, isMine)
    }

    @Test
    fun `mesh router relays packet with decremented TTL and deduplicates loops`() {
        val nodeA = MeshRouter(myNodeId = "AAAA")
        val nodeB = MeshRouter(myNodeId = "BBBB")
        val nodeC = MeshRouter(myNodeId = "CCCC")

        val originMsg = nodeA.createLocalMessage(
            senderName = "Anna",
            senderClass = "3TM",
            channel = "#ogólny",
            text = "Spotkanie koła o 14:00",
            ttl = 3
        )

        // Node B receives from Node A
        val bOutcome = nodeB.onIncomingPacket(originMsg, fromPeerAddress = "ADDR_A", myRelayTag = "Tel-B")
        assertNotNull(bOutcome.acceptedMessage)
        assertNotNull(bOutcome.forwardPacket)
        assertEquals(1, bOutcome.forwardPacket?.hopCount)
        assertEquals(2, bOutcome.forwardPacket?.ttl)
        assertEquals(listOf("Tel-B"), bOutcome.forwardPacket?.relayTrail)

        // Node C receives relayed packet from Node B
        val cOutcome = nodeC.onIncomingPacket(bOutcome.forwardPacket!!, fromPeerAddress = "ADDR_B", myRelayTag = "Tel-C")
        assertNotNull(cOutcome.acceptedMessage)
        assertEquals(1, cOutcome.acceptedMessage?.hopCount)
        assertNotNull(cOutcome.forwardPacket)
        assertEquals(2, cOutcome.forwardPacket?.hopCount)
        assertEquals(1, cOutcome.forwardPacket?.ttl)

        // Duplicate delivery back to Node B should be ignored
        val bDup = nodeB.onIncomingPacket(cOutcome.forwardPacket!!, fromPeerAddress = "ADDR_C", myRelayTag = "Tel-B")
        assertNull(bDup.acceptedMessage)
        assertNull(bDup.forwardPacket)

        // Store-and-carry: a brand new neighbor ADDR_D should see pending packet on Node B
        val pendingForD = nodeB.pendingPacketsForPeer("ADDR_D")
        assertEquals(1, pendingForD.size)
        assertEquals(originMsg.id, pendingForD[0].id)
    }

    @Test
    fun `advert codec strictly respects 24 byte payload limit for 31 byte BLE frames`() {
        val payload = com.example.mesh.MeshAdvertCodec.encodeScanResponsePayload(
            nodeId = "AB12CD34",
            label = "Łukasz_2TI_BardzoDługiNickSzkolny"
        )
        assertTrue(payload.size <= 24)
        val decoded = com.example.mesh.MeshAdvertCodec.decodeScanResponsePayload(payload)
        assertNotNull(decoded)
        assertEquals("AB12", decoded?.nodeId)
        assertTrue(decoded?.displayLabel?.startsWith("Łukasz_2TI") == true)
    }

    @Test
    fun `mesh router delivers direct message only to matching recipient while relaying across hops`() {
        val nodeA = MeshRouter(myNodeId = "AAAA")
        val nodeB = MeshRouter(myNodeId = "BBBB")
        val nodeC = MeshRouter(myNodeId = "CCCC")

        val dm = nodeA.createLocalMessage(
            senderName = "Ania",
            senderClass = "2TI",
            channel = com.example.mesh.MeshMessage.DIRECT_CHANNEL,
            text = "Hej Tomek, masz notatki?",
            targetRecipient = "Tomek"
        )

        // Intermediate node B ("Kasia") should NOT display DM in inbox, only relay forward
        val bOutcome = nodeB.onIncomingPacket(dm, fromPeerAddress = "ADDR_A", myRelayTag = "Kasia")
        assertNull(bOutcome.acceptedMessage)
        assertNotNull(bOutcome.forwardPacket)

        // Target node C ("Tomek") receives and accepts the relayed DM
        val cOutcome = nodeC.onIncomingPacket(bOutcome.forwardPacket!!, fromPeerAddress = "ADDR_B", myRelayTag = "Tomek")
        assertNotNull(cOutcome.acceptedMessage)
        assertEquals("Tomek", cOutcome.acceptedMessage?.targetRecipient)
        assertEquals("Hej Tomek, masz notatki?", cOutcome.acceptedMessage?.text)
    }

    @Test
    fun `mesh router replies to nick availability probe and delivers probe ack to requester`() {
        val requester = MeshRouter(myNodeId = "REQ1")
        val relay = MeshRouter(myNodeId = "REL1")
        val target = MeshRouter(myNodeId = "TRG1")

        val probe = requester.createLocalMessage(
            senderName = "Ania",
            senderClass = "",
            channel = com.example.mesh.MeshMessage.DIRECT_CHANNEL,
            text = "PING",
            targetRecipient = "Tomek",
            isProbe = true
        )

        // Relay does not match Tomek -> forwards probe
        val relayOutcome = relay.onIncomingPacket(probe, fromPeerAddress = "ADDR_REQ", myRelayTag = "Marek")
        assertNull(relayOutcome.acceptedMessage)
        assertNull(relayOutcome.probeReplyPacket)
        assertNotNull(relayOutcome.forwardPacket)

        // Target matches "Tomek" -> creates isProbeAck reply back to "Ania"
        val targetOutcome = target.onIncomingPacket(relayOutcome.forwardPacket!!, fromPeerAddress = "ADDR_REL", myRelayTag = "Tomek")
        assertNull(targetOutcome.acceptedMessage)
        assertNotNull(targetOutcome.probeReplyPacket)
        assertTrue(targetOutcome.probeReplyPacket!!.isProbeAck)
        assertEquals("Ania", targetOutcome.probeReplyPacket!!.targetRecipient)

        // Requester ("Ania") receives the ack -> extracts probeAckFrom
        val ackOnRelay = relay.onIncomingPacket(targetOutcome.probeReplyPacket!!, fromPeerAddress = "ADDR_TRG", myRelayTag = "Marek")
        assertNull(ackOnRelay.probeAckFrom)
        assertNotNull(ackOnRelay.forwardPacket)

        val ackOnRequester = requester.onIncomingPacket(ackOnRelay.forwardPacket!!, fromPeerAddress = "ADDR_REL", myRelayTag = "Ania")
        assertNotNull(ackOnRequester.probeAckFrom)
        assertEquals("Tomek", ackOnRequester.probeAckFrom?.senderName)
    }
}
