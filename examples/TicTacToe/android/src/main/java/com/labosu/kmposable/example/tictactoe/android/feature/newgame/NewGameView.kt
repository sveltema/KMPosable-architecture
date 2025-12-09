package com.labosu.kmposable.example.tictactoe.android.feature.newgame

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.labosu.kmposable.Store
import com.labosu.kmposable.example.tictactoe.start.StartGameFeature
import kotlinx.coroutines.flow.map

@Composable
fun NewGameView(
    store: Store<StartGameFeature.State, StartGameFeature.Action>,
    modifier: Modifier = Modifier
) {
    val p1Name by store.state.map { it.playerOneName }.collectAsStateWithLifecycle("")
    val p2Name by store.state.map { it.playerTwoName }.collectAsStateWithLifecycle("")

    Column(modifier = modifier.padding(16.dp)) {
        OutlinedTextField(
            value = p1Name,
            onValueChange = {
                store.send(StartGameFeature.Action.UpdatePlayerOneName(it))
            },
            label = { Text("Player 1 Name") },
            modifier = Modifier.padding(bottom = 8.dp)
        )
        OutlinedTextField(
            value = p2Name,
            onValueChange = { store.send(StartGameFeature.Action.UpdatePlayerTwoName(it)) },
            label = { Text("Player 2 Name") },
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Button(
            onClick = {
                store.send(StartGameFeature.Action.StartGameTapped)
            },
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text("Start Game")
        }
    }
}


@Preview(showBackground = true)
@Composable
fun NewGameViewPreview() {
    NewGameView(store = object : Store<StartGameFeature.State, StartGameFeature.Action> {
        override val state = kotlinx.coroutines.flow.flowOf(StartGameFeature.State())
        override fun <ChildAction : Any> actionScope(fromChildAction: (ChildAction) -> StartGameFeature.Action?): Store<StartGameFeature.State, ChildAction> =
            this as Store<StartGameFeature.State, ChildAction>

        override fun <ChildState, ChildAction : Any> optionalScope(
            toChildState: (StartGameFeature.State) -> ChildState?,
            fromChildAction: (ChildAction) -> StartGameFeature.Action?
        ): Store<ChildState, ChildAction> = this as Store<ChildState, ChildAction>

        override fun <ChildState> scope(toChildState: (StartGameFeature.State) -> ChildState): Store<ChildState, StartGameFeature.Action> =
            this as Store<ChildState, StartGameFeature.Action>

        override fun <ChildState, ChildAction : Any> scope(
            toChildState: (StartGameFeature.State) -> ChildState,
            fromChildAction: (ChildAction) -> StartGameFeature.Action?
        ): Store<ChildState, ChildAction> =
            this as Store<ChildState, ChildAction>

        override fun send(action: StartGameFeature.Action) {}
        override fun sendAll(actions: Collection<StartGameFeature.Action>) {}
    })
}