package com.labosu.kmposable.example.tictactoe.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.labosu.kmposable.example.tictactoe.AppFeature
import com.labosu.kmposable.example.tictactoe.android.feature.game.GameScreen
import com.labosu.kmposable.example.tictactoe.android.feature.newgame.NewGameView
import com.labosu.kmposable.example.tictactoe.appStore
import com.labosu.kmposable.example.tictactoe.game.gameStore
import com.labosu.kmposable.example.tictactoe.start.startGameStore

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val state by appStore.state.collectAsStateWithLifecycle(AppFeature.State())
                    if (state.game != null) {
                        GameScreen(appStore.gameStore())
                    } else {
                        NewGameView(appStore.startGameStore())
                    }
                }
            }
        }
    }
}

@Composable
fun GreetingView(text: String) {
    Text(text = text)
}

@Preview
@Composable
fun DefaultPreview() {
    MyApplicationTheme {
        GreetingView("Hello, Android!")
    }
}
