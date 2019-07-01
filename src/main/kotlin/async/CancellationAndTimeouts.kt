package async

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

fun `cancell job`() = runBlocking {
    val job = launch {
        repeat(1000) {
            println("Sleeping $it")
            // The delay checks for cancellation
            delay(500L)
        }
    }
    delay(1300L)
    job.cancel()
    job.join()
}

/**
 * Not all coroutines are cancellable.
 */
fun `do not check for cancellation`() = runBlocking {
    val startTime = System.currentTimeMillis()
    val job = launch(Dispatchers.Default) {
        var nextPrintTime = startTime
        var i = 0
        while (i < 5) {
            if (System.currentTimeMillis() >= nextPrintTime) {
                println("job: Sleeping ${i++}")
                nextPrintTime += 500L
            }
        }
    }
    delay(1300L)
    println("main: Calling cancel")
    job.cancelAndJoin()
    println("main: Quitting")
}

/**
 * We can check the [kotlinx.coroutines.CoroutineScope.isActive] to see
 * if the job was cancelled or finished. Another possibility is to
 * call suspending functions from [kotlinx.coroutines] package.
 */
fun `check isActive flag for cancellation`() = runBlocking {
    val startTime = System.currentTimeMillis()
    val job = launch(Dispatchers.Default) {
        var nextPrintTime = startTime
        var i = 0
        // Check the active state in coroutine scope
        while (isActive) {
            if (System.currentTimeMillis() >= nextPrintTime) {
                println("job: Sleeping ${i++}")
                nextPrintTime += 500L
            }
        }
    }
    delay(1300L)
    println("main: Calling cancel")
    job.cancelAndJoin()
    println("main: Quitting")
}

fun `running finally block after cancellation`() = runBlocking {
    val job = launch {
        try {
            repeat(1000) {
                println("job: Sleeping $it")
                delay(500L)
            }
        } finally {
            println("job: Finally")
        }
    }
    delay(1300L)
    println("main: Calling cancel")
    job.cancelAndJoin()
    println("main: Quitting")
}

/**
 * The call to suspending function in finally block
 * will throw [kotlinx.coroutines.CancellationException]
 * because the coroutine running it is already cancelled.
 */
fun `the suspending call in finally will throw`() = runBlocking {
    val job = launch {
        try {
            repeat(1000) {
                println("job: Sleeping $it")
                delay(500L)
            }
        } finally {
            println("job: Finally")
            // This suspending function throws CancellationException
            delay(1000L)
        }
    }
    delay(1300L)
    println("main: Calling cancel")
    job.cancelAndJoin()
    println("main: Quitting")
}

/**
 * The way to run suspending function in cancelled coroutine
 * is to use [NonCancellable] context.
 */
fun `run in NonCancellable context`() = runBlocking {
    val job = launch {
        try {
            repeat(1000) {
                println("job: Sleeping $it")
                delay(500L)
            }
        } finally {
            withContext(NonCancellable) {
                println("job: Finally")
                delay(1000L)
                println("job: After delay")
            }
        }
    }
    delay(1300L)
    println("main: Calling cancel")
    job.cancelAndJoin()
    println("main: Quitting")
}

/**
 * Cancel coroutine when timeout expires. It will throw
 * [kotlinx.coroutines.TimeoutCancellationException].
 */
fun withTimeout() = runBlocking {
    withTimeout(1300L) {
        repeat(1000) {
            println("Sleeping $it")
            delay(500L)
        }
    }
}
