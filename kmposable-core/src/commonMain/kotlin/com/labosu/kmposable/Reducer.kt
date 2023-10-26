package com.labosu.kmposable

import com.labosu.kmposable.internal.reduceScoped
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

data class Reduced<out State, Action>(val state: State, val effect: Effect<Action>? = null)

fun interface Reducer<State, Action> {
    fun reduce(state: State, action: Action): Reduced<State, Action>
}

fun <State, Action> loggingReducer(
    reducer: Reducer<State, Action>,
    preLambda: (State, Action) -> Unit,
    postLambda: (State, Action) -> Unit
): Reducer<State, Action> =
    Reducer { state, action ->
        preLambda(state, action)
        reducer.reduceScoped(state, action).also {
            postLambda(state, action)
        }
    }

/**
 * Combine
 * State will be updated in order, and resultant effects merged into a single effect
 *
 * @param State
 * @param Action
 * @param reducers
 * @return
 */
fun <State, Action> combine(vararg reducers: Reducer<State, Action>): Reducer<State, Action> = Reducer { state, action ->
    var innerState = state
    val effects = reducers.mapNotNull {
        val reduced = it.reduceScoped(innerState, action)
        innerState = reduced.state
        reduced.effect
    }
    Reduced(innerState, effects.merge())
}

fun <State, Action> Reducer<State, Action>.combined(other: Reducer<State, Action>): Reducer<State, Action> = Reducer { state, action ->
    val reduced = this.reduceScoped(state, action)
    val otherReduced = other.reduceScoped(reduced.state, action)
    Reduced(otherReduced.state, listOfNotNull(reduced.effect, otherReduced.effect).merge())
}

fun <ChildState, ParentState, ChildAction, ParentAction> Reducer<ChildState, ChildAction>.pullback(
    mapToChildState: (ParentState) -> ChildState,
    mapToChildAction: (ParentAction) -> ChildAction?,
    mapToParentState: (ParentState, ChildState) -> ParentState,
    mapToParentAction: (ChildAction) -> ParentAction
): Reducer<ParentState, ParentAction> = Reducer { state, action ->
    val childAction = mapToChildAction(action) ?: return@Reducer Reduced<ParentState, ParentAction>(state)
    val (childState, childEffect) = this.reduceScoped(mapToChildState(state), childAction)
    Reduced(mapToParentState(state, childState), childEffect?.map { mapToParentAction(it) })
}

fun <ChildState, ParentState, ChildAction, ParentAction> Reducer<ChildState, ChildAction>.optionalPullback(
    mapToChildState: (ParentState) -> ChildState?,
    mapToChildAction: (ParentAction) -> ChildAction?,
    mapToParentState: (ParentState, ChildState?) -> ParentState,
    mapToParentAction: (ChildAction) -> ParentAction
): Reducer<ParentState, ParentAction> = Reducer { state, action ->
    val childAction = mapToChildAction(action) ?: return@Reducer Reduced<ParentState, ParentAction>(state)
    val (childState, childEffect) = mapToChildState(state)?.let { this.reduceScoped(it, childAction) } ?: return@Reducer Reduced<ParentState, ParentAction>(state)
    Reduced(mapToParentState(state, childState), childEffect?.map { mapToParentAction(it) })
}

// https://github.com/pointfreeco/episode-code-samples/blob/main/0202-reducer-protocol-pt2/swift-composable-architecture/Sources/ComposableArchitecture/ReducerProtocol.swift