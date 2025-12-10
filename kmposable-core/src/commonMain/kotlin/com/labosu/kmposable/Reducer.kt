@file:Suppress("unused")

package com.labosu.kmposable

import com.labosu.kmposable.internal.reduceScoped

/**
 * A functional interface representing a reducer.
 * A reducer takes the current state and an action, and returns a new state along with an optional effect.
 * See https://github.com/pointfreeco/episode-code-samples/blob/main/0202-reducer-protocol-pt2/swift-composable-architecture/Sources/ComposableArchitecture/ReducerProtocol.swift
 *
 * @param State The type of the state.
 * @param Action The type of the action.
 */
fun interface Reducer<State, Action> {
    /**
     * Reduces the current state with the given action.
     *
     * @param state The current state.
     * @param action The action to apply.
     * @return A [Reduced] object containing the new state and an optional [Effect].
     */
    fun reduce(state: State, action: Action): Reduced<State, Action>
}

/**
 * Creates a reducer that logs actions and state changes.
 *
 * @param State The type of the state.
 * @param Action The type of the action.
 * @param reducer The underlying reducer to wrap with logging.
 * @param preLambda A lambda executed before the reducer processes the action. It receives the current state and action.
 * @param postLambda A lambda executed after the reducer has processed the action. It receives the (potentially modified) state and action.
 * @return A new [Reducer] that incorporates logging.
 */
fun <State, Action> loggingReducer(
    reducer: Reducer<State, Action>,
    preLambda: (State, Action) -> Unit,
    postLambda: (State, Action) -> Unit
): Reducer<State, Action> =
    Reducer { state, action ->
        preLambda(state, action)
        reducer.reduceScoped(state, action).also {
            postLambda(it.state, action)
        }
    }

/**
 * Combines multiple reducers into a single reducer.
 * The reducers are applied in the order they are provided.
 * State changes from each reducer are passed to the next, and all effects are merged.
 *
 * @param State The type of the state.
 * @param Action The type of the action.
 * @param reducers A vararg array of reducers to combine.
 * @return A single [Reducer] that applies all given reducers in sequence.
 */
fun <State, Action> combine(vararg reducers: Reducer<State, Action>): Reducer<State, Action> =
    Reducer { state, action ->
        var innerState = state
        val effects = reducers.mapNotNull {
            val reduced = it.reduceScoped(innerState, action)
            innerState = reduced.state
            reduced.effect
        }
        when {
            effects.isEmpty() -> Reduced(innerState, null)
            effects.size == 1 -> Reduced(innerState, effects.first())
            else -> Reduced(innerState, effects.merge())
        }
    }

/**
 * Combines this reducer with another reducer.
 * This reducer is applied first, and its resulting state is then passed to the other reducer.
 * Effects from both reducers are merged.
 *
 * @param State The type of the state.
 * @param Action The type of the action.
 * @param other The other reducer to combine with.
 * @return A new [Reducer] that is the combination of this reducer and the other.
 */
fun <State, Action> Reducer<State, Action>.combined(other: Reducer<State, Action>): Reducer<State, Action> =
    Reducer { state, action ->
        val reduced = this.reduceScoped(state, action)
        val otherReduced = other.reduceScoped(reduced.state, action)
        Reduced(otherReduced.state, listOfNotNull(reduced.effect, otherReduced.effect).merge())
    }

/**
 * Transforms a reducer that operates on child state and actions into a reducer that operates on parent state and actions.
 * This is useful for embedding a child component's logic within a parent component.
 *
 * @param ChildState The type of the child state.
 * @param ParentState The type of the parent state.
 * @param ChildAction The type of the child action.
 * @param ParentAction The type of the parent action.
 * @param mapToChildState A function to extract the child state from the parent state.
 * @param mapToChildAction A function to attempt to convert a parent action into a child action. If the parent action is not relevant to the child, it should return `null`.
 * @param mapToParentState A function to update the parent state with the new child state.
 * @param mapToParentAction A function to convert a child action (typically from an effect) into a parent action.
 * @return A [Reducer] that operates on the parent state and actions, delegating to the child reducer when appropriate.
 */
fun <ChildState, ParentState, ChildAction, ParentAction> Reducer<ChildState, ChildAction>.pullback(
    mapToChildState: (ParentState) -> ChildState,
    mapToChildAction: (ParentAction) -> ChildAction?,
    mapToParentState: (ParentState, ChildState) -> ParentState,
    mapToParentAction: (ChildAction) -> ParentAction
): Reducer<ParentState, ParentAction> =
    Reducer { state, action ->
        val childAction =
            mapToChildAction(action) ?: return@Reducer Reduced<ParentState, ParentAction>(state)
        val (childState, childEffect) = this.reduceScoped(mapToChildState(state), childAction)
        Reduced(mapToParentState(state, childState), childEffect?.map { mapToParentAction(it) })
    }

