package com.claudetv.hub.data.plex

import com.claudetv.hub.data.VideoFormat

enum class PlexType { MOVIE, SHOW, SEASON, EPISODE, OTHER;
    companion object {
        fun from(s: String?) = when (s) {
            "movie" -> MOVIE
            "show" -> SHOW
            "season" -> SEASON
            "episode" -> EPISODE
            else -> OTHER
        }
    }
}

data class PlexSection(val key: String, val title: String, val type: PlexType)

data class PlexPart(val key: String, val container: String?)

data class PlexMedia(
    val videoResolution: String?,
    val videoCodec: String?,
    val audioCodec: String?,
    val audioChannels: Int?,
    val parts: List<PlexPart>,
)

/** A movie, show, season or episode from your Plex server. */
data class PlexItem(
    val ratingKey: String,
    val type: PlexType,
    val title: String,
    val summary: String = "",
    val year: Int? = null,
    val thumb: String? = null,
    val art: String? = null,
    val logo: String? = null,
    /** For episodes: the show; for seasons: the show. */
    val showTitle: String? = null,
    val showKey: String? = null,
    /** For episodes: the season's ratingKey. */
    val seasonKey: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val showThumb: String? = null,
    val showArt: String? = null,
    val durationMs: Long? = null,
    val viewOffsetMs: Long? = null,
    val viewCount: Int = 0,
    val leafCount: Int? = null,
    val viewedLeafCount: Int? = null,
    val contentRating: String? = null,
    val rating: Double? = null,
    val genres: List<String> = emptyList(),
    val cast: List<String> = emptyList(),
    val directors: List<String> = emptyList(),
    val studio: String? = null,
    val media: List<PlexMedia> = emptyList(),
    /** Only filled from the full metadata call, where Plex includes stream details. */
    val formats: Set<VideoFormat> = emptySet(),
    val audioLabel: String? = null,
    val videoLabel: String? = null,
) {
    val key: String get() = "plex:$ratingKey"

    val progress: Float?
        get() {
            val d = durationMs ?: return null
            val o = viewOffsetMs ?: return null
            return if (d > 0 && o > 0) (o.toFloat() / d).coerceIn(0f, 1f) else null
        }

    val isWatched: Boolean
        get() = when (type) {
            PlexType.SHOW, PlexType.SEASON -> leafCount != null && leafCount > 0 && viewedLeafCount == leafCount
            else -> viewCount > 0 && (viewOffsetMs ?: 0L) == 0L
        }

    val unwatchedCount: Int? get() = if (leafCount != null && viewedLeafCount != null) leafCount - viewedLeafCount else null

    /** Best guess from list data (resolution only); details add HDR/DV/Atmos from streams. */
    val quickFormats: Set<VideoFormat>
        get() = if (formats.isNotEmpty()) formats else buildSet {
            if (media.any { it.videoResolution.equals("4k", true) }) add(VideoFormat.UHD_4K)
        }

    val episodeLabel: String?
        get() = if (type == PlexType.EPISODE && seasonNumber != null && episodeNumber != null) "S$seasonNumber E$episodeNumber" else null

    /** Poster-style image: for episodes and seasons, the show's poster. */
    val posterPath: String? get() = if (type == PlexType.EPISODE) showThumb ?: thumb else thumb

    /** Landscape image: episode still, else background art. */
    val landscapePath: String? get() = if (type == PlexType.EPISODE) thumb ?: showArt else art ?: showArt ?: thumb

    val displayTitle: String get() = if (type == PlexType.EPISODE) showTitle ?: title else title
    val displaySubtitle: String?
        get() = when (type) {
            PlexType.EPISODE -> listOfNotNull(episodeLabel, title).joinToString(" · ")
            PlexType.SHOW -> unwatchedCount?.takeIf { it > 0 }?.let { "$it unwatched" } ?: year?.toString()
            else -> year?.toString()
        }

    val remainingLabel: String?
        get() {
            val d = durationMs ?: return null
            val left = (d - (viewOffsetMs ?: 0L)) / 60_000
            return if (left <= 0) null else if (left >= 60) "${left / 60}h ${left % 60}m left" else "${left}m left"
        }
}

data class PlexConnection(val uri: String, val local: Boolean, val relay: Boolean)

data class PlexServer(
    val name: String,
    val clientId: String,
    val accessToken: String,
    val owned: Boolean,
    val connections: List<PlexConnection>,
)

/** A server we successfully reached, with the address that worked. */
data class ConnectedServer(val name: String, val baseUrl: String, val token: String, val clientId: String)

data class PlexPin(val id: Long, val code: String, val expiresAt: Long)
