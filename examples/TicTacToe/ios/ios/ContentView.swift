import SwiftUI
import shared

struct ContentView: View {
    @StateObject private var appState = AppStateObservable()
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            if appState.appState?.game != nil {
                GameView()
            } else {
                NewGameView()
            }
        }
        .task {
            await appState.launch()
        }
    }
}

class AppStateObservable: ObservableObject {
    @Published
    private(set) var appState: AppFeature.State?

    @MainActor
    func launch() async {
        for await state in AppFeatureKt.appStore.state {
            appState = state as? AppFeature.State
         }
    }
}

struct ContentView_Previews: PreviewProvider {
	static var previews: some View {
		ContentView()
	}
}
