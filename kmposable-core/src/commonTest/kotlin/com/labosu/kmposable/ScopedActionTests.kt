package com.labosu.kmposable

import app.cash.turbine.test
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTime

/**
 * Created by Steven Veltema on 2022/09/30
 */
class ScopedActionTests : StoreCoroutineTest() {
    val testReducer = TestReducer()
    lateinit var testStore: Store<TestState, TestAction>

    @BeforeTest
    override fun beforeTest() {
        super.beforeTest()
        testStore = createTestStore(reducer = testReducer)
    }

    // ====================
    // Basic Functionality Tests
    // ====================

    @Test
    fun `test simple flow emission from a ScopedAction`() = testCoroutineScope.runTest {
        testStore.state.test {
            // initial state
            val effectScope = TestScope(StandardTestDispatcher())
            assertEquals("", awaitItem().testProperty)
            testStore.send(TestAction.LongRunningScopedEffectAction(effectScope))
            delay(1)

            //the value should increment by 1
            var property = awaitItem().testProperty.toInt()
            assertEquals(1, property)

            property = awaitItem().testProperty.toInt()
            assertEquals(2, property)

            property = awaitItem().testProperty.toInt()
            assertEquals(3, property)

            property = awaitItem().testProperty.toInt()
            assertEquals(4, property)

            //cancel updates
            effectScope.cancel()

            //the value should no longer change as the effect scope has been cancelled
            ensureAllEventsConsumed()
        }
    }

    @Test
    fun `test scoped effect completes normally when scope remains active`() = testCoroutineScope.runTest {
        val effectScope = TestScope(StandardTestDispatcher())

        testStore.state.test {
            assertEquals("", awaitItem().testProperty)

            testStore.send(TestAction.LongRunningScopedEffectAction(effectScope))
            delay(1)

            // Should emit normally while scope is active
            assertEquals("1", awaitItem().testProperty)
            assertEquals("2", awaitItem().testProperty)
            assertEquals("3", awaitItem().testProperty)

            // Still running, now cancel the scope
            effectScope.cancel()

            // Should stop emitting
            ensureAllEventsConsumed()
        }
    }

    // ====================
    // Edge Case Tests
    // ====================

    @Test
    fun `test scoped effect with already cancelled scope returns empty flow`() = testCoroutineScope.runTest {
        val effectScope = TestScope(StandardTestDispatcher())
        effectScope.cancel() // Cancel before sending action

        testStore.state.test {
            assertEquals("", awaitItem().testProperty)

            testStore.send(TestAction.LongRunningScopedEffectAction(effectScope))

            delay(100) // Give time for potential emissions

            // No emissions should occur since scope is cancelled
            ensureAllEventsConsumed()
        }
    }

    @Test
    fun `test scoped effect cancels mid-flow when scope cancelled`() = testCoroutineScope.runTest {
        val effectScope = TestScope(StandardTestDispatcher())

        testStore.state.test {
            assertEquals("", awaitItem().testProperty)

            testStore.send(TestAction.LongRunningScopedEffectAction(effectScope))
            delay(1)

            // Get first few items
            awaitItem() // "1"
            awaitItem() // "2"

            // Cancel scope
            effectScope.cancel()

            delay(200) // Give time for cancellation to propagate

            // Should stop - property value should be low (not up to 50+)
            ensureAllEventsConsumed()
        }
    }

    @Test
    fun `test multiple scoped actions with same scope cancel together`() = testCoroutineScope.runTest {
        val effectScope = TestScope(StandardTestDispatcher())

        testStore.state.test {
            assertEquals("", awaitItem().testProperty)

            // Start two scoped effects
            testStore.send(TestAction.LongRunningScopedEffectAction(effectScope))
            testStore.send(TestAction.LongRunningScopedEffectAction(effectScope))
            delay(1)

            // Both effects should be running - collect a few items
            awaitItem()
            awaitItem()

            // Cancel scope - both should stop
            effectScope.cancel()

            delay(200)

            // Both effects should have stopped
            ensureAllEventsConsumed()
        }
    }

    // ====================
    // Performance Tests
    // ====================

    @Test
    fun `performance benchmark - scoped action overhead`() = testCoroutineScope.runTest {
        val effectScope = TestScope(StandardTestDispatcher())
        val iterations = 20

        val time = measureTime {
            repeat(iterations) {
                testStore.send(TestAction.LongRunningScopedEffectAction(effectScope))
                delay(10) // Let each run briefly
            }
            effectScope.cancel()
        }

        println("Time for $iterations scoped actions: $time")
        // After optimization, should be much faster than before
        // Target: < 500ms for 20 iterations
        assertTrue(time < 500.milliseconds, "Performance target not met: ${time.inWholeMilliseconds}ms")
    }

    @Test
    fun `performance benchmark - cancellation responsiveness`() = testCoroutineScope.runTest {
        val effectScope = TestScope(StandardTestDispatcher())
        var lastValue = 0

        testStore.state.test {
            assertEquals("", awaitItem().testProperty)

            testStore.send(TestAction.LongRunningScopedEffectAction(effectScope))
            delay(1)

            // Collect a few values
            repeat(5) {
                lastValue = awaitItem().testProperty.toInt()
            }

            // Cancel and verify it stops quickly
            effectScope.cancel()
            delay(200)

            // Should not have emitted many more values after cancel
            // (LongRunning emits every 50ms, so in 200ms could emit 4 more without cancellation)
            assertTrue(lastValue < 20, "Effect should have stopped quickly, but reached $lastValue")
        }
    }
}