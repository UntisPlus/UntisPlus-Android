# Google Play store listing — UntisPlus v1.4.1

Existing Fastlane assets in `fastlane/metadata/android/` can be reused. Play
field limits are stricter for the short description, so use the values below.

## Text fields

**App name (max 30):** `UntisPlus – Timetable Client`
(If the dash causes issues, use `UntisPlus Timetable`.)

**Short description (max 80):**

> Open-source Untis timetable client with widgets, QR login and local notifications.

**Full description:** use
`fastlane/metadata/android/en-US/full_description.txt`. It is under the 4,000
character limit, already discloses the default proxy, and now accurately
mentions home screen widgets. German text is in `de-DE/full_description.txt`.

**Privacy policy URL:** <https://untisplus.github.io/UntisPlus-Android/privacy/>

**What's new (v1.4.1):**

> - Add official-style login QR export from profile management
> - Revive timetable home screen widgets and shortcuts
> - Target Android 16 / API 36
> - Add an in-app privacy policy link and remove dependency-info metadata
> - Keep the build fully FOSS with no ads, analytics, or tracking SDKs

## Assets

- App icon: existing `fastlane/metadata/android/en-US/images/icon.png`.
- Feature graphic: existing `fastlane/metadata/android/en-US/images/featureGraphic.png`.
- Phone screenshots: use 2–8 from `fastlane/metadata/android/en-US/images/phoneScreenshots/`.
- Do not copy Untis GmbH logos or imply official affiliation.

## App content answers

- Ads: **No**.
- In-app purchases: **No**.
- Target audience: general / school education tool; not child-directed as a
  standalone service. Select the appropriate content rating questionnaire answers.
- App access: see `REVIEW_NOTES.md`; needs a dedicated test account or endpoint.
- Data safety: see `DATA_SAFETY.md`.
- Foreground service: Data sync, with description and demo video from
  `REVIEW_NOTES.md`.

## Release checklist

1. Enable GitHub Pages with Actions as source; verify privacy URL is public.
2. Build signed AAB from the v1.4.1 source commit (`./gradlew bundleRelease`).
3. Upload AAB; confirm version name `v1.4.1` and version code `1040199`.
4. Enroll in Play App Signing; back up `app/UntisPlus.jks` and signing secrets.
5. Complete app content, data safety, and review notes.
6. Start closed test with 12 opted-in testers for 14 days if required.
7. After user authorization, submit for review and then production.
