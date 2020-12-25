package com.diousk.coroutinesample

import androidx.appcompat.widget.AppCompatImageView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import com.diousk.coroutinesample.ext.retry
import com.diousk.coroutinesample.ext.square
import io.reactivex.rxjava3.processors.PublishProcessor
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*
import timber.log.Timber
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class MainViewModel : ViewModel() {

    val channel = Channel<Int>().receiveAsFlow().asLiveData()

    val bus = PublishProcessor.create<Int>()
    private var items: MutableList<Int> = mutableListOf()
    init {
//        bus.toObservable()
//            .concatMap {
//                Observable.just(it).delay(Random.nextLong(1, 5), TimeUnit.SECONDS)
//            }
//            .subscribe { Timber.d("item $it") }
    }

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

    fun nestedCoroutine() {
        val job = viewModelScope.launch {
            Timber.d("child 1 start")
            launch {
                Timber.d("nested 1 start")
                delay(400)
                // this log will not be printed
                Timber.d("nested 1 end")
            }
        }
        viewModelScope.launch {
            Timber.d("child 2 start")
            delay(500)
            Timber.d("child 2 end")
        }

        viewModelScope.launch {
            Timber.d("child 3 start")
            delay(100)
            Timber.d("child 3 end")
            job.cancel()
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
        Timber.d("errorAsyncCoroutine")

        (1..20).onEach {
            bus.offer(it)
        }
    }

    fun scopedErrorAsyncCoroutine() {
        suspend fun childCoroutineScope() = coroutineScope {
            val result2 = async { delay(100); error("an error"); return@async 2 }
            // throw at scope layer
            result2.await()
        }

        GlobalScope.launch(Dispatchers.Default) {
            Timber.d("start async")
            val result1 = async { delay(400); return@async 1 }

            // catch the scope error
            val valueForResult2 = try {
                childCoroutineScope()
            } catch (e: Exception) {
                // fine to catch here
                100
            }

            Timber.d("sum of result = ${result1.await() + valueForResult2}")
        }
    }


    fun customScopeWithNormalJobLaunch() {
        val exceptionHandler = CoroutineExceptionHandler { _, _ -> }
        val job = Job()
        val scope = CoroutineScope(Dispatchers.Default + job + exceptionHandler)
        scope.launch {
            Timber.d("child coroutine 1 start on ${Thread.currentThread().name}")
            delay(400)
            // this log will not be printed
            Timber.d("child coroutine 1 end on ${Thread.currentThread().name}")
        }

        scope.launch(CoroutineExceptionHandler { coroutineContext, throwable ->
            Timber.d("child coroutine 2 handle error")
        }) {
            Timber.d("child coroutine 2 start on ${Thread.currentThread().name}")
            delay(200)
            Timber.d("child coroutine 2 end on ${Thread.currentThread().name}")
            // throw in scope layer
            throw Exception("child 2 throw") // propagate error to its parent
        }
    }

    fun customScopeSupervisorJobLaunch() {
        val job = SupervisorJob()
        val scope = CoroutineScope(Dispatchers.Default + job)
        scope.launch {
            Timber.d("child coroutine 1 start on ${Thread.currentThread().name}")
            delay(400)
            // this log will be printed
            Timber.d("child coroutine 1 end on ${Thread.currentThread().name}")
        }

        scope.launch(CoroutineExceptionHandler { _, throwable ->
            Timber.d("child coroutine 2 handle error")
        }) {
            Timber.d("child coroutine 2 start on ${Thread.currentThread().name}")
            delay(200)
            Timber.d("child coroutine 2 end on ${Thread.currentThread().name}")
            // throw in scope layer
            throw Exception("child 2 throw") // propagate error to its parent
        }
    }

    // Not recommended
    fun customScopeSupervisorJobNestedLaunch() {
        val job = Job()
        val scope = CoroutineScope(Dispatchers.Default + job)
        // passing SupervisorJob to launch only works for this "scope.launch{}",
        // and does not work for the nested coroutines
        scope.launch(SupervisorJob() + CoroutineExceptionHandler { _, error ->
            // this log will be printed, child error is handled here
            Timber.d("parent handle error $error")
        }) {
            Timber.d("child coroutine 1 start on ${Thread.currentThread().name}")
            launch {
                Timber.d("child 1-1 launch")
                delay(500)
                // this log will not be printed because it will be cancelled
                Timber.d("child 1-1 done")
            }

            launch(CoroutineExceptionHandler { _, _ ->
                // this log will not be printed, propagate error to its parent
                // use supervisorScope{} if we want to handle error individually, see
                // supervisorScopeAsyncError() or supervisorScopeLaunchError()
                Timber.d("child 1-2 handle error")
            }) {
                Timber.d("child 1-2 launch with error")
                delay(100)
                throw IllegalStateException("child 1-2 throw")
            }
            Timber.d("child coroutine 1 end on ${Thread.currentThread().name}")
        }

        scope.launch {
            Timber.d("another child start")
            delay(1000)
            // this log will still be printed because the original coroutine runs on another job
            Timber.d("another child end")
        }
    }

    fun supervisorScopeAsyncError() {
        suspend fun childSupervisor() = supervisorScope {
            launch { delay(400); Timber.d("child done") }
            val result = async { delay(100); error("an error"); 1 }
            try {
                result.await()
            } catch (e: Exception) {
                // fine to catch
                Timber.d("child catch $e")
            }
        }
        viewModelScope.launch {
            childSupervisor()
        }
    }

    fun supervisorScopeLaunchError() {
        suspend fun childSupervisor() = supervisorScope {
            val result = async {
                delay(400); Timber.d("child done"); 1
            }

            launch(CoroutineExceptionHandler { _, exception ->
                // handle error here because this child's failure can be handled individually
                println("Caught $exception")
            }) {
                delay(100)
                error("an error")
            }

            result.await()
        }
        viewModelScope.launch {
            childSupervisor()
        }
    }

    fun supervisorScopeScopeError() {
        suspend fun childSupervisor(): Unit = supervisorScope {
            launch {
                delay(100)
            }
            error("scope error")
        }
        viewModelScope.launch {
            try {
                childSupervisor()
            } catch (e: Exception) {
                println("Caught $e")
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
                Timber.d("child 1-1 throw")
                // await() will propagate error to its parent
                result2.await()
            } catch (e: Exception) {
                Timber.d("child 1-1 catch")
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
                // it's ok to handle error individually
                result2.await()
            } catch (e: Exception) {
                0
            }
            result1.await() + result2recover
        }

        val job = SupervisorJob()
        val scope = CoroutineScope(Dispatchers.Default + job)

        scope.launch(CoroutineExceptionHandler { _, throwable ->
            Timber.d("parent handle child error $throwable")
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

    fun retryOnSuspend() {
        viewModelScope.launch(CoroutineExceptionHandler { coroutineContext, throwable ->
            Timber.d("handle error $throwable")
        }) {
            val value = retry(times = 3) {
                withContext(Dispatchers.IO) { delay(100); error("an error"); "" }
            }
        }
    }

    fun liveDataBuilder() {
        suspend fun fetchData(): String = withContext(Dispatchers.Default) { "" }

        val liveData = liveData {
            emit(fetchData())
        }
    }

    // --- Channel ---

    fun simpleChannel() {
        val channel = Channel<Int>()
        viewModelScope.launch {
            launch {
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

    fun channelBuilder() {
        viewModelScope.launch {
            val channel = produce {
                (1..10).forEach {
                    delay(100)
                    Timber.d("send : $it")
                    send(it)
                }
            }
            // if we don't call consumeEach, the produce is still active
            channel.consumeEach {
                Timber.d("recv : $it")
            }
        }
    }

    @Deprecated("the actor will be removed in the future")
    fun simpleActor() {
        viewModelScope.launch {
            val actorChannel = actor<Int> {
                consumeEach { Timber.d("recv: $it") }
            }

            (1..10).forEach { actorChannel.send(it) }
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
        val channel = BroadcastChannel<Int>(Channel.CONFLATED)
        viewModelScope.launch {
            launch {
                // note: the consumeEach will suspend until close
                channel.openSubscription().consumeEach {
                    println("recv1 $it")
                }
                println("recv1 done")
            }
            launch {
                channel.openSubscription().consumeEach {
                    println("recv2 $it")
                }
                println("recv2 done")
            }
            launch {
                (1..10).forEach { delay(100); channel.send(it) }
                println("send done")
                channel.close()
            }
        }
    }

    // --- Flow ---

    fun simpleFlow() {
        val intFlow = flow {
            (1..10).forEach { emit(it) }
        }
        intFlow
            .onEach { Timber.d("recv: $it") }
            .launchIn(viewModelScope)
    }

    fun basicFlow() {
        val intFlow = flow {
            (1..10).forEach { emit(it) }
        }
        viewModelScope.launch {
            intFlow.collect {
                Timber.d("recv: $it")
            }
        }
    }

    fun errorFlow() {
        val flow = flow {
            // error to emit element in another coroutine
            viewModelScope.launch {
                emit(1)
            }
        }
        viewModelScope.launch {
            flow.collect { Timber.d("recv: $it") }
        }
    }

    fun channelFlow() {
        // use channelFlow when need to emit item from different coroutines (like errorFlow() above)
        viewModelScope.launch {
            val flow = channelFlow {
                launch { send(2) }
                send(1)
            }
            flow.collect { Timber.d("recv: $it") }
        }
    }

    fun switchDispatcherFlow() {
        flow {
            Timber.d("emit on thread: ${Thread.currentThread().name}")
            (1..10).forEach { emit(it) }
        }
            .flowOn(Dispatchers.IO) // apply dispatcher IO above flowOn
            .onEach {
                Timber.d("first onEach $it on thread: ${Thread.currentThread().name}")
            }
            .flowOn(newSingleThreadContext("custom-thread")) // apply custom-thread above flowOn
            .onEach {
                Timber.d("second onEach $it on thread: ${Thread.currentThread().name}")
            }
            .launchIn(viewModelScope)
    }

    fun zipFlows() {
        val flow1 = (1..10).asFlow()
        val flow2 = (11..20).asFlow()
        flow1.zip(flow2) { i, j -> i + j}
            .onEach { Timber.d("value : $it") }
            .launchIn(viewModelScope)
    }

    fun mergeFlows() {
        val flow1 = (1..100).asFlow()
        val flow2 = (101..200).asFlow()
        merge(flow1, flow2)
            .onEach { Timber.d("value : $it") }
            .launchIn(viewModelScope)
    }

    fun squareFlow() {
        val flow = (1..10).asFlow()
        flow.square()
            .onEach { Timber.d("value : $it") }
            .launchIn(viewModelScope)
    }
}