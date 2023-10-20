@file:OptIn(ExperimentalCoroutinesApi::class)

package com.labosu.kmposable

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.ExperimentalTime

class StoreStateTests : StoreCoroutineTest() {
    lateinit var testStore: Store<TestState, TestAction>

    @BeforeTest
    override fun beforeTest() {
        super.beforeTest()
        testStore = createTestStore()
    }


    @Test
    fun `state is emitted in batches after all sent actions were reduced`() = testCoroutineScope.runTest {
        testStore.state.test {

            // initial state
            assertEquals("", awaitItem().testProperty)

            testStore.send(TestAction.DoNothingAction)
            testStore.send(TestAction.ChangeTestProperty("123"))
            testStore.send(TestAction.AddToTestProperty("4"))
            testStore.send(TestAction.DoNothingAction)

            assertEquals("1234", awaitItem().testProperty)
            cancelAndConsumeRemainingEvents()
        }
    }

    @ExperimentalTime
    @Test
    fun `new state is emitted only when it's different than the previous one`() = testCoroutineScope.runTest {
        testStore.state.test {
            testStore.send(TestAction.DoNothingAction)
            testStore.send(TestAction.ChangeTestProperty("123"))
            testStore.send(TestAction.AddToTestProperty("4"))
            testStore.send(TestAction.ChangeTestProperty(""))

            // initial state
            val testProperty = awaitItem().testProperty
            assertEquals("", testProperty)

            cancelAndConsumeRemainingEvents()
        }
    }

    @ExperimentalTime
    @Test
    fun `effects emit new state only after the result state of previously sent actions was emitted`() = testCoroutineScope.runTest {
        testStore.state.test {
            testStore.send(TestAction.DoNothingAction)
            testStore.send(TestAction.ChangeTestProperty("123"))
            testStore.send(TestAction.AddToTestProperty("4"))
            testStore.send(TestAction.StartEffectAction(TestAction.DoNothingFromEffectAction.asEffect()))
            testStore.send(TestAction.StartEffectAction(TestAction.ClearTestPropertyFromEffect.asEffect()))

            // initial state
            assertEquals("", awaitItem().testProperty)

            assertEquals("1234", awaitItem().testProperty)

            assertEquals("", awaitItem().testProperty)

            cancelAndConsumeRemainingEvents()
        }
    }
}
