package com.labosu.kmposable

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * Tests for the ifCaseLet reducer combinator.
 * Verifies sealed class case handling and composition.
 */
class IfCaseLetReducerTests {

    // Define test sealed classes for state
    sealed class AppState {
        object Loading : AppState()
        data class LoggedIn(val userName: String, val count: Int = 0) : AppState()
        data class LoggedOut(val errorMessage: String? = null) : AppState()
    }

    // Define test sealed classes for actions
    sealed class AppAction {
        data class LoginAction(val action: LoginScreenAction) : AppAction()
        data class HomeAction(val action: HomeScreenAction) : AppAction()
        object ShowLoading : AppAction()
    }

    sealed class LoginScreenAction {
        data class UpdateError(val message: String) : LoginScreenAction()
        object ClearError : LoginScreenAction()
    }

    sealed class HomeScreenAction {
        object Increment : HomeScreenAction()
        object Decrement : HomeScreenAction()
        data class SetUserName(val name: String) : HomeScreenAction()
    }

    // Login screen reducer (operates on LoggedOut case)
    private val loginReducer = Reducer<AppState.LoggedOut, LoginScreenAction> { state, action ->
        when (action) {
            is LoginScreenAction.UpdateError ->
                Reduced(state.copy(errorMessage = action.message))
            LoginScreenAction.ClearError ->
                Reduced(state.copy(errorMessage = null))
        }
    }

    // Home screen reducer (operates on LoggedIn case)
    private val homeReducer = Reducer<AppState.LoggedIn, HomeScreenAction> { state, action ->
        when (action) {
            HomeScreenAction.Increment ->
                Reduced(state.copy(count = state.count + 1))
            HomeScreenAction.Decrement ->
                Reduced(state.copy(count = state.count - 1))
            is HomeScreenAction.SetUserName ->
                Reduced(state.copy(userName = action.name))
        }
    }

    @Test
    fun `ifCaseLet handles matching state and action`() {
        // Compose login reducer into app-level reducer
        val appReducer = loginReducer.ifCaseLet<AppState.LoggedOut, AppState, LoginScreenAction, AppAction>(
            mapToChildAction = { (it as? AppAction.LoginAction)?.action },
            mapToParentState = { _, loggedOut -> loggedOut },
            mapToParentAction = { AppAction.LoginAction(it) }
        )

        val initialState = AppState.LoggedOut(errorMessage = null)
        val action = AppAction.LoginAction(LoginScreenAction.UpdateError("Invalid credentials"))

        val result = appReducer.reduce(initialState, action)

        assertIs<AppState.LoggedOut>(result.state)
        assertEquals("Invalid credentials", result.state.errorMessage)
        assertNull(result.effect)
    }

    @Test
    fun `ifCaseLet ignores non-matching state`() {
        val appReducer = loginReducer.ifCaseLet<AppState.LoggedOut, AppState, LoginScreenAction, AppAction>(
            mapToChildAction = { (it as? AppAction.LoginAction)?.action },
            mapToParentState = { _, loggedOut -> loggedOut },
            mapToParentAction = { AppAction.LoginAction(it) }
        )

        // State is LoggedIn, but reducer expects LoggedOut
        val initialState = AppState.LoggedIn("Alice", 5)
        val action = AppAction.LoginAction(LoginScreenAction.UpdateError("Error"))

        val result = appReducer.reduce(initialState, action)

        // State should be unchanged
        assertIs<AppState.LoggedIn>(result.state)
        assertEquals("Alice", result.state.userName)
        assertEquals(5, result.state.count)
        assertNull(result.effect)
    }

    @Test
    fun `ifCaseLet ignores non-matching action`() {
        val appReducer = loginReducer.ifCaseLet<AppState.LoggedOut, AppState, LoginScreenAction, AppAction>(
            mapToChildAction = { (it as? AppAction.LoginAction)?.action },
            mapToParentState = { _, loggedOut -> loggedOut },
            mapToParentAction = { AppAction.LoginAction(it) }
        )

        val initialState = AppState.LoggedOut(errorMessage = "Old error")
        // Action is HomeAction, not LoginAction
        val action = AppAction.HomeAction(HomeScreenAction.Increment)

        val result = appReducer.reduce(initialState, action)

        // State should be unchanged
        assertIs<AppState.LoggedOut>(result.state)
        assertEquals("Old error", result.state.errorMessage)
        assertNull(result.effect)
    }

    @Test
    fun `ifCaseLet ignores loading state`() {
        val appReducer = loginReducer.ifCaseLet<AppState.LoggedOut, AppState, LoginScreenAction, AppAction>(
            mapToChildAction = { (it as? AppAction.LoginAction)?.action },
            mapToParentState = { _, loggedOut -> loggedOut },
            mapToParentAction = { AppAction.LoginAction(it) }
        )

        val initialState = AppState.Loading
        val action = AppAction.LoginAction(LoginScreenAction.ClearError)

        val result = appReducer.reduce(initialState, action)

        // State should be unchanged
        assertIs<AppState.Loading>(result.state)
        assertNull(result.effect)
    }

