package com.labosu.kmposable.internal

import com.labosu.kmposable.CompletedException
import com.labosu.kmposable.Effect
import com.labosu.kmposable.Reduced
import com.labosu.kmposable.Reducer
import com.labosu.kmposable.ScopedAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive

/**
 * Created by Steven Veltema on 2022/12/28
 *
 * Sending a ScopedAction, will collect the flows from the
 * resulting effect only while the provided CoroutineScope is active.
 * Once the scope becomes inactive, the effect will cancel
 */

/**
 * Make an effect scoped to a coroutine scope.
 *
 * The effect will automatically cancel when the scope is cancelled.
 * If the scope is already cancelled, returns an empty flow.
 *
 * This implementation uses Job.invokeOnCompletion for immediate, lightweight
 * cancellation detection without the overhead of channelFlow or racing coroutines.
 *
 * @param scope The coroutine scope to bind this effect to
 * @param onScopeCancelled Optional callback invoked when scope cancellation stops the effect
 * @return Scoped effect that cancels with the scope
 */
internal fun <Action> Effect<Action>.scoped(
    scope: CoroutineScope,
    onScopeCancelled: (suspend () -> Unit)? = null
): Effect<Action> = Effect {
    if (!scope.isActive) {
        return@Effect flow {
            onScopeCancelled?.invoke()
        }
    }

    val innerFlow = this().cancellable()
    val scopeJob = scope.coroutineContext[Job]

    flow {
        var scopeCancelled = false
        val cancellationHandle = scopeJob?.invokeOnCompletion { cause ->
            scopeCancelled = true
        }

        try {
            // Collect from inner flow until scope is cancelled
            innerFlow.collect { value ->
                if (scopeCancelled || !scope.isActive) {
                    throw CompletedException()
                }
                emit(value)
            }
        } catch (_: CompletedException) {
            onScopeCancelled?.invoke()
        } finally {
            cancellationHandle?.dispose()
        }
    }
}

internal fun <State, Action> Reducer<State, Action>.reduceScoped(state: State, action: Action): Reduced<State, Action> {
    return if (action is ScopedAction) {
        reduce(state, action).let { it.copy(effect = it.effect?.scoped(action.scope)) }
    } else {
        reduce(state, action)
    }
}
