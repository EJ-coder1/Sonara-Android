# Sonara

A modern, private music player for Android. Plays the songs on your phone and streams free, Creative Commons music from independent artists (via Jamendo). Built with Kotlin, Jetpack Compose, Material 3 and Media3.

This is original Sonara code. It does not copy code from ArchiveTune or LastWave Native (both reference projects are GPL-3.0). Their discovery, queue and provider patterns were studied, but Sonara's provider layer is implemented independently. See "Ideas borrowed" and `docs/PROVIDER_ARCHITECTURE.md`.

## Features
- Home: time-aware greeting, "Continue listening" card, quick picks, recently played, mood chips, trending and new releases (online)
- Full-screen Now playing: animated artwork backdrop, wavy seek bar, play/pause, next/previous, shuffle, repeat, queue, like button, live format badge (for example `FLAC • 987 kbps • 44.1 kHz`)
- Synced lyrics (opt-in, via lrclib.net): auto-scroll, karaoke-style word sweep, tap a line to jump, text size and timing offset
- Audio & playback sheet: in-app volume, speed and pitch (linked or independent), equalizer with presets, bass boost, loudness boost, sleep timer
- Track menu: Start mix with this song, play next, add to queue/playlist, Go to artist, Go to album, Details & audio specs, copy link
- Looks: AMOLED black, 7 accent colors, wallpaper colors, wavy bar and backdrop can be turned off
- Background playback health: notification artwork for phone songs, auto-skip of broken tracks inside the service, and a one-tap fix for phones that restrict background apps (Xiaomi, Samsung, Oppo...)
- Mini player with progress, notification + lock-screen + Bluetooth + headset controls (MediaSession)
- Library: all songs, liked songs, playlists (create, rename, delete, add and remove songs) and a Downloads tab
- Downloads: save online songs for offline listening (only where the artist allows it), over Wi-Fi only if you like. Progress, retry and delete in Library > Downloads, \"Download all\" for liked songs and playlists, an offline badge on downloaded songs
- Play cache: songs you stream are kept on the phone as they play, so playing them again uses no data. Pick the size (128 MB to 2 GB) or clear it in Settings
- Resume where you left off: your last queue and position come back (paused) when you open the app
- Theme Studio: 12 two-color palettes plus your own (pick the main and second color with sliders or a hex code, with a live preview), wallpaper colors, AMOLED black, light/dark/auto
- Mini player: cover art in a circle, cookie, flower or rounded shape that turns while music plays, with a progress ring; swipe it left or right to change song
- Premium Settings: card layout with icon chips, a hero card in your palette and gentle entrance animations (and an Animations switch for a calmer app)
- Library: Albums and Artists tabs, sorting (title, artist, newest, longest) and a Downloads tab; Search remembers your recent searches
- Skip silence, now-playing bars in song lists, a bounce on the like button
- App icon: 16 launcher icons in 5 groups (Aurora, Nebula, Obsidian, Graphite, Neon, Midnight, Slate, Pearl, Cloud, Mint, Candy, Sunset, Ocean, Forest, Crimson, Gold), each with a themed-icon layer. Switch in Settings > Appearance > App icon
- Search: your phone, playable Jamendo tracks/albums/artists/playlists, worldwide Apple/iTunes catalog metadata across PH/US/UK/KR storefronts, and one-tap handoff to official YouTube Music, Spotify and Apple Music search
- Online discovery tab: recently played online tracks, a For You mix based on Jamendo similar-track results, weekly trending, latest releases, country-based charts and artists, moods, Jamendo hot playlists, popular albums and artists, and genre mixes
- Online catalog pagination: 30-track pages with Load more for trending, new releases and regional charts; catalogue metadata is cached in memory for 10–30 minutes
- Mood Radio Mixes: one-tap, 60-track queues generated from Jamendo mood tags (not continuous broadcast radio streams)
- Settings: light/dark/system theme, wallpaper colors (Android 12+), rescan, delete all my data
- Legal: Privacy Policy, Terms of Use, licenses (offline, in-app) + hosted copies in `docs/`
- Proper states everywhere: loading shimmer, offline/error with Retry, empty, permission needed
- Edge-to-edge, predictive back, adaptive + themed (monochrome) icon, splash screen