    @Test
    fun `multiple ifCaseLet reducers can be combined`() {
        // Compose both login and home reducers
        val appReducer = combine(
            loginReducer.ifCaseLet<AppState.LoggedOut, AppState, LoginScreenAction, AppAction>(
                mapToChildAction = { (it as? AppAction.LoginAction)?.action },
                mapToParentState = { _, loggedOut -> loggedOut },
                mapToParentAction = { AppAction.LoginAction(it) }
            ),
            homeReducer.ifCaseLet<AppState.LoggedIn, AppState, HomeScreenAction, AppAction>(
                mapToChildAction = { (it as? AppAction.HomeAction)?.action },
                mapToParentState = { _, loggedIn -> loggedIn },
                mapToParentAction = { AppAction.HomeAction(it) }
            )
        )

        // Test login action on LoggedOut state
        val loggedOutState = AppState.LoggedOut()
        val loginAction = AppAction.LoginAction(LoginScreenAction.UpdateError("Error"))
        val loginResult = appReducer.reduce(loggedOutState, loginAction)

        assertIs<AppState.LoggedOut>(loginResult.state)
        assertEquals("Error", loginResult.state.errorMessage)

        // Test home action on LoggedIn state
        val loggedInState = AppState.LoggedIn("Bob", 10)
        val homeAction = AppAction.HomeAction(HomeScreenAction.Increment)
        val homeResult = appReducer.reduce(loggedInState, homeAction)

        assertIs<AppState.LoggedIn>(homeResult.state)
        assertEquals(11, homeResult.state.count)
    }

    @Test
    fun `ifCaseLet forwards effects from child reducer`() {
        // Create reducer that produces effects
        val reducerWithEffect = Reducer<AppState.LoggedOut, LoginScreenAction> { state, action ->
            when (action) {
                is LoginScreenAction.UpdateError -> {
                    val effect = Effect<LoginScreenAction> {
                        kotlinx.coroutines.flow.flow {
                            emit(LoginScreenAction.ClearError)
                        }
                    }
                    Reduced(state.copy(errorMessage = action.message), effect)
                }
                LoginScreenAction.ClearError ->
                    Reduced(state.copy(errorMessage = null))
            }
        }

        val appReducer = reducerWithEffect.ifCaseLet<AppState.LoggedOut, AppState, LoginScreenAction, AppAction>(
            mapToChildAction = { (it as? AppAction.LoginAction)?.action },
            mapToParentState = { _, loggedOut -> loggedOut },
            mapToParentAction = { AppAction.LoginAction(it) }
        )

        val initialState = AppState.LoggedOut()
        val action = AppAction.LoginAction(LoginScreenAction.UpdateError("Test"))

        val result = appReducer.reduce(initialState, action)

        // Should have effect
        assertIs<AppState.LoggedOut>(result.state)
        assertEquals("Test", result.state.errorMessage)
        assertEquals(true, result.effect != null)
    }

    @Test
    fun `ifCaseLet handles state transitions within same sealed class`() {
        val homeReducer = Reducer<AppState.LoggedIn, HomeScreenAction> { state, action ->
            when (action) {
                HomeScreenAction.Increment ->
                    Reduced(state.copy(count = state.count + 1))
                is HomeScreenAction.SetUserName ->
                    Reduced(state.copy(userName = action.name))
                HomeScreenAction.Decrement ->
                    Reduced(state.copy(count = state.count - 1))
            }
        }

        val appReducer = homeReducer.ifCaseLet<AppState.LoggedIn, AppState, HomeScreenAction, AppAction>(
            mapToChildAction = { (it as? AppAction.HomeAction)?.action },
            mapToParentState = { _, loggedIn -> loggedIn },
            mapToParentAction = { AppAction.HomeAction(it) }
        )

        var currentState: AppState = AppState.LoggedIn("Alice", 0)

        // Increment
        currentState = appReducer.reduce(currentState, AppAction.HomeAction(HomeScreenAction.Increment)).state
        assertIs<AppState.LoggedIn>(currentState)
        assertEquals(1, currentState.count)

        // Change name
        currentState = appReducer.reduce(currentState, AppAction.HomeAction(HomeScreenAction.SetUserName("Bob"))).state
        assertIs<AppState.LoggedIn>(currentState)
        assertEquals("Bob", currentState.userName)
        assertEquals(1, currentState.count)

        // Decrement
        currentState = appReducer.reduce(currentState, AppAction.HomeAction(HomeScreenAction.Decrement)).state
        assertIs<AppState.LoggedIn>(currentState)
        assertEquals(0, currentState.count)
    }

    // Nested sealed class hierarchy for testing
    sealed class ComplexState {
        sealed class Active : ComplexState() {
            data class Running(val progress: Int) : Active()
            data class Paused(val reason: String) : Active()
        }
        object Inactive : ComplexState()
    }

    sealed class ComplexAction {
        data class UpdateProgress(val progress: Int) : ComplexAction()
    }

    @Test
    fun `ifCaseLet with complex nested sealed class hierarchy`() {
        val runningReducer = Reducer<ComplexState.Active.Running, ComplexAction> { state, action ->
            when (action) {
                is ComplexAction.UpdateProgress ->
                    Reduced(state.copy(progress = action.progress))
            }
        }

        val complexReducer = runningReducer.ifCaseLet<ComplexState.Active.Running, ComplexState, ComplexAction, ComplexAction>(
            mapToChildAction = { it },
            mapToParentState = { _, running -> running },
            mapToParentAction = { it }
        )

        // Works with Running state
        val runningState = ComplexState.Active.Running(50)
        val result1 = complexReducer.reduce(runningState, ComplexAction.UpdateProgress(75))
        assertIs<ComplexState.Active.Running>(result1.state)
        assertEquals(75, result1.state.progress)

        // Ignores Paused state (different case of Active)
        val pausedState = ComplexState.Active.Paused("User paused")
        val result2 = complexReducer.reduce(pausedState, ComplexAction.UpdateProgress(100))
        assertIs<ComplexState.Active.Paused>(result2.state)
        assertEquals("User paused", result2.state.reason)

        // Ignores Inactive state
        val inactiveState = ComplexState.Inactive
        val result3 = complexReducer.reduce(inactiveState, ComplexAction.UpdateProgress(100))
        assertIs<ComplexState.Inactive>(result3.state)
    }
}
