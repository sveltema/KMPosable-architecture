@file:OptIn(ExperimentalCoroutinesApi::class)

package com.labosu.kmposable

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Created by Steven Veltema on 2022/09/26
 */
class StoreReducerTests : StoreCoroutineTest() {
    val testReducer = TestReducer()
    lateinit var testStore: Store<TestState, TestAction>

    @BeforeTest
    override fun beforeTest() {
        super.beforeTest()
        testStore = createTestStore(reducer = testReducer)
    }


    @Test
    fun `reducer should be called exactly once if one action is sent`() = testCoroutineScope.runTest {
        testStore.send(TestAction.DoNothingAction)
        testCoroutineScope.runCurrent()
        assertTrue { testReducer.reduceCount == 1 }
    }

    @Test
    fun `reducer should be called for each action sent in order in which they were provided`() = testCoroutineScope.runTest {
        val startUselessEffectAction = TestAction.StartEffectAction(TestAction.DoNothingFromEffectAction.asEffect())

        val actionList = mutableListOf(
            TestAction.DoNothingAction,
            startUselessEffectAction,
            TestAction.DoNothingAction,
        )

        actionList.forEach {  testStore.send(it)}
        testCoroutineScope.runCurrent()

        //add the effect action
        actionList.add(TestAction.DoNothingFromEffectAction)
        assertTrue { testReducer.actions == actionList }
    }
}