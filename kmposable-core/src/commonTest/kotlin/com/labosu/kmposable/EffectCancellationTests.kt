@file:OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)

package com.labosu.kmposable

import app.cash.turbine.test
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Created by Steven Veltema on 2022/09/30
 */
class EffectCancellationTests : StoreCoroutineTest() {
    val testReducer = TestReducer()
    lateinit var testStore: Store<TestState, TestAction>

    @BeforeTest
    override fun beforeTest() {
        super.beforeTest()
        testStore = createTestStore(reducer = testReducer)
    }

    @Test
    fun `test simple flow emission of a cancellableEffect`() = testCoroutineScope.runTest {
        val testList = listOf(1, 2, 3)
        val testCancellableEffect = Effect { testList.asFlow() }
            .cancellable("test", false)

        assertEquals(testList, testCancellableEffect().toList())
    }

    @Test
    fun `test completion the emission of a cancellableEffect`() = testCoroutineScope.runTest {
        val testCancellableEffect = Effect {
            channelFlow<Int> {
                withContext(Dispatchers.Default) {
                    send(1)
                    send(2)
                    //delay the third value to allow time for cancellation to complete the flow
                    delay(2000)
                    send(3)
                }
            }
        }
            .cancellable("test", false)

        testCancellableEffect().test {
            assertEquals(1, awaitItem())
            assertEquals(2, awaitItem())
            testCancellableEffect.cancel("test").invoke().firstOrNull()
            awaitComplete()
        }
    }

}