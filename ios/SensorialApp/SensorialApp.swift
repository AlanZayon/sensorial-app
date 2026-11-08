import SwiftUI
import SensorialData
import SensorialDomain

@main
struct SensorialApp: App {
    @State private var container: SensorialContainer?
    @State private var loadError: String?
    @State private var authorized = false

    var body: some Scene {
        WindowGroup {
            Group {
                if let container {
                    if container.usesSpotify, let auth = container.spotifyAuth, !authorized && !auth.isAuthorized {
                        SpotifyAuthView(auth: auth)
                            .onChange(of: auth.isAuthorized) { _, value in
                                authorized = value
                            }
                    } else {
                        FeedView(session: container.makeSession())
                    }
                } else if let loadError {
                    Text(loadError).foregroundStyle(.red).padding()
                } else {
                    ProgressView("Carregando…")
                        .task {
                            do {
                                let created = try await SensorialContainer.create()
                                container = created
                                authorized = !created.usesSpotify || (created.spotifyAuth?.isAuthorized ?? false)
                            } catch {
                                loadError = error.localizedDescription
                            }
                        }
                }
            }
        }
    }
}
