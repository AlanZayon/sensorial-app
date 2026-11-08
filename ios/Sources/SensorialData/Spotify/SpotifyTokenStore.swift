import Foundation
import Security

public struct SpotifyTokens: Sendable {
    public var accessToken: String
    public var refreshToken: String?
    public var expiresAtEpochMs: Int64

    public func isExpired(now: Int64 = Int64(Date().timeIntervalSince1970 * 1000), skewMs: Int64 = 60_000) -> Bool {
        now >= (expiresAtEpochMs - skewMs)
    }
}

public final class SpotifyTokenStore: @unchecked Sendable {
    private let service = "com.sensorial.app.spotify"
    private let defaultsKey = "spotify_scope_version"

    public init() {}

    public func read() -> SpotifyTokens? {
        migrateScopesIfNeeded()
        guard let data = keychainData(account: "tokens"),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let access = json["access"] as? String
        else { return nil }
        let expires: Int64
        if let value = json["expires"] as? Int64 {
            expires = value
        } else if let value = json["expires"] as? Int {
            expires = Int64(value)
        } else {
            return nil
        }
        return SpotifyTokens(
            accessToken: access,
            refreshToken: json["refresh"] as? String,
            expiresAtEpochMs: expires
        )
    }

    public func write(_ tokens: SpotifyTokens) {
        let payload: [String: Any?] = [
            "access": tokens.accessToken,
            "refresh": tokens.refreshToken,
            "expires": tokens.expiresAtEpochMs,
        ]
        guard let data = try? JSONSerialization.data(withJSONObject: payload.compactMapValues { $0 }) else { return }
        setKeychainData(data, account: "tokens")
        UserDefaults.standard.set(SpotifyConfig.scopeVersion, forKey: defaultsKey)
    }

    public func clear() {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: "tokens",
        ]
        SecItemDelete(query as CFDictionary)
        UserDefaults.standard.removeObject(forKey: defaultsKey)
    }

    private func migrateScopesIfNeeded() {
        let stored = UserDefaults.standard.integer(forKey: defaultsKey)
        if stored < SpotifyConfig.scopeVersion, keychainData(account: "tokens") != nil {
            clear()
        }
    }

    private func keychainData(account: String) -> Data? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
        ]
        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)
        guard status == errSecSuccess else { return nil }
        return item as? Data
    }

    private func setKeychainData(_ data: Data, account: String) {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
        SecItemDelete(query as CFDictionary)
        var attributes = query
        attributes[kSecValueData as String] = data
        attributes[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        SecItemAdd(attributes as CFDictionary, nil)
    }
}
