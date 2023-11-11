package com.labosu.kmposable

import com.labosu.kmposable.internal.MutableStateFlowStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow


interface Store<State, Action : Any> {

    /**
     * A flow that emits whenever the state changes
     */
    val state: Flow<State>

    /**
     * Sends actions to be processed
     */
    fun send(action: Action)
    fun send(actions: Iterable<Action>)

    /**
     * Transforms this store in a more specific store that can only emit
     * a subset of its actions and access a subset of its state
     * @param toChildState Function to transform the global state into the local state
     * @param fromChildAction Function to transform the local action into a global action for
     * forwarding to the parent store
     */
    fun <ChildState, ChildAction : Any> scope(
        toChildState: (State) -> ChildState,
        fromChildAction: (ChildAction) -> Action?
    ): Store<ChildState, ChildAction>

    fun <ChildState, ChildAction : Any> optionalScope(
        toChildState: (State) -> ChildState?,
        fromChildAction: (ChildAction) -> Action?
    ): Store<ChildState, ChildAction>

    fun <ChildState> scope(
        toChildState: (State) -> ChildState
    ): Store<ChildState, Action>

    val stateless: Store<Unit, Action>
        get() = this.scope { }

    val actionless: Store<State, Nothing>
        get() = this.optionalScope(toChildState = { cst: State -> cst }, fromChildAction = { null })

}

fun <State, Action : Any> createStore(
    initialState: State,
    reducer: Reducer<State, Action>,
    exceptionHandler: ExceptionHandler = object : ExceptionHandler {
        override suspend fun handleException(exception: Throwable): Boolean = throw exception
    },
    storeScope: CoroutineScope,
    storeDispatcher: CoroutineDispatcher = Dispatchers.Default,
    effectDispatcher: CoroutineDispatcher = Dispatchers.IO
) = MutableStateFlowStore.create(
    initialState = initialState,
    reducer = reducer,
    exceptionHandler = exceptionHandler,
    storeScope = storeScope,
    storeDispatcher = storeDispatcher,
    effectDispatcher = effectDispatcher,
)
