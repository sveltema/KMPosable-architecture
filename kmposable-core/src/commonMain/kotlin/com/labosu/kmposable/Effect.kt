package com.labosu.kmposable

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

fun interface Effect<out Action> {
    operator fun invoke(): Flow<Action>
}

internal val none = Effect<Nothing> { emptyFlow() }

fun <Action> emptyEffect(): Effect<Action> = none
fun <Action> noEffect(): Effect<Action> = none

fun <Action, R> Effect<Action>.map(mapFn: (Action) -> R): Effect<R> = Effect { this.invoke().map { mapFn(it) } }

fun <Action> Action.asEffect(): Effect<Action> = Effect { flowOf(this) }
fun <Action> Iterable<Action>.asEffect(): Effect<Action> = Effect { this.asFlow() }

@OptIn(ExperimentalCoroutinesApi::class)
fun <Action> Flow<Iterable<Action>>.concatAsEffect(): Effect<Action> = Effect { this.flatMapConcat { it.asFlow() } }
fun <Action> Iterable<Flow<Action>>.mergeAsEffect(): Effect<Action> = Effect { this.merge() }

fun <Action> Flow<Action>.asEffect(): Effect<Action> = Effect { this }

fun <Action> (() -> Action).asEffect(): Effect<Action> = Effect { flow { emit(invoke()) } }

fun <Action> Iterable<Effect<Action>>.merge() = Effect { this.map { it.invoke() }.merge() }

fun <Action> Effect<Action>.concatenate(other: Effect<Action>) = Effect { this.invoke().onCompletion { if (it == null) emitAll(other.invoke()) } }
@OptIn(ExperimentalCoroutinesApi::class)
fun <Action> Iterable<Effect<Action>>.concatenate() = Effect { this.map { it.invoke() }.asFlow().flattenConcat() }

fun <Action> (() -> Unit).fireAndForget(): Effect<Action> = Effect { flow { invoke() } } //never emits
fun <Action> (suspend () -> Unit).fireAndForget(): Effect<Action> = Effect { flow { invoke() } } //never emits