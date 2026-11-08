import Testing
@testable import SensorialDomain

@Suite("GestureInterpreter")
struct GestureInterpreterTests {
    private let interpreter = GestureInterpreter()

    @Test("swipe up is navigate forward")
    func verticalUp() {
        let intent = interpreter.interpret(
            RawGestureInput(deltaX: 10, deltaY: 200, velocity: 500, startXNormalized: 0.5)
        )
        #expect(intent == .navigateForward)
    }

    @Test("swipe down is navigate back")
    func verticalDown() {
        let intent = interpreter.interpret(
            RawGestureInput(deltaX: 10, deltaY: -200, velocity: 500, startXNormalized: 0.5)
        )
        #expect(intent == .navigateBack)
    }

    @Test("short gesture invalid")
    func shortGesture() {
        let intent = interpreter.interpret(
            RawGestureInput(deltaX: 10, deltaY: 10, velocity: 100, startXNormalized: 0.5)
        )
        #expect(intent == .invalid)
    }

    @Test("swipe left is deepen")
    func deepen() {
        let intent = interpreter.interpret(
            RawGestureInput(deltaX: -250, deltaY: 5, velocity: 800, startXNormalized: 0.6)
        )
        #expect(intent == .discover(.deepen))
    }

    @Test("swipe right is explore")
    func explore() {
        let intent = interpreter.interpret(
            RawGestureInput(deltaX: 250, deltaY: 5, velocity: 800, startXNormalized: 0.6)
        )
        #expect(intent == .discover(.explore))
    }

    @Test("edge swipe alone is not navigation when edges disabled")
    func edgeDisabled() {
        let intent = interpreter.interpret(
            RawGestureInput(deltaX: 180, deltaY: 20, velocity: 900, startXNormalized: 0.05)
        )
        #expect(intent == .invalid || intent == .discover(.explore))
    }
}
