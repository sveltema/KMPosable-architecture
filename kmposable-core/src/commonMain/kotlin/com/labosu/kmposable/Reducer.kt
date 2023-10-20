package com.labosu.kmposable

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge

//WHEN CREATING EFFECTS IN THE REDUCER, ANY NESTED `reduce(state, action)` MUST NOT HAPPEN INSIDE THE EFFECT{}
//UNLESS THE STORE UPDATE CAN ALSO HANDLE ASYNC UPDATES OUTSIDE IT'S CORE sendFn LOOP

fun interface Reducer<State, Action> {
    fun reduce(state: Mutable<State>, action: Action): Effect<Action>
}

fun <State, Action> loggingReducer(
    reducer: Reducer<State, Action>,
    preLambda: (State, Action) -> Unit,
    postLambda: (State, Action) -> Unit
): Reducer<State, Action> =
    Reducer { state, action ->
        preLambda(state.invoke(), action)
        reducer.reduceScoped(state, action).also {
            postLambda(state.invoke(), action)
        }
    }


fun <State, Action> emptyReducer(): Reducer<State, Action> = Reducer { _, _ -> emptyEffect() }

fun <State, Action> combine(vararg reducers: Reducer<State, Action>): Reducer<State, Action> = Reducer { state, action ->
    //immediately run the reducer
    val newFlow = reducers.map { it.reduceScoped(state, action).invoke() }
    Effect { newFlow.merge() }
}

fun <State, Action> Iterable<Reducer<State, Action>>.combine(): Reducer<State, Action> = Reducer { state, action ->
    //immediately run the reducer
    val newFlow = this.map { it.reduceScoped(state, action).invoke() }
    Effect { newFlow.merge() }
}

fun <State, Action> Reducer<State, Action>.combined(other: Reducer<State, Action>): Reducer<State, Action> = Reducer { state, action ->
    //immediately run the reducer
    val flows = listOf(this.reduceScoped(state, action).invoke(), other.reduceScoped(state, action).invoke()).merge()
    Effect { flows }
}

fun <ChildState, ParentState, ChildAction, ParentAction> Reducer<ChildState, ChildAction>.pullback(
    mapToChildState: (ParentState) -> ChildState,
    mapToChildAction: (ParentAction) -> ChildAction?,
    mapToParentState: (ParentState, ChildState) -> ParentState,
    mapToParentAction: (ChildAction) -> ParentAction
): Reducer<ParentState, ParentAction> = Reducer { state, action ->
    val childAction = mapToChildAction(action) ?: return@Reducer noEffect()
    val childState = state.map(mapToChildState, mapToParentState)
    val childEffect = this.reduceScoped(childState, childAction)
    val effectFlow = childEffect().map { childEffectAction -> childEffectAction.run(mapToParentAction) }

    Effect { effectFlow }
}

fun <ChildState, ParentState, ChildAction, ParentAction> Reducer<ChildState, ChildAction>.optionalPullback(
    mapToChildState: (ParentState) -> ChildState?,
    mapToChildAction: (ParentAction) -> ChildAction?,
    mapToParentState: (ParentState, ChildState?) -> ParentState,
    mapToParentAction: (ChildAction) -> ParentAction
): Reducer<ParentState, ParentAction> = Reducer { state, action ->
    val localAction = mapToChildAction(action) ?: return@Reducer noEffect()
    //the mutable value will update the localState
    var localState: ChildState = state.map(mapToChildState, mapToParentState).withValue<ChildState?> { this } ?: return@Reducer noEffect()
    val localMutableValue = Mutable({ localState }) { localState = it }
    //reduce the local state
    val childEffectFlow = this.reduceScoped(localMutableValue, localAction).invoke()
    //finally mutate the parent state with the result
    state.mutate { mapToParentState(this, localState) }
    //map back to an effect with a parent action
    val effectFlow = childEffectFlow.mapNotNull { childEffectAction -> childEffectAction?.run(mapToParentAction) }
    Effect { effectFlow }
}


fun <State, Action> Reducer<State, Action>.optional(): Reducer<State?, Action> = Reducer { optionalState, action ->
    if (optionalState() != null) {
        val newState = Mutable<State>(
            getValue = { optionalState()!! },
            setValue = { value -> optionalState.mutate { value } }
        )
        reduceScoped(newState, action)
    } else {
        print(
            """
           An "optional" reducer (${this::class.simpleName}) received an action when state was "null". …
           Action: ${action}

          This is generally considered an application logic error, and can happen for a few reasons:
          • The optional reducer was combined with or run from another reducer that set "%@" to
          "nil" before the optional reducer ran. Combine or run optional reducers before
          reducers that can set their state to "nil". This ensures that optional reducers can
          handle their actions while their state is still non-"nil".
          • An in-flight effect emitted this action while state was "nil". While it may be
          perfectly reasonable to ignore this action, you may want to cancel the associated
          effect before state is set to "nil", especially if it is a long-living effect.
          • This action was sent to the store while state was "nil". Make sure that actions for
          this reducer can only be sent to a view store when state is non-"nil".
            """.trimIndent()
        )
        noEffect()
    }
}


// https://github.com/pointfreeco/episode-code-samples/blob/main/0202-reducer-protocol-pt2/swift-composable-architecture/Sources/ComposableArchitecture/ReducerProtocol.swift