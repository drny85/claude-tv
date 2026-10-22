# Claude TV

A native Google TV / Android TV app that puts all your streaming services in one Apple TV–style home screen. Built in Kotlin with Jetpack Compose for TV, tuned for a TCL 98" 4K Google TV and the D-pad remote.

## What's in the app

| Screen | What it does |
| --- | --- |
| **Home** | Full-bleed hero carousel (top title from each of your services, with title logos), Continue Watching, Top Picks for You, Up Next from your Watchlist, Trending, Popular on each service, Critically Acclaimed. The backdrop follows whatever card you focus. |
| **Search** | One search across every service with a remote-friendly keyboard. Titles included in your subscriptions are listed first; filter by Included, Movies or Series. |
| **Details** | Logo, metadata, rating, cast, trailer, every way to watch (included / free / rent / buy), picture and sound formats, More Like This. |
| **Watchlist** | One watchlist for everything, stored on the TV. |
| **Services** | Toggle which services you pay for; see which apps are installed; open any of them. |
| **Settings** | TMDB key, region, your TV's detected 4K / HDR / Dolby Vision / Atmos support, and the "real vs simulated" list below. |

Supported services: Netflix, Disney+, Prime Video, Apple TV+, Max, Hulu, Paramount+, Peacock (plus Google TV for rentals).

## What's real and what's simulated

| Feature | Status | Notes |
| --- | --- | --- |
| Catalog, artwork, trending, search | **Real** (with TMDB key) | Live from [TMDB](https://www.themoviedb.org/). Without a key the app runs on a clearly labeled sample catalog. |
| Which service has each title | **Real** | TMDB watch-provider data (powered by JustWatch), per region: included, free, rent, buy. |
| Prioritizing your subscriptions | **Real** | Hero, Top Picks, service rows and search ranking all use the services you toggle on. |
| Open in the official app | **Real, best-effort** | Hands off to the installed app. Where the app accepts its own web links it opens on a search for the title, otherwise on the app's home. Jumping straight to an exact title or episode needs each service's private content ids, which no public API offers (JustWatch's commercial partner API does). |
| Your TV's 4K / HDR10 / Dolby Vision / Atmos | **Real** | Read from Android's display and audio APIs. |
| Per-title 4K / Dolby Vision / Atmos | **Approximated** | Shown as "up to" the service's top tier. No public API lists formats per title. |
| Watchlist | **Real, local** | Stored on the TV. Services' own lists (e.g. Netflix My List) can't be read by other apps. |
| Recommendations | **Real, local** | Built from your watchlist and recently opened titles, filtered to your services. |
| Continue Watching | **Limited** | Services don't share watch progress with third-party apps, so this row shows titles you opened from Claude TV. Demo mode shows sample progress bars. |

## Install on your TCL Google TV

Every push to `main` builds a signed APK and publishes it as the [`latest` release](../../releases/latest).

**Option A: Downloader app (easiest, needs a public repo)**
1. On the TV, install **Downloader** by AFTVnews from Google Play.
2. Settings → System → About → click **Android TV OS build** 7 times to enable developer options.
3. Settings → Apps → Security & restrictions → **Unknown sources** → allow **Downloader**.
4. In Downloader, enter `https://github.com/drny85/claude-tv/releases/latest/download/ClaudeTV.apk`, download and install.

If the repo is private, that link needs a GitHub login, so use option B or C instead.

**Option B: adb from your computer**
1. Enable developer options (step 2 above), then Settings → System → Developer options → turn on **USB debugging** (on Google TV this also allows network debugging).
2. Find the TV's IP in Settings → Network & Internet.
3. Download `ClaudeTV.apk` from the release on your computer, then:
   ```
   adb connect <tv-ip>:5555
   adb install -r ClaudeTV.apk
   ```
   Approve the prompt on the TV the first time.

**Option C: Send Files to TV**
Install **Send Files to TV** on your phone and the TV, send the APK, then open it on the TV with a file manager.

Claude TV then shows up in your apps row (Apps → See all) with its banner; long-press to move it to favorites.

## Turn on the live catalog

1. Create a free account at themoviedb.org → Settings → API → request a developer key (instant).
2. Either type the 32-character **API Key** in Claude TV → Settings → Catalog data, **or** add it as a repository secret named `TMDB_API_KEY` (GitHub → Settings → Secrets and variables → Actions) and re-run the build so every APK ships with it.

## Building locally

Requires JDK 17 and the Android SDK (platform 35).

```
echo "tmdb.apiKey=YOUR_KEY" >> local.properties   # optional
./gradlew assembleRelease
```

The APK is signed with `keystore/claudetv-sideload.jks`, a key only for personal sideloading, so new builds install over old ones.

## Credits

This product uses the TMDB API but is not endorsed or certified by TMDB. Streaming availability data by JustWatch.
