package async

import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

// Run the whole main as blocking coroutine.
fun example1() = runBlocking {
    // Starts new coroutine in GlobalScope.
    // GlobalScope is alive as long as JVM lives.
    GlobalScope.launch {
        // Suspends coroutine without blocking.
        delay(1_000L)
        println(Thread.currentThread())
        println("World!")
    }
    println(Thread.currentThread())
    println("Hello,")
    Thread.sleep(2_000L)

    GlobalScope.launch {
        delay(1_000L)
        println("World!")
    }
    println("Hello,")
    // This will block current thread.
    // Blocking inside coroutine is not recommended.
    runBlocking {
        delay(2_000L)
    }

    // Jobs are launched for there side-effects.
    val job: Job = GlobalScope.launch {
        delay(1_000L)
        println("World!")
    }
    println("Hello,")
    // Await completion.
    job.join()
}

fun runningAllInOneScope() = runBlocking {
    launch {
        delay(200L)
        println("Task from runBlocking")
    }

    // Create new scope. Will wait for all launched children.
    coroutineScope {
        launch {
            delay(500L)
            println("Task from nested launch")
        }

        delay(100L)
        println("Task from coroutine scope")
    }
    // This automatically waits until children are finished.
    println("Coroutine scope is over")
}

/**
 * Suspending function cannot invoke [launch]. They can use other suspending functions.
 */
suspend fun doWorld() {
    delay(1_000L)
    println("World!")
    // launch would require to have coroutine scope.
}

/**
 * The extension function on coroutine scope will have access to scope and thus will allow
 * to start new coroutine with coroutine builders.
 */
fun CoroutineScope.doWorld2() {
    launch {

    }
}

fun example2() = runBlocking {
    launch {
        doWorld()
        doWorld2()
    }
    Example3(this).doWorld3()
}

/**
 * We can store scope as a field and use it to launch new coroutine in it.
 */
class Example3 (private val scope: CoroutineScope) {
    fun doWorld3() {
        scope.launch {

        }
    }
}

/**
 * Another way would be to extend [CoroutineScope] and by that way retrieve access to scope.
 */
class Example4 : CoroutineScope {
    override val coroutineContext: CoroutineContext
        get() = TODO("not implemented")

    fun doWorld4() {
        this.launch {

        }
    }
}

fun globalScopeInsideOtherScopeIsDaemon() = runBlocking {
    // If we run GlobalScope inside other scope then it will be daemon thread.
    GlobalScope.launch {
        repeat(1000) {
            println("Sleeping $it")
            delay(500L)
        }
    }
    // This won't wait for full completion of previous launch.
    delay(1300L)
}
