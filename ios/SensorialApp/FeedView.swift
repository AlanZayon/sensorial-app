import Combine
import SwiftUI
import SensorialDomain

@MainActor
final class FeedViewModel: ObservableObject {
    @Published private(set) var state = FeedState()
    private let session: DiscoverySession

    init(session: DiscoverySession) {
        self.session = session
    }

    func start() {
        Task {
            await session.start()
            for await value in await session.stateStream {
                self.state = value
            }
        }
    }

    func previous() { Task { await session.goBack() } }
    func togglePause() { Task { await session.togglePause() } }
    func like() { Task { await session.explicitLike() } }
    func dislike() { Task { await session.explicitDislike() } }

    func onGestureCommitted(_ input: RawGestureInput) {
        Task { await session.interpretAndHandle(input) }
    }
}

struct FeedView: View {
    @StateObject private var viewModel: FeedViewModel
    @State private var showCoach = true

    init(session: DiscoverySession) {
        _viewModel = StateObject(wrappedValue: FeedViewModel(session: session))
    }

    var body: some View {
        ZStack {
            VStack(alignment: .leading, spacing: 12) {
                Text("Sensorial")
                    .font(.largeTitle.bold())
                    .onTapGesture { showCoach = true }
                Text("↑ próxima · ↓ anterior · ← aprofundar · → explorar")
                    .foregroundStyle(.secondary)

                if let label = playbackModeLabel(viewModel.state.playbackMode) {
                    Text(label)
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(.secondary)
                }

                ZStack {
                    switch viewModel.state.status {
                    case .loading where viewModel.state.currentTrack == nil:
                        ProgressView()
                    case .error:
                        Text(viewModel.state.errorMessage ?? "Erro").foregroundStyle(.red)
                    case .noRecommendations where viewModel.state.currentTrack == nil:
                        Text("Sem recomendações disponíveis")
                    default:
                        if let track = viewModel.state.currentTrack {
                            GestureFeedSurface(
                                track: track,
                                enabled: viewModel.state.status == .playing
                                    || viewModel.state.status == .paused
                                    || viewModel.state.status == .changingTrack,
                                onGestureCommitted: viewModel.onGestureCommitted
                            )
                            .id(track.id.value)
                            .transition(.opacity.combined(with: .scale(scale: 0.96)))
                            if viewModel.state.status == .changingTrack {
                                ProgressView()
                                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topTrailing)
                                    .padding(8)
                            }
                        } else {
                            ProgressView()
                        }
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .animation(.easeInOut(duration: 0.25), value: viewModel.state.currentTrack?.id.value)

                if let label = viewModel.state.lastIntentLabel, viewModel.state.status != .changingTrack {
                    Text(label)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(.orange)
                        .frame(maxWidth: .infinity)
                }

                PreferenceActionRow(
                    enabled: viewModel.state.currentTrack != nil
                        && viewModel.state.status != .changingTrack
                        && viewModel.state.status != .loading,
                    liked: viewModel.state.isCurrentTrackLiked,
                    onLike: viewModel.like,
                    onDislike: viewModel.dislike
                )

                HStack(spacing: 12) {
                    Button("← Anterior", action: viewModel.previous)
                        .disabled(!viewModel.state.canGoBack)
                    Spacer()
                    Button(viewModel.state.status == .paused ? "Play" : "Pause", action: viewModel.togglePause)
                        .disabled(viewModel.state.currentTrack == nil)
                }
            }
            .padding(24)

            if showCoach {
                GestureCoachOverlay(onDismiss: { showCoach = false })
            }
        }
        .onAppear { viewModel.start() }
    }
}

struct PreferenceActionRow: View {
    let enabled: Bool
    var liked: Bool = false
    let onLike: () -> Void
    let onDislike: () -> Void

    var body: some View {
        HStack {
            Button("🚫 Não quero", action: onDislike)
                .disabled(!enabled)
            Spacer()
            Button(liked ? "♥ Curtido (toque p/ remover)" : "♡ Gostei", action: onLike)
                .disabled(!enabled)
        }
        .buttonStyle(.bordered)
    }
}

struct GestureCoachOverlay: View {
    let onDismiss: () -> Void

    var body: some View {
        ZStack {
            Color.black.opacity(0.72)
                .ignoresSafeArea()
                .onTapGesture(perform: onDismiss)
            VStack(spacing: 14) {
                Text("Sensorial").font(.largeTitle.bold()).foregroundStyle(.white)
                Text("Deslize para descobrir").foregroundStyle(.white.opacity(0.8))
                coachLine("↑", "Próxima")
                coachLine("↓", "Anterior")
                coachLine("←", "Aprofundar")
                coachLine("→", "Explorar")
                coachLine("♡", "Gostei / desfazer (sem trocar)")
                coachLine("🚫", "Não quero (sem trocar)")
                Text("Toque para fechar")
                    .font(.caption.weight(.medium))
                    .foregroundStyle(.white.opacity(0.7))
                    .padding(.top, 8)
            }
            .padding(28)
        }
    }

    private func coachLine(_ gesture: String, _ meaning: String) -> some View {
        Text("\(gesture)  ·  \(meaning)")
            .foregroundStyle(.white.opacity(0.9))
    }
}

private func playbackModeLabel(_ mode: PlaybackMode) -> String? {
    switch mode {
    case .spotifyRemote: return "Spotify"
    case .preview: return "Preview 30s"
    case .none: return nil
    }
}
