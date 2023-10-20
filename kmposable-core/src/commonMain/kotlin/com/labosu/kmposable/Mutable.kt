package com.labosu.kmposable

/**
 * This class is meant as a way to overcome the limitation that Kotlin has regarding
 * value types. This allows you to mutate state inside reducers without sacrificing
 * the immutability and ease of use of data classes.
 */
class Mutable<T>(private val getValue: () -> T, private val setValue: (T) -> Unit) {

    operator fun invoke() = getValue()

    /**
     * Mutates the underlying value of this Mutable
     * @param transformFn   Function that transforms the state and returns a new one
     */
    fun mutate(transformFn: T.() -> (T)) {
        val newValue = transformFn(getValue())
        setValue(newValue)
    }

    /**
     * Allows mapping the current value of this mutable
     * @param fn A function that will be called using the current value of this mutable as this
     */
    fun <R> withValue(fn: T.() -> R): R = fn(getValue())

    internal fun <R> map(getMap: (T) -> R, mapSet: (T, R) -> T): Mutable<R> =
        Mutable(
            getValue = { getMap(getValue()) },
            setValue = { value -> this.mutate { mapSet(this, value) } }
        )
}

fun <T, Action> Mutable<T>.mutateWithoutEffects(mutateFn: T.() -> T): Effect<Action> = mutate(mutateFn).let { noEffect() }
fun <T, Action> Mutable<T>.mutateWithEffect(mutateFn: T.() -> T, effect: Effect<Action>): Effect<Action> = mutate(mutateFn).let { effect }
fun <T, Action> Mutable<T>.mutateWithEffect(mutateFn: T.() -> T, effectFn: T.() -> Effect<Action>): Effect<Action> = mutate(mutateFn).let { withValue(effectFn) }