@file:OptIn(ExperimentalCoroutinesApi::class)

package com.labosu.kmposable

import com.labosu.kmposable.internal.MutableStateFlowStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFailsWith

class StoreExceptionHandlingTests : StoreCoroutineTest() {
    lateinit var testStore: Store<TestState, TestAction>
    val testExceptionHandler = TestStoreExceptionHandler()

    @BeforeTest
    override fun beforeTest() {
        super.beforeTest()
        testStore = createTestStore(defaultExceptionHandler = testExceptionHandler)
    }

    //when a bare exception is (re)thrown, the whole `runTest` will fail with the exception

    @Test
    fun `reduce exception should be handled`() {
        assertFailsWith<MutableStateFlowStore.Companion.ReducerException> {
            testCoroutineScope.runTest {
                testStore.send(TestAction.ThrowExceptionAction)
                delay(1)
            }
        }
    }

    @Test
    fun `effect exception should be handled`() {
        assertFailsWith<MutableStateFlowStore.Companion.EffectException> {
            testCoroutineScope.runTest {
                testStore.send(TestAction.StartExceptionThrowingEffectAction)
                delay(1)
            }
        }
    }
}