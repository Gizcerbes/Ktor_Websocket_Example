package org.example

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.websocket.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Duration


suspend fun main() {
    val client = HttpClient(CIO) {
        install(WebSockets){
            this.pingInterval = Duration.parse("1s")
        }
        install(ContentNegotiation) { json() }
    }

    val job = CoroutineScope(Dispatchers.IO).launch {
        open(client, "first")
    }
//    val jobClause2 = CoroutineScope(Dispatchers.IO).launch {
//        open(client, "second")
//    }
//    val jobClause3 = CoroutineScope(Dispatchers.IO).launch {
//        open(client, "third")
//    }

    job.join()
//    jobClause3.join()
//    jobClause2.join()

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

    repeat(100000) { i ->
        stream.send(Output("$text $i")) {
            println("$it $i")
        }.join()
        delay(100)
    }
    keep = false
    delay(100)
}
