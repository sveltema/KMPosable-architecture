package com.labosu.kmposable

/**
 * Created by Steven Veltema on 2023/10/27
 */
data class Reduced<out State, out Action>(val state: State, val effect: Effect<Action>? = null)

infix fun <State, Action> State.with(that: Effect<Action>?): Reduced<State, Action> = Reduced(this, that)
fun <State, Action> State.withEffect(effect: Effect<Action>?): Reduced<State, Action> = Reduced(this, effect)
fun <State, Action> State.withEffect(transform: State.() -> Effect<Action>?): Reduced<State, Action> = Reduced(this, transform(this))
fun <State, Action> State.noEffect(): Reduced<State, Action> = Reduced(this)
