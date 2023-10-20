package com.labosu.kmposable

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

@OptIn(ExperimentalCoroutinesApi::class)
abstract class StoreCoroutineTest {
    val testDispatcher = StandardTestDispatcher()
    val testCoroutineScope = TestScope(testDispatcher)

    @BeforeTest
    open fun beforeTest() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    open fun afterTest() {
        Dispatchers.resetMain()
        testDispatcher.scheduler.runCurrent()
    }
}
