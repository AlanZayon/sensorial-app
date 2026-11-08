import Foundation
import SensorialDomain

public enum MockCatalog {
    public static func tracks() -> [Track] {
        [
            track("t01", "Neon Pulse", "Luna Vox", "Afterglow", "pop", 0.72, 0.80, 0.65, 122),
            track("t02", "City Lights", "Luna Vox", "Afterglow", "pop", 0.68, 0.75, 0.70, 118),
            track("t03", "Soft Static", "Mira Key", "Glass Room", "indie", 0.45, 0.55, 0.60, 104),
            track("t04", "Harbor Fog", "Mira Key", "Glass Room", "indie", 0.40, 0.48, 0.52, 98),
            track("t05", "Voltage", "Circuit Youth", "Gridlock", "electronic", 0.88, 0.90, 0.55, 128),
            track("t06", "Night Bus", "Circuit Youth", "Gridlock", "electronic", 0.82, 0.85, 0.48, 126),
            track("t07", "Redline", "Iron Grove", "Ash & Chrome", "rock", 0.85, 0.50, 0.40, 140),
            track("t08", "Broken Antenna", "Iron Grove", "Ash & Chrome", "rock", 0.78, 0.45, 0.35, 136),
            track("t09", "Dust Choir", "Cedar Saints", "Dry Season", "folk", 0.30, 0.35, 0.58, 92),
            track("t10", "Willow Road", "Cedar Saints", "Dry Season", "folk", 0.28, 0.32, 0.62, 88),
            track("t11", "Blue Smoke", "Nova Quartet", "Late Set", "jazz", 0.35, 0.40, 0.50, 110),
            track("t12", "Midnight Solo", "Nova Quartet", "Late Set", "jazz", 0.32, 0.38, 0.45, 108),
            track("t13", "Forge", "Black Orbit", "Gravity Well", "metal", 0.95, 0.42, 0.25, 160),
            track("t14", "Impact Crater", "Black Orbit", "Gravity Well", "metal", 0.92, 0.40, 0.22, 155),
            track("t15", "Palm Frequency", "Solara", "Coastal AM", "latin", 0.70, 0.88, 0.82, 100),
            track("t16", "Sunset Taxi", "Solara", "Coastal AM", "latin", 0.66, 0.84, 0.78, 98),
            track("t17", "Paper Planes", "Echo Park", "Demo Tapes", "hiphop", 0.60, 0.78, 0.55, 94),
            track("t18", "Sidewalk Cipher", "Echo Park", "Demo Tapes", "hiphop", 0.58, 0.80, 0.50, 96),
            track("t19", "Low Tide", "Aeon Room", "Depths", "ambient", 0.15, 0.20, 0.40, 70),
            track("t20", "Glass Ocean", "Aeon Room", "Depths", "ambient", 0.12, 0.18, 0.42, 68),
            track("t21", "Golden Hour", "Luna Vox", "Singles", "pop", 0.55, 0.70, 0.85, 112),
            track("t22", "Pixel Heart", "Circuit Youth", "Singles", "electronic", 0.75, 0.86, 0.72, 124),
            track("t23", "Cabin Fire", "Cedar Saints", "Singles", "folk", 0.33, 0.30, 0.70, 90),
            track("t24", "Astrolabe", "Aeon Room", "Singles", "ambient", 0.20, 0.22, 0.48, 72),
            track("t25", "Concrete Bloom", "Echo Park", "Singles", "hiphop", 0.62, 0.76, 0.60, 97),
            track("t26", "Signal Loss", "Iron Grove", "Singles", "rock", 0.80, 0.48, 0.38, 138),
            track("t27", "Samba Drift", "Solara", "Singles", "latin", 0.68, 0.90, 0.88, 102),
            track("t28", "Velvet Rain", "Nova Quartet", "Singles", "jazz", 0.38, 0.42, 0.55, 106),
            track("t29", "Overdrive", "Black Orbit", "Singles", "metal", 0.97, 0.44, 0.28, 170),
            track("t30", "Window Seat", "Mira Key", "Singles", "indie", 0.42, 0.50, 0.66, 100),
        ]
    }

    private static func track(
        _ id: String,
        _ title: String,
        _ artistName: String,
        _ albumName: String,
        _ genre: String,
        _ energy: Double,
        _ danceability: Double,
        _ valence: Double,
        _ tempo: Double
    ) -> Track {
        let artistId = ArtistId(artistName.lowercased().replacingOccurrences(of: " ", with: "-"))
        let artwork = URL(string: "https://picsum.photos/seed/\(id)/600")
        return Track(
            id: TrackId(id),
            title: title,
            artists: [Artist(id: artistId, name: artistName)],
            album: Album(
                id: albumName.lowercased().replacingOccurrences(of: " ", with: "-"),
                name: albumName,
                artworkURL: artwork
            ),
            genres: [GenreId(genre)],
            audioFeatures: AudioFeatures(
                energy: energy,
                danceability: danceability,
                valence: valence,
                tempo: tempo
            ),
            artworkURL: artwork
        )
    }
}
