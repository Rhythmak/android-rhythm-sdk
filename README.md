# RHYTHM

Official Android SDK for interacting with the RHYTHM agent.

## About

RHYTHM pairs a crypto wallet with a conversational agent. Users can explore their
on-chain assets, ask questions, and review proposed actions while keeping control
of their wallet decisions.

## SDK

> ⚠️ **Alpha / Active development — 0.0.1-alpha**
>
> This Kotlin interface is experimental. APIs may change before a stable release.

This release provides typed agent events, transaction request/result envelopes,
and a local preview for Android applications. It builds as a single Android library
(`rhythm`, an AAR), with Kotlin coroutines for asynchronous interaction.

**An official public network adapter is not available in this alpha.** An agent
without an explicit transport reports `PUBLIC_API_UNAVAILABLE`. The preview uses
no network, AI service, wallet, signing or transaction execution. No API key or
wallet credential is needed for the preview.

## Requirements

- Android 8.0 / API 26 or later (`minSdk = 26`)
- Android SDK Platform 35 to build (`compileSdk = 35`)
- JDK 17
- Gradle 8.11.1, supplied by the wrapper
- Android Gradle Plugin 8.9.2 and Kotlin 2.1.20, pinned in the build

Kotlin coroutines 1.10.2 is the public API dependency. The manifest requests no
permissions and installs no activities, services or background components.

## Installation

Clone the alpha and open its root directory in Android Studio:

```sh
git clone --branch main https://github.com/Rhythmak/android-rhythm-sdk.git
cd android-rhythm-sdk
```

Set `ANDROID_HOME` to your installed Android SDK, or use Android Studio's local
SDK configuration. Then build, test and install the artifact into your **local**
Maven repository:

```sh
bash ./gradlew :rhythm:testDebugUnitTest :rhythm:lintRelease :rhythm:assembleRelease
bash ./gradlew :rhythm:publishReleasePublicationToMavenLocal
```

On Windows use `gradlew.bat`. In the consuming app's `settings.gradle.kts`, enable
`mavenLocal()` alongside its normal repositories. In its module dependencies add:

```kotlin
implementation("com.rhythm:rhythm:0.0.1-alpha")
```

These coordinates resolve **only after the local publication step**. This alpha
is not published to Maven Central, Google Maven or JitPack. Use a compatible Kotlin
2.1+ Android application and the requirements above. The generated release AAR is
under `rhythm/build/outputs/aar/`; local Maven publication includes its dependency
metadata.

## Usage

The complete example below connects to an **in-memory preview**, receives events,
sends text, and declines a fictional transaction request. It does not access
production RHYTHM or perform a wallet action.

```kotlin
import com.rhythm.sdk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect

suspend fun runLocalPreview() = coroutineScope {
    val preview = RhythmPreviewTransport()
    val agent = RhythmAgentBuilder.shared.build(
        network = RhythmNetwork.ROBINHOOD_CHAIN,
        transport = preview
    )
    val requestHandled = CompletableDeferred<Unit>()

    // One event collector per session; update Android views on the main thread.
    val listener = launch {
        agent.events.collect { event ->
            when (event) {
                is RhythmAgentEvent.Connected -> println("Preview connected")
                is RhythmAgentEvent.Message -> println(event.text)
                is RhythmAgentEvent.TransactionRequested -> {
                    // Explicit local decision. No signing or approval is performed.
                    agent.submit(RhythmTransactionResult(
                        event.request.id,
                        RhythmTransactionResult.Status.Declined
                    ))
                }
                is RhythmAgentEvent.TransactionResultReceived -> requestHandled.complete(Unit)
                RhythmAgentEvent.Disconnected -> println("Preview ended")
            }
        }
    }

    try {
        agent.connect()
        agent.send("Help me understand my assets") // Returns a labeled local echo.
        preview.previewTransactionRequest(RhythmTransactionRequest(
            network = RhythmNetwork.ROBINHOOD_CHAIN,
            summary = "Preview a wallet action for review"
        ))
        requestHandled.await()
    } finally {
        withContext(NonCancellable) { agent.disconnect() }
        listener.cancelAndJoin()
    }
}
```

In an Android app, launch this suspend function from a lifecycle-owned coroutine
scope. A session is single-use: disconnect closes its event stream. Create a new
agent and preview for a subsequent session. Events buffer in memory for one
collector; the preview is a small development aid, not a production transport.

### Transaction requests and results

`RhythmTransactionRequest` contains an ID, chain and display summary. It is **not a
signable transaction**. A future host integration must obtain complete details
through a trusted flow, independently validate them, show them to the user, and
handle authorization/signing in its own wallet integration.

The host can return `Declined`, `Failed(reason)` or `Submitted(transactionHash)`.
A reported hash does not prove on-chain confirmation. Never send wallet
credentials or sensitive data in messages, summaries or failure reasons.

The preview correlates pending request IDs and rejects duplicate results and
wrong-network requests. It never approves, signs, transfers or executes anything.

### Networks and future transports

`RhythmNetwork` identifies Ethereum Mainnet (1) and Robinhood Chain (4663). Choosing
one sets context only; it does not switch a wallet or connect to an RPC.

`RhythmAgentTransport` defines the experimental connect/events/text/result/disconnect
boundary. No public server wire protocol or authentication flow is supplied yet.
An official network adapter will require a separately published API contract;
internal website endpoints are not an SDK API.

## UI

RHYTHM chat UI components are planned and experimental. This alpha contains no
Compose views, XML screens or ready-made wallet UI. Use the local event interface
to prototype your own Android presentation.

## Links

- Website: [rhythm.casa](https://rhythm.casa)
- X: [@Rhythmsak](https://x.com/Rhythmsak)
