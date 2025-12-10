package com.labosu.kmposable.internal

import com.labosu.kmposable.Effect
import com.labosu.kmposable.ExceptionHandler
import com.labosu.kmposable.Reducer
import com.labosu.kmposable.Store
import com.labosu.kmposable.emptyEffect
import com.labosu.kmposable.merge
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class MutableStateFlowStore<State, Action : Any> private constructor(
    override val state: Flow<State>,
    private val sendFn: (Action) -> Unit,
    private val sendAllFn: (Collection<Action>) -> Unit
) : Store<State, Action> {

    override fun <ChildState, ChildAction : Any> scope(
        toChildState: (State) -> ChildState,
        fromChildAction: (ChildAction) -> Action?
    ): Store<ChildState, ChildAction> = MutableStateFlowStore(
        state = state.map { toChildState(it) }.distinctUntilChanged(),
        sendFn = { childAction -> fromChildAction(childAction)?.let { sendFn(it) } },
        sendAllFn = { childActions -> sendAllFn(childActions.mapNotNull { fromChildAction(it) }) }
    )

    override fun <ChildState, ChildAction : Any> optionalScope(
        toChildState: (State) -> ChildState?,
        fromChildAction: (ChildAction) -> Action?
    ): Store<ChildState, ChildAction> = MutableStateFlowStore(
        state = state.mapNotNull { toChildState(it) }.distinctUntilChanged(),
        sendFn = { childAction -> fromChildAction(childAction)?.let { sendFn(it) } },
        sendAllFn = { childActions -> sendAllFn(childActions.mapNotNull { fromChildAction(it) }) }
    )

    override fun <ChildState> scope(
        toChildState: (State) -> ChildState
    ): Store<ChildState, Action> = MutableStateFlowStore(
        state = state.map { toChildState(it) }.distinctUntilChanged(),
        sendFn = sendFn,
        sendAllFn = sendAllFn
    )

    override fun <ChildAction : Any> actionScope(
        fromChildAction: (ChildAction) -> Action?
    ): Store<State, ChildAction> = MutableStateFlowStore(
        state = state,
        sendFn = { childAction: ChildAction -> fromChildAction(childAction)?.let { sendFn(it) } },
        sendAllFn = { childActions -> sendAllFn(childActions.mapNotNull { fromChildAction(it) }) }
    )

    companion object {
        fun <State, Action : Any> create(
            initialState: State,
            reducer: Reducer<State, Action>,
            exceptionHandler: ExceptionHandler,
            storeScope: CoroutineScope,
            storeDispatcher: CoroutineDispatcher = Dispatchers.Default,
            effectDispatcher: CoroutineDispatcher = Dispatchers.IO,
        ): Store<State, Action> {
            // prevent multithreaded access to state
            val storeMutateDispatcher = storeDispatcher.limitedParallelism(1)
            // the backing state flow for the store
            val mutableStateFlow = MutableStateFlow(initialState)
            // action channel for queuing actions to be processed
            val actionChannel = Channel<Action>(Channel.UNLIMITED)
            // mutex to ensure only one processor runs at a time
            val processorMutex = Mutex()

            fun send(action: Action) {
                // Add action to channel
                actionChannel.trySend(action)

                // Try to acquire lock to process - if another processor is running, skip
                if (processorMutex.tryLock()) {
                    storeScope.launch(storeMutateDispatcher) {
                        try {
                            // Keep processing batches until channel is truly empty
                            while (true) {
                                ensureActive()

                                // Drain all available actions from channel and process as single batch
                                val batch = generateSequence { actionChannel.tryReceive().getOrNull() }.toList()

                                // Exit if no actions to process
                                if (batch.isEmpty()) break

                                // Process batch through reducer
                                var backingValue = mutableStateFlow.value
                                val effects = batch.mapNotNull { batchAction ->
                                    try {
                                        reducer.reduceScoped(backingValue, batchAction)
                                            .also { backingValue = it.state }
                                            .effect
                                    } catch (cause: Throwable) {
                                        exceptionHandler.handleReduceException(backingValue, batchAction, cause)
                                        null
                                    }
                                }

                                // set the final state
                                mutableStateFlow.value = backingValue

                                // Launch effects if any
                                if (effects.isNotEmpty()) {
                                    val effect = when {
                                        effects.size == 1 -> effects.first()
                                        else -> effects.merge()
                                    }

                                    ensureActive()

                                    effect()
                                        .catch { cause -> exceptionHandler.handleEffectException(cause) }
                                        .onEach { resultAction -> send(resultAction) }
                                        .flowOn(effectDispatcher)
                                        .launchIn(storeScope)
                                }
                            }
                        } finally {
                            processorMutex.unlock()
                        }
                    }
                }
            }

            fun sendAll(actions: Collection<Action>) {
                if (actions.isEmpty()) return

                val count = actions.size
                // Add all to channel, trigger processing with last one
                actions.forEachIndexed { idx, action ->
                    if (idx == count - 1) send(action)
                    else actionChannel.trySend(action)
                }
            }

            return MutableStateFlowStore(mutableStateFlow, ::send, ::sendAll)
        }

        class ReducerException(override val message: String?, override val cause: Throwable?) :
            Throwable(
                message,
                cause
            )

        private suspend fun <State, Action> ExceptionHandler.handleReduceException(
            state: State,
            action: Action,
            exception: Throwable
        ): Effect<Nothing> {
            val wrappedException =
                ReducerException("[ReducerException]($action): $state", exception)
            if (handleException(wrappedException)) return emptyEffect() else throw wrappedException
        }

        class EffectException(override val message: String?, override val cause: Throwable?) :
            Throwable(message, cause)

        private suspend fun ExceptionHandler.handleEffectException(exception: Throwable) {
            val wrappedException = EffectException("[EffectException]", exception)
            if (handleException(wrappedException)) return else throw wrappedException
        }
    }

    override fun send(action: Action) = sendFn(action)
    override fun sendAll(actions: Collection<Action>) = sendAllFn(actions)
}
