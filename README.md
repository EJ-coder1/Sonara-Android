# Sonara

A modern, private music player for Android. Plays the songs on your phone and streams free, Creative Commons music from independent artists (via Jamendo). Built with Kotlin, Jetpack Compose, Material 3 and Media3.

This is original code. It does not copy LastWave's code (LastWave Native is GPL-3.0, which would force this app to be GPL-3.0 too). Features were rebuilt from scratch after studying what they do and why they feel good, the "Steal Like an Artist" way: steal the idea, not the code. See "Ideas borrowed" below.

## Features
- Home: time-aware greeting, "Continue listening" card, quick picks, recently played, mood chips, trending and new releases (online)
- Full-screen Now playing: animated artwork backdrop, wavy seek bar, play/pause, next/previous, shuffle, repeat, queue, like button, live format badge (for example `FLAC • 987 kbps • 44.1 kHz`)
- Synced lyrics (opt-in, via lrclib.net): auto-scroll, karaoke-style word sweep, tap a line to jump, text size and timing offset
- Audio & playback sheet: in-app volume, speed and pitch (linked or independent), equalizer with presets, bass boost, loudness boost, sleep timer
- Track menu: Start mix with this song, play next, add to queue/playlist, Go to artist, Go to album, Details & audio specs, copy link
- Looks: AMOLED black, 7 accent colors, wallpaper colors, wavy bar and backdrop can be turned off
- Background playback health: notification artwork for phone songs, auto-skip of broken tracks inside the service, and a one-tap fix for phones that restrict background apps (Xiaomi, Samsung, Oppo...)
- Mini player with progress, notification + lock-screen + Bluetooth + headset controls (MediaSession)
- Library: all songs, liked songs, playlists (create, rename, delete, add and remove songs)
- Search: your phone and online at once
- Settings: light/dark/system theme, wallpaper colors (Android 12+), rescan, delete all my data
- Legal: Privacy Policy, Terms of Use, licenses (offline, in-app) + hosted copies in `docs/`
- Proper states everywhere: loading shimmer, offline/error with Retry, empty, permission needed
- Edge-to-edge, predictive back, adaptive + themed (monochrome) icon, splash screen

## Ideas borrowed (and how they were rebuilt)
| Idea seen in LastWave | What Sonara does instead |
|---|---|
| Wavy seek bar | `ui/player/WavySeekBar.kt`: own Canvas drawing, wave eases in and out with play/pause, math in `WaveMath.kt` (unit tested) |
| Fluid, rotating artwork background | `ui/player/FluidBackdrop.kt`: tiny cover bitmap drawn huge (smooth scaling is the blur), three layers drifting at different speeds, honors "Remove animations" |
| Word-by-word lyrics | `data/lyrics/`: LRCLIB line timing + estimated word timing (`LyricsTiming`), smooth per-frame sweep in `LyricsView.kt` |
| Audio & Playback sheet, EQ presets | `ui/player/AudioSheet.kt`, `playback/AudioEffectsManager.kt`, `EqPresets.kt` (presets stretch to any band count) |
| Track menu (mix, go to artist/album, specs) | `ui/TrackMenu.kt`, `data/MixBuilder.kt` (local songs never leave the phone) |
| Battery exemption row | `util/BatteryOptimization.kt` + Settings > Playback (opens the system page, no special permission) |
| Accent palettes, AMOLED | `ui/theme/Theme.kt` generates a full scheme from one hue (`util/ColorMath.kt`) |

Left out on purpose: scraping YouTube/Spotify (against their terms and not Play-safe), Discord presence, Jam (needs a server), Last.fm scrobbling (needs API keys), animated Canvas video.

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

## Known limits and ideas for later
- The latest changes were written and logic-tested outside Android Studio (pure logic runs in `app/src/test`), but the Compose UI could not be compiled there. Push to GitHub and open the Actions run: a compile error shows up as a short readable list.
- Lyrics are opt-in because a lookup sends the song's title, artist, album and length to lrclib.net. The privacy policy already says so. Update your Play Data safety form if you publish.
- Lyrics are line-synced; the word sweep inside a line is an estimate.
- Crossfade and offline downloads for online tracks are not built yet.
- Backups are off (`allowBackup=false`), so favorites and playlists don't move to a new phone. Add Auto Backup rules if you want that.
- Unit tests cover lyrics parsing/timing, equalizer presets, audio labels, wave math, color math and mix building. Add tests for `LibraryStore` and the view models, and a UI test for the first-run flow, before big changes.
