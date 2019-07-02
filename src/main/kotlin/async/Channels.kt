package async

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*

fun `chanell basics`() = runBlocking {
    val channel = Channel<Int>()
    launch {
        for (x in 1..5)
            // This function is suspending
            channel.send(x * x)
    }
    repeat(5) {
        println(
            // Suspending function
            channel.receive()
        )
    }
    println("End")
}

fun `closing channel`() = runBlocking {
    val channel = Channel<Int>()
    launch {
        for (x in 1..5) channel.send(x * x)
        // Denote finishing sending to channel
        channel.close()
    }
    // Suspend until channel is closed
    for (y in channel) println(y)
    println("End")
}

/**
 * There is coroutine builder [produce] that can be used
 * to denote producer.
 */
@ExperimentalCoroutinesApi
fun `producer consumer`() = runBlocking {
    val squares = produce {
        for (x in 1..5) send(x * x)
    }
    squares.consumeEach {
        println(it)
    }
    println("End")
}

/**
 * Infinite producer.
 */
@ExperimentalCoroutinesApi
fun CoroutineScope.produceNumbers() = produce {
    var x = 1
    while (true) send(x++)
}

/**
 * Using previous producer to receive numbers.
 */
@ExperimentalCoroutinesApi
fun CoroutineScope.square(numbers: ReceiveChannel<Int>): ReceiveChannel<Int> = produce {
    for (x in numbers) send(x * x)
}

@ExperimentalCoroutinesApi
fun `use producer consumer`() = runBlocking {
    val numbers = produceNumbers()
    val squares = square(numbers)
    for (i in 1..5) println(squares.receive())
    println("End")
    coroutineContext.cancelChildren()
}

@ExperimentalCoroutinesApi
fun CoroutineScope.produceTen() = produce {
    var x = 1
    while (true) {
        send(x++)
        delay(100)
    }
}

fun CoroutineScope.launchProcessor(id: Int, channel: ReceiveChannel<Int>) = launch {
    for (msg in channel) {
        println("Processor #$id received $msg")
    }
}

@ExperimentalCoroutinesApi
fun `consume from same channel with concurrent processors`() = runBlocking {
    val producer = produceTen()
    repeat(5) {
        // Take from same channel
        launchProcessor(it, producer)
    }
    delay(950)
    // This will close channel ending iteration
    producer.cancel()
}

suspend fun sendString(channel: SendChannel<String>, s: String, time: Long) {
    while (true) {
        delay(time)
        channel.send(s)
    }
}

fun `send to the same channel from concurrent producers`() = runBlocking {
    val channel = Channel<String>()
    // Send to the same channel
    launch { sendString(channel, "foo", 200L) }
    launch { sendString(channel, "BAR", 500L) }
    repeat(6) {
        // Receive not knowing who produced
        println(channel.receive())
    }
    coroutineContext.cancelChildren()
}

/**
 * The default channels do not have buffer. If send invoked first
 * then it is suspended until receive is invoked. If receive invoked
 * first the it is suspended until send is invoked.
 */
fun `specify buffer`() = runBlocking {
    // Specify capacity
    val channel = Channel<Int>(4)
    val sender = launch {
        repeat(10) {
            println("Sending $it")
            // This won't suspend until capacity is reached
            channel.send(it)
        }
    }
    delay(1000)
    sender.cancel()
}

data class Ball(var hits: Int)

suspend fun player(name: String, table: Channel<Ball>) {
    for (ball in table) {
        ball.hits++
        println("$name $ball")
        delay(300)
        table.send(ball)
    }
}

/**
 * Send and receive operations to channels are served in FIFO order.
 * The first coroutine to invoke receive gets the element.
 */
fun `fifo in send receive operations`() = runBlocking {
    val table = Channel<Ball>()
    // The order is respected
    launch { player("ping", table) }
    // The pong will be second as it is started after ping
    launch { player("pong", table) }
    table.send(Ball(0))
    delay(1000)
    coroutineContext.cancelChildren()
}

/**
 * Ticker produces [Unit] after delays in consumers.
 */
@ObsoleteCoroutinesApi
fun `producing ticker channel might be useful for timed events`() = runBlocking {
    val tickerChannel = ticker(delayMillis = 100, initialDelayMillis = 0)
    var nextElement = withTimeoutOrNull(1) {
        tickerChannel.receive()
    }
    println("Initial element is available immediately: $nextElement")

    nextElement = withTimeoutOrNull(50) {
        tickerChannel.receive()
    }
    println("Next element is not ready in 50 ms: $nextElement")

    nextElement = withTimeoutOrNull(60) {
        tickerChannel.receive()
    }
    println("Next element is ready in 100 ms: $nextElement")

    println("Consumer pauses for 150 ms")
    delay(150)

    nextElement = withTimeoutOrNull(1) {
        tickerChannel.receive()
    }
    println("Next element is available immediately after large delay: $nextElement")

    nextElement = withTimeoutOrNull(50) {
        tickerChannel.receive()
    }
    println("Next element is ready in 50 ms after consumer pause: $nextElement")

    tickerChannel.cancel()
}
