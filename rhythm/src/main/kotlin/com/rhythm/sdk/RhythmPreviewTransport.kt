package com.rhythm.sdk

import java.util.UUID
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Local preview only. No AI, network calls, wallet access or transaction execution. */
class RhythmPreviewTransport : RhythmAgentTransport {
    private val lock = Mutex()
    private val channel = Channel<RhythmAgentEvent>(Channel.UNLIMITED)
    override val events: Flow<RhythmAgentEvent> = channel.receiveAsFlow()
    private var network: RhythmNetwork? = null
    private var closed = false
    private val pending = mutableSetOf<UUID>()
    private val seen = mutableSetOf<UUID>()

    override suspend fun connect(network: RhythmNetwork) = lock.withLock {
        check(!closed, RhythmFailure.CLOSED)
        check(this.network == null, RhythmFailure.ALREADY_CONNECTED)
        this.network = network
        emit(RhythmAgentEvent.Connected(network))
    }

    override suspend fun send(text: String) = lock.withLock {
        check(network != null, RhythmFailure.NOT_CONNECTED)
        check(text.isNotBlank(), RhythmFailure.EMPTY_MESSAGE)
        emit(RhythmAgentEvent.Message("Local preview received: $text"))
    }

    /** Injects a fictional request into the host's review UI, without taking action. */
    suspend fun previewTransactionRequest(request: RhythmTransactionRequest) = lock.withLock {
        check(network != null, RhythmFailure.NOT_CONNECTED)
        check(request.network == network, RhythmFailure.NETWORK_MISMATCH)
        check(seen.add(request.id), RhythmFailure.DUPLICATE_REQUEST)
        pending.add(request.id)
        emit(RhythmAgentEvent.TransactionRequested(request))
    }

    override suspend fun submit(result: RhythmTransactionResult) = lock.withLock {
        check(network != null, RhythmFailure.NOT_CONNECTED)
        check(pending.remove(result.requestId), RhythmFailure.UNKNOWN_REQUEST)
        emit(RhythmAgentEvent.TransactionResultReceived(result))
    }

    override suspend fun disconnect() = lock.withLock {
        if (!closed) {
            closed = true
            network = null
            pending.clear()
            seen.clear()
            emit(RhythmAgentEvent.Disconnected)
            channel.close()
        }
    }

    private fun emit(event: RhythmAgentEvent) { channel.trySend(event).getOrThrow() }
    private fun check(condition: Boolean, reason: RhythmFailure) {
        if (!condition) throw RhythmException(reason)
    }
}
