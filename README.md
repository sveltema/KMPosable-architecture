# KMPosable Architecture

Kotlin Multiplatform implementation of [Point-Free's The Composable Architecture](https://github.com/pointfreeco/swift-composable-architecture), loosely based on [Toggl's Android implementation](https://github.com/toggl/komposable-architecture) with several fundamental improvements. It provides a unidirectional data flow architecture for building scalable KMM applications.

## Features

- **Mobile-centric Kotlin Multiplatform Support** - Works on Android, iOS (iosX64, iosArm64, iosSimulatorArm64)
- **Flow-Based Effects** - Long-running operations using Kotlin Flows instead of suspending functions
- **Explicit Effect Cancellation** - Cancel effects by ID with `cancellable()` and `cancel()` APIs
- **Scoped Actions** - Lifecycle-aware effects via `ScopedAction` interface
- **High Performance** - Optimized store with action buffering and batching
- **Composable Reducers** - Rich set of composition functions including sealed class support
- **Type-Safe** - Leverages Kotlin's type system for compile-time safety

## Installation

Add the GitHub Packages repository to your `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        maven {
            url = uri("https://maven.pkg.github.com/sveltema/KMPosable-architecture")
            credentials {
                username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_ACTOR")
                password = project.findProperty("gpr.token") as String? ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
```

Add the dependency to your module's `build.gradle.kts`:

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("com.labosu.kmposable:kmposable-core:0.1.2")
        }
    }
}
```

## Quick Start

### 1. Define Your State and Actions

```kotlin
data class CounterState(val count: Int = 0)

sealed class CounterAction {
    object Increment : CounterAction()
    object Decrement : CounterAction()
    data class SetCount(val value: Int) : CounterAction()
}
```

### 2. Create a Reducer

```kotlin
val counterReducer = Reducer<CounterState, CounterAction> { state, action ->
    when (action) {
        is CounterAction.Increment ->
            state.copy(count = state.count + 1).noEffect()
        is CounterAction.Decrement ->
            state.copy(count = state.count - 1).noEffect()
        is CounterAction.SetCount ->
            state.copy(count = action.value).noEffect()
    }
}
```

### 3. Create a Store

```kotlin
val store = createStore(
    initialState = CounterState(),
    reducer = counterReducer,
    storeScope = viewModelScope
)
```

### 4. Observe State and Send Actions

```kotlin
// Observe state
store.state.collect { state ->
    println("Count: ${state.count}")
}

// Send actions
store.send(CounterAction.Increment)
store.sendAll(listOf(CounterAction.Increment, CounterAction.Increment))
```

## Architecture Concepts

### Store

The runtime that holds state and processes actions. Provides:
- `state: Flow<State>` - Observable state
- `send(action: Action)` - Send single action
- `sendAll(actions: Collection<Action>)` - Efficiently send multiple actions
- `scope()` / `optionalScope()` - Transform store for child features

### Reducer

Pure functions that take current state and action, returning new state and optional effects:

```kotlin
fun interface Reducer<State, Action> {
    fun reduce(state: State, action: Action): Reduced<State, Action>
}
```

### Effects

Side effects represented as Flows:

```kotlin
// From Flow
myFlow.asEffect()

// Single action
myAction.asEffect()

// Fire-and-forget
{ doWork() }.fireAndForget<Action>()

// Cancellable effect
myEffect.cancellable(id = "myEffect", cancelInFlight = true)

// Cancel effects
cancelEffect<Action>(id = "myEffect")
```

### Reducer Composition

Powerful composition functions for building complex reducers:

- `combine()` - Combine multiple reducers sequentially
- `pullback()` - Lift child reducer to parent scope
- `optionalPullback()` - For optional child state
- `ifCaseLet()` - Compose reducers for sealed class cases
- `forEachReducer()` / `forEachMapReducer()` - Operate on collections

#### Sealed Class Composition Example

```kotlin
sealed class AppState {
    object Loading : AppState()
    data class LoggedIn(val user: User) : AppState()
    data class LoggedOut(val error: String?) : AppState()
}

val loginReducer: Reducer<AppState.LoggedOut, LoginAction> = // ...

val appReducer = loginReducer.ifCaseLet<AppState.LoggedOut, AppState, LoginAction, AppAction>(
    mapToChildAction = { (it as? AppAction.Login)?.action },
    mapToParentState = { _, loggedOut -> loggedOut },
    mapToParentAction = { AppAction.Login(it) }
)
```

## Key Improvements Over Toggl's Implementation

- **Kotlin Multiplatform** - Full KMM support for shared business logic
- **Flow-Based Effects** - Better support for long-running operations
- **Explicit Cancellation** - Fine-grained control over effect lifecycle
- **Scoped Actions** - Automatic effect cancellation tied to coroutine scopes
- **Performance Optimizations** - Improved action buffering and batching
- **Sealed Class Support** - `ifCaseLet` reducer for type-safe sealed class composition
- **Batch Actions** - `sendAll()` for efficiently processing multiple actions
- **No Subscriptions** - Simplified API surface

## Project Structure

- **kmposable-core** - Core architecture (Store, Reducer, Effect, etc.)
- **kmposable** - Platform-specific utilities

## Requirements

- Kotlin 2.2.21+
- Kotlin Coroutines 1.10.2+
- JVM Target 11+

## Platform Support

- Android (minSdk 26)
- iOS (iosX64, iosArm64, iosSimulatorArm64)

## © Licence

```
Copyright 2025 Steven Veltema

The Initial Developer of some parts of the framework, which are copied from, derived from,
or inspired by Toggle komposable-architecture, is Toggl LLC (https://toggl.com).
Copyright 2021 Toggl LLC.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
