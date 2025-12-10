# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

KMPosable-architecture is a Kotlin Multiplatform implementation of Point-Free's The Composable Architecture (TCA), loosely based on Toggl's Android implementation (https://github.com/toggl/komposable-architecture) with several fundamental improvements. It provides a unidirectional data flow architecture for building scalable KMM applications.

Key differences from Toggl's implementation:
- Full Kotlin Multiplatform support (Android, iOS via iosX64, iosArm64, iosSimulatorArm64)
- Effects based on Flows instead of suspending functions for long-running operations
- Explicit effect cancellation via `cancellable()` and `cancel()` APIs
- `ScopedAction` interface for lifecycle-aware effects
- Improved `MutableStateFlowStore` with action buffering and batching

## Project Structure

The project consists of two main modules:

### kmposable-core
Core library module containing the architecture fundamentals:
- `Store.kt` - Store interface and `createStore()` factory function
- `Reducer.kt` - Reducer protocol with composition functions (`combine`, `pullback`, `optionalPullback`, `ifCaseLet`, `forEachReducer`, `forEachMapReducer`)
- `Effect.kt` - Effect interface (wraps Flow) with transformation functions
- `EffectCancellation.kt` - Explicit effect cancellation via `cancellable(id)` and `cancel(id)`
- `Action.kt` - `ScopedAction` interface for lifecycle-scoped effects
- `internal/MutableStateFlowStore.kt` - Store implementation with action buffering and single-threaded state mutations
- `internal/ScopedActionExtensions.kt` - Automatic effect scoping for `ScopedAction`s

### kmposable
Higher-level module that depends on kmposable-core and provides platform-specific utilities.

## Build Commands

### Running Tests

```bash
# Run all tests across all platforms
./gradlew allTests

# Run tests for specific modules
./gradlew :kmposable-core:allTests
./gradlew :kmposable:allTests

# Run tests for specific platforms
./gradlew :kmposable-core:iosSimulatorArm64Test
./gradlew :kmposable-core:iosX64Test
./gradlew :kmposable-core:testDebugUnitTest  # Android unit tests

# Run single test class (use platform-specific task)
./gradlew :kmposable-core:testDebugUnitTest --tests "com.labosu.kmposable.StoreStateTests"
```

### Building

```bash
# Build all modules and run checks
./gradlew build

# Build specific module
./gradlew :kmposable-core:build
./gradlew :kmposable:build

# Assemble without running tests
./gradlew assemble

# Clean build
./gradlew clean build
```

### Code Quality

```bash
# Run linting
./gradlew lint

# Auto-fix lint issues
./gradlew lintFix

# Check for all issues
./gradlew check
```

### Publishing

```bash
# Publish to GitHub Packages (requires GITHUB_ACTOR and GITHUB_TOKEN in gradle.properties)
./gradlew publish
```

## Architecture Concepts

### Store
The `Store` is the runtime that holds state and processes actions. It's created via:
```kotlin
createStore(
    initialState: State,
    reducer: Reducer<State, Action>,
    storeScope: CoroutineScope,
    exceptionHandler: ExceptionHandler = ...,
    storeDispatcher: CoroutineDispatcher = Dispatchers.Default,
    effectDispatcher: CoroutineDispatcher = Dispatchers.IO
)
```

The store provides:
- `state: Flow<State>` - Observable state flow
- `send(action: Action)` - Send single action
- `sendAll(actions: Collection<Action>)` - Send multiple actions efficiently
- `scope()` / `optionalScope()` - Transform store for child features

Implementation detail: `MutableStateFlowStore` uses a buffering channel to batch rapidly-fired actions together before processing them through the reducer, improving performance. State mutations are serialized on a single-threaded dispatcher to prevent race conditions.

### Reducer
Reducers are pure functions that take current state and an action, returning new state and optional effects:
```kotlin
fun interface Reducer<State, Action> {
    fun reduce(state: State, action: Action): Reduced<State, Action>
}
```

Composition functions:
- `combine()` - Combines multiple reducers sequentially
- `pullback()` - Lifts child reducer to parent scope
- `optionalPullback()` - Like pullback but for optional child state
- `ifCaseLet()` - Lifts reducer for specific sealed class case to parent sealed class
- `forEachReducer()` - Operates on collections of child states by ID
- `forEachMapReducer()` - Operates on maps of child states (better performance for large collections)

#### Sealed Class Composition with ifCaseLet

The `ifCaseLet()` combinator is designed for Kotlin's sealed classes, enabling reducers that operate on specific cases to be composed into reducers for the parent sealed class:

```kotlin
sealed class AppState {
    object Loading : AppState()
    data class LoggedIn(val userName: String, val count: Int) : AppState()
    data class LoggedOut(val errorMessage: String?) : AppState()
}

sealed class AppAction {
    data class Login(val action: LoginAction) : AppAction()
    data class Home(val action: HomeAction) : AppAction()
}

// Reducer that only operates on LoggedOut case
val loginReducer: Reducer<AppState.LoggedOut, LoginAction> = // ...

// Lift to parent sealed class
val appReducer = loginReducer.ifCaseLet<AppState.LoggedOut, AppState, LoginAction, AppAction>(
    mapToChildAction = { (it as? AppAction.Login)?.action },
    mapToParentState = { _, loggedOut -> loggedOut },
    mapToParentAction = { AppAction.Login(it) }
)
```

When the state doesn't match the expected case, or the action can't be mapped, the parent state is returned unchanged. Multiple `ifCaseLet` reducers can be combined to handle different sealed class cases.

### Effects
Effects represent side effects as Flows wrapped in a functional interface:
```kotlin
fun interface Effect<out Action> {
    operator fun invoke(): Flow<Action>
}
```

Creating effects:
- `Flow<Action>.asEffect()` - From existing flow
- `Action.asEffect()` - Single action
- `{ action }.asEffect()` - From lambda
- `{ doWork() }.fireAndForget<Action>()` - Fire-and-forget (returns no actions)

Transforming effects:
- `effect.map { }` - Transform emitted actions
- `listOf(effect1, effect2).merge()` - Run effects in parallel
- `listOf(effect1, effect2).concatenate()` - Run effects sequentially

### Effect Cancellation
Effects can be explicitly cancelled using cancellation IDs:
```kotlin
// Make effect cancellable
myEffect.cancellable(id = "myEffectId", cancelInFlight = true)

// Cancel an effect
cancelEffect<Action>(id = "myEffectId")
cancelEffects<Action>(ids = setOf("id1", "id2"))

// Or via extension
myEffect.cancel(id = "myEffectId")
```

Implementation: Uses a shared mutable map of `MutableSharedFlow` signals keyed by cancellation ID. The `cancellable()` operator races the inner flow against the signal flow.

### Scoped Actions
Actions implementing `ScopedAction` automatically scope their effects to the provided `CoroutineScope`:
```kotlin
interface ScopedAction {
    val scope: CoroutineScope
}
```

When the scope cancels (e.g., ViewModel scope on Android), the effect automatically cancels. Implemented via `ScopedActionExtensions.kt:reduceScoped()`.

## Testing

Tests use:
- `kotlin-test` for assertions
- `kotlinx-coroutines-test` for coroutine testing utilities
- `turbine` for Flow testing

Test structure is located in `src/commonTest/kotlin/` with key test files:
- `StoreStateTests.kt` - Store state management tests
- `StoreReducerTests.kt` - Reducer composition tests
- `StorePerformanceTests.kt` - Store performance benchmarks
- `EffectCancellationTests.kt` - Effect cancellation tests
- `ScopedActionTests.kt` - Scoped action lifecycle tests
- `IfCaseLetReducerTests.kt` - Sealed class composition tests
- `StoreExceptionHandlingTests.kt` - Exception handling tests

## Publishing

Libraries are published to GitHub Packages at `https://maven.pkg.github.com/sveltema/KMPosable-architecture`

Both modules (`kmposable-core` and `kmposable`) publish with:
- Group: `com.labosu.kmposable`
- Android release variant only
- iOS targets: iosX64, iosArm64, iosSimulatorArm64
