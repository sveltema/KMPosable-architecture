package com.labosu.kmposable.example.tictactoe.game

import kotlin.random.Random

private const val SIZE = 3

data class GameBoard(
    // Generally Mutable Collections should be avoided for State as changes
    // will not trigger StateFlow update propagation. If used particular care is needed.
    private val field: Array<Array<GameFeature.Player?>> = emptyBoard,
    private val identifier: Int = Random.nextInt()
) {

    sealed class State {
        data class HasWinner(val player: GameFeature.Player) : State()
        data object Draw : State()
        data object InPlay : State()
    }

    private val winner by lazy {
        winConditions.firstNotNullOfOrNull { condition ->
            val winningPositions = condition.map { field[it % SIZE][it / SIZE] }
            val playerPositions = winningPositions.groupBy { it }
            val winnerPosition = playerPositions.filter { (player, position) -> player != null && position.size == SIZE }
            winnerPosition.keys.firstOrNull()
        }
    }

    private val isFilled by lazy {
        field.all { arr -> arr.all { it != null } }
    }

    val boardState: State
        get() {
            winner?.let { return State.HasWinner(it) }

            return if (isFilled) {
                State.Draw
            } else {
                State.InPlay
            }
        }

    fun getCell(row: Int, column: Int): GameFeature.Player? = field[row][column]

    // setting a cell is a mutating function.  Will not update state!
    // need to create a "new" state to propagate
    fun setCell(row: Int, column: Int, value: GameFeature.Player?): GameBoard {
        field[row][column] = value
        return this.copy(identifier = Random.nextInt())
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as GameBoard

        if (identifier != other.identifier) return false
        if (!field.contentDeepEquals(other.field)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = field.contentDeepHashCode()
        result = 31 * result + identifier
        return result
    }

    companion object {
        private val winConditions = arrayOf(
            intArrayOf(0, 1, 2),
            intArrayOf(3, 4, 5),
            intArrayOf(6, 7, 8),

            intArrayOf(0, 3, 6),
            intArrayOf(1, 4, 7),
            intArrayOf(2, 5, 8),

            intArrayOf(0, 4, 8),
            intArrayOf(6, 4, 2),
        )

        val emptyBoard: Array<Array<GameFeature.Player?>>
            // always create new instance so resets cause state updates
            get() = Array(SIZE) { Array(SIZE) { null } }
    }

}