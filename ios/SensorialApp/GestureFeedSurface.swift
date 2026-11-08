import SwiftUI
import SensorialDomain

struct GestureFeedSurface: View {
    let track: Track
    let enabled: Bool
    let onGestureCommitted: (RawGestureInput) -> Void

    @State private var offset: CGSize = .zero
    @State private var preview: IntentPreview = .none
    @State private var progress: Double = 0
    @State private var startX: CGFloat = 0
    @State private var containerSize: CGSize = CGSize(width: 1, height: 1)

    var body: some View {
        GeometryReader { geo in
            TrackCardView(
                track: track,
                overlay: AnyView(IntentOverlayView(preview: preview, progress: progress))
            )
            .offset(offset)
            .rotationEffect(.degrees(Double(offset.width / max(geo.size.width, 1)) * 12))
            .opacity(1 - min(0.45, (abs(offset.width) + abs(offset.height)) / 900))
            .gesture(enabled ? dragGesture(in: geo.size) : nil)
            .onAppear { containerSize = geo.size }
            .onChange(of: geo.size) { _, newValue in containerSize = newValue }
        }
    }

    private func dragGesture(in size: CGSize) -> some Gesture {
        DragGesture(minimumDistance: 8, coordinateSpace: .local)
            .onChanged { value in
                if startX == 0 {
                    startX = value.startLocation.x
                }
                offset = value.translation
                let result = GestureMapper.preview(
                    translation: value.translation,
                    startX: startX == 0 ? value.startLocation.x : startX,
                    containerWidth: size.width
                )
                preview = result.0
                progress = result.1
            }
            .onEnded { value in
                let input = GestureMapper.toRawGestureInput(
                    translation: value.translation,
                    velocity: value.velocity,
                    startX: startX == 0 ? value.startLocation.x : startX,
                    containerWidth: size.width,
                    containerHeight: size.height
                )
                let intent = GestureInterpreter().interpret(input)
                let shouldCommit: Bool = {
                    switch intent {
                    case .invalid: return false
                    default: return true
                    }
                }()

                if shouldCommit {
                    let exit: CGSize = {
                        if abs(value.translation.width) >= abs(value.translation.height) {
                            return CGSize(
                                width: value.translation.width > 0 ? 480 : -480,
                                height: value.translation.height * 1.4
                            )
                        }
                        return CGSize(
                            width: value.translation.width * 1.4,
                            height: value.translation.height > 0 ? 480 : -480
                        )
                    }()
                    withAnimation(.easeOut(duration: 0.22)) {
                        offset = exit
                    }
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.22) {
                        onGestureCommitted(input)
                        preview = .none
                        progress = 0
                        startX = 0
                        offset = .zero
                    }
                } else {
                    withAnimation(.spring(response: 0.35, dampingFraction: 0.82)) {
                        offset = .zero
                        preview = .none
                        progress = 0
                        startX = 0
                    }
                }
            }
    }
}
