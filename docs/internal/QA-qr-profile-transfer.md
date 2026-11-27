# QA checklist — QR profile transfer, UntisPlus → UntisPlus

Status: **NOT RUN by subagents.** This is a user-run device checklist only.

- No subagent generated, scanned, or decoded a QR code; installed or launched the app; logged in; used an account, password, app key, cookie, or credential; or contacted a live school/proxy endpoint.
- No build, emulator, device, network capture, or execution step was performed for this checklist.
- The only pre-filled result below is the earlier **user-confirmed** official-app scan pass. Subagents did not repeat it.
- Do **not** put passwords, app keys, raw QR payloads, session cookies, tokens, or full endpoint URLs in this document, chat, issues, or commit messages. Use the device UI to compare values visually.
- No screenshots or screen captures are part of this checklist. Only add them if the user explicitly asks later, and redact the key/payload first.

Reference: `docs/internal/PLAN-post-agent-next-phase.md`, item “QR profile transfer, UntisPlus → UntisPlus” in **Still needs testing**. Source of truth for behavior is the app code in this worktree.

## Expected behavior (what the device test should prove)

- On install **A**, profile management exposes a share/QR action. Tapping it first shows a warning: “Show login QR code?” / “This QR contains your app login key…”. The QR is not visible until **Show QR code** is confirmed.
- A standard profile produces an official-style payload, shape only:

  ```text
  untis://setschool?url=<school-server-host>&school=<login-name>&user=<user>&key=<redacted>&schoolNumber=<school-id>
  ```

  UntisPlus-only extras (`anonymous`, `apiUrl`, `skipAppSecret`) are appended only for UntisPlus-specific cases; when present, the QR screen shows the UntisPlus-specific-options note.
- On install **B**, `Scan QR code` accepts that payload without an “Invalid QR code” dialog, resolves the same school and user, treats `key` as the app shared secret (no password re-entry), and opens a working timetable.
- Known, documented caveat: an official-format QR has no `apiUrl`, so **B defaults to the UntisPlus proxy**. This is expected for a profile that already used the default proxy. A profile with a custom host carries `apiUrl` and imports as **Custom**; a beta host may import as **Custom** with the same beta host. If A was explicitly set to **Official/direct**, B becoming proxy is a documented limitation, not a silent regression — record it as a decision/fail if strict OFFICIAL parity was required.
- The imported profile must be usable, not a duplicate or broken extra entry. A fresh proxy session is kept for the import; normal profile switching clears the proxy session so profiles do not share stale cookies. Cache is purged when the endpoint changes; otherwise a cached timetable may appear briefly before the fresh load.

## Preconditions (user, later, with their own devices/account)

| # | Setup step / expected result | Pass | Fail | Notes (no secrets) |
|---|---|---|---|---|
| 0.1 | Two UntisPlus installs with the QR export/import code under test: **A** = source, **B** = destination. Both have camera/QR scanning available. | ☐ | ☐ | Use the user's own builds/devices; subagents did not install anything. |
| 0.2 | On B, either start with no equivalent profile, or plan to scan into an existing profile's edit screen when testing “update in place”. | ☐ | ☐ | Do not record profile names or keys here. |
| 0.3 | Note the local profile count on B before the test, plus whether A's profile is standard proxy, custom/beta, anonymous, or direct Official. Keep the note locally; do not paste it into this file. | ☐ | ☐ | Needed to judge duplicate/provider expectations. |
| 0.4 | The user accepts that normal app login during this test will contact their own school/proxy. No credential or endpoint detail is shared with subagents. | ☐ | ☐ | Subagents performed none of this. |

## 1. Generate QR on install A

