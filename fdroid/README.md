# F-Droid submission

Copy `metadata/com.deelabs.untisplus.yml` to `metadata/com.deelabs.untisplus.yml` in an `fdroiddata` fork, then run the usual `fdroid readmeta`, `fdroid lint`, and `fdroid build` checks before opening the merge request.

The pinned build commit is the stable v1.4.0 source plus the Fastlane text and image metadata in `fastlane/metadata/android/`. The icon, feature graphic, screenshots, locale text, and historical changelogs were imported from the BetterUntis `fdroid` branch and then rebranded for UntisPlus. The `v1.4.0` tag was left untouched.

Auto-update is enabled via `UpdateCheckMode: Tags` and `UpdateCheckData` reading `fdroid/version.txt`. When cutting a release tag, update `fdroid/version.txt` in the same commit and keep it in sync with `versionNameValue` and `deriveVersionCode()` in `app/build.gradle.kts`.

The `Tracking` anti-feature is declared deliberately because the default login route relays credentials and timetable traffic through the developer-operated proxy. The MR notes ask maintainers to decide whether `NonFreeNet` alone is sufficient; do not remove `Tracking` unilaterally.
