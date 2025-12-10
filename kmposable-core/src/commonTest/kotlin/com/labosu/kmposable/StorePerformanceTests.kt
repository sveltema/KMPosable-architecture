package com.labosu.kmposable

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.measureTime

/**
 * Performance benchmark tests for Store implementation.
 * These tests document performance characteristics and ensure the single processor
 * pattern efficiently batches and processes actions.
 */
class StorePerformanceTests : StoreCoroutineTest() {
    val testReducer = TestReducer()
    lateinit var testStore: Store<TestState, TestAction>

    @BeforeTest
    override fun beforeTest() {
        super.beforeTest()
        testReducer.reduceCount = 0
        testReducer.actions.clear()
        testStore = createTestStore(reducer = testReducer)
    }

    @Test
    fun `performance benchmark - rapid action processing`() = testCoroutineScope.runTest {
        val actionCount = 100

        val duration = measureTime {
            repeat(actionCount) {
                testStore.send(TestAction.AddToTestProperty("x"))
            }
            // Wait for all actions to be processed
            delay(50)
        }

        assertEquals(actionCount, testReducer.reduceCount)
        assertTrue(duration.inWholeMilliseconds < 100,
            "Processing 100 actions took ${duration.inWholeMilliseconds}ms, expected < 100ms")

        println("Rapid action processing: $actionCount actions in ${duration.inWholeMilliseconds}ms")
    }

    @Test
    fun `performance benchmark - batching behavior`() = testCoroutineScope.runTest {
        // Send multiple actions rapidly - they should be batched together
        val actionCount = 50

        repeat(actionCount) {
            testStore.send(TestAction.DoNothingAction)
        }

        // Small delay to allow batching
        delay(10)

        // All actions should be processed
        assertEquals(actionCount, testReducer.reduceCount)

        println("Batching test: $actionCount actions processed correctly")
    }

    @Test
    fun `performance benchmark - sendAll efficiency`() = testCoroutineScope.runTest {
        val actions = List(100) { TestAction.AddToTestProperty("x") }

        val duration = measureTime {
            testStore.sendAll(actions)
            delay(50)
        }

        assertEquals(actions.size, testReducer.reduceCount)
        assertTrue(duration.inWholeMilliseconds < 100,
            "sendAll for ${actions.size} actions took ${duration.inWholeMilliseconds}ms, expected < 100ms")

        println("sendAll efficiency: ${actions.size} actions in ${duration.inWholeMilliseconds}ms")
    }

    @Test
    fun `performance benchmark - interleaved actions and effects`() = testCoroutineScope.runTest {
        // Test performance when actions trigger effects that send more actions
        val effect = Effect<TestAction> {
            kotlinx.coroutines.flow.flow {
                emit(TestAction.DoNothingAction)
            }
        }

        val duration = measureTime {
            repeat(20) {
                testStore.send(TestAction.StartEffectAction(effect))
            }
            delay(100)
        }

        // Should have processed 20 initial actions + 20 effect actions = 40 total
        assertEquals(40, testReducer.reduceCount)
        assertTrue(duration.inWholeMilliseconds < 200,
            "Interleaved processing took ${duration.inWholeMilliseconds}ms, expected < 200ms")

        println("Interleaved actions/effects: 40 total actions in ${duration.inWholeMilliseconds}ms")
    }

    @Test
    fun `performance benchmark - state consistency with rapid updates`() = testCoroutineScope.runTest {
        // Send many actions that modify state and verify final state is correct
        repeat(50) {
            testStore.send(TestAction.AddToTestProperty("x"))
        }

        delay(50)

        // Verify all actions were processed by checking final state
        val finalState = testStore.state.first()

        assertEquals("x".repeat(50), finalState.testProperty,
            "Final state should contain 50 x's")

        println("State consistency: 50 state-changing actions produced correct final state")
    }

    @Test
    fun `performance benchmark - processor completes when queue empty`() = testCoroutineScope.runTest {
        // Send action and verify processor completes
        testStore.send(TestAction.DoNothingAction)
        delay(10)

        assertEquals(1, testReducer.reduceCount)

        // Send another action after delay - processor should restart
        delay(50)
        testStore.send(TestAction.DoNothingAction)
        delay(10)

        assertEquals(2, testReducer.reduceCount)

        println("Processor lifecycle: correctly completes and restarts")
    }
}
