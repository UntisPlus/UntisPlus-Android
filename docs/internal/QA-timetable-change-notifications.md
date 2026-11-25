# QA checklist: Timetable-change notifications

## Status and provenance

- **This is a checklist only. No subagent ran any step below.** No device session, build execution, login, account, credential, proxy session/JSESSIONID, or live Untis/proxy endpoint was used to prepare this document.
- Every step is for **the user to run later, on their own device, with their own account and their own proxy instance**. No secret, token, password, cookie, or real endpoint is recorded here.
- Source item: `docs/internal/PLAN-post-agent-next-phase.md` → “Still needs testing (implemented, not verified)”, item 2 (“Timetable-change notifications | SSE stream, 15-minute WorkManager backstop, channels, process death | Implementation done; device verification only”), with adjacent item 3 and the “Not yet device-tested” note for permissions/battery/profile-switch/logout behavior.
- Implementation inspected statically in this worktree: `TimetableChangeService` (SSE foreground service), `TimetableChangeWorker` (15-minute periodic backstop), `TimetableChangeNotifications` (channels and notification posting), `SettingsCategoryNotifications`, `MainActivity` (re-arm on app open), and `UserRepository` (profile switch/delete).
- **No screenshots and no screen capture.** Record text observations only, unless the user explicitly asks for images in a separate request.

## Ground rules for the person running this

- Use your own school account and your own proxy. Do not add real credentials, JWTs, cookies, proxy URLs containing tokens, school names, user IDs, or person IDs to this file or to shared chat.
- Prefer observation over dumping state. If you inspect logs, use **tag-filtered** logcat during a short test window only (see “Attributing the delivery path” below). Do not paste raw `logcat -d` output, configuration files, or WorkManager databases anywhere.
- If a note contains an identifier or URL, redact it before saving the result.
- Result legend: tick exactly one box per check. Leave all boxes empty if the check was not run.

  - [ ] PASS — observed behavior matched “Expected”
  - [ ] FAIL — observed behavior did not match “Expected”; add a short text note
  - [ ] N/A — with reason (for example: no second profile available, logs unavailable)

## Preconditions (record before starting)

- [ ] A debug build containing this feature is installed (`com.deelabs.untisplus.debug`) **or** a release build you control. Write the exact version string in your own notes, not here.
- [ ] At least one saved profile that uses the UntisPlus proxy and has a class/target whose timetable you can legitimately change.
- [ ] For the profile-switch check: a second profile, or a non-proxy profile, available on the same device.
- [ ] Android notification permission granted to the app (Android 13+), and neither notification channel blocked by the user.
- [ ] A way to publish **one harmless, real timetable change** for your own class/target through your normal school or proxy workflow. Do not record the endpoint or credentials here.
- [ ] Baseline understood: the first SSE snapshot only stores the current version; it should **not** produce an alert. Test delivery with a **new** change published after notifications are enabled.

---

## 1. Notification permission and channels

**Steps**

1. Android Settings → Apps → the app → Notifications. Look for the two app-created channels.
2. In the app, open Settings → Notifications. Turn the master notification switch on (grant the Android 13+ prompt if asked), then turn on “Timetable change notifications”.

**Expected**

- Two channels exist: **“Timetable changes”** (default importance; may make sound/vibrate per device settings) and **“Live timetable monitoring”** (low importance/silent). Their internal IDs are `notifications_channel_timetablechanges` and `notifications_channel_timetablechanges_service`.
- With the feature enabled, an ongoing, silent notification titled **“Watching for timetable changes”** appears. It is the service notification, not a change alert. Tapping it opens the app.
- Turning the master notification switch off clears notifications, cancels the periodic work, and stops the service. Turning “Timetable change notifications” off cancels the work and stops the service while leaving the master switch intact.
- Blocking the “Timetable changes” channel suppresses change alerts; blocking “Live timetable monitoring” does not by itself disable detection.

**Result**

- [ ] PASS
- [ ] FAIL
- [ ] N/A

Notes (text only): ______________________________________________

---

## 2. SSE stream delivery (live path)

**Steps**

1. Keep the app in the foreground with the “Watching for timetable changes” notification visible.
2. Confirm the live stream target in Settings → Notifications → Timetable changes (“Live stream target”). Default class-scoped watching is sufficient for the first run; repeat once with a different target you legitimately control if available.
3. Publish one harmless new substitution/change for that target. Record only the wall-clock time difference, not the change content.
4. Enable the feature fresh with already-published changes present; confirm no alert is produced just from the initial snapshot.

**Expected**

