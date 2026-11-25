# F-Droid submission

Copy `metadata/com.deelabs.untisplus.yml` to `metadata/com.deelabs.untisplus.yml` in an `fdroiddata` fork, then run the usual `fdroid readmeta`, `fdroid lint`, and `fdroid build` checks before opening the merge request.

The pinned build commit is the v1.4.1 source with QR export, revived widgets,
API 36 targeting, the public privacy policy, and the updated Fastlane text and
changelogs in `fastlane/metadata/android/`. The icon and feature graphic were
drawn locally for UntisPlus; phone screenshots were captured manually from the
installed app after the account switch, then resized and given an UntisPlus
header overlay. Locale text and historical changelogs derive from BetterUntis
and were rebranded. The `v1.4.0` tag was left untouched; `v1.4.1` is the new
parity release for GitHub, F-Droid and Google Play.

Auto-update is enabled via `UpdateCheckMode: Tags` and `UpdateCheckData` reading `fdroid/version.txt`. When cutting a release tag, update `fdroid/version.txt` in the same commit and keep it in sync with `versionNameValue` and `deriveVersionCode()` in `app/build.gradle.kts`.

The `Tracking` anti-feature is declared deliberately because the default login route relays credentials and timetable traffic through the developer-operated proxy. The MR notes ask maintainers to decide whether `NonFreeNet` alone is sufficient; do not remove `Tracking` unilaterally.
