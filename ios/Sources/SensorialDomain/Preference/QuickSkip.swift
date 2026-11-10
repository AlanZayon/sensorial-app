import Foundation

public enum QuickSkip {
    public static func progressThreshold(
        trackDurationMs: Int64,
        baseProgress: Double = 0.12,
        minListeningMs: Int64 = 8_000,
        maxThreshold: Double = 0.4
    ) -> Double {
        guard trackDurationMs > 0 else { return baseProgress }
        let fromAbsolute = Double(minListeningMs) / Double(trackDurationMs)
        return max(baseProgress, min(fromAbsolute, maxThreshold))
    }

    public static func isQuickSkip(
        progress: Double,
        trackDurationMs: Int64,
        baseProgress: Double = 0.12,
        minListeningMs: Int64 = 8_000
    ) -> Bool {
        progress <= progressThreshold(
            trackDurationMs: trackDurationMs,
            baseProgress: baseProgress,
            minListeningMs: minListeningMs
        )
    }

    public static func isQuickSkip(
        interaction: TrackInteraction,
        trackDurationMs: Int64,
        config: PreferenceUpdateConfig = PreferenceUpdateConfig()
    ) -> Bool {
        guard interaction.interactionType != .navigationReturn else { return false }
        guard interaction.interactionType == .navigationAdvance ||
                interaction.interactionType == .discovery else { return false }
        let duration = (interaction.trackDurationMs ?? 0) > 0
            ? (interaction.trackDurationMs ?? trackDurationMs)
            : trackDurationMs
        return isQuickSkip(
            progress: interaction.effectiveListeningProgress,
            trackDurationMs: duration,
            baseProgress: config.quickSkipProgress,
            minListeningMs: config.quickSkipMinMs
        )
    }
}
