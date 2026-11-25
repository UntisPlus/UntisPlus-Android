# Google Play release preparation (v1.4.1)

This directory contains the non-code material needed before uploading the
`v1.4.1` AAB. Nothing here is submitted yet, and no Play Console account or
credentials have been accessed.

## Version parity

All three channels use the same version for this release:

| Channel | Version | Artifact / source |
|---|---|---|
| GitHub Releases | `v1.4.1` | APK + AAB produced by `.github/workflows/deploy.yml` from `master` |
| F-Droid | `v1.4.1`, code `1040199` | `fdroid/metadata/com.deelabs.untisplus.yml` pinned to the v1.4.1 source commit |
| Google Play | `v1.4.1`, code `1040199` | signed `app-release.aab` from the same source commit |

The `v1.4.0` tag is deliberately untouched. `v1.4.1` is a new version because
this release adds QR export, revived widgets, API 36 targeting, and the privacy
policy link.

## Code changes already included

- `compileSdk` and `targetSdk` set to 36 (Play requirement since 2026-08-31).
- Google dependency-info block removed from APK and AAB.
- Public privacy policy at <https://untisplus.github.io/UntisPlus-Android/privacy/>.
- In-app privacy policy link in Settings → About.
- GitHub Pages workflow to publish `site/`.

## User actions still required

1. Create/finish the Play Console account and pay the one-time US$25 fee.
2. Enable GitHub Pages for this repository with **GitHub Actions** as the source,
   then confirm the privacy URL resolves publicly over HTTPS.
3. Provide a dedicated reviewer account or anonymous school endpoint. Do not use
   a personal Untis account. See `REVIEW_NOTES.md`.
4. Record the short foreground-service demo described in `REVIEW_NOTES.md`.
5. Run the 12-tester / 14-day closed test required for new personal accounts.
6. After the user authorizes posting, push the release branch and let the
   existing deploy workflow publish the GitHub Release.

## Documents

- `REVIEW_NOTES.md` — app access, permissions, and foreground-service text.
- `DATA_SAFETY.md` — Play Data safety mapping.
- `STORE_LISTING.md` — listing fields and asset checklist.

## FOSS/proxy disclosure

Do not remove the default-proxy disclosure or the `NonFreeNet` and `Tracking`
anti-features from F-Droid metadata. Google Play distribution does not change
the app's data flows: no GMS, Firebase, ads, analytics, or crash SDKs.
