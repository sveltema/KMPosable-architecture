# KMP-composable-architecture

Kotlin Multiplatform implementation of [Point-Free's The Composable Architecture](https://github.com/pointfreeco/swift-composable-architecture)
loosely based on [Toggl's Android implementation](https://github.com/toggl/komposable-architecture) but with several fundamental changes and improvements.

## Version information:
| Version       | Kotlin | Coroutines |
|---------------|--------|:----------:|
| _latest_      | 1.9.10 |   1.7.3    |

## Differences from Toggl's komposable-architecture
* Kotlin Multiplatform support for use in KMM projects
* The `Effect` interface is based on `Flows` instead of suspending functions allowing for long running Effects
* Addition of explicit cancellation of Effects
* Addition of `ScopedActions` for providing scopes for long running effects (for example ViewModelScopes in Android)
* Removal of `Subscriptions`
* An improved `MutableStateFlowStore` send function with support for buffering and batching actions.
* Minor name changes to more closely match the original Swift implementation

## © Licence

```
Copyright 2023 Steven Veltema

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
