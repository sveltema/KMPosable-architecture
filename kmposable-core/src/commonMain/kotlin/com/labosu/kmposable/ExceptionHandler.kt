package com.labosu.kmposable

/**
 * Allows one to inject custom exception handling in a store
 */
interface ExceptionHandler {

    /**
     * Called whenever an exception is thrown during the reduce process
     * @return true if the exception was handled, false if it should be propagated
     */
    suspend fun handleException(exception: Throwable): Boolean
}
