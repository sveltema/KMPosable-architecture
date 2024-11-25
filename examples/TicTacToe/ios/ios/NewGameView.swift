//
//  NewGameView.swift
//  ios
//
//  Created by Steven Veltema on 2024/09/27.
//  Copyright © 2024 orgName. All rights reserved.
//

import SwiftUI
import shared

struct NewGameView: View {
    @StateObject private var viewModel = NewGameViewModel()

    var body: some View {
          VStack(alignment: .leading, spacing: 16) {
              TextField("Player 1 Name", text: $viewModel.playerOneName)
                  .textFieldStyle(RoundedBorderTextFieldStyle())
                  .padding(.bottom, 8)


              TextField("Player 2 Name", text: $viewModel.playerTwoName)
                  .textFieldStyle(RoundedBorderTextFieldStyle())
                  .padding(.bottom, 8)

              Button(action: {
                  viewModel.newGameStore.send(action:StartGameFeature.ActionStartGameTapped())
              }) {
                  Text("Start Game")
              }
              .padding(.top, 16)
          }
          .padding(16)
          .task {
              await viewModel.launch()
          }
      }
  }

class NewGameViewModel: ObservableObject {

    let newGameStore = AppFeatureKt.appStore.startGameStore()

    @Published
    var playerOneName: String = "" {
        didSet {
            newGameStore.send(action:StartGameFeature.ActionUpdatePlayerOneName(name: playerOneName))
        }
    }

    @Published
    var playerTwoName: String = "" {
        didSet {
            newGameStore.send(action: StartGameFeature.ActionUpdatePlayerTwoName(name: playerTwoName))
        }
    }

    @MainActor
    func launch() async {
        for await state in newGameStore.state {
            playerOneName = (state as? StartGameFeature.State)?.playerOneName ?? ""
            playerTwoName = (state as? StartGameFeature.State)?.playerTwoName ?? ""
         }
    }
}


#Preview {
    NewGameView()
}
