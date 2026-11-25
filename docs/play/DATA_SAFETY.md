# Google Play Data safety mapping — UntisPlus v1.4.1

Use this as the answers in Play Console → App content → Data safety.

## Summary answers

- **Data collected:** Yes, limited to what is needed to sign in and show the
  timetable.
- **Data shared with third parties:** Yes, with the endpoint selected by the
  user and with GitHub for update/contributor checks.
- **Ads:** No.
- **Analytics / crash reporting:** No SDKs, no advertising IDs, no profiling.
- **Encryption in transit:** Yes, HTTPS/TLS for proxy and GitHub requests.
  Direct WebUntis endpoints use the scheme configured by the user/school.
- **Deletion:** Profiles can be deleted in the app; proxy-side deletion requests
  can be sent to <info@deelabs.tech>.

## Data types

| Play category | Data | Collected in app? | Shared off-device? | Purpose | Retention / deletion |
|---|---|---|---|---|---|
| Personal info | User ID / username, school login name, school ID | Yes | Yes — to selected endpoint (default developer proxy, school WebUntis server, or custom proxy) | App functionality: authenticate and fetch timetable | Stored locally until profile deletion; proxy logs kept only as long as needed to operate/troubleshoot; deletion on request |
| Personal info | Credentials (password or stored app-specific key) | Yes | Yes — to selected endpoint | App functionality: sign in to WebUntis | Stored locally until profile deletion; not used for ads or analytics; deletion on request |
| App activity / user content | Timetable lessons, rooms, teachers, messages, events, absences, contact hours, room-finder queries | Yes | Yes — to selected endpoint | App functionality: display timetable and school information | Cached locally; cleared with profile deletion or app uninstall |
| App info / performance | Crash logs, diagnostics, device identifiers | No | No | Not collected by any SDK | N/A |
| Location | Approximate or precise location | No | No | Not collected | N/A |
| Financial info | Purchases, payment info | No | No | No in-app purchases | N/A |
| Contacts / SMS / call logs | Contacts, SMS, call history | No | No | Not collected | N/A |

## Endpoint disclosure

The default endpoint for new accounts is `api-untis.deelabs.tech`, operated by
the developer. It relays credentials and timetable traffic and performs a
`/me` compatibility probe at login. This is disclosed in the app's privacy
policy and in F-Droid anti-features. Users can instead use their school's
official WebUntis server or a self-hosted/custom proxy; then the developer
proxy is not contacted for those requests.

## Third-party recipients

- **Selected WebUntis/proxy endpoint:** receives login and timetable requests
  described above. Required for the app's core function.
- **GitHub:** contacted for the latest-release check and contributor list shown
  in About. No Untis credentials are sent to GitHub. GitHub's privacy statement
  applies to those requests.

## Account deletion

UntisPlus does not create an online app account; WebUntis profiles are local
configuration on the device. Users delete them in Settings/Profile management.
For proxy-side deletion requests, contact <info@deelabs.tech> with the
school/login identifier involved. This is also stated in the public privacy
policy linked from the app.

## Consistency check

These answers must match `site/privacy/index.html`, the Fastlane descriptions,
and the F-Droid anti-feature text. Do not remove the default-proxy disclosure
to simplify the Play form.
