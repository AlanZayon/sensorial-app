import Foundation
import SwiftData

@Model
final class HistoryMetaModel {
    var id: Int
    var currentIndex: Int
    var maxSize: Int

    init(id: Int = 1, currentIndex: Int, maxSize: Int) {
        self.id = id
        self.currentIndex = currentIndex
        self.maxSize = maxSize
    }
}

@Model
final class HistoryTrackModel {
    @Attribute(.unique) var position: Int
    var trackData: Data

    init(position: Int, trackData: Data) {
        self.position = position
        self.trackData = trackData
    }
}

@Model
final class InteractionModel {
    @Attribute(.unique) var id: String
    var trackId: String
    var interactionType: String
    var verticalIntent: String
    var horizontalIntent: String
    var gestureIntensity: String
    var gestureVelocity: Double
    var listeningDurationMs: Int64
    var timestampEpochMs: Int64

    init(
        id: String,
        trackId: String,
        interactionType: String,
        verticalIntent: String,
        horizontalIntent: String,
        gestureIntensity: String,
        gestureVelocity: Double,
        listeningDurationMs: Int64,
        timestampEpochMs: Int64
    ) {
        self.id = id
        self.trackId = trackId
        self.interactionType = interactionType
        self.verticalIntent = verticalIntent
        self.horizontalIntent = horizontalIntent
        self.gestureIntensity = gestureIntensity
        self.gestureVelocity = gestureVelocity
        self.listeningDurationMs = listeningDurationMs
        self.timestampEpochMs = timestampEpochMs
    }
}

@Model
final class PreferenceModel {
    var id: Int
    var profileData: Data

    init(id: Int = 1, profileData: Data) {
        self.id = id
        self.profileData = profileData
    }
}

@Model
final class SettingsModel {
    var id: Int
    var settingsData: Data

    init(id: Int = 1, settingsData: Data) {
        self.id = id
        self.settingsData = settingsData
    }
}
