package com.labosu.kmposable

import kotlinx.coroutines.flow.Flow

/**
 * Created by Steven Veltema on 2023/10/27
 */
data class Reduced<out State, out Action>(val state: State, val effect: Effect<Action>? = null)

fun <State, Action> State.noEffect(): Reduced<State, Action> = Reduced(this)

infix fun <State, Action> State.with(that: Action): Reduced<State, Action> = Reduced(this, that.asEffect())
infix fun <State, Action> State.with(that: Effect<Action>?): Reduced<State, Action> = Reduced(this, that)
infix fun <State, Action> State.with(that: Flow<Action>?): Reduced<State, Action> = Reduced(this, that?.asEffect())

fun <State, Action> State.withEffect(effect: Effect<Action>?): Reduced<State, Action> = Reduced(this, effect)
fun <State, Action> State.withEffect(transform: State.() -> Effect<Action>?): Reduced<State, Action> = Reduced(this, transform(this))