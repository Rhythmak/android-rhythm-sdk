package com.rhythm.sdk

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** Experimental adapter boundary. Each session has one event collector. */
interface RhythmAgentTransport {
    val events: Flow<RhythmAgentEvent>
    suspend fun connect(network: RhythmNetwork)
    suspend fun send(text: String)
    suspend fun submit(result: RhythmTransactionResult)
    suspend fun disconnect()
}

class RhythmAgent internal constructor(
    val network: RhythmNetwork,
    private val transport: RhythmAgentTransport
) {
    val events: Flow<RhythmAgentEvent> get() = transport.events
    suspend fun connect() = transport.connect(network)
    suspend fun send(text: String) {
        if (text.isBlank()) throw RhythmException(RhythmFailure.EMPTY_MESSAGE)
        transport.send(text)
    }
    suspend fun submit(result: RhythmTransactionResult) = transport.submit(result)
    suspend fun disconnect() = transport.disconnect()
}

class RhythmAgentBuilder {
    companion object { val shared = RhythmAgentBuilder() }

    /** With no adapter supplied, connect/send/submit report PUBLIC_API_UNAVAILABLE. */
    fun build(
        network: RhythmNetwork,
        transport: RhythmAgentTransport = UnavailableTransport()
    ): RhythmAgent = RhythmAgent(network, transport)
}

private class UnavailableTransport : RhythmAgentTransport {
    override val events: Flow<RhythmAgentEvent> = emptyFlow()
    override suspend fun connect(network: RhythmNetwork): Unit = unavailable()
    override suspend fun send(text: String): Unit = unavailable()
    override suspend fun submit(result: RhythmTransactionResult): Unit = unavailable()
    override suspend fun disconnect() = Unit
    private fun unavailable(): Nothing = throw RhythmException(RhythmFailure.PUBLIC_API_UNAVAILABLE)
}