/**
 * Transforms a reducer that operates on an optional child state and actions into a reducer that operates on parent state and actions.
 * This is useful when a child component might not always exist (e.g., conditional navigation).
 * If the child state is `null`, or if the parent action cannot be mapped to a child action, the parent state is returned unchanged.
 *
 * @param ChildState The type of the child state (which can be `null`).
 * @param ParentState The type of the parent state.
 * @param ChildAction The type of the child action.
 * @param ParentAction The type of the parent action.
 * @param mapToChildState A function to extract the optional child state from the parent state.
 * @param mapToChildAction A function to attempt to convert a parent action into a child action. Returns `null` if not applicable.
 * @param mapToParentState A function to update the parent state with the new (optional) child state.
 * @param mapToParentAction A function to convert a child action (typically from an effect) into a parent action.
 * @return A [Reducer] that operates on the parent state and actions, delegating to the child reducer if the child state exists and the action is relevant.
 */
fun <ChildState, ParentState, ChildAction, ParentAction> Reducer<ChildState, ChildAction>.optionalPullback(
    mapToChildState: (ParentState) -> ChildState?,
    mapToChildAction: (ParentAction) -> ChildAction?,
    mapToParentState: (ParentState, ChildState?) -> ParentState,
    mapToParentAction: (ChildAction) -> ParentAction
): Reducer<ParentState, ParentAction> =
    Reducer { state, action ->
        val childAction =
            mapToChildAction(action) ?: return@Reducer Reduced<ParentState, ParentAction>(state)
        val (childState, childEffect) = mapToChildState(state)?.let {
            this.reduceScoped(it, childAction)
        } ?: return@Reducer Reduced<ParentState, ParentAction>(state)
        Reduced(mapToParentState(state, childState), childEffect?.map { mapToParentAction(it) })
    }

/**
 * Transforms a reducer that operates on a specific sealed class case into a reducer that operates
 * on the parent sealed class type.
 *
 * This is particularly useful for composing reducers that work with different cases of a sealed class,
 * such as navigation states, loading states, or authentication states.
 *
 * If the parent state is not an instance of the expected case type, or if the parent action cannot
 * be mapped to a child action, the parent state is returned unchanged.
 *
 * @param CaseState The specific sealed class case type that this reducer operates on.
 * @param ParentState The parent sealed class type.
 * @param ChildAction The type of the child action.
 * @param ParentAction The type of the parent action.
 * @param mapToChildAction A function to attempt to convert a parent action into a child action.
 *                         Returns `null` if the action is not relevant to this case.
 * @param mapToParentState A function to update the parent state with the new case state.
 *                         Typically just returns the case state as it already is a ParentState.
 * @param mapToParentAction A function to convert a child action (typically from an effect) into a parent action.
 * @return A [Reducer] that operates on the parent sealed class, delegating to the child reducer
 *         when the state matches the expected case.
 *
 * @sample
 * ```
 * sealed class AppState {
 *     object Loading : AppState()
 *     data class LoggedIn(val user: User) : AppState()
 *     data class LoggedOut(val loginForm: LoginForm) : AppState()
 * }
 *
 * sealed class AppAction {
 *     data class Login(val action: LoginAction) : AppAction()
 *     data class Home(val action: HomeAction) : AppAction()
 * }
 *
 * val loginReducer: Reducer<AppState.LoggedOut, LoginAction> = // ...
 *
 * val appReducer = loginReducer.ifCaseLet<AppState.LoggedOut, AppState, LoginAction, AppAction>(
 *     mapToChildAction = { (it as? AppAction.Login)?.action },
 *     mapToParentState = { _, loggedOut -> loggedOut },
 *     mapToParentAction = { AppAction.Login(it) }
 * )
 * ```
 */
inline fun <reified CaseState : ParentState, ParentState, ChildAction, ParentAction>
Reducer<CaseState, ChildAction>.ifCaseLet(
    crossinline mapToChildAction: (ParentAction) -> ChildAction?,
    crossinline mapToParentState: (ParentState, CaseState) -> ParentState,
    crossinline mapToParentAction: (ChildAction) -> ParentAction
): Reducer<ParentState, ParentAction> =
    Reducer { state, action ->
        // Extract child action, or return unchanged if not applicable
        val childAction = mapToChildAction(action)
            ?: return@Reducer Reduced<ParentState, ParentAction>(state)

        // Check if state is the expected case type
        val caseState = state as? CaseState
            ?: return@Reducer Reduced<ParentState, ParentAction>(state)

        // Apply child reducer to case state
        val (newCaseState, childEffect) = this.reduce(caseState, childAction)

        // Map back to parent
        Reduced(
            mapToParentState(state, newCaseState),
            childEffect?.map { mapToParentAction(it) }
        )
    }