- A **“Timetable changed”** notification appears **within seconds** (typically well under a minute), not after the 15-minute poll window.
- Its text shows the number of changes and a short digest (for example `+`, `–`, `~` lines for subject/room or period label).
- The initial snapshot after enabling sets the baseline and produces **no** change alert.
- Changing the stream target restarts the watcher; the next snapshot after that restart is also silent until a genuinely new change is published.
- If Android notification permission is missing, no alert appears; this is a permission failure, not an SSE failure.

**Result**

- [ ] PASS
- [ ] FAIL
- [ ] N/A

Notes (text only): ______________________________________________

---

## 3. 15-minute WorkManager fallback

**Steps**

1. Keep “Timetable change notifications” **on** but pause the live stream using one of the code-supported conditions:
   - turn on Android Battery Saver, **or**
   - turn on “Only on Wi-Fi” and switch the device to mobile data.
2. Publish one harmless new change.
3. Wait for the periodic backstop. Note the time of the last successful check if known; otherwise allow up to roughly 15 minutes plus normal OS deferral.
4. Separately, with “Only on Wi-Fi” ON and mobile data active, publish a change and confirm the metered-connection skip; then reconnect to unmetered Wi-Fi and wait for the next window.
5. Restore normal conditions (Battery Saver OFF / unmetered Wi-Fi) and publish another change.

**Expected**

- With the stream paused but notifications enabled, the change is still announced by the worker on its periodic run — within about **15 minutes**, allowing for JobScheduler/Doze deferral.
- The worker is constrained to a connected network and is a no-op for a non-proxy user, a user without a class, or when notifications are disabled.
- With “Only on Wi-Fi” ON on a metered connection, both the stream and the worker skip the check: **no** alert from mobile data. On unmetered Wi-Fi, the next run (within ~15 minutes) announces the pending change.
- After conditions are restored, the service reconnects on its ~15-second retry loop and subsequent changes again arrive within seconds.
- Doze/standby may delay the worker past 15 minutes while the device is untouched; record the observed delay before calling this a failure.

**Result**

- [ ] PASS
- [ ] FAIL
- [ ] N/A

Notes (text only): ______________________________________________

---

## 4. Foreground, background, and process death

**Steps**

1. **Foreground:** repeat the live-delivery step with the app open.
2. **Background:** press Home (do not swipe the app away), publish a change, and watch the shade.
3. **Process death:** run `adb shell am force-stop com.deelabs.untisplus.debug` (or the release package ID) while the feature is enabled. Publish a change. Then reopen the app and publish a second change.
4. **Reboot (optional):** reboot with the feature enabled, do not open the app, publish a change; then open the app and publish another.

**Expected**

- Foreground and ordinary background both deliver within seconds. The foreground service is not tied to the Activity lifecycle.
- After force-stop, no live notification is expected because the service was killed. The scheduled periodic worker still runs when constraints allow, so the change is announced on the next ~15-minute window.
- Reopening the app re-arms both the worker and the service in `MainActivity`; the “Watching…” notification returns and the next change arrives within seconds.
- After reboot, do not assume the foreground service restarts itself from the background on Android 12+. Expect worker-based delivery until the app is opened; after opening, live delivery resumes.
- No crash, no ANR, and no duplicate alert across these transitions.

**Result**

- [ ] PASS
- [ ] FAIL
- [ ] N/A

Notes (text only): ______________________________________________

---

## 5. Deduplication

**Steps**

1. Publish exactly one change and count alerts in the shade.
2. Reopen the app, toggle the feature off and on, and wait through at least one worker run without publishing anything new.
3. Publish one change containing multiple affected periods (if possible).
4. Publish a change while SSE is live, then force-stop the app before the next worker run, reopen, and wait for that worker run.

**Expected**

- One logical change produces **one** “Timetable changed” alert. Notifications reuse ID 5401, so a later alert replaces the earlier one rather than stacking indefinitely.
- Reopening the app, toggling settings, or an extra worker run with no new version produces **no** repeat alert.
- Multiple rows in one event produce a **single** alert with the correct count and digest lines.
- SSE and the worker share the persisted version marker; a change already announced by SSE is not re-announced by the later worker (and vice versa).

**Result**

- [ ] PASS
- [ ] FAIL
- [ ] N/A

Notes (text only): ______________________________________________

---

## 6. Profile switch and logout cleanup

**Steps**

1. With profile A (proxy, feature enabled) active and the watcher running, switch to profile B that is non-proxy or has the feature disabled.
2. Observe for about a minute without publishing anything. Then publish a change for profile A's class and confirm no alert.
3. Switch back to profile A and open the app; publish a change for A and confirm delivery resumes.
4. Delete/log out the last saved profile through profile management so no user remains. Publish a change and observe.
5. Add/sign in a profile again and confirm the feature can be enabled fresh.

**Expected**

