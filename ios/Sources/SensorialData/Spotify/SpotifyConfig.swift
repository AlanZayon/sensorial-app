import Foundation

public struct SpotifyConfig: Sendable {
    public let clientId: String
    public let redirectURI: URL
    public let scopes: [String]

    public init(
        clientId: String,
        redirectURI: URL = URL(string: "com.sensorial.app://callback")!,
        scopes: [String] = ["app-remote-control", "user-read-email", "user-read-private"]
    ) {
        self.clientId = clientId
        self.redirectURI = redirectURI
        self.scopes = scopes
    }

    public var isConfigured: Bool { !clientId.isEmpty }

    /// Bump when scopes change so stored tokens are invalidated and user re-consents.
    public static let scopeVersion = 2

    public static let authorizeURL = URL(string: "https://accounts.spotify.com/authorize")!
    public static let tokenURL = URL(string: "https://accounts.spotify.com/api/token")!
    public static let apiBase = URL(string: "https://api.spotify.com/v1")!
}

public enum SpotifySecrets {
    /// Reads `SPOTIFY_CLIENT_ID` from Info.plist or process environment.
    public static func clientId() -> String {
        if let plist = Bundle.main.object(forInfoDictionaryKey: "SPOTIFY_CLIENT_ID") as? String,
           !plist.isEmpty {
            return plist
        }
        return ProcessInfo.processInfo.environment["SPOTIFY_CLIENT_ID"] ?? ""
    }

    public static func useMock() -> Bool {
        if let flag = Bundle.main.object(forInfoDictionaryKey: "SPOTIFY_USE_MOCK") as? Bool {
            return flag
        }
        if let env = ProcessInfo.processInfo.environment["SPOTIFY_USE_MOCK"] {
            return env.lowercased() == "true" || env == "1"
        }
        return clientId().isEmpty
    }
}
