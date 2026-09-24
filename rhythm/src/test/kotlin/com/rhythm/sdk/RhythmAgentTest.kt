package com.rhythm.sdk

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class RhythmAgentTest {
    private suspend fun expect(reason: RhythmFailure, block: suspend () -> Unit) {
        try { block(); fail("Expected $reason") }
        catch (error: RhythmException) { assertEquals(reason, error.reason) }
    }

    @Test fun unavailableByDefault() = runTest {
        val agent = RhythmAgentBuilder.shared.build(RhythmNetwork.ETHEREUM_MAINNET)
        expect(RhythmFailure.PUBLIC_API_UNAVAILABLE) { agent.connect() }
        expect(RhythmFailure.PUBLIC_API_UNAVAILABLE) { agent.send("Hello") }
        assertTrue(agent.events.toList().isEmpty())
    }

    @Test fun previewLifecycleAndBufferedEvents() = runTest {
        val agent = RhythmAgentBuilder.shared.build(RhythmNetwork.ROBINHOOD_CHAIN, RhythmPreviewTransport())
        agent.connect()
        agent.send("Hello Rhythm")
        agent.disconnect()
        assertEquals(listOf(
            RhythmAgentEvent.Connected(RhythmNetwork.ROBINHOOD_CHAIN),
            RhythmAgentEvent.Message("Local preview received: Hello Rhythm"),
            RhythmAgentEvent.Disconnected
        ), agent.events.toList())
        expect(RhythmFailure.CLOSED) { agent.connect() }
    }

    @Test fun explicitResultAndReplayRejection() = runTest {
        val preview = RhythmPreviewTransport()
        val agent = RhythmAgentBuilder.shared.build(RhythmNetwork.ROBINHOOD_CHAIN, preview)
        agent.connect()
        val request = RhythmTransactionRequest(RhythmNetwork.ROBINHOOD_CHAIN, "Preview only")
        preview.previewTransactionRequest(request)
        val result = RhythmTransactionResult(request.id, RhythmTransactionResult.Status.Declined)
        agent.submit(result)
        expect(RhythmFailure.UNKNOWN_REQUEST) { agent.submit(result) }
        expect(RhythmFailure.DUPLICATE_REQUEST) { preview.previewTransactionRequest(request) }
        agent.disconnect()
        val events = agent.events.toList()
        assertEquals(RhythmAgentEvent.TransactionRequested(request), events[1])
        assertEquals(RhythmAgentEvent.TransactionResultReceived(result), events[2])
    }

    @Test fun validatesConnectionInputAndNetwork() = runTest {
        val preview = RhythmPreviewTransport()
        val agent = RhythmAgentBuilder.shared.build(RhythmNetwork.ETHEREUM_MAINNET, preview)
        expect(RhythmFailure.NOT_CONNECTED) { agent.send("Hello") }
        agent.connect()
        expect(RhythmFailure.ALREADY_CONNECTED) { agent.connect() }
        expect(RhythmFailure.EMPTY_MESSAGE) { agent.send(" \n ") }
        expect(RhythmFailure.NETWORK_MISMATCH) {
            preview.previewTransactionRequest(RhythmTransactionRequest(RhythmNetwork.ROBINHOOD_CHAIN, "Wrong network"))
        }
        agent.disconnect()
    }

    @Test fun sessionsCannotResolveEachOthersRequests() = runTest {
        val first = RhythmPreviewTransport()
        val second = RhythmPreviewTransport()
        first.connect(RhythmNetwork.ETHEREUM_MAINNET)
        second.connect(RhythmNetwork.ETHEREUM_MAINNET)
        val request = RhythmTransactionRequest(RhythmNetwork.ETHEREUM_MAINNET, "Preview only")
        first.previewTransactionRequest(request)
        expect(RhythmFailure.UNKNOWN_REQUEST) {
            second.submit(RhythmTransactionResult(request.id, RhythmTransactionResult.Status.Declined))
        }
        first.disconnect()
        second.disconnect()
    }
}
