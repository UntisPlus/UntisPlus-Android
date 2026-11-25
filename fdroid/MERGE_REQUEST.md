# New App: com.deelabs.untisplus

UntisPlus is a hard fork of BetterUntis with its own application ID
(`com.deelabs.untisplus`), so it installs side by side with the original app.
It is an Android client for the Untis timetable system with teacher class
tools, parent/guardian views, RoomFinder, an Info Center, notifications and
home screen widgets.

## Freedom and licensing

- Source: https://github.com/UntisPlus/UntisPlus-Android
- License: GPL-3.0-or-later
- Issue tracker: https://github.com/UntisPlus/UntisPlus-Android/issues
- No Google Play Services, Firebase, ML Kit, ads, in-app purchases, or
  third-party analytics/crash-reporting SDKs.
- `scripts/check-foss.sh` guards source and `releaseRuntimeClasspath` against
  proprietary Google components in CI.
- Release runtime audit found 202 resolved libraries, all with accepted FOSS
  licenses (Apache-2.0, MIT, BSD-3-Clause, GPL-2.0-with-Classpath-Exception).

## Network and privacy

New accounts use the developer-operated UntisPlus proxy at
`api-untis.deelabs.tech` by default. Login credentials and timetable requests
are relayed through it, and a `/me` compatibility probe is made once at login.
Users can choose the official school endpoint or a custom endpoint to avoid the
developer proxy. The metadata therefore declares both `NonFreeNet` and
`Tracking`, with reasons in the build metadata.

No third-party tracking SDKs are included, and official accounts do not make
proxy calls. Optional proxy features only send what the user explicitly uses.

## Build

The build is pinned to the stable v1.4.0 source with Fastlane metadata. The
upstream `v1.4.0` tag is not used because it predates the final FOSS manifest
cleanup and CI fixes.

Verified locally:

- `./scripts/check-foss.sh` passed on the pinned commit.
- `./gradlew :app:assembleRelease` built the release APK with JDK 21 and SDK 35.

`versionCode` is derived in Gradle from `versionName`; stable releases add 99,
so `v1.4.0` is `1040099`. Tag-based auto updates should stay disabled until a
static version-code source is added.

## Store metadata

The icon, feature graphic, screenshots, locale descriptions, and historical
changelogs were imported from the upstream BetterUntis `fdroid` branch and
adapted for UntisPlus where needed.
