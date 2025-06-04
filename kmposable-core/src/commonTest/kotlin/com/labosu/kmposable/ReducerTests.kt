package com.labosu.kmposable

import kotlinx.coroutines.flow.flowOf // Added import
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ReducerTests {

    data class TestState(val count: Int, val text: String = "")

    sealed class TestAction
    data class IncrementAction(val payload: Int = 0) : TestAction()
    data class DecrementAction(val payload: Int = 0) : TestAction()
    data class TextAction(val text: String) : TestAction()
    data object EffectAction : TestAction()

    private val testReducer = Reducer<TestState, TestAction> { state, action ->
        when (action) {
            is IncrementAction -> Reduced(state.copy(count = state.count + action.payload))
            is DecrementAction -> Reduced(state.copy(count = state.count - action.payload))
            is TextAction -> Reduced(state.copy(text = action.text))
            EffectAction -> Reduced(state) { flowOf(TextAction("EFFECT_TRIGGERED")) }
        }
    }

    @Test
    fun loggingReducer_callsPreAndPostLambdasAndReturnsCorrectReduced() {
        var preLambdaCalled = false
        var postLambdaCalled = false
        val initialState = TestState(0)

        val action = IncrementAction(1)

        val logging = loggingReducer(
            reducer = testReducer,
            preLambda = { state, act ->
                assertEquals(initialState, state)
                assertEquals(action, act)
                preLambdaCalled = true
            },
            postLambda = { state, act ->
                // Expected state reflects IncrementAction logic
                assertEquals(TestState(1), state)
                assertEquals(action, act)
                postLambdaCalled = true
            }
        )

        val result = logging.reduce(initialState, action)

        // Expected state reflects IncrementAction logic
        assertEquals(TestState(1), result.state)
        assertNull(result.effect)
        kotlin.test.assertTrue(preLambdaCalled)
        kotlin.test.assertTrue(postLambdaCalled)
    }

    @Test
    fun combine_combinesMultipleReducersCorrectly() {
        val initialState = TestState(0)
        val action = IncrementAction(1)

        val reducer1 = Reducer<TestState, TestAction> { state, act ->
            if (act is IncrementAction) {
                Reduced(state.copy(count = state.count + act.payload))
            } else {
                Reduced(state)
            }
        }
        val reducer2 = Reducer<TestState, TestAction> { state, _ ->
            Reduced(state.copy(text = "processed by reducer2"))
        }
        val reducer3 = Reducer<TestState, TestAction> { state, _ ->
            Reduced(state) { flowOf(TextAction("EFFECT_FROM_REDUCER3")) }
        }

        val combinedReducer = combine(reducer1, reducer2, reducer3)
        val result = combinedReducer.reduce(initialState, action)

        assertEquals(TestState(1, "processed by reducer2"), result.state)
        assertNotNull(result.effect)
    }

    @Test
    fun combine_mergesEffectsFromMultipleReducers() {
        val initialState = TestState(0)
        // Updated to use EffectAction
        val action = EffectAction

        val reducer1 = Reducer<TestState, TestAction> { state, act ->
            if (act is EffectAction) {
                Reduced(state) { flowOf(TextAction("EFFECT1")) }
            } else {
                Reduced(state)
            }
        }
        val reducer2 = Reducer<TestState, TestAction> { state, act ->
            if (act is EffectAction) {
                Reduced(state) { flowOf(TextAction("EFFECT2")) }
            } else {
                Reduced(state)
            }
        }

        val combinedReducer = combine(reducer1, reducer2)
        val result = combinedReducer.reduce(initialState, action)

        assertEquals(initialState, result.state)
        assertNotNull(result.effect)
    }


    @Test
    fun combined_extensionFunctionCombinesReducersCorrectly() {
        val initialState = TestState(0)
        val action = IncrementAction(1)

        val reducer1 = Reducer<TestState, TestAction> { state, act ->
            if (act is IncrementAction) {
                Reduced(state.copy(count = state.count + act.payload))
            } else {
                Reduced(state)
            }
        }
        val reducer2 = Reducer<TestState, TestAction> { state, _ ->
            Reduced(state.copy(text = "processed by reducer2"))
        }

        val combinedReducer = reducer1.combined(reducer2)
        val result = combinedReducer.reduce(initialState, action)

        assertEquals(TestState(1, "processed by reducer2"), result.state)
    }

    @Test
    fun combined_extensionFunctionMergesEffects() {
        val initialState = TestState(0)
        val action = EffectAction

        val reducer1 = Reducer<TestState, TestAction> { state, act ->
            if (act is EffectAction) Reduced(
                state
            )
            { flowOf(TextAction("EFFECT1_COMBINED")) }
            else Reduced(state)
        }
        val reducer2 = Reducer<TestState, TestAction> { state, act ->
            if (act is EffectAction) Reduced(
                state
            )
            { flowOf(TextAction("EFFECT2_COMBINED")) }
            else Reduced(state)
        }

        val combinedReducer = reducer1.combined(reducer2)
        val result = combinedReducer.reduce(initialState, action)

        assertEquals(initialState, result.state)
        assertNotNull(result.effect)
    }

    // --- pullback Tests ---
    data class ParentState(val child: TestState, val parentCounter: Int = 0)
    data class ParentAction(
        val childAction: TestAction?,
        val parentSpecificAction: String? = null,
        val effectOrigin: String? = null
    )

    private val childReducerForPullback = Reducer<TestState, TestAction> { state, action ->
        when (action) {
            is IncrementAction ->
                Reduced(state.copy(count = state.count + action.payload))
                { flowOf(TextAction("CHILD_EFFECT_TEXT_FROM_INCREMENT")) }

            else -> Reduced(state)
        }
    }

    @Test
    fun pullback_mapsStateAndActionCorrectly_andEmbedsEffect() {
        val pullbackReducer =
            childReducerForPullback.pullback<TestState, ParentState, TestAction, ParentAction>(
                mapToChildState = { it.child },
                mapToChildAction = { it.childAction },
                mapToParentState = { parentState, newChildState -> parentState.copy(child = newChildState) },
                mapToParentAction = { childEffectAction ->
                    ParentAction(
                        childAction = childEffectAction,
                        effectOrigin = "child_effect"
                    )
                }
            )

        val initialState = ParentState(TestState(0))
        val action = ParentAction(IncrementAction(1))
        val result = pullbackReducer.reduce(initialState, action)

        assertEquals(ParentState(TestState(1)), result.state)
        assertNotNull(result.effect)
    }

    @Test
    fun pullback_returnsParentStateUnchanged_whenActionNotForChild() {
        val pullbackReducer =
            childReducerForPullback.pullback<TestState, ParentState, TestAction, ParentAction>(
                mapToChildState = { it.child },
                mapToChildAction = { it.childAction },
                mapToParentState = { parentState, newChildState -> parentState.copy(child = newChildState) },
                mapToParentAction = { childEffectAction -> ParentAction(childEffectAction) }
            )

        val initialState = ParentState(TestState(0))
        val action = ParentAction(null, "PARENT_ACTION")

        val result = pullbackReducer.reduce(initialState, action)

        assertEquals(initialState, result.state)
        assertNull(result.effect)
    }

    // --- optionalPullback Tests ---
    data class OptionalParentState(val optionalChild: TestState?, val name: String = "parent")
    // Reusing ParentAction

    private val childReducerForOptionalPullback = Reducer<TestState, TestAction> { state, action ->
        when (action) {
            is IncrementAction -> Reduced(
                state.copy(count = state.count + action.payload)
            )
            { flowOf(TextAction("CHILD_EFFECT_OPTIONAL_TEXT")) }

            else -> Reduced(state)
        }
    }

    @Test
    fun optionalPullback_mapsStateAndAction_whenChildStateExists() {
        val optionalPullbackReducer =
            childReducerForOptionalPullback.optionalPullback<TestState, OptionalParentState, TestAction, ParentAction>(
                mapToChildState = { it.optionalChild },
                mapToChildAction = { it.childAction },
                mapToParentState = { parentState, newChildState -> parentState.copy(optionalChild = newChildState) },
                mapToParentAction = { childEffectAction -> // childEffectAction is TextAction
                    ParentAction(
                        childAction = childEffectAction,
                        effectOrigin = "optional_child_effect"
                    )
                }
            )

        val initialState = OptionalParentState(TestState(5))
        val action = ParentAction(IncrementAction(1))

        val result = optionalPullbackReducer.reduce(initialState, action)

        // State update depends on IncrementAction logic in childReducerForOptionalPullback
        assertEquals(OptionalParentState(TestState(6)), result.state)
        assertNotNull(result.effect)
    }

    @Test
    fun optionalPullback_returnsParentStateUnchanged_whenChildStateIsNull() {
        val optionalPullbackReducer =
            childReducerForOptionalPullback.optionalPullback<TestState, OptionalParentState, TestAction, ParentAction>(
                mapToChildState = { it.optionalChild },
                mapToChildAction = { it.childAction },
                mapToParentState = { parentState, newChildState -> parentState.copy(optionalChild = newChildState) },
                mapToParentAction = { childEffectAction -> ParentAction(childEffectAction) }
            )

        val initialState = OptionalParentState(null)
        val action = ParentAction(IncrementAction(1))

        val result = optionalPullbackReducer.reduce(initialState, action)

        assertEquals(initialState, result.state)
        assertNull(result.effect)
    }

    @Test
    fun optionalPullback_returnsParentStateUnchanged_whenActionNotForChild() {
        val optionalPullbackReducer =
            childReducerForOptionalPullback.optionalPullback<TestState, OptionalParentState, TestAction, ParentAction>(
                mapToChildState = { it.optionalChild },
                mapToChildAction = { it.childAction },
                mapToParentState = { parentState, newChildState -> parentState.copy(optionalChild = newChildState) },
                mapToParentAction = { childEffectAction -> ParentAction(childEffectAction) }
            )

        val initialState = OptionalParentState(TestState(5))
        val action = ParentAction(null, "PARENT_ONLY_ACTION")

        val result = optionalPullbackReducer.reduce(initialState, action)

        assertEquals(initialState, result.state)
        assertNull(result.effect)
    }

    // --- forEachReducer Tests ---
    data class ItemState(val id: String, val value: Int)

    sealed class ItemAction
    data class ItemIncrementAction(val by: Int = 1) : ItemAction()
    data class ItemEffectTriggeredAction(val originalItemId: String, val description: String) :
        ItemAction()


    data class CollectionParentState(val items: List<ItemState>)

    data class CollectionParentAction(
        val itemId: String,
        val itemAction: ItemAction, // This will be ItemIncrementAction etc.
        val effectOrigin: String? = null
    )

    private val itemReducer = Reducer<ItemState, ItemAction> { state, action ->
        when (action) {
            is ItemIncrementAction -> Reduced(
                state.copy(value = state.value + action.by)
            )
            { flowOf(ItemEffectTriggeredAction(state.id, "ITEM_EFFECT_FROM_INCREMENT")) }

            else -> Reduced(state)
        }
    }

    @Test
    fun forEachReducer_appliesActionToCorrectItemAndEmbedsEffect() {
        val forEach =
            forEachReducer<CollectionParentState, CollectionParentAction, ItemState, ItemAction, String>(
                getChildStates = { it.items },
                setChildStates = { parentState, newItems -> parentState.copy(items = newItems) },
                extractAction = { parentAction -> parentAction.itemId to parentAction.itemAction },
                embedAction = { itemId, itemEffectAction -> // itemEffectAction is ItemEffectTriggeredAction
                    CollectionParentAction(
                        itemId,
                        itemEffectAction, // Pass the action from effect
                        effectOrigin = "from_foreach_embed"
                    )
                },
                idSelector = { it.id },
                childReducer = itemReducer
            )

        val initialState = CollectionParentState(listOf(ItemState("a", 0), ItemState("b", 10)))
        val action = CollectionParentAction("b", ItemIncrementAction(by = 1))

        val result = forEach.reduce(initialState, action)

        assertEquals(
            CollectionParentState(listOf(ItemState("a", 0), ItemState("b", 11))),
            result.state
        )
        assertNotNull(result.effect)
    }

    @Test
    fun forEachReducer_returnsParentStateUnchanged_whenActionNotExtracted() {
        val forEach =
            forEachReducer<CollectionParentState, CollectionParentAction, ItemState, ItemAction, String>(
                getChildStates = { it.items },
                setChildStates = { parentState, newItems -> parentState.copy(items = newItems) },
                extractAction = { null },
                embedAction = { itemId, itemEffectAction ->
                    CollectionParentAction(
                        itemId,
                        itemEffectAction
                    )
                },
                idSelector = { it.id },
                childReducer = itemReducer
            )

        val initialState = CollectionParentState(listOf(ItemState("a", 0)))
        val action = CollectionParentAction("a", ItemIncrementAction())

        val result = forEach.reduce(initialState, action)
        assertEquals(initialState, result.state)
        assertNull(result.effect)
    }

    @Test
    fun forEachReducer_returnsParentStateUnchanged_whenItemIdNotFound() {
        val forEach =
            forEachReducer<CollectionParentState, CollectionParentAction, ItemState, ItemAction, String>(
                getChildStates = { it.items },
                setChildStates = { parentState, newItems -> parentState.copy(items = newItems) },
                extractAction = { parentAction -> parentAction.itemId to parentAction.itemAction },
                embedAction = { itemId, itemEffectAction ->
                    CollectionParentAction(
                        itemId,
                        itemEffectAction
                    )
                },
                idSelector = { it.id },
                childReducer = itemReducer
            )

        val initialState = CollectionParentState(listOf(ItemState("a", 0)))
        val action = CollectionParentAction("nonExistentId", ItemIncrementAction())

        val result = forEach.reduce(initialState, action)

        assertEquals(initialState, result.state)
        assertNull(result.effect)
    }

    // --- forEachMapReducer Tests ---
    data class MapParentState(val itemsById: Map<String, ItemState>)
    // Reusing CollectionParentAction and ItemAction (now sealed)

    @Test
    fun forEachMapReducer_appliesActionToCorrectItemAndEmbedsEffect() {
        val forEachMap =
            forEachMapReducer<MapParentState, CollectionParentAction, ItemState, ItemAction, String>(
                getChildStatesMap = { it.itemsById },
                setChildStatesMap = { parentState, newItemsMap -> parentState.copy(itemsById = newItemsMap) },
                extractAction = { parentAction -> parentAction.itemId to parentAction.itemAction },
                embedAction = { itemId, itemEffectAction -> // itemEffectAction is ItemEffectTriggeredAction
                    CollectionParentAction(
                        itemId,
                        itemEffectAction, // Pass the action from effect
                        effectOrigin = "from_foreachmap_embed"
                    )
                },
                childReducer = itemReducer
            )

        val initialState =
            MapParentState(mapOf("x" to ItemState("x", 5), "y" to ItemState("y", 50)))
        val action = CollectionParentAction("y", ItemIncrementAction(by = 1))

        val result = forEachMap.reduce(initialState, action)

        assertEquals(
            MapParentState(mapOf("x" to ItemState("x", 5), "y" to ItemState("y", 51))),
            result.state
        )
        assertNotNull(result.effect)
    }

    @Test
    fun forEachMapReducer_returnsParentStateUnchanged_whenActionNotExtracted() {
        val forEachMap =
            forEachMapReducer<MapParentState, CollectionParentAction, ItemState, ItemAction, String>(
                getChildStatesMap = { it.itemsById },
                setChildStatesMap = { parentState, newItemsMap -> parentState.copy(itemsById = newItemsMap) },
                extractAction = { null },
                embedAction = { itemId, itemEffectAction ->
                    CollectionParentAction(
                        itemId,
                        itemEffectAction
                    )
                },
                childReducer = itemReducer
            )

        val initialState = MapParentState(mapOf("x" to ItemState("x", 5)))
        val action = CollectionParentAction("x", ItemIncrementAction())

        val result = forEachMap.reduce(initialState, action)
        assertEquals(initialState, result.state)
        assertNull(result.effect)
    }

    @Test
    fun forEachMapReducer_returnsParentStateUnchanged_whenItemIdNotFoundInMap() {
        val forEachMap =
            forEachMapReducer<MapParentState, CollectionParentAction, ItemState, ItemAction, String>(
                getChildStatesMap = { it.itemsById },
                setChildStatesMap = { parentState, newItemsMap -> parentState.copy(itemsById = newItemsMap) },
                extractAction = { parentAction -> parentAction.itemId to parentAction.itemAction },
                embedAction = { itemId, itemEffectAction ->
                    CollectionParentAction(
                        itemId,
                        itemEffectAction
                    )
                },
                childReducer = itemReducer
            )

        val initialState = MapParentState(mapOf("x" to ItemState("x", 5)))
        val action = CollectionParentAction("nonExistentKey", ItemIncrementAction())

        val result = forEachMap.reduce(initialState, action)

        assertEquals(initialState, result.state)
        assertNull(result.effect)
    }
}
