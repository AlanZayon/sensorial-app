import SwiftUI
import SensorialDomain

enum IntentPreview: Equatable {
    case none
    case deepen
    case explore
    case navigateBack
    case navigateForward

    var label: String {
        switch self {
        case .none: return ""
        case .deepen: return "APROFUNDAR"
        case .explore: return "EXPLORAR"
        case .navigateBack: return "ANTERIOR"
        case .navigateForward: return "PRÓXIMA"
        }
    }

    var tint: Color {
        switch self {
        case .none: return .clear
        case .deepen: return Color(red: 0.24, green: 0.35, blue: 0.50)
        case .explore: return Color(red: 0.55, green: 0.37, blue: 0.24)
        case .navigateBack: return Color(red: 0.36, green: 0.42, blue: 0.45)
        case .navigateForward: return Color(red: 0.06, green: 0.43, blue: 0.42)
        }
    }

    static func from(intent: UserIntent) -> IntentPreview {
        switch intent {
        case .navigateBack: return .navigateBack
        case .navigateForward: return .navigateForward
        case let .discover(mode):
            switch mode {
            case .deepen: return .deepen
            case .explore: return .explore
            case .normal: return .navigateForward
            }
        case .invalid: return .none
        }
    }
}
