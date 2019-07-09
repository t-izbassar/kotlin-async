package async

import kotlinx.coroutines.*
import java.io.IOException

/**
 * Coroutine builders come in two flavors:
 * - [launch], [actor] propagates exceptions automatically;
 * - [async], [produce] exposes them to users.
 *
 * The automatic propagation treat exceptions as unhandled,
 * while the latter is relying on the user to consume
 * the final exception (via [await] or [receive] calls).
 */
fun exceptionPropagation() = runBlocking {
    val job = GlobalScope.launch {
        println("Throwing exception from launch")
        // Will be printed to the console by Thread.defaultUncaughtExceptionHandler
        throw IndexOutOfBoundsException()
    }
    job.join()
    println("Joined failed job")
    val deferred = GlobalScope.async {
        println("Throwing exception from async")
        // Relying on user to call await
        throw ArithmeticException()
    }
    try {
        deferred.await()
        println("Unreached")
    } catch (e: ArithmeticException) {
        println("Caught ArithmeticException")
    }
}


fun registeringCoroutineExceptionHandler() = runBlocking {
    // Handler is invoked only on exceptions which are not expected to be handled by the user
    val handler = CoroutineExceptionHandler { coroutineContext, throwable ->
        println("Caught $throwable in $coroutineContext")
    }
    val job = GlobalScope.launch(handler) {
        throw AssertionError()
    }
    // Has no effect, as it is expected from user to handle it in await call
    val deferred = GlobalScope.async(handler) {
        throw ArithmeticException()
    }
    job.join()
    deferred.join()
}

/**
 * If coroutine encounters exception other than [CancellationException]
 * it cancels its parent with that exception.
 */
fun cancelChildrenWithoutCancellingItself() = runBlocking {
    val job = launch {
        val child = launch {
            try {
                delay(Long.MAX_VALUE)
            } finally {
                println("child cancelled")
            }
        }
        yield()
        println("cancelling child")
        // Does not cancel the parent if cause is not provided
        child.cancel()
        child.join()
        yield()
        println("parent is not cancelled")
    }
    job.join()
}

/**
 * The original exception is handled by the parent when all its children terminate.
 */
fun handleExceptionAfterTermination() = runBlocking {
    val handler = CoroutineExceptionHandler { coroutineContext, throwable ->
        println("Caught $throwable in $coroutineContext")
    }
    val job = GlobalScope.launch(handler) {
        launch {
            try {
                delay(Long.MAX_VALUE)
            } finally {
                withContext(NonCancellable) {
                    // Exception is handled after all children terminate
                    println("Children cancelled, but exception is not handled")
                    delay(100)
                    println("The first child finished")
                }
            }
        }
        launch {
            delay(10)
            println("Second throws")
            throw ArithmeticException()
        }
    }
    job.join()
}

/**
 * First exception wins, other exceptions will be suppressed.
 */
fun throwingMultipleExceptions() = runBlocking {
    val handler = CoroutineExceptionHandler { _, throwable ->
        println("Caught $throwable with suppressed ${throwable.suppressed.contentToString()}")
    }
    val job = GlobalScope.launch(handler) {
        launch {
            try {
                delay(Long.MAX_VALUE)
            } finally {
                throw ArithmeticException()
            }
        }
        launch {
            delay(100)
            throw IOException()
        }
        delay(Long.MAX_VALUE)
    }
    job.join()
}

/**
 * Cancellation exceptions are unwrapped by default.
 */
fun unwrapCancellationExceptions() = runBlocking {
    val handler = CoroutineExceptionHandler { _, throwable ->
        println("Caught original $throwable")
    }
    val job = GlobalScope.launch(handler) {
        val inner = launch {
            launch {
                launch {
                    throw IOException()
                }
            }
        }
        try {
            inner.join()
            // Catching CancellationException instead of IOException
        } catch (e: CancellationException) {
            println("Rethrowing original $e")
            throw e
        }
    }
    // But this will caught IOException
    job.join()
}

/**
 * With [SupervisorJob] it is possible to cancel children.
 */
fun cancelChildrenWithSupervisor() = runBlocking {
    val supervisor = SupervisorJob()
    with(CoroutineScope(coroutineContext + supervisor)) {
        val first = launch(CoroutineExceptionHandler { _, _ ->  }) {
            println("First is failing")
            throw AssertionError()
        }
        val second = launch {
            first.join()
            println("First cancelled: ${first.isCancelled}, but second alive")
            try {
                delay(Long.MAX_VALUE)
            } finally {
                println("Second cancelled because supervisor is cancelled")
            }
        }
        first.join()
        println("Cancelling supervisor")
        supervisor.cancel()
        second.join()
    }
}

/**
 * With [supervisorScope] we can propagate cancellation downwards.
 */
fun propagateWithSupervisorScope() = runBlocking {
    try {
        supervisorScope {
            launch {
                try {
                    println("Child sleep")
                    delay(Long.MAX_VALUE)
                } finally {
                    println("Child cancelled")
                }
            }
            yield()
            println("Throwing from scope")
            throw AssertionError()
        }
    } catch (e: AssertionError) {
        println("Caught $e")
    }
}

/**
 * Every child in supervised jobs should handle its exceptions by itself. This
 * difference comes from the fact that child's failure is not propagated to the
 * parent.
 */
fun exceptionPropagationInSupervisedScope() = runBlocking {
    val handler = CoroutineExceptionHandler { _, throwable ->
        println("Caught $throwable")
    }
    supervisorScope {
        launch(handler) {
            println("Child throws")
            throw AssertionError()
        }
        println("Scope completed")
    }
    println("After scope")
}