| # | Step / expected result | Pass | Fail | Notes (no secrets) |
|---|---|---|---|---|
| 1.1 | Open profile management, choose a profile, tap the share/QR icon (“Login QR code”). | ☐ | ☐ | |
| 1.2 | Warning dialog appears **before** any QR is visible, with the app-key/timed-data caution. | ☐ | ☐ | |
| 1.3 | Dismiss/cancel first: no QR is revealed and no profile state changes. | ☐ | ☐ | Optional cancellation check. |
| 1.4 | Confirm **Show QR code**. A QR renders; no “Could not generate the QR code.” error. | ☐ | ☐ | Keep the screen on; do not save or capture it. |
| 1.5 | Standard profile: no UntisPlus-specific-options note appears. Custom/beta/anonymous profile: the note appears and `apiUrl`/`anonymous` is included as expected. | ☐ | ☐ | Compare only the note/presence, not the raw payload. |

## 2. Import on install B

| # | Step / expected result | Pass | Fail | Notes (no secrets) |
|---|---|---|---|---|
| 2.1 | On B, open add-profile → **Scan QR code** and scan A's on-screen QR with the embedded scanner. | ☐ | ☐ | No photo/screenshot workflow. |
| 2.2 | No “Invalid QR code” dialog. Login proceeds and reaches the timetable. | ☐ | ☐ | A password prompt here is a fail for official-format `key` import. |
| 2.3 | The app shows the current/expected week and a usable timetable, not an empty or broken profile screen. | ☐ | ☐ | Compare visually; do not copy lesson data into this doc. |
| 2.4 | If scanning into an existing profile's edit screen, the same profile is updated in place rather than a second profile appearing. | ☐ | ☐ | Run only if testing the edit-existing path. |

## 3. Same school / user / proxy / provider behavior

Compare A and B on-device; compare labels and behavior, not the secret value.

| # | Check / expected result | Pass | Fail | Notes (no secrets) |
|---|---|---|---|---|
| 3.1 | School login name and username shown in B's profile/edit UI match A. School number/host resolves to the same school; no “invalid school” or ambiguous-school-search error. | ☐ | ☐ | Do not transcribe the app key. |
| 3.2 | `key` is accepted as the app shared secret: no password re-entry and no credential error from the QR path. | ☐ | ☐ | Official-format codes infer `skipAppSecret` from `url`. |
| 3.3 | Standard proxy source: B remains on the UntisPlus/default proxy path and loads the same timetable behavior as A. | ☐ | ☐ | Expected default for a QR without `apiUrl`. |
| 3.4 | Custom source: B uses the Custom provider with the same custom host behavior and loads the same timetable. Beta source: same beta host is preserved; a Custom label on B is acceptable if the effective host matches. | ☐ | ☐ | Extras note on A should correspond to `apiUrl` on B. |
| 3.5 | Direct-Official source: record the result against the documented proxy-default caveat. Strict OFFICIAL parity is **not** provided by the five-param QR; treat as fail/blocked if that parity was required. | ☐ | ☐ | N/A if no direct-Official profile was tested. |
| 3.6 | Anonymous profile (if tested): anonymous state and demo/anonymous timetable behavior match A. | ☐ | ☐ | N/A if not applicable. |
| 3.7 | Profile switching on B keeps each profile's own endpoint/provider behavior; scanning the QR did not silently switch another profile's provider. | ☐ | ☐ | |

## 4. Official-format export regression — already passed by user

- Earlier user test: the rebuilt QR was scanned by the **official Untis app** and accepted; the prior “invalid” result was resolved by matching the five official parameters (`url`, `school`, `user`, `key`, `schoolNumber`).
- Subagents did **not** repeat this test. Do not mark it as freshly verified by this checklist.

| # | Check / expected result | Pass | Fail | Notes (no secrets) |
|---|---|---|---|---|
| 4.1 | Prior user-confirmed result is recorded as **PASS (user, earlier)**, not as subagent evidence. | ☐ | ☐ | Tick Pass only to acknowledge the recorded prior result. |
| 4.2 | Optional re-run by the user with their own official Untis app, only if code changed since: standard QR is accepted, no “invalid” message. | ☐ | ☐ | N/A if not re-run. Do not share the decoded key. |

