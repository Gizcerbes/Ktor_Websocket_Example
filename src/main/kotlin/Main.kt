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
        pingPeriod = Duration.parse("1s")
        timeout = Duration.parse("10s")
        maxFrameSize = Long.MAX_VALUE
        masking = true
    }

    routing {

        val list = ArrayList<WebSocketSession>()
        val messageResponseFlow = MutableSharedFlow<MessageResponse>()
        val sharedFlow = messageResponseFlow.asSharedFlow()

        sharedFlow.onEach { message ->
            list.forEach {
                runCatching {
                    println(message.message)
                    if (it.isActive) it.send(message.message)
                }.onFailure { println("Catch error $it") }
            }
        }.launchIn(CoroutineScope(Dispatchers.IO))

        val dataFlow = MutableSharedFlow<ByteArray>(extraBufferCapacity = 256)

        dataFlow.onEach { data ->
            messageResponseFlow.emit(MessageResponse(data.decodeToString()))
        }.launchIn(CoroutineScope(Dispatchers.IO))

        webSocket("/ws") {

            list.add(this)
            println("open")

            try {
                runCatching {
                    incoming.consumeEach { frame ->
                        when (frame) {
                            is Frame.Text -> {
                                val data = frame.data
                                dataFlow.emit(data)
                            }

                            else -> {
                                println("else")
                            }
                        }
                    }
                }.onFailure { exception ->
                    exception.printStackTrace()
                    println("WebSocket exception: ${exception.localizedMessage}")
                }


                println("canceling")
                close(CloseReason(CloseReason.Codes.NORMAL, "leave"))

            } catch (e: ClosedReceiveChannelException) {
                println("onClose ${closeReason.await()}")
            } catch (e: Throwable) {
                println("onError ${closeReason.await()}")
                e.printStackTrace()
            }

            list.remove(this)

        }
    }
}