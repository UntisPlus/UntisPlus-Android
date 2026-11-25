# Privacy Policy

**UntisPlus** — Android client for the Untis timetable system
Last updated: 2026-11-25

## The short version

UntisPlus has no ads, advertising IDs, or third-party analytics/crash-reporting
SDKs. New accounts use the developer-operated UntisPlus proxy at
`api-untis.deelabs.tech` by default. That means login credentials and
timetable/school requests are relayed through that server. You can choose your
school's official Untis endpoint or a custom endpoint during login if you do
not want traffic going through the developer proxy.

## Data the app handles

- **Your Untis credentials** (username and an app-specific access key) are
  entered by you and stored **only locally on your device**, in the app's
  private storage. They are transmitted to the endpoint selected at login:
  the developer-operated UntisPlus proxy by default, your school's server with
  the official endpoint, or a server you configure yourself.
- **Timetable and school data** (your timetable, absences, messages, etc.) is
  fetched through the selected endpoint and cached **locally on your device**.
  With the default provider, the developer-operated proxy relays these
  requests.
- **Proxy compatibility data**: after logging in with a proxy account, the app
  makes one `GET /me` request to cache which proxy features are available.
  The result is stored locally; a failed probe never blocks login.
- **Optional proxy features** (teacher cancellation reports, calendar feeds,
  notification webhooks/ntfy) send only the data needed for the feature you
  explicitly use to the configured proxy.
- **Notifications and auto-mute** run locally on your device using your
  timetable data; they do not involve external services.

## Permissions

The app requests only the permissions needed for its features (for example:
notifications, network access, optionally camera for QR-code profile import).
None of them are used to collect advertising identifiers or location data.

## Third parties

UntisPlus contains no third-party tracking or crash-reporting SDKs. The app
communicates with your school's Untis infrastructure, the UntisPlus proxy when
selected or used by default, and URLs you configure or open yourself (school
search, SSO provider pages, custom links, imported calendars, and the GitHub
release check you can open from Settings).

## Untis itself

This app is an unofficial client. Your use of Untis is additionally governed
by your school's and Untis GmbH's own policies, which are independent of this
project.

## Contact

Open an issue at https://github.com/UntisPlus/UntisPlus-Android/issues if
you have questions about this policy.
