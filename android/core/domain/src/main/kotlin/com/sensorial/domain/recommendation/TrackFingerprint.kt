package com.sensorial.domain.recommendation

import com.sensorial.domain.model.Track

/**
 * Identity of a musical work independent of Spotify track id / release version.
 * Remasters, radio edits, live takes, and deluxe cuts share the same fingerprint
 * when title + primary artist align after normalization.
 */
object TrackFingerprint {
    private val noisePatterns: List<Regex> = listOf(
        Regex("""\s*[\(\[\{].*?(remaster(?:ed)?|remasterização|live|ao vivo|radio\s*edit|single\s*version|album\s*version|deluxe|explicit|clean|acoustic|acoustic\s*version|instrumental|remix|mix|edit|bonus\s*track|extended|mono|stereo|demo|session|unplugged|orchestra(?:l)?|cover|karaoke|sped\s*up|slowed|reverb|nightcore).*?[\)\]\}]\s*""", RegexOption.IGNORE_CASE),
        Regex("""\s*[-–—]\s*(remaster(?:ed)?.*|live.*|ao vivo.*|radio\s*edit.*|remix.*|acoustic.*|instrumental.*|deluxe.*)$""", RegexOption.IGNORE_CASE),
        Regex("""\s+(remaster(?:ed)?|live|ao vivo|radio\s*edit|remix|acoustic|instrumental)\b.*$""", RegexOption.IGNORE_CASE),
        Regex("""\s*feat\.?\s+.*$""", RegexOption.IGNORE_CASE),
        Regex("""\s*ft\.?\s+.*$""", RegexOption.IGNORE_CASE),
        Regex("""\s*featuring\s+.*$""", RegexOption.IGNORE_CASE),
        Regex("""[^\p{L}\p{N}\s]""", RegexOption.IGNORE_CASE),
    )

    fun normalizeTitle(title: String): String {
        var result = title.lowercase().trim()
        noisePatterns.forEach { pattern ->
            result = result.replace(pattern, " ")
        }
        return result
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    fun normalizeArtistName(name: String): String =
        name.lowercase()
            .replace(Regex("""[^\p{L}\p{N}\s]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

    /** Stable key: normalized title + primary artist id (fallback: name). */
    fun of(track: Track): String {
        val title = normalizeTitle(track.title)
        val artistKey = track.primaryArtist.id.value.ifBlank {
            normalizeArtistName(track.primaryArtist.name)
        }
        return "$title|$artistKey"
    }

    fun isSameWork(a: Track, b: Track): Boolean = of(a) == of(b)
}
