import Foundation

/// Identity of a musical work independent of Spotify track id / release version.
public enum TrackFingerprint {
    private static let noisePatterns: [NSRegularExpression] = [
        try! NSRegularExpression(
            pattern: #"\s*[\(\[\{].*?(remaster(?:ed)?|remasterização|live|ao vivo|radio\s*edit|single\s*version|album\s*version|deluxe|explicit|clean|acoustic|acoustic\s*version|instrumental|remix|mix|edit|bonus\s*track|extended|mono|stereo|demo|session|unplugged|orchestra(?:l)?|cover|karaoke|sped\s*up|slowed|reverb|nightcore).*?[\)\]\}]\s*"#,
            options: [.caseInsensitive]
        ),
        try! NSRegularExpression(
            pattern: #"\s*[-–—]\s*(remaster(?:ed)?.*|live.*|ao vivo.*|radio\s*edit.*|remix.*|acoustic.*|instrumental.*|deluxe.*)$"#,
            options: [.caseInsensitive]
        ),
        try! NSRegularExpression(
            pattern: #"\s+(remaster(?:ed)?|live|ao vivo|radio\s*edit|remix|acoustic|instrumental)\b.*$"#,
            options: [.caseInsensitive]
        ),
        try! NSRegularExpression(pattern: #"\s*feat\.?\s+.*$"#, options: [.caseInsensitive]),
        try! NSRegularExpression(pattern: #"\s*ft\.?\s+.*$"#, options: [.caseInsensitive]),
        try! NSRegularExpression(pattern: #"\s*featuring\s+.*$"#, options: [.caseInsensitive]),
        try! NSRegularExpression(pattern: #"[^\p{L}\p{N}\s]"#, options: []),
    ]

    public static func normalizeTitle(_ title: String) -> String {
        var result = title.lowercased().trimmingCharacters(in: .whitespacesAndNewlines)
        for pattern in noisePatterns {
            let range = NSRange(result.startIndex..<result.endIndex, in: result)
            result = pattern.stringByReplacingMatches(in: result, options: [], range: range, withTemplate: " ")
        }
        return result
            .replacingOccurrences(of: #"\s+"#, with: " ", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    public static func normalizeArtistName(_ name: String) -> String {
        name.lowercased()
            .replacingOccurrences(of: #"[^\p{L}\p{N}\s]"#, with: " ", options: .regularExpression)
            .replacingOccurrences(of: #"\s+"#, with: " ", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    public static func of(_ track: Track) -> String {
        let title = normalizeTitle(track.title)
        let artistKey = track.primaryArtist.id.value.isEmpty
            ? normalizeArtistName(track.primaryArtist.name)
            : track.primaryArtist.id.value
        return "\(title)|\(artistKey)"
    }

    public static func isSameWork(_ a: Track, _ b: Track) -> Bool {
        of(a) == of(b)
    }
}
