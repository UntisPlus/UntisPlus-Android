# New App: com.deelabs.untisplus

UntisPlus is a hard fork of BetterUntis with its own application ID
(`com.deelabs.untisplus`), so it installs side by side with the original app.
It is an Android client for the Untis timetable system with teacher class
tools, parent/guardian views, RoomFinder, an Info Center, notifications and
home screen widgets.

## Freedom and licensing

- Source: https://github.com/UntisPlus/UntisPlus-Android
- License: GPL-3.0-only
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
In the Advanced login options, users can instead connect directly to their
school's official WebUntis server, point the app at their own self-hosted
UntisPlus proxy, or use another custom endpoint. The backend contract is
documented in `docs/PROXY-API.md`.

The metadata therefore declares both `NonFreeNet` and `Tracking`, with reasons
in the build metadata. There is no analytics or crash-reporting SDK, and
official accounts do not make proxy calls. Optional proxy features only send
what the user explicitly uses. We are deliberately leaving `Tracking` declared
because the default route relays identifiable data to a developer-operated
server; maintainers are asked to decide whether `NonFreeNet` alone is
sufficient and to remove `Tracking` on merge if they agree.

## Build

The build is pinned to the stable v1.4.0 source with Fastlane metadata. The
upstream `v1.4.0` tag is not used because it predates the final FOSS manifest
cleanup and CI fixes.

Verified locally:

- `./scripts/check-foss.sh` passed on the pinned commit.
- `./gradlew :app:assembleRelease` built the release APK with JDK 21 and SDK 35.

`versionCode` is derived in Gradle from `versionName`; stable releases add 99,
so `v1.4.0` is `1040099`. Auto-update is enabled: `UpdateCheckMode: Tags`
watches `vX.Y.Z` tags and `UpdateCheckData` reads `fdroid/version.txt` for the
name and code. Release tags must update that file in the same commit.

## Store metadata

The icon, feature graphic, screenshots, locale descriptions, and historical
changelogs were imported from the upstream BetterUntis `fdroid` branch and then
rebranded for UntisPlus. The remaining BetterUntis mentions are attribution for
the fork's origin.
