//
//  GameView.swift
//  ios
//
//  Created by Steven Veltema on 2024/09/27.
//  Copyright © 2024 orgName. All rights reserved.
//

import SwiftUI
import shared

struct GameView: View {
    @StateObject private var gviewModel: GameViewModel = GameViewModel()

    var body: some View {
        VStack(alignment: .center, spacing: 16) {
            Text("Current Player: \(gviewModel.gameState?.currentPlayer.name ?? "") (\(gviewModel.gameState?.currentPlayer.symbol.symbol ?? ""))")

            ForEach(0..<3, id: \.self) { row in
                HStack(spacing: 8) {
                    let rrow = Int32(row)
                    ForEach(0..<3, id: \.self) { col in
                        let ccol = Int32(col)
                        let player = gviewModel.gameState?.board.getCell(row: rrow, column: ccol)
                        let symbol = player?.symbol.symbol ?? ""
                        BoxView(symbol:symbol) {
                            gviewModel.gameStore.send(action: GameFeature.ActionCellTapped(row: rrow, column: ccol))
                       }
                    }
                }
            }

            Text(gviewModel.gameState?.winnerText ?? "")

            Button(action: {
                gviewModel.gameStore.send(action: GameFeature.ActionPlayAgainTapped())
            }) {
                Text("New Game")
            }

            Button(action: {
                gviewModel.gameStore.send(action: GameFeature.ActionEndTapped())
            }) {
                Text("End")
            }
        }
        .padding(16)
        .task {
            await gviewModel.launch()
        }
    }
}

struct BoxView: View {
    let symbol: String
    let action: () -> Void

    var body: some View {
        Text(symbol)
            .frame(width: 100, height: 100)
            .background(Color.gray)
            .foregroundColor(.black)
            .font(.largeTitle)
            .onTapGesture {
                action()
            }
    }
}

class GameViewModel: ObservableObject {

    let gameStore = AppFeatureKt.appStore.gameStore()

    @Published
    private(set) var gameState: GameFeature.State?

    @Published
    private(set) var winnerText: String = ""


    @MainActor
    func launch() async {
        for await state in gameStore.state {
            gameState = state as? GameFeature.State
        }
    }
}
