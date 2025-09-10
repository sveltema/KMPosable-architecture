@file:Suppress("unused")

package com.labosu.kmposable

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flattenConcat
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onCompletion

fun interface Effect<out Action> {
    operator fun invoke(): Flow<Action>
}

internal val none = Effect<Nothing> { emptyFlow() }
fun <Action> emptyEffect(): Effect<Action> = none

// asEffect functions
fun <Action> Flow<Action>.asEffect(): Effect<Action> = Effect { this }

fun <Action> Action.asEffect(): Effect<Action> = Effect { flowOf(this) }
fun <Action> Iterable<Action>.asEffect(): Effect<Action> = Effect { asFlow() }

fun <Action> (() -> Action).asEffect(): Effect<Action> = Effect { flow { emit(invoke()) } }
fun <Action> (suspend () -> Action).asEffect(): Effect<Action> = Effect { flow { emit(invoke()) } }

fun <Action> (() -> Unit).fireAndForget(): Effect<Action> = Effect { flow<Nothing> { invoke() } }// never emits
fun <Action> (suspend () -> Unit).fireAndForget(): Effect<Action> = Effect { flow<Nothing> { invoke() } }// never emits

// transformations
inline fun <Action, R> Effect<Action>.map(crossinline mapFn: suspend (Action) -> R): Effect<R> =
    Effect { this.invoke().map { mapFn(it) } }

fun <Action> Iterable<Effect<Action>>.merge() = Effect { this.map { it.invoke() }.merge() }

@OptIn(ExperimentalCoroutinesApi::class)
fun <Action> Iterable<Effect<Action>>.concatenate() = Effect { this.map { it.invoke() }.asFlow().flattenConcat() }
fun <Action> Effect<Action>.concatenate(other: Effect<Action>) =
    Effect { this.invoke().onCompletion { if (it == null) emitAll(other.invoke()) } }