## Ideas borrowed (and how they were rebuilt)
| Idea seen in ArchiveTune / LastWave | What Sonara does instead |
|---|---|
| Wavy seek bar | `ui/player/WavySeekBar.kt`: own Canvas drawing, wave eases in and out with play/pause, math in `WaveMath.kt` (unit tested) |
| Fluid, rotating artwork background | `ui/player/FluidBackdrop.kt`: tiny cover bitmap drawn huge (smooth scaling is the blur), three layers drifting at different speeds, honors "Remove animations" |
| Word-by-word lyrics | `data/lyrics/`: LRCLIB line timing, enhanced-LRC word stamps when available, estimated word timing otherwise, strict title/artist/version matching, and a smooth playback clock |
| Audio & Playback sheet, EQ presets | `ui/player/AudioSheet.kt`, `playback/AudioEffectsManager.kt`, `EqPresets.kt` (presets stretch to any band count) |
| Track menu (mix, go to artist/album, specs) | `ui/TrackMenu.kt`, `data/MixBuilder.kt` (local songs never leave the phone) |
| Battery exemption row | `util/BatteryOptimization.kt` + Settings > Playback (opens the system page, no special permission) |
| Spinning shaped cover with progress ring | `ui/player/SpinningArtwork.kt` draws a scalloped outline with a polar curve, turns it with a frame-time loop that pauses when playback pauses, and a ring arc shows the progress |
| Choose your own colors | `ui/settings/ThemeStudioScreen.kt` + `ui/theme/Theme.kt`: a palette is two colors; the whole color scheme and the signature gradient are generated from them (`ColorMath` does HSL and hex) |
| Premium settings look | `ui/settings/SettingsComponents.kt`: hero card, group cards, icon chips, press feedback and staggered entrance (`ui/components/Motion.kt`) |
| Download for offline | `data/downloads/DownloadsManager.kt` hands the transfer to Android's own DownloadManager (keeps going when the app is closed), files live in the app's private music folder |
| Play cache | `playback/PlayCache.kt` + `PlaybackService`: streams go through Media3's cache; `CacheTrimPlanner` removes the least recently played songs when the size you chose is exceeded |
| Pick a different app icon | `data/AppIcon.kt` + `util/AppIconManager.kt` switch one `<activity-alias>` on at a time; icons are single generated XML files in `res/mipmap-anydpi-v26`; `AppIconTest` keeps list, manifest and files in sync |
| Accent palettes, AMOLED | `ui/theme/Theme.kt` generates a full scheme from one hue (`util/ColorMath.kt`) |

Left out on purpose: extracting or scraping YouTube/Spotify audio (not an authorized playback API), Discord presence, Jam (needs a server), Last.fm scrobbling (needs API keys), animated Canvas video.

## Requirements
- Android Studio (latest stable), JDK 17 (bundled with Android Studio)
- Android 8.0+ (API 26) devices. Compiles against API 36, which Google Play requires for new apps.

## Run it
1. Open the `Sonara` folder in Android Studio and let Gradle sync.
2. (Optional, enables online music) Get a free client ID at https://devportal.jamendo.com and add `JAMENDO_CLIENT_ID=...` to `local.properties`.
3. Run on a phone that has some music on it. Accept the permission on the welcome screen.

