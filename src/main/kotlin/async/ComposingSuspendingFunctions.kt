package async

import kotlinx.coroutines.*
import kotlin.system.measureTimeMillis

suspend fun doSomethingOne(): Int {
    delay(1000L)
    return 13
}

suspend fun doSomethingTwo(): Int {
    delay(1000L)
    return 29
}

/**
 * The suspending functions are sequential by default.
 */
fun runSequentially() = runBlocking {
    val time = measureTimeMillis {
        val one = doSomethingOne()
        val two = doSomethingTwo()
        println("Answer is ${one + two}")
    }
    // Will denote completion in 2 seconds
    println("Completed in $time ms")
}

/**
 * [async] is used to run computations concurrently. Concurrency
 * with coroutines is explicit.
 */
fun runAsynchronously() = runBlocking {
    val time = measureTimeMillis {
        val one = async { doSomethingOne() }
        val two = async { doSomethingTwo() }
        println("Answer is ${one.await() + two.await()}")
    }
    // Will be faster than 2 seconds
    println("Completed in $time ms")
}

/**
 * We can run async computations lazily. Only run it with explicit start
 * call or when await is invoking (meaning that result is required).
 */
fun runLazily() = runBlocking {
    val time = measureTimeMillis {
        val one = async(start = CoroutineStart.LAZY) {
            doSomethingOne()
        }
        val two = async(start = CoroutineStart.LAZY) {
            doSomethingTwo()
        }
        one.start()
        two.start()
        println("Answer is ${one.await() + two.await()}")
    }
    // Will be faster than 2 seconds
    println("Completed in $time ms")
}

/**
 * Asynchronous functions:
 */
fun doSomethingOneAsync() = GlobalScope.async {
    doSomethingOne()
}

fun doSomethingTwoAsync() = GlobalScope.async {
    doSomethingTwo()
}

/**
 * This approach is not recommended as it contradicts with the idea
 * of structured concurrency.
 */
fun runAsyncFunctionsOutsideOfCoroutineScope() {
    val time = measureTimeMillis {
        // Can be called outside of coroutine scope
        val one = doSomethingOneAsync()
        val two = doSomethingTwoAsync()
        runBlocking {
            println("Answer is ${one.await() + two.await()}")
        }
    }
    println("Completed in $time ms")
}

/**
 * We can use coroutineScope to attach to the outer scope
 * when this suspending function is invoked.
 */
suspend fun concurrentSum(): Int = coroutineScope {
    val one = async { doSomethingOne() }
    val two = async { doSomethingTwo() }
    one.await() + two.await()
}

fun extractFunction() = runBlocking {
    val time = measureTimeMillis {
        println("Answer is ${concurrentSum()}")
    }
    println("Completed in $time ms")
}

suspend fun failedConcurrentSum(): Int = coroutineScope {
    val one = async {
        try {
            delay(Long.MAX_VALUE)
            42
        } finally {
            println("First cancelled")
        }
    }
    val two = async<Int> {
        println("Second throws exception")
        throw ArithmeticException()
    }
    one.await() + two.await()
}

/**
 * With structured concurrency approach the childs of the
 * outer coroutines are cancelled if something fails.
 */
fun childsAreCancelled() = runBlocking<Unit> {
    try {
        failedConcurrentSum()
    } catch (e: ArithmeticException) {
        println("Computation failed")
    }
}
