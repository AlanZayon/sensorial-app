import Foundation

public struct RawGestureInput: Sendable {
    public let deltaX: Double
    public let deltaY: Double
    public let velocity: Double
    public let startXNormalized: Double
    public let screenWidth: Double
    public let screenHeight: Double

    public init(
        deltaX: Double,
        deltaY: Double,
        velocity: Double,
        startXNormalized: Double,
        screenWidth: Double = 1,
        screenHeight: Double = 1
    ) {
        precondition((0...1).contains(startXNormalized))
        precondition(screenWidth > 0 && screenHeight > 0)
        self.deltaX = deltaX
        self.deltaY = deltaY
        self.velocity = velocity
        self.startXNormalized = startXNormalized
        self.screenWidth = screenWidth
        self.screenHeight = screenHeight
    }
}

public enum UserIntent: Sendable, Equatable {
    case navigateBack
    case navigateForward
    case discover(DiscoveryMode)
    case invalid
}

public struct GestureInterpreterConfig: Sendable {
    public var minDistance: Double = 140
    public var edgeBackWidthNormalized: Double = 0
    public var edgeForwardWidthNormalized: Double = 0
    public var axisDominanceRatio: Double = 1.35
    public var velocityLowThreshold: Double = 400
    public var velocityHighThreshold: Double = 1200
    public var distanceLowThreshold: Double = 160
    public var distanceHighThreshold: Double = 360

    public init() {}
}

public struct GestureInterpreter: Sendable {
    private let config: GestureInterpreterConfig

    public init(config: GestureInterpreterConfig = GestureInterpreterConfig()) {
        self.config = config
    }

    /// ↑ next · ↓ previous · ← deepen · → explore
    public func interpret(_ input: RawGestureInput) -> UserIntent {
        let distance = hypot(input.deltaX, input.deltaY)
        guard distance >= config.minDistance else { return .invalid }

        let absX = abs(input.deltaX)
        let absY = abs(input.deltaY)

        if absY >= absX * config.axisDominanceRatio {
            return input.deltaY > 0 ? .navigateForward : .navigateBack
        }
        if absX >= absY * config.axisDominanceRatio {
            return input.deltaX < 0 ? .discover(.deepen) : .discover(.explore)
        }
        return .invalid
    }
}
