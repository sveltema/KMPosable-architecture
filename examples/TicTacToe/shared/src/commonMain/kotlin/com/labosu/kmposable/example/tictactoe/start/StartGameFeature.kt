package com.labosu.kmposable.example.tictactoe.start

import com.labosu.kmposable.Reduced
import com.labosu.kmposable.Reducer
import com.labosu.kmposable.Store
import com.labosu.kmposable.example.tictactoe.AppFeature
import com.labosu.kmposable.example.tictactoe.appStore
import com.labosu.kmposable.noEffect
import com.labosu.kmposable.pullback
import com.labosu.kmposable.withEffect

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

            Action.StartGameTapped -> {
                if (state.playerOneName.isNotBlank() && state.playerTwoName.isNotBlank())
                    state.withEffect {
                        // use an Effect to send non-feature Actions
                        appStore.send(AppFeature.Action.StartGame)
                        null
                    }
                else
                    state.noEffect()
            }
        }
    }
}

// ----- STORE ----------------
// mapping from AppStore -> GameFeatureStore
fun Store<AppFeature.State, AppFeature.Action>.startGameStore(): Store<StartGameFeature.State, StartGameFeature.Action> {
    return this.scope(
        toChildState = {
            StartGameFeature.State(playerOneName = it.playerOne.name, playerTwoName = it.playerTwo.name)
        },
        fromChildAction = { AppFeature.Action.StartGameFeatureAction(it) }
    )
}

// Game Reducer Pullback (GameFeatureStore -> AppStore)
fun Reducer<StartGameFeature.State, StartGameFeature.Action>.pullbackReducer(): Reducer<AppFeature.State, AppFeature.Action> =
    pullback(
        mapToChildState = {
            StartGameFeature.State(playerOneName = it.playerOne.name, playerTwoName = it.playerTwo.name)
        },
        mapToChildAction = {
            if (it is AppFeature.Action.StartGameFeatureAction) it.action else null
        },
        mapToParentState = { appState, startGameState ->
            appState.copy(
                playerOne = appState.playerOne.copy(name = startGameState.playerOneName),
                playerTwo = appState.playerTwo.copy(name = startGameState.playerTwoName),
            )
        },
        mapToParentAction = { AppFeature.Action.StartGameFeatureAction(it) }
    )