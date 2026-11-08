import SwiftUI
import SensorialDomain

struct TrackCardView: View {
    let track: Track
    var overlay: AnyView = AnyView(EmptyView())

    var body: some View {
        VStack(spacing: 16) {
            ZStack {
                RoundedRectangle(cornerRadius: 8)
                    .fill(Color.teal.opacity(0.22))
                    .aspectRatio(1, contentMode: .fit)
                Text(track.genres.first?.value.uppercased() ?? "—")
                    .font(.title.bold())
                overlay
            }
            Text(track.title)
                .font(.title2.bold())
                .multilineTextAlignment(.center)
            Text(track.primaryArtist.name)
                .foregroundStyle(.secondary)
            Text(track.album.name)
                .font(.subheadline)
                .foregroundStyle(.tertiary)
        }
        .padding(20)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 12))
    }
}
