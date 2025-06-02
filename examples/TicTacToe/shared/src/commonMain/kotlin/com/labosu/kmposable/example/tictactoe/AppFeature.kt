package com.labosu.kmposable.example.tictactoe

import com.labosu.kmposable.Reduced
import com.labosu.kmposable.Reducer
import com.labosu.kmposable.Store
import com.labosu.kmposable.combine
import com.labosu.kmposable.createStore
import com.labosu.kmposable.example.tictactoe.game.GameFeature
import com.labosu.kmposable.example.tictactoe.game.pullbackReducer
import com.labosu.kmposable.example.tictactoe.start.StartGameFeature
import com.labosu.kmposable.example.tictactoe.start.pullbackReducer
import com.labosu.kmposable.noEffect
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope

object AppFeature : Reducer<AppFeature.State, AppFeature.Action> {

    // ----- ACTION ----------------
    sealed class Action {
        data object StartGame : Action()
        data object EndGame : Action()
        data class GameFeatureAction(val action: GameFeature.Action) : Action()
        data class StartGameFeatureAction(val action: StartGameFeature.Action) : Action()
    }

    // ----- STATE ----------------
    data class State(
        val playerOne: GameFeature.Player = GameFeature.Player(
            "Player 1",
            GameFeature.PlayerSymbol.X
        ),
        val playerTwo: GameFeature.Player = GameFeature.Player(
            "Player 2",
            GameFeature.PlayerSymbol.O
        ),
        val game: GameFeature.State? = null
    )

    override fun reduce(state: State, action: Action): Reduced<State, Action> {
        return when (action) {
            Action.StartGame ->
                state.copy(
                    game = GameFeature.State(
                        playerOne = state.playerOne,
                        playerTwo = state.playerTwo
                    )
                ).noEffect()

            Action.EndGame ->
                state.copy(game = null).noEffect()

            else -> state.noEffect()
        }
    }
}

@OptIn(DelicateCoroutinesApi::class)
val appStore: Store<AppFeature.State, AppFeature.Action> = createStore(
    initialState = AppFeature.State(
        playerOne = GameFeature.Player("Player1", symbol = GameFeature.PlayerSymbol.X),
        playerTwo = GameFeature.Player("Player2", symbol = GameFeature.PlayerSymbol.O)
    ),
    storeScope = GlobalScope,
    // combine all feature reducers
    reducer = combine(
        AppFeature,
        StartGameFeature.pullbackReducer(),
        GameFeature.pullbackReducer(),
    )
)

