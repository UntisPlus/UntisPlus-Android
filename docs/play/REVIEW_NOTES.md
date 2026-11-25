# Google Play review notes — UntisPlus v1.4.1

Paste/adapt these notes in Play Console. Replace bracketed placeholders before
submission.

## App access

> UntisPlus is a client for school-provided WebUntis accounts. The app opens to
> a login screen; no developer account is bundled. For review, use the supplied
> test account: `[DEDICATED_TEST_ACCOUNT / ANONYMOUS_SCHOOL_ENDPOINT]`.
>
> If no test account is supplied, the reviewer can still evaluate the signed-out
> experience: login options, embedded QR scanner, settings, About screen, and
> the privacy-policy link. Timetable, notification, and widget flows require a
> school WebUntis account. The developer's personal account must not be used.

Do not put the user's personal Untis credentials here. A dedicated school test
account or an anonymous public endpoint is still needed for a full review.

## Permissions

| Permission / feature | Justification |
|---|---|
| `android.permission.INTERNET` | Required for WebUntis/proxy login and timetable requests. |
| `android.permission.CAMERA` | Only used by the embedded ZXing scanner when the user scans a login/profile-transfer QR code. No camera stream is recorded or uploaded. |
| `android.permission.POST_NOTIFICATIONS` | Lesson-change, break, and info notifications requested by the user. |
| `android.permission.SCHEDULE_EXACT_ALARM` | User-configured lesson/break reminders must fire at exact school times. |
| `android.permission.FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC` | The timetable watcher polls the saved profile while running in the background so lesson-change notifications can be delivered. Type is `dataSync`, not location, media, or microphone. |
| `android.permission.ACCESS_NOTIFICATION_POLICY` | Used only when the user enables notification-policy-dependent settings. |
| Boot receiver | Restores user-enabled timetable alarms after device restart. |

## Foreground service declaration

**Type:** Data synchronization (`android.permission.FOREGROUND_SERVICE_DATA_SYNC`)

**Description to paste:**

> UntisPlus runs a user-initiated timetable watcher while the app is in the
> background. It periodically refreshes the saved WebUntis timetable through
> the same endpoint chosen by the user (default developer proxy, school server,
> or custom proxy) and posts a notification when a lesson or break changes.
> The service shows a persistent notification while active and can be stopped
> from the app. It does not access location, microphone, camera, contacts, or
> files, and it does not run ads or analytics.

**Demo video script (record on a device with a test profile):**

1. Open UntisPlus and show the login/profile screen.
2. Open Settings → Notifications and enable the timetable watcher.
3. Show the persistent "syncing timetable" notification in the shade.
4. Trigger or simulate a timetable change and show the resulting notification.
5. Open the app again and stop the watcher; show the persistent notification
   disappearing.

No screen capture or UI dump should be taken without explicit user permission.
The reviewer video should be recorded by the user on their own device.

## No prohibited SDKs

UntisPlus contains no Google Play Services, Firebase, ML Kit, ad network,
analytics, or crash-reporting SDK. QR scanning uses an embedded ZXing library,
and update checks use the public GitHub API. `scripts/check-foss.sh` and the
release dependency audit verify this in CI.

## Data safety

See `DATA_SAFETY.md`. Credentials and timetable data are sent off-device only
to the endpoint selected by the user; the default is the developer-operated
proxy. Purpose is app functionality only. Encryption in transit is used, and
there is no sale, advertising, or profiling.
