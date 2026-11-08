import CoreGraphics
import Foundation
import SensorialDomain

enum GestureMapper {
    /// UIKit/SwiftUI Y grows downward; domain Y grows upward.
    static func toRawGestureInput(
        translation: CGSize,
        velocity: CGSize,
        startX: CGFloat,
        containerWidth: CGFloat,
        containerHeight: CGFloat
    ) -> RawGestureInput {
        let width = max(containerWidth, 1)
        let speed = hypot(velocity.width, velocity.height)
        return RawGestureInput(
            deltaX: Double(translation.width),
            deltaY: Double(-translation.height),
            velocity: Double(speed),
            startXNormalized: Double((startX / width).clamped(to: 0...1)),
            screenWidth: Double(width),
            screenHeight: Double(max(containerHeight, 1))
        )
    }

    static func preview(
        translation: CGSize,
        startX: CGFloat,
        containerWidth: CGFloat,
        interpreter: GestureInterpreter = GestureInterpreter()
    ) -> (IntentPreview, Double) {
        let distance = hypot(translation.width, translation.height)
        let progress = min(1, distance / 80)
        if distance < 28 {
            return (.none, progress)
        }
        let input = toRawGestureInput(
            translation: translation,
            velocity: CGSize(width: 600, height: 600),
            startX: startX,
            containerWidth: containerWidth,
            containerHeight: containerWidth
        )
        let intent = interpreter.interpret(input)
        let preview = IntentPreview.from(intent: intent)
        if preview == .none {
            return (directionalHint(translation), progress)
        }
        return (preview, max(progress, 0.55))
    }

    private static func directionalHint(_ translation: CGSize) -> IntentPreview {
        let absX = abs(translation.width)
        let absY = abs(translation.height)
        // SwiftUI Y: negative = up → próxima; positive = down → anterior
        if absY >= absX && translation.height < 0 { return .navigateForward }
        if absY >= absX && translation.height > 0 { return .navigateBack }
        if absX > absY && translation.width < 0 { return .deepen }
        if absX > absY && translation.width > 0 { return .explore }
        return .none
    }
}

private extension Comparable {
    func clamped(to range: ClosedRange<Self>) -> Self {
        min(max(self, range.lowerBound), range.upperBound)
    }
}
