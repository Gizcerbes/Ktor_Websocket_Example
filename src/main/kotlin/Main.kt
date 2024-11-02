package org.example

import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlin.time.Duration

fun main() {
    embeddedServer(
        factory = CIO,
        configure = {
            this.connector {
                port = 8078
            }
        },
        module = {
            configureSocket()
        }).start(true)
}


@OptIn(DelicateCoroutinesApi::class)
fun Application.configureSocket() {

    install(WebSockets) {
        pingPeriod = Duration.parse("15s")
        timeout = Duration.parse("15s")
        maxFrameSize = Long.MAX_VALUE
        masking = true
    }

    val exceptionHandler = CoroutineExceptionHandler { _, exception ->
        when (exception) {
            is java.net.SocketException -> {
                if (exception.message == "Connection reset") {
                    println("Connection reset by peer: ${exception.message}")
                } else {
                    println("Socket error: ${exception.message}")
                }
            }

            else -> println("Unhandled exception: ${exception.message}")
        }
    }


    routing {


        val list = ArrayList<WebSocketSession>()
        val messageResponseFlow = MutableSharedFlow<MessageResponse>()
        val sharedFlow = messageResponseFlow.asSharedFlow()

        sharedFlow.onEach { message ->
            list.forEach {
                runCatching { it.send(message.message) }
                    .onFailure { println("Catch error") }
            }
        }.launchIn(CoroutineScope(Dispatchers.IO))

        webSocket("/ws") {

            list.add(this)
            println("open")

            try {
                runCatching {
                    incoming.consumeEach { frame ->
                        when (frame) {
                            is Frame.Text -> {
                                val receivedText = frame.readText()
                                println(receivedText)
                                val messageResponse = MessageResponse(receivedText)
                                messageResponseFlow.emit(messageResponse)
                            }
                            else -> {
                                println("else")
                            }
                        }
                    }
                }.onFailure { exception ->
                    println("WebSocket exception: ${exception.localizedMessage}")
                }

                list.remove(this)

                println("canceling")
                close(CloseReason(CloseReason.Codes.NORMAL, "leave"))

            } catch (e: ClosedReceiveChannelException) {
                println("onClose ${closeReason.await()}")
            } catch (e: Throwable) {
                println("onError ${closeReason.await()}")
                e.printStackTrace()
            }
        }
    }
}