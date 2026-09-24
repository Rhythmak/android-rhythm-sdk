package com.rhythm.sdk

import java.util.UUID

/** Chain identity; asset names and tickers are not identifiers. */
enum class RhythmNetwork(val chainId: Long) {
    ETHEREUM_MAINNET(1), ROBINHOOD_CHAIN(4663)
}

/** Experimental review envelope. It is not a signable transaction or an approval. */
data class RhythmTransactionRequest(
    val network: RhythmNetwork,
    val summary: String,
    val id: UUID = UUID.randomUUID()
)

/** A host-reported hash does not establish on-chain confirmation. */
data class RhythmTransactionResult(val requestId: UUID, val status: Status) {
    sealed interface Status {
        data object Declined : Status
        data class Submitted(val transactionHash: String) : Status
        data class Failed(val reason: String) : Status
    }
}

sealed interface RhythmAgentEvent {
    data class Connected(val network: RhythmNetwork) : RhythmAgentEvent
    data class Message(val text: String) : RhythmAgentEvent
    data class TransactionRequested(val request: RhythmTransactionRequest) : RhythmAgentEvent
    data class TransactionResultReceived(val result: RhythmTransactionResult) : RhythmAgentEvent
    data object Disconnected : RhythmAgentEvent
}

enum class RhythmFailure {
    PUBLIC_API_UNAVAILABLE, NOT_CONNECTED, ALREADY_CONNECTED, CLOSED,
    EMPTY_MESSAGE, UNKNOWN_REQUEST, DUPLICATE_REQUEST, NETWORK_MISMATCH
}

class RhythmException(val reason: RhythmFailure) : IllegalStateException(reason.name)
