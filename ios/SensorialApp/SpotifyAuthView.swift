import SwiftUI
import SensorialData

struct SpotifyAuthView: View {
    @ObservedObject var auth: SpotifyAuthRepository
    @State private var busy = false
    @State private var errorMessage: String?

    var body: some View {
        VStack(spacing: 16) {
            Text("Sensorial")
                .font(.largeTitle.bold())
            Text(
                "Conecte sua conta Spotify para descobrir músicas com gestos. " +
                "Reprodução completa via App Remote (Premium) ou preview de 30s."
            )
            .foregroundStyle(.secondary)
            .multilineTextAlignment(.center)

            if busy {
                ProgressView()
            } else {
                Button("Conectar Spotify") {
                    Task {
                        busy = true
                        errorMessage = nil
                        do {
                            try await auth.authorize()
                        } catch {
                            errorMessage = error.localizedDescription
                        }
                        busy = false
                    }
                }
                .buttonStyle(.borderedProminent)
            }

            if let errorMessage {
                Text(errorMessage).foregroundStyle(.red)
            }
        }
        .padding(28)
    }
}
