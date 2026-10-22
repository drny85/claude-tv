package com.claudetv.hub.data

import com.claudetv.hub.data.StreamingService.*
import com.claudetv.hub.data.VideoFormat.*

/**
 * SAMPLE DATA used when no TMDB key is set. Titles are real shows and films, but the
 * availability, formats and ratings here are illustrative and may not match your region.
 * Artwork is generated in-app rather than downloaded.
 */
object DemoCatalog {
    private val full = setOf(UHD_4K, DOLBY_VISION, DOLBY_ATMOS)
    private val hdr = setOf(UHD_4K, HDR10)

    private var nextId = -1
    private fun t(
        name: String, type: MediaType, year: String, service: StreamingService,
        overview: String, genres: List<Int>, rating: Double, formats: Set<VideoFormat> = full,
    ) = Title(
        id = nextId--, type = type, name = name, overview = overview, year = year, rating = rating,
        genreIds = genres, demoService = service, demoFormats = formats,
    )

    val titles: List<Title> = listOf(
        t("Stranger Things", MediaType.TV, "2016", NETFLIX, "When a young boy vanishes, a small town uncovers a mystery involving secret experiments and terrifying supernatural forces.", listOf(10765, 18), 8.6),
        t("Wednesday", MediaType.TV, "2022", NETFLIX, "Wednesday Addams investigates a murder spree while making new friends and foes at Nevermore Academy.", listOf(35, 9648), 8.4),
        t("The Crown", MediaType.TV, "2016", NETFLIX, "The political rivalries and romance of Queen Elizabeth II's reign, and the events that shaped the second half of the 20th century.", listOf(18), 8.1),
        t("Glass Onion", MediaType.MOVIE, "2022", NETFLIX, "Detective Benoit Blanc travels to Greece to peel back the layers of a mystery involving a tech billionaire and his eclectic crew.", listOf(35, 80, 9648), 7.1),
        t("The Mandalorian", MediaType.TV, "2019", DISNEY_PLUS, "After the fall of the Galactic Empire, a lone gunfighter makes his way through the outer reaches of the lawless galaxy.", listOf(10765, 10759), 8.5),
        t("Andor", MediaType.TV, "2022", DISNEY_PLUS, "The tale of the burgeoning rebellion against the Empire and how people and planets became involved.", listOf(10765, 18), 8.4),
        t("Inside Out 2", MediaType.MOVIE, "2024", DISNEY_PLUS, "Joy, Sadness, Anger, Fear and Disgust make room for some unexpected new emotions in Riley's mind.", listOf(16, 35), 7.6),
        t("The Boys", MediaType.TV, "2019", PRIME_VIDEO, "A group of vigilantes set out to take down corrupt superheroes who abuse their powers.", listOf(10765, 10759), 8.5, setOf(UHD_4K, HDR10_PLUS, DOLBY_ATMOS)),
        t("Reacher", MediaType.TV, "2022", PRIME_VIDEO, "Jack Reacher, a veteran military police investigator, has just recently entered civilian life.", listOf(10759, 80), 8.1, setOf(UHD_4K, HDR10_PLUS, DOLBY_ATMOS)),
        t("Fallout", MediaType.TV, "2024", PRIME_VIDEO, "In a future, post-apocalyptic Los Angeles, citizens must live in underground bunkers to protect themselves from radiation.", listOf(10765, 18), 8.3, setOf(UHD_4K, DOLBY_VISION, DOLBY_ATMOS)),
        t("Severance", MediaType.TV, "2022", APPLE_TV_PLUS, "Mark leads a team of office workers whose memories have been surgically divided between their work and personal lives.", listOf(18, 9648, 10765), 8.4),
        t("Ted Lasso", MediaType.TV, "2020", APPLE_TV_PLUS, "An American football coach is hired to manage a British soccer team despite having no experience.", listOf(35, 18), 8.5),
        t("Slow Horses", MediaType.TV, "2022", APPLE_TV_PLUS, "A dysfunctional team of MI5 agents, banished to Slough House, serve as their own brand of spies.", listOf(80, 18), 8.2),
        t("The Last of Us", MediaType.TV, "2023", MAX, "Twenty years after modern civilization has been destroyed, a hardened survivor is hired to smuggle a 14-year-old girl out of a quarantine zone.", listOf(18, 10765), 8.6),
        t("House of the Dragon", MediaType.TV, "2022", MAX, "The reign of House Targaryen begins with this story set 200 years before the events of Game of Thrones.", listOf(10765, 18), 8.3),
        t("Dune: Part Two", MediaType.MOVIE, "2024", MAX, "Paul Atreides unites with Chani and the Fremen while seeking revenge against the conspirators who destroyed his family.", listOf(878, 12), 8.2),
        t("The Bear", MediaType.TV, "2022", HULU, "A young chef from the fine dining world comes home to Chicago to run his family's sandwich shop.", listOf(18, 35), 8.2, hdr),
        t("Shōgun", MediaType.TV, "2024", HULU, "A shipwrecked English sailor gets caught up in the battle for power in feudal Japan.", listOf(18, 10759), 8.6, hdr),
        t("Only Murders in the Building", MediaType.TV, "2021", HULU, "Three strangers who share an obsession with true crime suddenly find themselves wrapped up in one.", listOf(35, 9648), 8.1, hdr),
        t("Yellowstone", MediaType.TV, "2018", PEACOCK, "The Dutton family controls the largest contiguous ranch in the United States and fights to protect it.", listOf(18, 37), 8.2, setOf(UHD_4K, HDR10)),
        t("Oppenheimer", MediaType.MOVIE, "2023", PEACOCK, "The story of J. Robert Oppenheimer's role in the development of the atomic bomb.", listOf(18, 36), 8.1, setOf(UHD_4K, HDR10, DOLBY_ATMOS)),
        t("Top Gun: Maverick", MediaType.MOVIE, "2022", PARAMOUNT_PLUS, "After more than thirty years of service as a top naval aviator, Pete Mitchell is where he belongs.", listOf(28, 18), 8.2),
        t("Tulsa King", MediaType.TV, "2022", PARAMOUNT_PLUS, "A New York mafia capo is exiled to Tulsa, where he builds a new criminal empire.", listOf(80, 18), 8.3),
        t("Gladiator II", MediaType.MOVIE, "2024", PARAMOUNT_PLUS, "Lucius is forced to enter the Colosseum after his home is conquered by the tyrannical emperors.", listOf(28, 12, 18), 6.8),
    )

    fun find(key: String): Title? = titles.firstOrNull { it.key == key }

    fun offersFor(title: Title): List<Offer> {
        val s = title.demoService ?: return emptyList()
        return listOf(
            Offer(s.primaryTmdbId, s.displayName, null, OfferType.SUBSCRIPTION, s),
            Offer(StreamingService.PRIME_VIDEO.primaryTmdbId, "Prime Video Store", null, OfferType.RENT, StreamingService.PRIME_VIDEO),
        ).distinctBy { it.service to it.type }
    }

    /** Sample Continue Watching entries so the row is visible in demo mode. */
    val sampleContinue: List<Pair<Title, Float>> = listOf(
        titles[13] to 0.62f, titles[10] to 0.35f, titles[0] to 0.81f, titles[7] to 0.18f,
    )
}
