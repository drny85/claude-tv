package com.claudetv.hub.data

enum class MediaType(val path: String, val label: String) {
    MOVIE("movie", "Movie"),
    TV("tv", "Series");

    companion object {
        fun from(path: String?): MediaType? = entries.firstOrNull { it.path == path }
    }
}

/** A movie or show as it appears on cards and in rows. */
data class Title(
    val id: Int,
    val type: MediaType,
    val name: String,
    val overview: String = "",
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val year: String? = null,
    val rating: Double? = null,
    val genreIds: List<Int> = emptyList(),
    /** Only set for demo titles: the service the sample data says it is on. */
    val demoService: StreamingService? = null,
    /** Only set for demo titles: sample formats. */
    val demoFormats: Set<VideoFormat> = emptySet(),
) {
    val key: String get() = "${type.path}:$id"
    val isDemo: Boolean get() = id < 0
}

enum class OfferType(val label: String, val rank: Int) {
    SUBSCRIPTION("Included", 0),
    FREE("Free", 1),
    ADS("Free with ads", 2),
    RENT("Rent", 3),
    BUY("Buy", 4),
}

/** One way to watch a title, from TMDB's watch-provider data (powered by JustWatch). */
data class Offer(
    val providerId: Int,
    val providerName: String,
    val logoPath: String?,
    val type: OfferType,
    val service: StreamingService?,
)

enum class VideoFormat(val label: String) {
    UHD_4K("4K"),
    DOLBY_VISION("Dolby Vision"),
    HDR10("HDR10"),
    HDR10_PLUS("HDR10+"),
    DOLBY_ATMOS("Dolby Atmos"),
}

data class TitleDetails(
    val title: Title,
    val tagline: String? = null,
    val genres: List<String> = emptyList(),
    val runtimeMinutes: Int? = null,
    val seasons: Int? = null,
    val certification: String? = null,
    val cast: List<String> = emptyList(),
    val trailerYoutubeKey: String? = null,
    val offers: List<Offer> = emptyList(),
    val similar: List<Title> = emptyList(),
    val logoPath: String? = null,
)

/** A row on the home screen. */
data class ContentRow(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val items: List<Title>,
    val style: RowStyle = RowStyle.LANDSCAPE,
    val service: StreamingService? = null,
)

enum class RowStyle { LANDSCAPE, POSTER, CONTINUE }

/** Something the person opened from the hub; drives Continue Watching. */
data class RecentItem(
    val title: Title,
    val service: StreamingService?,
    val openedAt: Long,
)

data class HeroItem(
    val title: Title,
    val logoPath: String?,
    val service: StreamingService?,
)
