package com.diousk.coroutinesample

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.*
import timber.log.Timber

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

    fun simpleAsyncCoroutine() {
        GlobalScope.launch(Dispatchers.Default) {
            Timber.d("start async")
            val result1 = async { delay(100); return@async 1 }
            val result2 = async { delay(100); return@async 2 }
            Timber.d("sum of result = ${result1.await() + result2.await()}")
        }
    }

    fun customScope() {
        val job = Job()
        val scope = CoroutineScope(Dispatchers.Default + job)
        scope.launch {
            Timber.d("child coroutine 1 start on ${Thread.currentThread().name}")
            delay(400)
            // this log will not be printed
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
}