If `./gradlew` is missing (the wrapper jar isn't included), open the project in Android Studio, or run `gradle wrapper --gradle-version 8.11.1` once.

## Make it yours
| What | Where |
|---|---|
| App name | `app/src/main/res/values/strings.xml` |
| Application ID / version | `app/build.gradle.kts` (`namespace`, `applicationId`, `versionCode`, `versionName`) |
| Colors | `ui/theme/Theme.kt` (brand colors at the top), `res/values/colors.xml` |
| Logo | `res/drawable/ic_launcher_*.xml`, `ic_logo.xml`, `ic_splash.xml`; masters in `branding/` |
| Email, privacy URL | `util/AppInfo.kt` |
| Legal text | `app/src/main/assets/legal/*.txt` and `docs/*.html` |
| Online moods | `Moods` in `ui/AppViewModel.kt` |

## Project layout
```
app/src/main/java/com/sonara/player/
  SonaraApp.kt, MainActivity.kt
  data/        Track models, MediaStore reader, Jamendo client, DataStore library
  playback/    PlaybackService (Media3 session) and PlayerController (UI bridge)
  ui/          theme, view models, navigation root, screens, shared components
  util/        formatting helpers, AppInfo
branding/      logo.svg, Play Store icon, feature graphic
docs/          privacy-policy.html, terms.html (host these, e.g. GitHub Pages)
play-store/    listing text and the step-by-step CHECKLIST.md
```

## Online discovery notes
- Worldwide search uses the Apple/iTunes Search API for catalog metadata and official store links. Sonara does not use the returned preview audio or extract Apple/YouTube/Spotify audio; full playback happens in the respective official app or website. Search terms and storefront country codes are sent to Apple when this search section is used.
- The YouTube Music, Spotify and Apple Music chips are official-service search handoffs, not in-app stream resolvers. If the matching app is installed, Android may open it; otherwise the link opens in a browser.
- Jamendo country charts and artist locations use the artist country/location declared on Jamendo. Sonara starts with the device's region (Philippines is the fallback if no country is set); the user can tap Use location to request approximate foreground location and update country-level recommendations. Only the ISO country code is sent to Jamendo; Sonara does not send or store latitude/longitude.
- Jamendo is a worldwide independent-artist catalogue, not a complete catalogue of every commercial release. Each track has its own license and download permissions. Sonara does not scrape pirated sources or bypass stream/download restrictions.
- The For You section asks Jamendo for tracks similar to the most recently played online track. With no online listening history, it falls back to this week’s trending tracks.
- Mood Radio Mixes build a normal Sonara playback queue from Jamendo tags. Jamendo currently warns that the URL returned by its continuous radio stream method is not working, so Sonara does not present this as a live radio broadcast.
- Track metadata is cached in memory (about 10 minutes for track lists and 30 minutes for catalogues); the existing Media3 play cache handles audio caching separately.
- Track download actions must continue to respect Jamendo’s `audiodownload_allowed` field and each track’s license.

## Known limits and ideas for later
- The global Apple/iTunes results are metadata listings, not Sonara-playable audio. The current in-app online playback provider remains Jamendo, where track-level license and download rules are respected. Provider expansion must use official/authorized playback APIs; do not add audio extraction from undocumented endpoints.
- The latest changes were written and logic-tested outside Android Studio (pure logic runs in `app/src/test`), but the Compose UI could not be compiled there. Push to GitHub and open the Actions run: a compile error shows up as a short readable list.
- Lyrics are opt-in because a lookup sends the song's title, artist, album and length to lrclib.net. Before publishing, review Play Console's Data safety disclosures for optional approximate location and the online requests sent to Jamendo/LRCLIB.
- Lyrics are line-synced. Enhanced LRC sources can provide word timestamps; when a source has only line timestamps, the word sweep inside a line remains an estimate. The line clock interpolates between player updates, and matching rejects different titles, artists, durations and recording versions before display.
- Crossfade is not built yet.
- Downloads exist only for online songs, and only when the artist allows it (Jamendo flags this per song). Songs already on your phone need no download. Downloaded files are kept in the app's private folder, so other music apps cannot see them and uninstalling the app removes them.
- Changing the app icon: some launchers take a few seconds to refresh and a few close the app when the icon changes. A pinned shortcut must be pinned again.
- Every GitHub Actions build is signed with a fresh test key, so uninstall the old test build before installing a newer one.
- Backups are off (`allowBackup=false`), so favorites and playlists don't move to a new phone. Add Auto Backup rules if you want that.
- Unit tests cover color hex/HSL helpers, song sorting, album/artist grouping, lyrics parsing/timing and metadata matching, equalizer presets, audio labels, wave math, color math, mix building, the app icon list vs. manifest vs. icon files, play-cache trimming, session saving and size formatting. Add tests for `LibraryStore` and the view models, and a UI test for the first-run flow, before big changes.
