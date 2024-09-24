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
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

internal class MutableStateFlowStore<State, Action : Any> private constructor(
    override val state: Flow<State>,
    private val sendFn: (Action) -> Unit
) : Store<State, Action> {

    override fun <ChildState, ChildAction : Any> scope(
        toChildState: (State) -> ChildState,
        fromChildAction: (ChildAction) -> Action?
    ): Store<ChildState, ChildAction> = MutableStateFlowStore(
        state = state.map { toChildState(it) }.distinctUntilChanged(),
        sendFn = { childAction -> fromChildAction(childAction)?.let { sendFn(it) } }
    )

    override fun <ChildState, ChildAction : Any> optionalScope(
        toChildState: (State) -> ChildState?,
        fromChildAction: (ChildAction) -> Action?
    ): Store<ChildState, ChildAction> = MutableStateFlowStore(
        state = state.mapNotNull { toChildState(it) }.distinctUntilChanged(),
        sendFn = { childAction -> fromChildAction(childAction)?.let { sendFn(it) } }
    )

    override fun <ChildState> scope(
        toChildState: (State) -> ChildState
    ): Store<ChildState, Action> = MutableStateFlowStore(
        state = state.map { toChildState(it) }.distinctUntilChanged(),
        sendFn = sendFn
    )

    override fun <ChildAction : Any> actionScope(
        fromChildAction: (ChildAction) -> Action?
    ): Store<State, ChildAction> = MutableStateFlowStore(
        state = state,
        sendFn = { childAction: ChildAction -> fromChildAction(childAction)?.let { sendFn(it) } }
    )

    companion object {
        @OptIn(ExperimentalCoroutinesApi::class)
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
            // buffer channel for buffering together quickly fired actions for better performance
            val bufferChannel = Channel<Action>(Channel.UNLIMITED)

            fun send(action: Action) {
                // send actions to buffer channel, and attempt to perform together with any waiting actions
                bufferChannel.trySend(action)

                // launch in single parallel context
                storeScope.launch(context = storeMutateDispatcher) {
                    ensureActive()

                    // get next X actions waiting in buffer channel and group together for faster processing
                    // don't suspend waiting for next result, try to get one and fail if not present
                    val bufferedActions = mutableListOf<Action>()
                    while (true) {
                        val element = bufferChannel.tryReceive().getOrNull() ?: break
                        bufferedActions.add(element)
                    }

                    if (bufferedActions.isEmpty()) return@launch

                    // gather all of the effects returned by the reducers
                    var backingValue = mutableStateFlow.value
                    val effects = bufferedActions.mapNotNull { action ->
                        try {
                            reducer.reduceScoped(backingValue, action)
                                .also { backingValue = it.state }
                                .effect
                        } catch (cause: Throwable) {
                            exceptionHandler.handleReduceException(backingValue, action, cause)
                            null
                        }
                    }

                    // set the final state
                    mutableStateFlow.value = backingValue

                    val effect = when {
                        effects.isEmpty() -> return@launch
                        effects.size == 1 -> effects.first()
                        else -> effects.merge()
                    }

                    ensureActive()

                    // collect the effects
                    effect()
                        .catch { cause -> exceptionHandler.handleEffectException(cause) }
                        .onEach { action -> send(action) }
                        .flowOn(effectDispatcher)
                        .launchIn(storeScope)
                }
            }

            return MutableStateFlowStore(mutableStateFlow, ::send)
        }

        class ReducerException(override val message: String?, override val cause: Throwable?) : Throwable(
            message,
            cause
        )

        private suspend fun <State, Action> ExceptionHandler.handleReduceException(
            state: State,
            action: Action,
            exception: Throwable
        ): Effect<Nothing> {
            val wrappedException = ReducerException("[ReducerException]($action): $state", exception)
            if (handleException(wrappedException)) return emptyEffect() else throw wrappedException
        }

        class EffectException(override val message: String?, override val cause: Throwable?) : Throwable(message, cause)

        private suspend fun ExceptionHandler.handleEffectException(exception: Throwable) {
            val wrappedException = EffectException("[EffectException]", exception)
            if (handleException(wrappedException)) return else throw wrappedException
        }
    }

    override fun send(action: Action) = sendFn(action)
    override fun send(actions: Iterable<Action>) = actions.forEach(sendFn)
}
