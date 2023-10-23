@file:OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)

package com.labosu.kmposable

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

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
}