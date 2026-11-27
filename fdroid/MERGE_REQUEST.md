# New App: com.deelabs.untisplus

UntisPlus is a hard fork of BetterUntis with its own application ID
(`com.deelabs.untisplus`), so it installs side by side with the original app.
It is an Android client for the Untis timetable system with teacher class
tools, parent/guardian views, RoomFinder, an Info Center, notifications,
official-style login QR export and home screen widgets.

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
documented in `docs/PROXY-API.md`. The full privacy policy is published at
https://untisplus.github.io/UntisPlus-Android/privacy/ and linked from the app.

The metadata therefore declares both `NonFreeNet` and `Tracking`, with reasons
in the build metadata. There is no analytics or crash-reporting SDK, and
official accounts do not make proxy calls. Optional proxy features only send
what the user explicitly uses. We are deliberately leaving `Tracking` declared
because the default route relays identifiable data to a developer-operated
server; maintainers are asked to decide whether `NonFreeNet` alone is
sufficient and to remove `Tracking` on merge if they agree.

## Build

The build is pinned to the v1.4.1 source with Fastlane metadata. The old
`v1.4.0` tag is intentionally untouched; v1.4.1 is the shared release version
for GitHub, F-Droid and Google Play.

Verified locally:

- `./scripts/check-foss.sh` passed on the pinned commit.
- `./gradlew assembleRelease bundleRelease` built a signed APK and AAB with
  JDK 21 and SDK 36.
- `./gradlew :app:testDebugUnitTest` passed.
- No Google dependency-info block is present in the APK or AAB.

`versionCode` is derived in Gradle from `versionName`; stable releases add 99,
so `v1.4.1` is `1040199`. The metadata has a `prebuild` step that installs
SDK 36 if the build image does not already provide it. Auto-update is enabled:
`UpdateCheckMode: Tags` watches `vX.Y.Z` tags and `UpdateCheckData` reads
`fdroid/version.txt` for the name and code. Release tags must update that file
in the same commit.

## Issuebot follow-up

The RFP bot report on `c5c5f0b5` was addressed upstream:

- The Gradle wrapper was regenerated with the official Gradle 8.13 wrapper JAR
  (`81a82aae...`) and `distributionSha256Sum=20f1b117...` for the 8.13 binary
  distribution. The previous JAR matched the official 8.9 wrapper.
- The Foojay toolchain-resolver convention plugin was removed from
  `settings.gradle.kts`. The F-Droid recipe installs JDK 21 explicitly, and
  `gradle/gradle-daemon-jvm.properties` keeps the daemon on Java 21.
- A local `fdroid build` with fdroidserver 2.4.6 found that `yes | sdkmanager`
  exited 141 under the recipe shell's `pipefail`, so the build stopped after the
  SDK packages were installed. The prebuild now accepts only that specific
  status; real sdkmanager failures still fail the build.
- A forced `fdroid build -t` then exposed a configuration-cache interaction:
  the project enables `org.gradle.configuration-cache=true`, and F-Droid runs
  `clean` and `assembleRelease` as two separate Gradle invocations. The second
  one failed at `lintVitalReportRelease`/`minifyReleaseWithR8`. Passing
  `-Porg.gradle.configuration-cache=false` does not override the project
  setting, and recipes cannot pass arbitrary `-D` flags, so the prebuild now
  appends `org.gradle.configuration-cache=false` to the build checkout's
  `gradle.properties`. A forced fdroidserver 2.4.6 build against the fixed
  source revision then succeeded.
- `Translation` points to the source string directory. `crowdin.yml` exists,
  but the repository does not publish a public Crowdin project URL; maintainers
  should tell us if they prefer a different translation guide URL.
- The OpenCensus entries came from dependency verification metadata generated
  for the whole build (`help` configuration), pulled in by Android Gradle
  Plugin, UTP and KSP. They are not app runtime dependencies:
  `:app:dependencyInsight --dependency opencensus-api --configuration releaseRuntimeClasspath`
  reports no match, and a `dexdump` scan of the release APK reports no
  `io/opencensus` classes. Both `NonFreeNet` and `Tracking` remain declared.
  As before, maintainers are asked whether `NonFreeNet` alone is sufficient.

## Store metadata

The icon and feature graphic were drawn locally for UntisPlus and remain
placeholders pending commissioned artwork. Phone screenshots were captured
manually from the installed app after the account switch, then resized and
given a simple UntisPlus header overlay. No seeded demo data or instrumented
test harness was used for the final images. No Untis GmbH or F-Droid logo
artwork was copied.
Locale descriptions and historical changelogs derive from upstream BetterUntis
and were rebranded; the remaining BetterUntis mentions are attribution for the
fork's origin.
