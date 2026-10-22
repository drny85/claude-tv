package com.claudetv.hub.data

/**
 * The services the hub knows how to show and hand off to.
 *
 * [tmdbIds] are TMDB watch-provider ids (the first is used for discover queries).
 * [packages] are Android TV package names, tried in order.
 * [searchUrl] is a web search link we try to open inside the installed app; many TV apps
 * accept their own web links, but there is no public per-title deep link without the
 * service's internal content id, so this is best-effort.
 * [formats] is what the service supports at its top tier. Public APIs do not say which
 * formats an individual title has, so the UI always labels this as "up to".
 */
enum class StreamingService(
    val displayName: String,
    val tmdbIds: List<Int>,
    val brandColor: Long,
    val packages: List<String>,
    val searchUrl: String?,
    val formats: Set<VideoFormat>,
    val isStore: Boolean = false,
) {
    NETFLIX(
        "Netflix", listOf(8, 1796), 0xFFE50914,
        listOf("com.netflix.ninja", "com.netflix.mediaclient"),
        "https://www.netflix.com/search?q=%s",
        setOf(VideoFormat.UHD_4K, VideoFormat.DOLBY_VISION, VideoFormat.HDR10, VideoFormat.DOLBY_ATMOS),
    ),
    DISNEY_PLUS(
        "Disney+", listOf(337), 0xFF1A3DD6,
        listOf("com.disney.disneyplus"),
        "https://www.disneyplus.com/search?q=%s",
        setOf(VideoFormat.UHD_4K, VideoFormat.DOLBY_VISION, VideoFormat.HDR10, VideoFormat.DOLBY_ATMOS),
    ),
    PRIME_VIDEO(
        "Prime Video", listOf(9, 119, 10), 0xFF1FA5E0,
        listOf("com.amazon.amazonvideo.livingroom"),
        "https://www.primevideo.com/search/?phrase=%s",
        setOf(VideoFormat.UHD_4K, VideoFormat.DOLBY_VISION, VideoFormat.HDR10_PLUS, VideoFormat.DOLBY_ATMOS),
    ),
    APPLE_TV_PLUS(
        "Apple TV+", listOf(350, 2), 0xFFF5F5F7,
        listOf("com.apple.atve.androidtv.appletv"),
        null,
        setOf(VideoFormat.UHD_4K, VideoFormat.DOLBY_VISION, VideoFormat.HDR10, VideoFormat.DOLBY_ATMOS),
    ),
    MAX(
        "Max", listOf(1899, 384), 0xFF2F5BFF,
        listOf("com.wbd.stream", "com.hbo.hbonow"),
        "https://play.max.com/search?q=%s",
        setOf(VideoFormat.UHD_4K, VideoFormat.DOLBY_VISION, VideoFormat.HDR10, VideoFormat.DOLBY_ATMOS),
    ),
    HULU(
        "Hulu", listOf(15), 0xFF1CE783,
        listOf("com.hulu.livingroomplus"),
        "https://www.hulu.com/search?q=%s",
        setOf(VideoFormat.UHD_4K, VideoFormat.HDR10, VideoFormat.DOLBY_VISION),
    ),
    PARAMOUNT_PLUS(
        "Paramount+", listOf(531, 2303), 0xFF2D6BFF,
        listOf("com.cbs.ott"),
        "https://www.paramountplus.com/search/?q=%s",
        setOf(VideoFormat.UHD_4K, VideoFormat.DOLBY_VISION, VideoFormat.HDR10, VideoFormat.DOLBY_ATMOS),
    ),
    PEACOCK(
        "Peacock", listOf(386, 387), 0xFFF2C94C,
        listOf("com.peacocktv.peacockandroid"),
        null,
        setOf(VideoFormat.UHD_4K, VideoFormat.HDR10, VideoFormat.DOLBY_ATMOS),
    ),
    GOOGLE_TV(
        "Google TV", listOf(3), 0xFF8AB4F8,
        listOf("com.google.android.videos"),
        null,
        setOf(VideoFormat.UHD_4K, VideoFormat.HDR10, VideoFormat.DOLBY_VISION),
        isStore = true,
    );

    val primaryTmdbId: Int get() = tmdbIds.first()

    companion object {
        /** Services the person can toggle as subscriptions. */
        val subscribable: List<StreamingService> = entries.filter { !it.isStore }

        fun forTmdbId(id: Int): StreamingService? = entries.firstOrNull { id in it.tmdbIds }
    }
}