/**
 * A higher-order reducer that operates on a collection of child states.
 *
 * It identifies the target child state based on an ID extracted from the parent action,
 * applies the child reducer to it, and updates the parent state with the modified
 * collection of child states. Effects from the child reducer are mapped back to parent actions.
 *
 * @param ParentState The type of the parent state.
 * @param ParentAction The type of the parent action.
 * @param ChildState The type of the child state.
 * @param ChildAction The type of the child action.
 * @param ID The type of the identifier for child states.
 * @param getChildStates A function to extract the list of child states from the parent state.
 * @param setChildStates A function to update the parent state with a new list of child states.
 * @param extractAction A function to attempt to extract a child ID and child action from a parent action.
 *                       Returns `null` if the parent action does not target a child.
 * @param embedAction A function to embed a child ID and child action (typically from an effect) into a parent action.
 * @param idSelector A function to select the ID from a child state.
 * @param childReducer The reducer to apply to individual child states.
 * @return A `Reducer` that operates on the parent state and actions, delegating to the child reducer for items in the collection.
 */
fun <ParentState, ParentAction, ChildState, ChildAction, ID> forEachReducer(
    getChildStates: (ParentState) -> List<ChildState>,
    setChildStates: (ParentState, List<ChildState>) -> ParentState,
    extractAction: (ParentAction) -> Pair<ID, ChildAction>?,
    embedAction: (ID, ChildAction) -> ParentAction,
    idSelector: (ChildState) -> ID,
    childReducer: Reducer<ChildState, ChildAction>
): Reducer<ParentState, ParentAction> = Reducer { state, action ->
    val extracted = extractAction(action) ?: return@Reducer Reduced(state)
    val (id, childAction) = extracted

    val childStates = getChildStates(state)
    val index = childStates.indexOfFirst { idSelector(it) == id }

    if (index == -1) {
        // Optionally throw if a child action is received for a non-existent ID
        return@Reducer Reduced(state)
    }

    val childState = childStates[index]

    val reduced = childReducer.reduceScoped(childState, childAction)

    val newChildStates = childStates.toMutableList().apply {
        this[index] = reduced.state
    }

    val newState = setChildStates(state, newChildStates)
    val mappedEffect = reduced.effect?.map { effectAction -> embedAction(id, effectAction) }
    Reduced(newState, mappedEffect)
}

/**
 * A higher-order reducer that operates on a map of child states.
 *
 * It identifies the target child state based on an ID extracted from the parent action,
 * applies the child reducer to it, and updates the parent state with the modified
 * map of child states. Effects from the child reducer are mapped back to parent actions.
 * This is useful when child states are keyed by a unique identifier.
 * This also has better performance characteristics when the number of children is large
 *
 * @param ParentState The type of the parent state.
 * @param ParentAction The type of the parent action.
 * @param ChildState The type of the child state.
 * @param ChildAction The type of the child action.
 * @param ID The type of the key used in the map of child states.
 * @param getChildStatesMap A function to extract the map of child states from the parent state.
 * @param setChildStatesMap A function to update the parent state with a new map of child states.
 * @param extractAction A function to attempt to extract a child ID (key) and child action from a parent action.
 *                       Returns `null` if the parent action does not target a child.
 * @param embedAction A function to embed a child ID (key) and child action (typically from an effect) into a parent action.
 * @param childReducer The reducer to apply to individual child states.
 * @return A `Reducer` that operates on the parent state and actions, delegating to the child reducer for items in the map.
 */
fun <ParentState, ParentAction, ChildState, ChildAction, ID> forEachMapReducer(
    getChildStatesMap: (ParentState) -> Map<ID, ChildState>,
    setChildStatesMap: (ParentState, Map<ID, ChildState>) -> ParentState,
    extractAction: (ParentAction) -> Pair<ID, ChildAction>?,
    embedAction: (ID, ChildAction) -> ParentAction,
    childReducer: Reducer<ChildState, ChildAction>
): Reducer<ParentState, ParentAction> = Reducer { state, action ->
    val extracted = extractAction(action) ?: return@Reducer Reduced(state)
    val (id, childAction) = extracted

    val childStatesMap = getChildStatesMap(state)

    // Optionally throw if a child action is received for a non-existent ID
    val childState = childStatesMap[id] ?: return@Reducer Reduced(state)

    val reduced = childReducer.reduceScoped(childState, childAction)

    val newChildStatesMap = childStatesMap.toMutableMap().apply {
        this[id] = reduced.state
    }
    val newState = setChildStatesMap(state, newChildStatesMap)
    val mappedEffect = reduced.effect?.map { effectAction -> embedAction(id, effectAction) }
    Reduced(newState, mappedEffect)
}

