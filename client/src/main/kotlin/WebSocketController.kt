package org.example

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import org.example.WebSocketStream.SendStatus

class WebSocketController<I : Any, O> {

    private var streamFlow = MutableStateFlow<WebSocketStream<I, O>?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val output = streamFlow.flatMapLatest {
        it?.output ?: flow { }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val status = streamFlow.flatMapLatest {
        it?.status ?: flow { }
    }


    fun connect(stream: WebSocketStream<I, O>?) {
        streamFlow.value = stream
        stream?.connectWhile { stream == streamFlow.value }
    }

    fun disconnect() {
        streamFlow.value = null
    }

    fun send(
        obj: O,
        retries: Int = 1,
        statusHandler: (SendStatus) -> Unit = {}
    ) = streamFlow.value?.send(obj, retries, statusHandler)


}