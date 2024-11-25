package com.labosu.kmposable.example.tictactoe.game

import com.labosu.kmposable.Reduced
import com.labosu.kmposable.Reducer
import com.labosu.kmposable.Store
import com.labosu.kmposable.example.tictactoe.AppFeature
import com.labosu.kmposable.example.tictactoe.appStore
import com.labosu.kmposable.noEffect
import com.labosu.kmposable.optionalPullback
import com.labosu.kmposable.withEffect
import kotlin.random.Random


object GameFeature : Reducer<GameFeature.State, GameFeature.Action> {
    // ----- ACTION ----------------
    sealed class Action {
        data class CellTapped(val row: Int, val column: Int) : Action()
        data object PlayAgainTapped : Action()
        data object EndTapped : Action()
    }

    // ----- STATE ----------------
    enum class PlayerSymbol(val symbol: String) {
        X("❌"), O("⭕️")
    }

    data class Player(
        val name: String, val symbol: PlayerSymbol
    )

    data class State(
        val playerOne: Player = Player("Player 1", PlayerSymbol.X),
        val playerTwo: Player = Player("Player 2", PlayerSymbol.O),
        val currentPlayer: Player = playerOne,
        val board: GameBoard = GameBoard()
    ) {

        val winnerText: String
            get() {
                val bstate = board.boardState
                return when (bstate) {
                    GameBoard.State.InPlay -> ""
                    GameBoard.State.Draw -> "Cat's game!"
                    is GameBoard.State.HasWinner -> "Winner: ${bstate.player.name}"
                }
            }
    }

    // ----- REDUCER ----------------
    override fun reduce(state: State, action: Action): Reduced<State, Action> {
        return when (action) {
            is Action.CellTapped -> {
                if (state.board.getCell(action.row, action.column) != null || state.board.boardState != GameBoard.State.InPlay) {
                    return state.noEffect()
                }

                // altering a collection does not cause state update flow to propagate
                // need to create a "new" state to propagate
                val newBoard = state.board.setCell(action.row, action.column, state.currentPlayer)

                if (newBoard.boardState == GameBoard.State.InPlay) {
                    // toggle to other player
                    val nextPlayer = if (state.playerOne == state.currentPlayer) state.playerTwo else state.playerOne
                    state.copy(board = newBoard, currentPlayer = nextPlayer).noEffect()
                } else {
                    // ensure state updates
                    state.copy(board = newBoard).noEffect()
                }
            }

            Action.PlayAgainTapped -> {
                //reset
                state.copy(currentPlayer = state.playerOne, board = GameBoard()).noEffect()
            }

            Action.EndTapped -> state.withEffect {
                // This is not particularly great but sending to appStore
                // lets us send actions outside the existing feature
                appStore.send(AppFeature.Action.EndGame)
                null
            }
        }
    }
}

// ----- STORE ----------------
// mapping from AppStore to GameFeatureStore
fun Store<AppFeature.State, AppFeature.Action>.gameStore(): Store<GameFeature.State, GameFeature.Action> {
    return this.optionalScope(
        toChildState = { it.game },
        fromChildAction = { AppFeature.Action.GameFeatureAction(it) }
    )
}

// Game Reducer Pullback
fun Reducer<GameFeature.State, GameFeature.Action>.pullbackReducer(): Reducer<AppFeature.State, AppFeature.Action> =
    optionalPullback(
        mapToChildState = { it.game },
        mapToChildAction = { if (it is AppFeature.Action.GameFeatureAction) it.action else null },
        mapToParentState = { appState, gameState -> appState.copy(game = gameState) },
        mapToParentAction = { AppFeature.Action.GameFeatureAction(it) }
    )
