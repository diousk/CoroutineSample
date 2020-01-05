package com.diousk.coroutinesample.ext

import android.view.View
import androidx.annotation.CheckResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

/**
 * Example of usage:
 * ```
    button.clicks()
    .throttleFist(1000)
    .onEach {
        // handle each click
    }
    .launchIn(lifecycleScope)
 * ```
 */
@CheckResult
@UseExperimental(ExperimentalCoroutinesApi::class)
fun View.clicks(): Flow<Unit> {
    val flow: Flow<Unit> = callbackFlow {
        val listener = View.OnClickListener {
            safeOffer(Unit)
        }
        setOnClickListener(listener)
        awaitClose { setOnClickListener(null) }
    }
    return flow.conflate()
}