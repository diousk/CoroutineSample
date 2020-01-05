package com.diousk.coroutinesample

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*
import timber.log.Timber
import java.util.*

class MainViewModel : ViewModel() {
    fun simpleCoroutine() {
        GlobalScope.launch {
            Timber.d("child coroutine 1 start on ${Thread.currentThread().name}")
            delay(400)
            Timber.d("child coroutine 1 end on ${Thread.currentThread().name}")
        }

        GlobalScope.launch {
            Timber.d("child coroutine 2 start on ${Thread.currentThread().name}")
            delay(200)
            Timber.d("child coroutine 2 end on ${Thread.currentThread().name}")
        }
    }

    fun customScopeWithNormalJob() {
        val exceptionHandler = CoroutineExceptionHandler { _, _ -> }
        val job = Job()
        val scope = CoroutineScope(Dispatchers.Default + job + exceptionHandler)
        scope.launch {
            Timber.d("child coroutine 1 start on ${Thread.currentThread().name}")
            delay(400)
            // this log will not be printed
            Timber.d("child coroutine 1 end on ${Thread.currentThread().name}")
        }

        scope.launch {
            Timber.d("child coroutine 2 start on ${Thread.currentThread().name}")
            delay(200)
            Timber.d("child coroutine 2 end on ${Thread.currentThread().name}")
            try {
                throw Throwable("child 2 throw") // propagate error to its parent
            } catch (e: Exception) {
                // this log will not be printed
                Timber.d("child 2 catch")
            }
        }
    }

    fun simpleAsyncCoroutine() {
        GlobalScope.launch(Dispatchers.Default) {
            Timber.d("start async")
            val result1 = async { delay(100); return@async 1 }
            val result2 = async { delay(100); return@async 2 }
            Timber.d("sum of result = ${result1.await() + result2.await()}")
        }
    }

    fun errorAsyncCoroutine() {
        GlobalScope.launch(Dispatchers.Default) {
            Timber.d("start async")
            val result1 = async { delay(400); return@async 1 }
            val result2 = async { delay(100); error("an error"); return@async 2 }
            val valueForResult2 = try {
                result2.await()
            } catch (e: Exception) {
                // can not catch here because the error propagate to its parent first
                100
            }
            Timber.d("sum of result = ${result1.await() + valueForResult2}")
        }
    }

    fun scopedErrorAsyncCoroutine() {
        GlobalScope.launch(Dispatchers.Default) {
            Timber.d("start async")
            val result1 = async { delay(400); return@async 1 }

            val valueForResult2 = try {
                coroutineScope {
                    val result2 = async { delay(100); error("an error"); return@async 2 }
                    result2.await()
                }
            } catch (e: Exception) {
                // fine to catch here
                100
            }

            Timber.d("sum of result = ${result1.await() + valueForResult2}")
        }
    }

    fun customScopeSupervisorJob() {
        val job = SupervisorJob()
        val scope = CoroutineScope(Dispatchers.Default + job)
        scope.launch {
            Timber.d("child coroutine 1 start on ${Thread.currentThread().name}")
            delay(400)
            // this log will be printed
            Timber.d("child coroutine 1 end on ${Thread.currentThread().name}")
        }

        scope.launch(CoroutineExceptionHandler { _, throwable ->
            Timber.d("child 2 parent handle $throwable")
        }) {
            Timber.d("child coroutine 2 start on ${Thread.currentThread().name}")
            delay(200)
            Timber.d("child coroutine 2 end on ${Thread.currentThread().name}")
            try {
                throw Throwable("child 2 throw") // propagate error to its parent
            } catch (e: Exception) {
                Timber.d("child 2 catch")
            }
        }
    }

    fun supervisorScopeAsyncError() {
        suspend fun childSupervisor() = supervisorScope {
            launch { delay(400); Timber.d("child done") }
            val result = async { delay(100); error("an error"); 1 }
            try {
                result.await()
            } catch (e: Exception) {
                Timber.d("child catch $e")
            }
        }
        viewModelScope.launch {
            childSupervisor()
        }
    }

    fun supervisorScopeLaunchError() {
        suspend fun childSupervisor() = supervisorScope {
            val result = async { delay(400); Timber.d("child done"); 1 }

            val handler = CoroutineExceptionHandler { _, exception ->
                println("Caught $exception")
            }
            launch(handler) { // handle error here because child's failure is not propagated to the parent
                delay(100)
                error("an error")
            }

            result.await()
        }
        viewModelScope.launch {
            childSupervisor()
        }
    }

    fun structuredConcurrency() {
        suspend fun childScopeFunction() = coroutineScope {
            val result1 = async { delay(500); 1 }
            val result2 = async {
                delay(400)
                error("error 1")
                2
            }
            val result2recover = try {
                result2.await()
            } catch (e: Exception) { // propagate error to its parent
                // won't be able to catch here
                0
            }
            result1.await() + result2recover
        }

        suspend fun childSupervisorFunction() = supervisorScope {
            val result1 = async { delay(500); 1 }
            val result2 = async {
                delay(400)
                error("error 2")
                2
            }
            val result2recover = try {
                result2.await()
            } catch (e: Exception) {
                0
            }
            result1.await() + result2recover
        }

        val job = SupervisorJob()
        val scope = CoroutineScope(Dispatchers.Default + job)

        scope.launch(CoroutineExceptionHandler { _, throwable ->
            Timber.d("parent handle error $throwable")
        }) {
            try {
                Timber.d("1-1 child start")
                childScopeFunction()
                Timber.d("1-1 child done")
            } catch (e: Exception) {
                Timber.d("1-1 child scope catch $e")
            }

            try {
                Timber.d("1-2 child start")
                childSupervisorFunction()
                Timber.d("1-2 child done")
            } catch (e: Exception) {
                Timber.d("1-2 child scope catch $e")
            }
        }
    }

    fun switchDispatcher() {
        viewModelScope.launch {
            Timber.d("launch on ${Thread.currentThread().name}")
            withContext(Dispatchers.Default) {
                Timber.d("dispatched on ${Thread.currentThread().name}")
            }
            Timber.d("end on ${Thread.currentThread().name}")
        }
    }

    fun simpleChannel() {
        val channel = Channel<Int>()
        viewModelScope.launch {
            launch(Dispatchers.Default) {
                (1..5).forEach {
                    delay(100)
                    Timber.d("send on ${Thread.currentThread().name}")
                    channel.send(it)
                }
                channel.close()
                Timber.d("done send")
            }

            launch {
                channel.consumeEach {
                    Timber.d("recv $it on ${Thread.currentThread().name}")
                }
                Timber.d("done recv on ${Thread.currentThread().name}")
            }
        }
    }

    fun bufferChannel() {
        val channel = Channel<Int>(3)
        viewModelScope.launch {
            launch {
                (1..10).forEach {
                    delay(100)
                    println("send $it")
                    channel.send(it)
                }
                channel.close()
                println("done send")
            }

            launch {
                delay(500)
                println("recv start consume")
                channel.consumeEach {
                    println("recv $it")
                }
                println("done recv")
            }
        }
    }

    fun broadcastChannel() {

    }

    fun channelFlow() {

    }
}