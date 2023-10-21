package com.labosu.kmposable.internal

import com.labosu.kmposable.CompletedException
import com.labosu.kmposable.Effect
import com.labosu.kmposable.Mutable
import com.labosu.kmposable.Reducer
import com.labosu.kmposable.ScopedAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Created by Steven Veltema on 2022/12/28
 *
 * Sending a ScopedAction, will collect the flows from the
 * resulting effect only while the provided CoroutineScope is active.
 * Once the scope becomes inactive, the effect will cancel
 */

internal fun <Action> Effect<Action>.scoped(scope: CoroutineScope): Effect<Action> = Effect {
    //use a notifier to stop the flow immediately when the scope is cancelled
    val notifier = channelFlow<Int> {
        val waitJob = scope.launch {
            //wait for scope to be cancelled
            awaitCancellation()
        }
        //wait for job to complete
        waitJob.join()
        if (isActive) send(1)
        close()
    }

    val innerFlow = this().cancellable()

    flow<Action> {
        try {
            coroutineScope {
                val job = launch(start = CoroutineStart.UNDISPATCHED) {
                    //collect until notifier closes
                    notifier.collect()
                    throw CompletedException()
                }
                innerFlow.collect { emit(it) }
                job.cancel()
            }
        } catch (e: CompletedException) {
            //ignore the completed exception when the notifier cancelled
            //the exception will short circuit the innerFlow.collect
            //and the outer flow will finish and close
        }
    }
        //ensures cancellation, but not immediately, waits for flow to emit complete
        .takeWhile { scope.isActive }
}

internal fun <State, Action> Reducer<State, Action>.reduceScoped(state: Mutable<State>, action: Action): Effect<Action> {
    return if (action is ScopedAction) {
        reduce(state, action).scoped(action.scope)
    } else {
        reduce(state, action)
    }
}
