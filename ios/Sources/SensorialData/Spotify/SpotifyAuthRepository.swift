import AuthenticationServices
import Combine
import Foundation
#if canImport(UIKit)
import UIKit
#endif

@MainActor
public final class SpotifyAuthRepository: NSObject, ObservableObject {
    public let config: SpotifyConfig
    private let tokenStore: SpotifyTokenStore
    private var codeVerifier: String?
    private var session: ASWebAuthenticationSession?

    @Published public private(set) var isAuthorized: Bool

    public init(config: SpotifyConfig, tokenStore: SpotifyTokenStore = SpotifyTokenStore()) {
        self.config = config
        self.tokenStore = tokenStore
        self.isAuthorized = tokenStore.read() != nil
    }

    public func signOut() {
        tokenStore.clear()
        isAuthorized = false
    }

    public func authorize() async throws {
        let verifier = Pkce.makeVerifier()
        codeVerifier = verifier
        let challenge = Pkce.challenge(for: verifier)
        var components = URLComponents(url: SpotifyConfig.authorizeURL, resolvingAgainstBaseURL: false)!
        components.queryItems = [
            URLQueryItem(name: "client_id", value: config.clientId),
            URLQueryItem(name: "response_type", value: "code"),
            URLQueryItem(name: "redirect_uri", value: config.redirectURI.absoluteString),
            URLQueryItem(name: "scope", value: config.scopes.joined(separator: " ")),
            URLQueryItem(name: "code_challenge_method", value: "S256"),
            URLQueryItem(name: "code_challenge", value: challenge),
            // Force consent UI so newly added scopes (e.g. user-read-private) are granted.
            URLQueryItem(name: "show_dialog", value: "true"),
        ]
        let authURL = components.url!
        let callbackScheme = config.redirectURI.scheme ?? "com.sensorial.app"

        let callbackURL: URL = try await withCheckedThrowingContinuation { continuation in
            let session = ASWebAuthenticationSession(
                url: authURL,
                callbackURLScheme: callbackScheme
            ) { url, error in
                if let error {
                    continuation.resume(throwing: error)
                } else if let url {
                    continuation.resume(returning: url)
                } else {
                    continuation.resume(throwing: URLError(.badServerResponse))
                }
            }
            session.presentationContextProvider = self
            session.prefersEphemeralWebBrowserSession = false
            self.session = session
            if !session.start() {
                continuation.resume(throwing: URLError(.cannotStartLoad))
            }
        }

        guard
            let items = URLComponents(url: callbackURL, resolvingAgainstBaseURL: false)?.queryItems,
            let code = items.first(where: { $0.name == "code" })?.value
        else {
            throw URLError(.userAuthenticationRequired)
        }
        let tokens = try await exchangeCode(code, verifier: verifier)
        tokenStore.write(tokens)
        isAuthorized = true
    }

    public func validAccessToken() async throws -> String {
        guard let current = tokenStore.read() else {
            throw URLError(.userAuthenticationRequired)
        }
        if !current.isExpired() { return current.accessToken }
        guard let refresh = current.refreshToken else {
            throw URLError(.userAuthenticationRequired)
        }
        let refreshed = try await refreshAccessToken(refresh)
        tokenStore.write(refreshed)
        return refreshed.accessToken
    }

    private func exchangeCode(_ code: String, verifier: String) async throws -> SpotifyTokens {
        var request = URLRequest(url: SpotifyConfig.tokenURL)
        request.httpMethod = "POST"
        request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        let body = [
            "grant_type": "authorization_code",
            "code": code,
            "redirect_uri": config.redirectURI.absoluteString,
            "client_id": config.clientId,
            "code_verifier": verifier,
        ]
        request.httpBody = body
            .map { "\($0.key)=\($0.value.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? $0.value)" }
            .joined(separator: "&")
            .data(using: .utf8)
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
            throw URLError(.badServerResponse)
        }
        return try parseTokenResponse(data, fallbackRefresh: nil)
    }

    private func refreshAccessToken(_ refreshToken: String) async throws -> SpotifyTokens {
        var request = URLRequest(url: SpotifyConfig.tokenURL)
        request.httpMethod = "POST"
        request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        let body = [
            "grant_type": "refresh_token",
            "refresh_token": refreshToken,
            "client_id": config.clientId,
        ]
        request.httpBody = body
            .map { "\($0.key)=\($0.value.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? $0.value)" }
            .joined(separator: "&")
            .data(using: .utf8)
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
            throw URLError(.badServerResponse)
        }
        return try parseTokenResponse(data, fallbackRefresh: refreshToken)
    }

    private func parseTokenResponse(_ data: Data, fallbackRefresh: String?) throws -> SpotifyTokens {
        guard
            let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
            let access = json["access_token"] as? String
        else {
            throw URLError(.cannotParseResponse)
        }
        let expiresIn = (json["expires_in"] as? Int) ?? 3600
        let refresh = (json["refresh_token"] as? String) ?? fallbackRefresh
        return SpotifyTokens(
            accessToken: access,
            refreshToken: refresh,
            expiresAtEpochMs: Int64(Date().timeIntervalSince1970 * 1000) + Int64(expiresIn) * 1000
        )
    }
}

extension SpotifyAuthRepository: ASWebAuthenticationPresentationContextProviding {
    public func presentationAnchor(for session: ASWebAuthenticationSession) -> ASPresentationAnchor {
        #if os(iOS)
        return UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows)
            .first { $0.isKeyWindow } ?? ASPresentationAnchor()
        #else
        return ASPresentationAnchor()
        #endif
    }
}
