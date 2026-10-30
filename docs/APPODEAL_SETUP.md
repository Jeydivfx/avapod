# Appodeal migration — AvaPod

Implementation uses Appodeal core 4.4.0 with explicitly selected AppLovin,
Unity Ads, Vungle/Liftoff and IAB adapters. No AdMob adapter or Google Mobile Ads
SDK is requested. Firebase remains for the catalogue/authentication; advertising
ID support used by other networks is not an AdMob integration.

## Configuration before publication

1. In Appodeal confirm the supplied app key belongs to Android package
   `com.avapod.app`. Configure and enable the selected networks. Do not connect
   the suspended AdMob account or enable Google demand as a workaround.
2. Complete Appodeal's consent/CMP setup for the regions where the app is
   distributed. The SDK manages the initial form; Settings provides a privacy
   options entry point. Ad requests are gated by `ConsentManager.canShowAds()`.
3. Follow the dashboard's app-ads.txt instructions, using its actual entries on
   the developer website linked from the store. Update the privacy policy and
   Play Data safety declaration for the SDKs actually shipped.
4. In both Debug and Release, the existing Firebase ad configuration controls `ad_banner_enabled`
   and `ad_interstitial_enabled`. They must be true to enable their respective
   formats. Defaults are off until configuration is fetched. The obsolete
   `ad_rewarded_enabled` field is ignored. The legacy SQLite column is retained
   for upgrade compatibility and written as zero.

Debug respects the same remote switches and still requests only test ads.
Configuration retrieval runs independently of catalogue synchronization. Debug uses a separate
frequency store, a 15-second gap and two navigation interactions. Release keeps
the five-minute/five-interaction limits below. Consent and playback gates apply
to both builds. Filter Logcat by `AvapodAds` for initialization results, request,
load/show failures and placement eligibility. Release also logs configuration flags,
initialization state/errors, consent gating and load failures under `AvapodAds`.
Detailed placement traces remain Debug only.

No dashboard, Firebase rules, consent configuration or store declarations were
changed by this source migration. Switching providers does not resolve the
existing AdMob suspension or guarantee that future traffic is valid.

## Actual placement and limits

- Debug calls `Appodeal.setTesting(true)` before initialization; Release uses
  false. Do not change this to obtain fill during development. A test creative
  still depends on the selected network and configuration being available.
- Rewarded video is not initialized or shown. Downloads start directly.
- Interstitials can be shown only on an explicit return from podcast details to
  an internal list. They never run on launch, app exit, background resume, opening
  an episode, or tapping play.
- Eligibility requires at least five counted navigation interactions, five
  minutes since the ad manager's process session started, and five minutes since
  the last persisted display attempt. Attempts count even when display fails.
  Session time includes time spent in the background. Playback must be connected
  and idle (`playWhenReady == false` and `isPlaying == false`).
- The ad must already be loaded and `canShow` must be true. Missing ads do not
  delay navigation, and load callbacks never display an interstitial later.
- The player embeds `BANNER_VIEW` in a dedicated 50dp slot, with a 24dp label,
  24dp top separation and 16dp bottom margin. It is outside the scrollable
  controls, and is hidden while the player menu or timer sheet is open, or the
  activity is paused. Layouts narrower than 320dp skip display.
- Loading is manual and throttled independently per format to at most one
  explicit cache request per minute. There is no app-level retry loop.

## Android Studio acceptance checks (not executed here)

1. Sync dependencies and run Debug. Confirm test-mode creatives; never click a
   live ad for testing. Verify normal navigation and downloads with no ad fill.
2. At launch, during playback and on app exit, confirm no interstitial appears.
3. With playback paused, spend 15 seconds in Debug and perform two
   meaningful navigations; return from podcast details. If a test ad is ready,
   close it and verify exactly one return to the list. Rapid back presses must
   not cause another ad or multiple back-stack pops.
4. Repeat while offline/no fill and after backgrounding. Navigation must work.
5. Check player portrait/landscape, large text, light/dark themes, menu and timer
   sheets. Verify the banner never overlays seek, play, download or navigation.
6. Test consent acceptance/refusal and reopening Settings privacy options in a
   region where the CMP requires a form.
7. Before release inspect the resolved runtime dependencies and merged manifest
   in Android Studio. Confirm no Google Mobile Ads SDK or AdMob adapter appears.
   Test the minified Release through an internal track with registered test
   devices; do not use your own live impressions/clicks as a test.

Source verification: XML/resource checker passed; SDK methods and callbacks were
checked against the published 4.4.0 AAR, and adapter coordinates against official
POMs. No Gradle build, APK/AAB, device run, or resolved full dependency-tree test
was performed. These checks do not replace the acceptance checks above.

## References

- https://docs.appodeal.com/android/get-started
- https://docs.appodeal.com/android/ad-types/banner
- https://docs.appodeal.com/android/ad-types/interstitial
- https://docs.appodeal.com/android/advanced/testing
- https://docs.appodeal.com/android/data-protection/gdpr-and-ccpa
- https://support.google.com/admob/answer/6201362?hl=en
- https://support.google.com/admob/answer/6201350?hl=en
