# How to use

```
// Есть какой то класс оболочка на прием, а можно и без, всего то заменяем decoder
@Serializable
data class Data(
    @SerialName("data")
    val data: String
) : SerializedAny()

// Есть какой то класс оболочка на отправку, а можно и без, всего то заменяем encoder
@Serializable
data class Output(
    @SerialName("data")
    val data: String
)

private suspend fun open(client: HttpClient, text: String) {
    // Создаем наш сокет
  
     val stream = WebSocketStream<Data, Output>(
        client = client,
        workScope = CoroutineScope(Dispatchers.IO),
        urlString = "ws://localhost:8078/ws"
    )

    // вешаем слушателей
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

    // флаг, что нужно продолжать работу
    var keep = true

    // сокет с некоторой переодчностью сам спрашивает, нужно ли ему работать дальше
    // если связь оборвется, то будет пробовать переподключться
    stream.connectWhile { keep }

    // Тут мы отправляем данные, в ответ получаем Job. Можно ждать завершения посылки, а можно и не ждать
    repeat(5) {
        // job создается от workScope. Если workScope закрыть, то и производные закроются.
        stream.send(Output(text)).join()
        delay(1000)
    }

    // В конце говорим что продолжать не нужно и сокет сам отключится.
    keep = false
}
```
