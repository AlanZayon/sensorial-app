import SwiftUI

struct IntentOverlayView: View {
    let preview: IntentPreview
    let progress: Double

    var body: some View {
        if preview != .none, progress > 0.01 {
            ZStack {
                RoundedRectangle(cornerRadius: 8)
                    .fill(preview.tint.opacity(0.22 * progress))
                Text(preview.label)
                    .font(.title3.weight(.semibold))
                    .foregroundStyle(preview.tint)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 10)
                    .background(.white.opacity(0.88), in: RoundedRectangle(cornerRadius: 8))
            }
            .opacity(0.85 * progress)
            .allowsHitTesting(false)
        }
    }
}
