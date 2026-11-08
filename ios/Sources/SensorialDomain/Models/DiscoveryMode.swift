import Foundation

public enum DiscoveryMode: String, Sendable, Codable {
    case normal
    case deepen
    case explore
}

public enum NavigationAction: String, Sendable, Codable {
    case advance
    case returnBack = "return"
    case none
}

public func timeBucketForHour(_ hour: Int) -> Int {
    min(5, max(0, hour / 4))
}
