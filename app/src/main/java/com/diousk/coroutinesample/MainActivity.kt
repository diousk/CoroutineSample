package com.diousk.coroutinesample

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.jakewharton.rxrelay3.PublishRelay
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.processors.PublishProcessor
import io.reactivex.rxjava3.subjects.BehaviorSubject
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.asPublisher
import kotlinx.coroutines.rx3.asFlowable
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.zip.CRC32
import java.util.zip.CheckedInputStream


object DiskUtils {

    /**
     * return free disk space in MB
     * */
    @SuppressLint("UsableSpace")
    @JvmStatic
    fun getFreeSpace(context: Context): Long {
        val denominator = 1024 * 1024
        return context.externalCacheDir?.usableSpace?.div(denominator) ?: 0
    }

    fun getFileCRCCode(file: File?): String? {
        try {
            val fileinputstream = FileInputStream(file)
            val crc32 = CRC32()
            val buf = ByteArray(1024 * 64) //fetch 64k byte to speed up
            val checkedinputstream = CheckedInputStream(fileinputstream, crc32)
            while (checkedinputstream.read(buf) != -1) {
            }
            return String.format("%08X", crc32.value).toLowerCase()
        } catch (e: IOException) {
        }
        return ""
    }
}

class MainActivity : AppCompatActivity() {
    private val viewModel by viewModels<MainViewModel>()

    var relay: PublishRelay<Long> = PublishRelay.create()
    var behavior: BehaviorSubject<Long> = BehaviorSubject.create()
    var processor = PublishProcessor.create<Long>()

    private var items: MutableList<Int> = mutableListOf()

    private var disposable: Disposable? = null
    private var counter = 0

    val testSharedFlow = MutableSharedFlow<Int>(extraBufferCapacity = 4)

    val testScope = CoroutineScope(CoroutineName("testScopeName") + Dispatchers.IO)

    @ExperimentalCoroutinesApi
    val testCallbackFlow = callbackFlow {
        Timber.d("callbackFlow start")
        (0..5).forEach {
            delay(1000)
            Timber.d("callbackFlow send $it")
            send(it)
        }

        delay(2000)
        Timber.d("callbackFlow send $counter")
        lifecycleScope.launchWhenCreated { send(counter++) }

        awaitClose { Timber.d("testCallbackFlow close") }
    }.shareIn(testScope, SharingStarted.Lazily)

    @ExperimentalCoroutinesApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.plant(Timber.DebugTree())
        setContentView(R.layout.activity_main)

        Timber.d("locale ${Locale.getDefault().toString()}")

        val notifyChannel = MutableSharedFlow<Unit>(
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )

//        lifecycleScope.launchWhenCreated {
//            testSharedFlow
//                .conflate()
//                .onEach { delay(2000) }
//                .collect {
//                    Timber.d("testSharedFlow 1 recv $it")
//                }
//        }
//
//        lifecycleScope.launchWhenCreated {
//            testSharedFlow
//                .takeWhile { it < 5 }
//                .collect {
//                    Timber.d("testSharedFlow 2 recv $it")
//                }
//        }

        button.setOnClickListener {
//            lifecycleScope.launchWhenCreated {
//                (0..10).forEach {
//                    delay(100)
//                    Timber.d("start emit $it")
//                    testSharedFlow.emit(it)
//                }
//            }

            lifecycleScope.launchWhenCreated {
                testCallbackFlow
//                    .asFlowable()
//                    .doOnNext { Timber.d("doOnNext $it") }
////                    .timeout(1500, TimeUnit.MILLISECONDS)
////                    .onErrorReturn {
////                        Timber.d("error $it")
////                        999
////                    }
//                    .asFlow()
//                    .onEach {
//                        Timber.d("testCallbackFlow start delay for $it")
//                        delay(2000)
//                    }
                    .onCompletion { Timber.d("testCallbackFlow complete") }
                    .collect {
                        Timber.d("testCallbackFlow 1 recv $it")
                    }
            }

            lifecycleScope.launchWhenCreated {
                testCallbackFlow.takeWhile { it < 5 }.collect {
                    // Timber.d("testCallbackFlow 2 recv $it")
                }
            }
        }

//        with(items) {
//            clear()
//        }
//
//        button.setOnClickListener {
//            counter++
//            thread {
//                processor.onNext(counter)
//            }
//        }
//
//        Single.just(1)
//            .doOnSuccess { error("test") }
//            .onErrorReturn { 2; error("test2") }
//            .map { it*100 }
//            .subscribe ({
//                Timber.d("result $it")
//            }, {
//
//            })
    }

}
