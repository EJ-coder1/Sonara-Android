# Sonara: getting to the Play Store

Work top to bottom. Items marked (you) need your own information or accounts; I can't do those for you.

## A. Make it yours (before anything else)
- [ ] (you) Pick the final app name. Change `app_name` in `app/src/main/res/values/strings.xml`, and the name in `play-store/listing.md`. Check the name is not already used by another Play app or a trademark.
- [ ] (you) Choose your own application ID in `app/build.gradle.kts` (both `namespace` and `applicationId`), for example `com.yourname.sonara`. It can never be changed after your first upload. If you change `namespace`, also move the Kotlin package folders and update the `package` lines (Android Studio: Refactor > Rename package).
- [ ] (you) Edit `util/AppInfo.kt`: support email and public Privacy Policy URL.
- [ ] (you) Replace every [YOUR NAME OR COMPANY], [YOUR EMAIL], [DATE], [YOUR COUNTRY OR STATE] in `app/src/main/assets/legal/*.txt`, then regenerate or edit `docs/privacy-policy.html` and `docs/terms.html` to match.
- [ ] (you) Have the Privacy Policy and Terms reviewed by someone qualified. They are a solid starting point, not legal advice.

## B. Online music setup (Jamendo)
- [ ] (you) Create a free app at https://devportal.jamendo.com and copy the Client ID.
- [ ] Put it in `local.properties`: `JAMENDO_CLIENT_ID=...` (see `local.properties.example`). It is baked into the app at build time.
- [ ] (you) Read Jamendo's API terms. A free app without ads or payments is the normal case. If you ever add ads, subscriptions or paid features, you need a commercial arrangement with Jamendo first. If you don't want to deal with that, build without a Client ID: the app hides online sections and works as a local player.

## C. Build and test (Android Studio)
- [ ] Install the latest stable Android Studio, open the `Sonara` folder, and let Gradle sync. If it suggests newer library versions, accepting them is fine.
- [ ] Run on a real phone (and one emulator with Android 8). Test: first launch, allow and deny permission, play a local song, lock the screen, Bluetooth/headphone buttons, unplug headphones, notification controls, sleep timer, playlists, favorites, Home with Wi-Fi off, search, dark/light, rotate.
- [ ] Test Android 13+ (notification permission) and an Android 14/15/16 device if you can.

## D. Release build
- [ ] Create an upload key (Android Studio: Build > Generate Signed Bundle > Create new key store), or run `keytool -genkey -v -keystore release-upload-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias upload`.
- [ ] Copy `keystore.properties.example` to `keystore.properties` and fill it in. Back up the .jks and passwords somewhere safe. Never commit them.
- [ ] Bump `versionCode` (whole number, +1 for every upload) and `versionName` in `app/build.gradle.kts`.
- [ ] Build the bundle: `./gradlew bundleRelease`. Output: `app/build/outputs/bundle/release/app-release.aab`.
- [ ] Install a release build on a phone and test again (shrinking can reveal problems debug builds don't).

## E. Play Console
- [ ] (you) Create a Google Play developer account (one-time registration fee). Personal accounts created after November 2023 must run a closed test with at least 12 testers for 14 days before they can apply for production. Check the current rules in Play Console, they change.
- [ ] Create the app: name, default language, "App", "Free".
- [ ] Store listing: use `play-store/listing.md` + images from `branding/`. Add 2 to 8 phone screenshots.
- [ ] Privacy policy URL: host `docs/` (GitHub Pages works: Settings > Pages > deploy from the `docs` folder) and paste the public link.
- [ ] App access: "All functionality is available without special access."
- [ ] Ads: "No, my app does not contain ads."
- [ ] Content rating questionnaire: it's a music player; answer honestly. Music from Jamendo can include mature themes, mention user-generated/streamed content where asked.
- [ ] Target audience: 13+ or "All ages" is not recommended unless you want the Families policy; choose an adult/teen audience and say the app is not designed for children.
- [ ] Data safety form: see section F.
- [ ] Declarations: Foreground service type "Media playback" (used by the playback service). Explain: "Plays music in the background while the screen is off."
- [ ] Permissions declarations: READ_MEDIA_AUDIO is allowed for a music player; describe it as "core feature: play the user's local music files".
- [ ] Upload the .aab to Internal testing first, then Closed testing, then Production.

## F. Data safety form (suggested answers, check them against the current form)
- Does your app collect or share any of the required user data types? The app itself has no servers and no analytics. When the user searches or browses online, the request goes from their phone straight to Jamendo (a third party), including search words and IP address. Declare conservatively:
  - Data collected by you: None.
  - Data shared with third parties: if the form treats search queries sent to Jamendo as "shared", declare "App activity > In-app search history" (and IP address if prompted), purpose "App functionality", not used for ads, not optional to share when the user uses online search.
- Is all user data encrypted in transit? Yes (HTTPS).
- Can users request that data be deleted? Not applicable: no data is collected by you; users can delete everything stored on their phone in Settings > Delete all my data.

## G. After launch
- [ ] Watch Play Console > Quality > Crashes and ANRs.
- [ ] Every year Google raises the required target API level. Update `compileSdk` and `targetSdk` (now 36) and rebuild before the deadline.
- [ ] Keep `licenses.txt` and the privacy policy up to date whenever you add a library or a feature that touches data.