## 5. No duplicate or broken profile after import

| # | Step / expected result | Pass | Fail | Notes (no secrets) |
|---|---|---|---|---|
| 5.1 | After one Add-profile import into a clean B, profile count increases by exactly **1**; the new entry is the transferred profile. | ☐ | ☐ | Compare with the local pre-test count. |
| 5.2 | No second entry for the same school/user is created by the single import, and no blank/placeholder profile appears. | ☐ | ☐ | An intentional second import via **Add** creates another profile; clean that up rather than calling it a bug. |
| 5.3 | The imported profile opens timetable after a full app restart and remains selectable in profile management. | ☐ | ☐ | |
| 5.4 | Original profile on A is unchanged; export did not edit or delete it. | ☐ | ☐ | |
| 5.5 | No QR-format error, crash, empty master data, or broken profile screen during import. | ☐ | ☐ | |

## 6. Stale cache / cookies / session behavior

| # | Step / expected result | Pass | Fail | Notes (no secrets) |
|---|---|---|---|---|
| 6.1 | If B previously showed “Your session has expired. Please log in again.” for the target profile, importing a fresh QR into that profile clears the banner and loads the timetable with the fresh session. | ☐ | ☐ | Do not force expiry via password-reset/abuse; use only normal logout or natural expiry with the user's own account. N/A if no stale session existed. |
| 6.2 | First timetable load after import does not reuse a stale session; no expired-session banner on the imported profile. | ☐ | ☐ | The import keeps the freshly minted proxy session. |
| 6.3 | Timetable content is current for the expected week/school. A cached timetable may appear first when the endpoint is unchanged, but refresh/reopen resolves to current data. | ☐ | ☐ | Cache is cleared by the app when the endpoint changes. |
| 6.4 | Switching to another profile on B and back does not bleed timetable/session state between profiles; each profile loads its own data. | ☐ | ☐ | Normal switching clears proxy cookies; do not inspect or export cookie values. |
| 6.5 | Deleting the imported profile clears its proxy session; a subsequent profile does not inherit it. | ☐ | ☐ | Covered by existing profile-delete behavior; check only if cleanup was performed. |

## Rollback / cleanup

- Cancel the QR dialog at any point before confirmation: no profile is created or changed.
- Remove an imported test profile from profile management → delete icon. This also drops the proxy session for that profile; no key is written anywhere.
- If an existing profile was updated by scanning in edit mode, restore it by rescanning the source QR from A or by the user's normal login with their own credentials. Do not put those credentials in this file.
- If stale cache persists after import, first reopen/pull-to-refresh; if still wrong, delete and re-import the test profile. Clearing app data is a last resort and also removes local settings/widget configuration.
- Delete any temporary local notes containing the key/payload after the test. Do not attach logcat, network traces, or screenshots unless explicitly requested; redact secrets if any artifact is shared later.
- No repo files, git commits, pushes, PRs, metadata, or release state were changed by this subagent.

## Result summary

| Area | Pass | Fail | N/A | Notes |
|---|---|---|---|---|
| Warning + QR generation on A | ☐ | ☐ | ☐ | |
| Import on B | ☐ | ☐ | ☐ | |
| Same school/user/proxy/provider behavior | ☐ | ☐ | ☐ | Include caveats from §3.5. |
| Official-format export still works (already passed by user, not re-run) | ☐ | ☐ | ☐ | §4 |
| No duplicate/broken profile | ☐ | ☐ | ☐ | |
| Stale cache/cookies/session | ☐ | ☐ | ☐ | |
| Cleanup completed | ☐ | ☐ | ☐ | |

Overall: ☐ **Pass** ☐ **Fail** ☐ **Blocked — awaits user-run device test**

Subagent result: **NOT RUN / no account, credentials, live endpoint, or execution used.**
