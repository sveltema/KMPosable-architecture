@file:Suppress("unused")

package com.labosu.kmposable

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

/**
 * Created by Steven Veltema on 2022/09/22
 */

// All Effects are scoped to their Store and will be canceled when the StoreScope is canceled.

// access needs to be single threaded
private val cancellationMutex = Mutex()

// the scope/tokenSet pair uses the scope for cancellation and tokenSet to control individual flow completion and cache cleanup
private val cancellableFlowSignals = mutableMapOf<Any, Pair<MutableSharedFlow<Unit>, MutableSet<Long>>>()

internal class CompletedException : Exception("Cancellable has completed")

// use a MutableSharedFlow as a signal to cancel the effect flow
fun <Action> Effect<Action>.cancellable(cancellationId: Any, cancelInFlight: Boolean = false): Effect<Action> =
    Effect {
        val flowToken = Random.nextLong()
        val innerFlow = this().cancellable()

        flow {
            val cancellationNotifier = cancellationMutex.withLock {
                // cancel any active effects
                if (cancelInFlight) cancellableFlowSignals.remove(cancellationId)?.first?.emit(Unit)
                val notifierTokenPair = cancellableFlowSignals.getOrPut(cancellationId) {
                    Pair(MutableSharedFlow(), mutableSetOf())
                }
                notifierTokenPair.second.add(flowToken)
                notifierTokenPair.first
            }

            try {
                coroutineScope {
                    val job = launch(start = CoroutineStart.UNDISPATCHED) {
                        cancellationNotifier.take(1).collect()
                        // if the signal emits, throw an Exception to finish the scope
                        // which will end the outer flow
                        throw CompletedException()
                    }
                    innerFlow.collect { emit(it) }
                    // if the innerFlow completes, cancel the signal collection job
                    job.cancel()
                }
            } catch (exception: CompletedException) {
                // ignore
            }
        }
            .onCompletion {
                cancellationMutex.withLock {
                    val tokenSet = cancellableFlowSignals[cancellationId]?.second?.apply {
                        remove(flowToken)
                    }
                    if (tokenSet.isNullOrEmpty()) cancellableFlowSignals.remove(cancellationId)
                }
            }
    }

fun <Action> Effect<Action>.cancel(id: Any): Effect<Action> = cancelEffect(id)
fun <Action> Effect<Action>.cancel(ids: Set<Any>): Effect<Action> = cancelEffects(ids)

fun <Action> cancelEffect(id: Any): Effect<Action> = Effect {
    flow {
        cancellationMutex.withLock {
            cancellableFlowSignals.remove(id)?.first?.emit(Unit) // send the cancellation signal
        }
    }
}

fun <Action> cancelEffects(ids: Set<Any>): Effect<Action> = Effect {
    flow {
        cancellationMutex.withLock {
            ids.forEach { id ->
                cancellableFlowSignals.remove(id)?.first?.emit(Unit) // send the cancellation signal
            }
        }
    }
}
