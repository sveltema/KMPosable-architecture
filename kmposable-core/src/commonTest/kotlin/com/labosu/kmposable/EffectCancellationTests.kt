package com.labosu.kmposable

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Created by Steven Veltema on 2022/09/30
 */
class EffectCancellationTests : StoreCoroutineTest() {
    val testReducer = TestReducer()
    lateinit var testStore: Store<TestState, TestAction>

    // Define test cancellation IDs
    object TestCancellationIds {
        const val TEST = "test"
        const val TEST_CALLBACK = "test_callback"
        const val TEST_DEBUG = "test_debug"
    }

    @BeforeTest
    override fun beforeTest() {
        super.beforeTest()
        testStore = createTestStore(reducer = testReducer)
    }

    // ====================
    // CancellationStrategy API Tests
    // ====================

    @Test
    fun `test simple flow emission with CancellationStrategy`() = testCoroutineScope.runTest {
        val testList = listOf(1, 2, 3)
        val testCancellableEffect = Effect { testList.asFlow() }
            .cancellable(
                id = TestCancellationIds.TEST,
                strategy = CancellationStrategy.KEEP_EXISTING
            )

        assertEquals(testList, testCancellableEffect().toList())
    }

    @Test
    fun `test cancellation with CancellationStrategy`() = testCoroutineScope.runTest {
        val testCancellableEffect = Effect {
            channelFlow {
                withContext(Dispatchers.Default) {
                    send(1)
                    send(2)
                    delay(2000)
                    send(3)
                }
            }
        }
            .cancellable(
                id = TestCancellationIds.TEST,
                strategy = CancellationStrategy.KEEP_EXISTING
            )

        testCancellableEffect().test {
            assertEquals(1, awaitItem())
            assertEquals(2, awaitItem())
            cancelEffect<Int>(TestCancellationIds.TEST).invoke().firstOrNull()
            awaitComplete()
        }
    }

    @Test
    fun `test CANCEL_EXISTING strategy cancels previous effect`() = testCoroutineScope.runTest {
        var firstEffectCancelled = false
        var secondEffectCancelled = false

        val effect1 = Effect {
            channelFlow {
                send(1)
                delay(5000) // Long delay
                send(2)
            }
        }.cancellable(
            id = TestCancellationIds.TEST,
            strategy = CancellationStrategy.KEEP_EXISTING,
            onCancelled = { firstEffectCancelled = true }
        )

        val effect2 = Effect {
            channelFlow {
                send(10)
                send(20)
            }
        }.cancellable(
            id = TestCancellationIds.TEST,
            strategy = CancellationStrategy.CANCEL_EXISTING,
            onCancelled = { secondEffectCancelled = true }
        )

        // Start first effect
        effect1().test {
            assertEquals(1, awaitItem())

            // Start second effect with CANCEL_EXISTING - should cancel first
            effect2().toList()

            // First effect should be cancelled
            awaitComplete()
            delay(100)
            assertTrue(firstEffectCancelled, "First effect should have been cancelled")
            assertFalse(secondEffectCancelled, "Second effect should not have been cancelled")
        }
    }

    @Test
    fun `test onCancelled callback is invoked`() = testCoroutineScope.runTest {
        var cancellationCallbackInvoked = false

        val testEffect = Effect {
            channelFlow {
                withContext(Dispatchers.Default) {
                    send(1)
                    send(2)
                    delay(2000)
                    send(3)
                }
            }
        }.cancellable(
            id = TestCancellationIds.TEST_CALLBACK,
            strategy = CancellationStrategy.KEEP_EXISTING,
            onCancelled = { cancellationCallbackInvoked = true }
        )

        testEffect().test {
            assertEquals(1, awaitItem())
            assertEquals(2, awaitItem())

            // Cancel the effect
            cancelEffect<Int>(TestCancellationIds.TEST_CALLBACK).invoke().firstOrNull()

            awaitComplete()
            delay(100)
            assertTrue(cancellationCallbackInvoked, "onCancelled callback should have been invoked")
        }
    }

    @Test
    fun `test onCancelled callback not invoked on normal completion`() = testCoroutineScope.runTest {
        var cancellationCallbackInvoked = false

        val testEffect = Effect {
            channelFlow {
                send(1)
                send(2)
            }
        }.cancellable(
            id = TestCancellationIds.TEST_CALLBACK,
            strategy = CancellationStrategy.KEEP_EXISTING,
            onCancelled = { cancellationCallbackInvoked = true }
        )

        testEffect().test {
            assertEquals(1, awaitItem())
            assertEquals(2, awaitItem())
            awaitComplete()
        }

        delay(100)
        assertFalse(cancellationCallbackInvoked, "onCancelled callback should not be invoked on normal completion")
    }

    // ====================
    // Debugging API Tests
    // ====================

    @Test
    fun `test hasActiveEffects returns true when effects are active`() = testCoroutineScope.runTest {
        assertFalse(hasActiveEffects(TestCancellationIds.TEST_DEBUG), "Should have no active effects initially")

        val testEffect = Effect {
            channelFlow<Int> {
                delay(5000) // Long delay to keep effect active
            }
        }.cancellable(
            id = TestCancellationIds.TEST_DEBUG,
            strategy = CancellationStrategy.KEEP_EXISTING
        )

        testEffect().test {
            delay(100) // Give time for effect to register
            assertTrue(hasActiveEffects(TestCancellationIds.TEST_DEBUG), "Should have active effects")

            // Cancel and verify
            cancelEffect<Int>(TestCancellationIds.TEST_DEBUG).invoke().firstOrNull()
            awaitComplete()
        }

        delay(100)
        assertFalse(hasActiveEffects(TestCancellationIds.TEST_DEBUG), "Should have no active effects after cancellation")
    }

    @Test
    fun `test getActiveEffectCounts returns correct counts`() = testCoroutineScope.runTest {
        val effect1 = Effect {
            channelFlow<Int> {
                delay(5000)
            }
        }.cancellable(
            id = TestCancellationIds.TEST_DEBUG,
            strategy = CancellationStrategy.KEEP_EXISTING
        )

        val effect2 = Effect {
            channelFlow<Int> {
                delay(5000)
            }
        }.cancellable(
            id = TestCancellationIds.TEST_DEBUG,
            strategy = CancellationStrategy.KEEP_EXISTING
        )

        effect1().test {
            effect2().test {
                delay(100) // Give time for effects to register

                val counts = getActiveEffectCounts()
                assertEquals(2, counts[TestCancellationIds.TEST_DEBUG], "Should have 2 active effects")

                // Cancel all
                cancelEffect<Int>(TestCancellationIds.TEST_DEBUG).invoke().firstOrNull()
                awaitComplete()
            }
            awaitComplete()
        }
    }
}
