package com.labosu.kmposable.example.tictactoe.start

import com.labosu.kmposable.Reduced
import com.labosu.kmposable.Reducer
import com.labosu.kmposable.Store
import com.labosu.kmposable.example.tictactoe.AppFeature
import com.labosu.kmposable.noEffect
import com.labosu.kmposable.pullback

object StartGameFeature : Reducer<StartGameFeature.State, StartGameFeature.Action> {

    // ----- ACTION ----------------
    sealed class Action {
        data class UpdatePlayerOneName(val name: String) : Action()
        data class UpdatePlayerTwoName(val name: String) : Action()
        data object StartGameTapped : Action()
    }

    // ----- STATE ----------------
    data class State(
        val playerOneName: String = "Player 1",
        val playerTwoName: String = "Player 2"
    )

    // ----- REDUCER ----------------
    override fun reduce(state: State, action: Action): Reduced<State, Action> {
        return when (action) {
            is Action.UpdatePlayerOneName -> {
                state.copy(playerOneName = action.name).noEffect()
            }

            is Action.UpdatePlayerTwoName -> {
                state.copy(playerTwoName = action.name).noEffect()
            }

            Action.StartGameTapped -> state.noEffect()  // handled in AppFeature
        }
    }

    // ----- MAPPING ----------------
    internal val mapToChildState: (AppFeature.State) -> State = {
        State(playerOneName = it.playerOne.name, playerTwoName = it.playerTwo.name)
    }

    internal val mapToChildAction: (AppFeature.Action) -> Action? = {
        if (it is AppFeature.Action.StartGameFeatureAction) it.action else null
    }

    internal val mapToParentState: (AppFeature.State, State) -> AppFeature.State =
        { appState, startGameState ->
            appState.copy(
                playerOne = appState.playerOne.copy(name = startGameState.playerOneName),
                playerTwo = appState.playerTwo.copy(name = startGameState.playerTwoName),
            )
        }

    internal val mapToParentAction: (Action) -> AppFeature.Action = {
        when (it) {
            Action.StartGameTapped -> AppFeature.Action.StartGame
            else -> AppFeature.Action.StartGameFeatureAction(it)
        }
    }
}

// ----- STORE ----------------
// mapping from AppStore -> GameFeatureStore
fun Store<AppFeature.State, AppFeature.Action>.startGameStore(): Store<StartGameFeature.State, StartGameFeature.Action> {
    return this.scope(StartGameFeature.mapToChildState, StartGameFeature.mapToParentAction)
}

// Game Reducer Pullback (GameFeatureStore -> AppStore)
fun Reducer<StartGameFeature.State, StartGameFeature.Action>.pullbackReducer(): Reducer<AppFeature.State, AppFeature.Action> =
    pullback(
        StartGameFeature.mapToChildState, StartGameFeature.mapToChildAction,
        StartGameFeature.mapToParentState, StartGameFeature.mapToParentAction
    )

