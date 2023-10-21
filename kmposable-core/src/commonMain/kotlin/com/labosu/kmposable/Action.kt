package com.labosu.kmposable

import kotlinx.coroutines.CoroutineScope

/**
 * Created by Steven Veltema on 2022/12/28
 *
 * Sending a ScopedAction, will collect the flows from the
 * resulting effect only while the provided CoroutineScope is active.
 * Once the scope becomes inactive, the effect will cancel
 */

interface ScopedAction {
    val scope: CoroutineScope
}
