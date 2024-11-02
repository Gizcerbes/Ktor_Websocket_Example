package org.example

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable



suspend fun main() {
    val client = HttpClient(CIO) {
        install(WebSockets)
    }

    val job = CoroutineScope(Dispatchers.IO).launch { open(client, "first") }

    job.join()
    delay(3000)

    client.close()
}


@Serializable
data class Data(
    @SerialName("data")
    val data: String
)

@Serializable
data class Output(
    @SerialName("data")
    val data: String
)

private suspend fun open(client: HttpClient, text: String) {
    val workScope = CoroutineScope(Dispatchers.IO)
    val stream = WebSocketStream<Data, Output>(
        client = client,
        workScope = workScope,
        urlString = "ws://localhost:8078/ws"
    )
    CoroutineScope(Dispatchers.IO).launch {
        stream.output
            .onEach {
                println("$text $it")
            }.launchIn(this)

        stream.status
            .onEach {
                println(it)
            }.launchIn(this)
    }

    var keep = true

    stream.connectWhile { keep }

    repeat(5) {
        stream.send(Output("$text $it")).join()
    }
    //delay(10000)
    keep = false
}