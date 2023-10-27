package com.labosu.kmposable

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

fun interface Effect<out Action> {
    operator fun invoke(): Flow<Action>
}

internal val none = Effect<Nothing> { emptyFlow() }
fun <Action> emptyEffect(): Effect<Action> = none

// asEffect functions
fun <Action> Flow<Action>.asEffect(): Effect<Action> = Effect { this }

fun <Action> Action.asEffect(): Effect<Action> = flowOf(this).asEffect()
fun <Action> Iterable<Action>.asEffect(): Effect<Action> = this.asFlow().asEffect()

fun <Action> (() -> Action).asEffect(): Effect<Action> = flow { emit(invoke()) }.asEffect()
fun <Action> (suspend () -> Action).asEffect(): Effect<Action> = flow { emit(invoke()) }.asEffect()

fun <Action> (() -> Unit).fireAndForget(): Effect<Action> = flow<Nothing> { invoke() }.asEffect() //never emits
fun <Action> (suspend () -> Unit).fireAndForget(): Effect<Action> = flow<Nothing> { invoke() }.asEffect() //never emits

// transformations
inline fun <Action, R> Effect<Action>.map(crossinline mapFn: suspend (Action) -> R): Effect<R> = this.invoke().map { mapFn(it) }.asEffect()

fun <Action> Iterable<Effect<Action>>.merge() = this.map { it.invoke() }.merge().asEffect()

@OptIn(ExperimentalCoroutinesApi::class)
fun <Action> Iterable<Effect<Action>>.concatenate() = this.map { it.invoke() }.asFlow().flattenConcat().asEffect()
fun <Action> Effect<Action>.concatenate(other: Effect<Action>) = this.invoke().onCompletion { if (it == null) emitAll(other.invoke()) }.asEffect()