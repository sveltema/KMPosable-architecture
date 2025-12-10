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

/**
 * Strategy for handling existing effects with the same cancellation ID.
 */
enum class CancellationStrategy {
    /**
     * Keep any existing effects with the same ID running.
     * Multiple effects with the same ID can run concurrently.
     */
    KEEP_EXISTING,

    /**
     * Cancel any existing effects with the same ID before starting new one.
     * Only one effect with a given ID can run at a time.
     * Useful for debouncing or ensuring single-instance effects.
     */
    CANCEL_EXISTING
}

// access needs to be single threaded
private val cancellationMutex = Mutex()

// the scope/tokenSet pair uses the scope for cancellation and tokenSet to control individual flow completion and cache cleanup
private val cancellableFlowSignals = mutableMapOf<Any, Pair<MutableSharedFlow<Unit>, MutableSet<Long>>>()

/**
 * Internal exception used to signal flow completion due to cancellation.
 * Used by both effect cancellation and scoped action cancellation mechanisms
 * to gracefully terminate flows without propagating errors.
 */
internal class CompletedException : Exception("Cancellable has completed")

/**
 * Make an effect cancellable with explicit strategy and optional callback.
 *
 * @param id Unique identifier for this effect
 * @param strategy Optional strategy How to handle existing effects with same ID.  Defaults to KEEP_EXISTING
 * @param onCancelled Optional callback invoked when effect is cancelled (not on normal completion)
 * @return Cancellable effect
 *
 * Example:
 * ```
 * effect.cancellable(
 *     id = "user_load",
 *     strategy = CancellationStrategy.CANCEL_EXISTING,
 *     onCancelled = { logger.info("User load cancelled") }
 * )
 * ```
 */
fun <Action> Effect<Action>.cancellable(
    id: Any,
    strategy: CancellationStrategy = CancellationStrategy.KEEP_EXISTING,
    onCancelled: (suspend () -> Unit)? = null
): Effect<Action> = Effect {
    val flowToken = Random.nextLong()
    val innerFlow = this().cancellable()

    flow {
        // cancel any active effects if requested
        if (strategy == CancellationStrategy.CANCEL_EXISTING) {
            val toCancel = cancellationMutex.withLock {
                cancellableFlowSignals.remove(id)
            }
            toCancel?.first?.emit(Unit)
        }

        val cancellationNotifier = cancellationMutex.withLock {
            val (signal, tokens) = cancellableFlowSignals.getOrPut(id) {
                Pair(MutableSharedFlow(), mutableSetOf())
            }
            tokens.add(flowToken)
            signal
        }

        try {
            coroutineScope {
                val job = launch(start = CoroutineStart.UNDISPATCHED) {
                    cancellationNotifier.take(1).collect()
                    throw CompletedException()
                }
                innerFlow.collect { emit(it) }
                job.cancel()
            }
        } catch (exception: CompletedException) {
            onCancelled?.invoke()
        }
    }
        .onCompletion {
            cancellationMutex.withLock {
                val tokenSet = cancellableFlowSignals[id]?.second?.apply {
                    remove(flowToken)
                }
                if (tokenSet.isNullOrEmpty()) cancellableFlowSignals.remove(id)
            }
        }
}

fun <Action> Effect<Action>.cancel(id: Any): Effect<Action> = cancelEffect(id)
fun <Action> Effect<Action>.cancel(ids: Set<Any>): Effect<Action> = cancelEffects(ids)

fun <Action> cancelEffect(id: Any): Effect<Action> = Effect {
    flow {
        val toCancel = cancellationMutex.withLock {
            cancellableFlowSignals.remove(id)
        }
        toCancel?.first?.emit(Unit) // send the cancellation signal
    }
}

fun <Action> cancelEffects(ids: Set<Any>): Effect<Action> = Effect {
    flow {
        val toCancel = cancellationMutex.withLock {
            ids.mapNotNull { id ->
                cancellableFlowSignals.remove(id)
            }
        }
        toCancel.forEach { (signal, _) ->
            signal.emit(Unit) // send the cancellation signal
        }
    }
}

// ============================================================================
// Debugging Support
// ============================================================================

/**
 * Get count of active effects by cancellation ID.
 * Useful for debugging and monitoring effect lifecycle.
 *
 * Example:
 * ```
 * val active = getActiveEffectCounts()
 * println("Active effects: $active")
 * // Output: {user_load=2, image_cache=5}
 * ```
 *
 * @return Map of cancellation IDs to number of active effect instances
 */
fun getActiveEffectCounts(): Map<Any, Int> {
    return cancellableFlowSignals.mapValues { it.value.second.size }
}

/**
 * Check if a specific cancellation ID has active effects.
 *
 * @param id Cancellation identifier to check
 * @return true if there are active effects with this ID
 */
fun hasActiveEffects(id: Any): Boolean {
    return cancellableFlowSignals[id]?.second?.isNotEmpty() == true
}

