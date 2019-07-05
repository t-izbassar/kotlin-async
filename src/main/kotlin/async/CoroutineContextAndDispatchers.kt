package async

import kotlinx.coroutines.*

/**
 * Coroutines always execute in some context. The main element of the context
 * is job of the coroutine and it's dispatcher. The coroutine dispatcher
 * determines what thread or threads the corresponding coroutine uses.
 */
@ObsoleteCoroutinesApi
fun runCoroutinesWithDispatchers() = runBlocking<Unit> {
    // This will inherit the main thread from outer scope.
    launch {
        println("main runBlocking: Thread ${Thread.currentThread().name}")
    }
    launch(Dispatchers.Unconfined) {
        println("Unconfined: Thread ${Thread.currentThread().name}")
    }
    // Default uses the same thread as GlobalScope.launch which is background pool of threads.
    launch(Dispatchers.Default) {
        println("Default: Thread ${Thread.currentThread().name}")
    }
    // This will start dedicated thread.
    launch(newSingleThreadContext("MyThread")) {
        println("newSingleThreadContext: Thread ${Thread.currentThread().name}")
    }
    launch(Dispatchers.IO) {
        println("IO: Thread ${Thread.currentThread().name}")
    }
}

/**
 * The [Dispatchers.Unconfined] starts in caller thread, but only until first
 * suspension point. After suspension it resumes in the thread that is fully
 * determined by the suspending function that was invoked. It is appropriate
 * if coroutine does not consume CPU nor updates any shared data that is
 * confined to a specific thread.
 */
fun unconfinedVsConfined() = runBlocking<Unit> {
    launch(Dispatchers.Unconfined) {
        println("Unconfined: Thread ${Thread.currentThread().name}")
        delay(500)
        // After delay the thread is switched.
        println("Unconfined after delay: Thread ${Thread.currentThread().name}")
    }
    launch {
        println("main runBlocking: Thread ${Thread.currentThread().name}")
        delay(1000)
        println("main runBlocking after delay: Thread ${Thread.currentThread().name}")
    }
}

fun log(msg: String) = println("[${Thread.currentThread().name}] $msg")

/**
 * Debugging can be enabled with *-Dkotlinx.coroutines.debug* JVM option.
 * It will print the coroutine with the thread name since coroutines can
 * switch threads.
 */
fun debug() = runBlocking {
    val a = async {
        log("Computing a")
        6
    }
    val b = async {
        log("Computing b")
        7
    }
    log("Answer is ${a.await() + b.await()}")
}

@ObsoleteCoroutinesApi
fun switchingContexts() = runBlocking {
    newSingleThreadContext("Ctx1").use { ctx1 ->
        newSingleThreadContext("Ctx2").use { ctx2 ->
            // Running in specific context
            runBlocking(ctx1) {
                log("Started in ctx1")
                // Switching context
                withContext(ctx2) {
                    log("Working in ctx2")
                    // Retrieving job
                    println("Job is ${coroutineContext[Job]}")
                }
                log("Back to ctx1")
            }
        }
    }
}

/**
 * Children of other coroutines will be recursively cancelled
 * if their parent will be cancelled. The GlobalScope operates
 * independently.
 */
fun childrenGetCancelled() = runBlocking {
    val request = launch {
        GlobalScope.launch {
            println("job1: Run in GlobalScope")
            delay(1000)
            println("job1: Not affected with cancel")
        }
        launch {
            delay(100)
            println("job2: Child of the request")
            delay(1000)
            println("job2: Will be affected by cancel")
        }
    }
    delay(500)
    request.cancel()
    delay(1000)
    println("main: request cancelled")
}

/**
 * Parent always waits for completion of all its children. It happens
 * even without explicit [job.join] calls.
 */
fun parentWaitsForChildren() = runBlocking {
    val request = launch {
        repeat(3) { i ->
            launch {
                delay((i + 1) * 200L)
                println("Coroutine $i is done")
            }
        }
        println("request: No explicit waiting on children.")
    }
    request.join()
    println("request complete")
}

fun namingCoroutines() = runBlocking {
    log("Start main")
    val v1 = async(CoroutineName("v1")) {
        delay(500)
        log("Computing v1")
        252
    }
    val v2 = async(CoroutineName("v2")) {
        delay(1000)
        log("Computing v2")
        6
    }
    log("The answer: ${v1.await() / v2.await()}")
}

fun combiningContexts() = runBlocking<Unit> {
    launch(Dispatchers.Default + CoroutineName("named")) {
        println("In thread: ${Thread.currentThread().name}")
    }
}

fun usingOneScopeToControlTheLifecycle() {
    val scope = CoroutineScope(Dispatchers.Default)

    scope.launch {
        repeat(10) { i ->
            launch {
                delay((i + 1) * 200L) // variable delay 200ms, 400ms, ... etc
                println("Coroutine $i is done")
            }
        }
    }
    println("Launched")
    Thread.sleep(500L)
    scope.cancel()
    Thread.sleep(1000L)
}

val threadLocal = ThreadLocal<String?>()

/**
 * To preserve the thread local value within coroutines the usage of [ThreadLocal.asContextElement]
 * is required. The only limitation is that when thread-local is mutated, a new value is
 * not propagated to the coroutine caller and updated value is lost on the next suspension.
 */
fun usingThreadLocals() = runBlocking {
    threadLocal.set("main")
    println("Pre-main, thread: ${Thread.currentThread().name} value: ${threadLocal.get()}")
    val job = launch(Dispatchers.Default + threadLocal.asContextElement(value = "launch")) {
        println("Launch, thread: ${Thread.currentThread().name} value: ${threadLocal.get()}")
        yield()
        println("Launch after yield, thread ${Thread.currentThread().name} value ${threadLocal.get()}")
    }
    job.join()
    println("Post-main, thread: ${Thread.currentThread().name} value ${threadLocal.get()}")
}