- After switching away, no further alerts for the old profile's class. The watcher re-evaluates the active user on its loop and stops when there is no user, a non-proxy user, or a disabled setting; it may take up to the reconnect interval (~15 s, plus any in-flight network wait). The worker reads the active profile on each run and no-ops for a non-proxy/disabled profile.
- If the old “Watching…” notification or old-class alerts persist well beyond that window, record **FAIL**.
- Settings are per profile: enabling for A must not silently notify for B, and switching back must re-arm only after the app is opened (Android 12+ background start restrictions).
- After deleting the last profile, the service self-stops on its next check and the worker no-ops: no change alerts, no stale session reused for the next user. Profile switch and delete clear the proxy cookie store in `UserRepository`.
- Do **not** verify cleanup by exporting cookies/tokens; verify by behavior only.

**Result**

- [ ] PASS
- [ ] FAIL
- [ ] N/A

Notes (text only): ______________________________________________

---

## 7. Battery optimization and network-policy behavior

**Steps**

1. Battery Saver **ON**, feature enabled: publish a change; then turn Battery Saver **OFF** and publish another.
2. “Only on Wi-Fi” **ON** with mobile data: publish a change; then reconnect to unmetered Wi-Fi and publish another.
3. Optional: leave the screen off and untouched long enough for Doze, publish a change, note the delay, then interact with the device and note when delivery resumes.
4. Optional: compare behavior with and without “Ignore battery optimizations” for this app. Do not permanently change the user's settings without permission.

**Expected**

- Battery Saver pauses the SSE socket; delivery falls back to the worker window. Turning it off resumes the stream within ~15 seconds (reconnect delay).
- “Only on Wi-Fi” pauses both the stream and the worker on metered networks; unmetered Wi-Fi restores both paths.
- Under Doze, the worker may be deferred to a maintenance window; a longer delay is expected OS behavior, not automatically a defect. Delivery should resume after the device becomes active.
- The feature should not require the user to disable all battery optimizations permanently just to receive notifications eventually; strict real-time delivery under Doze is not guaranteed.

**Result**

- [ ] PASS
- [ ] FAIL
- [ ] N/A

Notes (text only): ______________________________________________

---

## Attributing the delivery path (only if logs are available)

This section is optional. If logs were not captured, mark it N/A and rely on timing plus the visible service notification.

**Safe capture window (user-run, short duration, then stop):**

```sh
adb logcat -v brief TimetableChangeService:V TimetableChangeWorker:V TimetableChangeNotifs:V *:S
```

Stop the capture immediately after the test. Do not save unfiltered logs, do not print request/response bodies, and redact any token, cookie, full URL, school name, or profile identifier before sharing notes.

**What the tags mean in this implementation**

- `TimetableChangeService` — only logs on an SSE drop/reconnect (`"SSE stream dropped, reconnecting"`). Seeing it means the service tried to stream and is retrying; it is **not** proof that an alert was posted, and repeated lines mean an unstable stream, not necessarily a broken feature.
- `TimetableChangeWorker` — only logs on a failed poll (`"Timetable change poll failed"`, followed by retry). Seeing it proves the worker ran the failure path. A successful poll is silent, so absence of this line does **not** prove SSE was used.
- `TimetableChangeNotifs` — only logs a missing notification permission (`"Notification permission missing"`), which explains a missing alert.

**Distinguishing the two paths**

- **Likely SSE:** the ongoing “Watching for timetable changes” notification is visible, Battery Saver is off, “Only on Wi-Fi” is not blocking, the app was not force-stopped, and the alert arrived within seconds. There is no `TimetableChangeWorker` failure line in the same window.
- **Likely worker fallback:** the alert arrived near a ~15-minute boundary, or the stream was paused/blocked (Battery Saver, metered Wi-Fi-only, force-stop, or app not yet reopened after reboot), or a `TimetableChangeWorker` failure/retry line appears near the window.
- **Ambiguous:** only a `TimetableChangeService` reconnect line with no worker line, or no logs at all. Record N/A for attribution; judge the feature by the expected delivery behavior above.
- WorkManager job state can be inspected with OS tools such as `adb shell dumpsys jobscheduler`, but there is no reliable per-change success marker in the app's logs. Do not claim a delivery path from a job listing alone.

**Result**

- [ ] PASS (attribution was possible and consistent with observations)
- [ ] FAIL (a notification was attributed to a path that contradicts the observed conditions)
- [ ] N/A (no logs; behavior-only verification)

Notes (redacted, text only): ______________________________________

---

## Final summary (fill in after running)

- Checks run: ______ / 8 sections (7 verification sections + optional log attribution)
- PASS: ______  FAIL: ______  N/A: ______
- Open defects (short, text-only descriptions, no secrets): ______________________

No screenshots or screen captures were taken or requested for this checklist.
