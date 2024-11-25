package com.labosu.kmposable.example.tictactoe.android.feature.game

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.labosu.kmposable.Store
import com.labosu.kmposable.example.tictactoe.game.GameFeature

@Composable
fun GameScreen(store: Store<GameFeature.State, GameFeature.Action>, modifier: Modifier = Modifier) {
    val state by store.state.collectAsStateWithLifecycle(GameFeature.State())

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Current Player: ${state.currentPlayer.name} (${state.currentPlayer.symbol.symbol})")

        Spacer(modifier = Modifier.height(16.dp))

        for (row in 0..2) {
            Row {
                for (col in 0..2) {
                    val cell = state.board.getCell(row, col)
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .background(Color.LightGray)
                            .clickable {
                                store.send(GameFeature.Action.CellTapped(row, col))
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = cell?.symbol?.symbol ?: "",
                            style = MaterialTheme.typography.headlineMedium
                        )
                    }
                    if (col < 2) Spacer(modifier = Modifier.width(8.dp))
                }
            }
            if (row < 2) Spacer(modifier = Modifier.height(8.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(text = state.winnerText)

        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { store.send(GameFeature.Action.PlayAgainTapped) }) {
            Text(text = "New Game")
        }
        Button(onClick = { store.send(GameFeature.Action.EndTapped) }) {
            Text(text = "End")
        }
    }
}