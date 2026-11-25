# F-Droid submission

Copy `metadata/com.deelabs.untisplus.yml` to `metadata/com.deelabs.untisplus.yml` in an `fdroiddata` fork, then run the usual `fdroid readmeta`, `fdroid lint`, and `fdroid build` checks before opening the merge request.

The pinned build commit is the stable v1.4.0 source plus the Fastlane text and image metadata in `fastlane/metadata/android/`. The icon, feature graphic, screenshots, locale text, and historical changelogs were imported from the BetterUntis `fdroid` branch and adapted for UntisPlus where needed. The `v1.4.0` tag was left untouched.
