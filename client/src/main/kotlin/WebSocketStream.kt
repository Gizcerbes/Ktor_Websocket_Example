package org.example

import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json


abstract class WebSocketStream<I : Any, O>(
    private val client: HttpClient,
    private val workScope: CoroutineScope,
    private val urlString: String,
    val encoder: (O) -> String,
    private val decoder: (String) -> I,
) {

    companion object {

        inline operator fun <reified I : Any, reified O> invoke(
            client: HttpClient,
            workScope: CoroutineScope,
            urlString: String,
            noinline encoder: (O) -> String = { defaultSerializer.encodeToString(it) },
            noinline decoder: (String) -> I = { defaultSerializer.decodeFromString(it) }
        ): WebSocketStream<I, O> {
            return object : WebSocketStream<I, O>(
                client = client,
                workScope = workScope,
                urlString = urlString,
                encoder = encoder,
                decoder = decoder
            ) {}
        }

        val defaultSerializer = Json {
            ignoreUnknownKeys = true
            allowSpecialFloatingPointValues = true
        }

    }


    sealed interface Output<out O> {
        data class Income<T : Any>(val data: T) : Output<T>
        data class IncomeData(val data: ByteArray) : Output<Nothing>
        data class Something(val frame: Frame) : Output<Nothing>
        data class Error(val e: Throwable) : Output<Nothing>
    }


    sealed interface Status {
        data object Connect : Status
        data object Connected : Status
        data object Disconnect : Status
    }

    sealed interface SendStatus {
        data object OK : SendStatus
        data class Error(val e: Throwable) : SendStatus
    }

    private data class Input(
        val data: String,
        val statusReceiver: (SendStatus) -> Unit
    )

    private val _stream = MutableSharedFlow<Output<I>>()
    val output = _stream.asSharedFlow()

    private val input = Channel<Input>()

    private val _status = MutableStateFlow<Status>(Status.Disconnect)
    val status = _status.asStateFlow()

    private var job: Job? = null


    fun connectWhile(
        keepOpen: () -> Boolean
    ) {
        job?.cancel()
        job = workScope.launch {
            while (keepOpen()) {
                runCatching {
                    _status.value = Status.Connect
                    connectSuspend(keepOpen)
                    _status.value = Status.Disconnect
                }.onFailure {
                    _stream.emit(Output.Error(it))
                }
                if (keepOpen()) delay(1000)
            }
        }
    }

    private suspend fun connectSuspend(keepOpen: () -> Boolean) {
        client.webSocket(urlString) {

            val connectJob = launch {

                incoming.receiveAsFlow()
                    .onStart {
                        _status.value = Status.Connected
                    }.onEach { message ->
                        when (message) {
                            is Frame.Text -> {
                                val rad = message.readText()
                                try {
                                    _stream.emit(Output.Income(decoder(rad)))
                                } catch (e: Exception) {
                                    _stream.emit(Output.Error(e))
                                }
                            }

                            is Frame.Binary -> {
                                val rad = message.readBytes()
                                try {
                                    _stream.emit(Output.IncomeData(rad))
                                } catch (e: Exception) {
                                    _stream.emit(Output.Error(e))
                                }
                            }

                            else -> {
                                _stream.emit(Output.Something(message))
                            }
                        }
                    }.launchIn(this)

                launch {
                    input.consumeEach { income ->
                        runCatching {
                            send(Frame.Text(income.data))
                            workScope.launch { income.statusReceiver(SendStatus.OK) }
                        }.onFailure {
                            workScope.launch { income.statusReceiver(SendStatus.Error(it)) }
                        }
                    }
                }


            }

            while (keepOpen()) delay(1000)

            _status.value = Status.Disconnect
            connectJob.cancel()
            send(Frame.Close())
            close(reason = CloseReason(CloseReason.Codes.NORMAL, "leave"))
        }

    }

    fun send(
        obj: O,
        statusHandler: (SendStatus) -> Unit = {}
    ) = workScope.launch {
        input.send(Input(encoder(obj), statusHandler))
    }

}