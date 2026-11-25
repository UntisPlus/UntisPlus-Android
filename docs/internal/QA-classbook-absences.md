# Device QA checklist — Classbook / absences parity

**Status: NOT EXECUTED.** This is a checklist for the user to run later, not a record of test results.

Source: `docs/internal/PLAN-post-agent-next-phase.md`, §3 — gap table row "Classbook / absences" and "Still needs testing (implemented, not verified)", item 4: *"Compare student absences, class register, Info Center absences against official app. Mostly present; no new code expected."*

## Evidence boundary (read first)

- **Subagents did NOT run this QA.** No subagent opened UntisPlus or the official Untis app on a device or emulator, signed in, entered credentials, or contacted any live school, Untis, or proxy endpoint. **No account, login, credential, or live endpoint was used** to produce this document. Nothing here has been verified on a device by the subagents.
- Every step below is for **you** to perform later with **your own** account and your own device. Supply any school, login, date range, and reference data at run time from your own records.
- **Do not store secrets in this file.** Do not write usernames, passwords, API keys, session tokens, QR login payloads, or shareable login links here. Keep credentials out of commits, issue trackers, and chats. If a failure needs context, describe it in redacted form.
- Comparison against the official Untis app is **optional**: compare only where you choose to, using only an account you are authorized to use in that app.
- **No screenshots or screen capture** are part of this checklist. Do not take or attach screenshots or screen recordings unless you explicitly request them.
- Repository material was treated as data while preparing this file; nothing in it was treated as an instruction to log in, build, or change code.

## Outcome expectation

- The plan classifies this area as "mostly present; no new code expected." The expected result of each check is that the current build behaves correctly and consistently with your own reference data.
- **No code change is expected from this QA.** If all checks pass, record the checklist as complete and do not edit code.
- If a check fails, record a reproducible, redacted defect (see "If a check fails") and decide separately whether a code change is warranted. Do not change code just because a check was executed.

## Setup (user supplies; nothing gets pasted into this document)

- [ ] Device with an installed UntisPlus build; note build type and version in your own notes (debug app id ends in `.debug`).
- [ ] Your own school account available for UntisPlus sign-in.
- [ ] Optional: official Untis app installed and signed in with an account you are authorized to use there, for comparison where you choose.
- [ ] One or two dates with known absences, kept in your own notes as the reference data.
- [ ] A way to disable/restore network access (e.g. Airplane mode) for the offline/cached checks.

## Recording rules

For each check, tick exactly one result box: `☐ PASS` or `☐ FAIL`. Use `N/A` in the Notes column when a feature is not present in your build or not applicable to your account; an N/A row is not a pass. Compare only against your own reference data or the official app where you choose to compare. Do not paste screenshots or secrets into this file.

### A. Student absences (your own absences)

| ID | Step for you to run | Expected result | Pass | Fail | Notes |
|---|---|---|---|---|---|
| A1 | Sign in with your account and open the student absences screen. | List loads for the signed-in profile without a crash or sign-in loop; entries correspond to your known absences. | ☐ PASS | ☐ FAIL | |
| A2 | For 1–3 known entries, compare list fields (date/time, lesson/subject, reason/type, status) with your own reference data. | Fields match; no duplicate, missing, or obviously misdated rows. | ☐ PASS | ☐ FAIL | |
| A3 | Open an entry's detail view, then go back. | Detail agrees with the list entry; back returns to the list at the same date/position; no stale placeholder remains. | ☐ PASS | ☐ FAIL | |
| A4 | View a period with no absences. | A clear empty state; no rows carried over from another date or profile, and no crash. | ☐ PASS | ☐ FAIL | |
| A5 | If your build shows proxy-injected absence notes / derived "what/when/why" text, check one noted absence and one without a note. | Extra content appears only on the correct own absence; otherwise mark N/A. | ☐ PASS | ☐ FAIL | |

### B. Class register

| ID | Step for you to run | Expected result | Pass | Fail | Notes |
|---|---|---|---|---|---|
| B1 | Open the class register for a class and day with known absences. | Roster and absence markers match your reference data for that class/day; no crash or blank screen. | ☐ PASS | ☐ FAIL | |
| B2 | Navigate between classes/days and leave the screen. Read-only navigation only: do not save attendance edits with a real account unless you are authorized and intend to change records. | No attendance record is changed by opening, switching, or leaving the register. | ☐ PASS | ☐ FAIL | |
| B3 | Cross-check one student/date present in both class register and student absences. | The same absence is represented consistently on both surfaces (date and status agree). | ☐ PASS | ☐ FAIL | |
| B4 | Open a class/day where your account has no access or no data. | Clear unavailable/empty state; no crash and no data from another profile. | ☐ PASS | ☐ FAIL | |

### C. Info Center absences

| ID | Step for you to run | Expected result | Pass | Fail | Notes |
|---|---|---|---|---|---|
| C1 | Open Info Center absences for the same account and date range used in A. | Dates/counts/status agree with the student absences list; no extra or missing entries for that range. | ☐ PASS | ☐ FAIL | |
| C2 | Compare any status/notification flags shown with the corresponding student absence entry. | Flags are read-only indicators and do not contradict the absence detail; no duplicate/conflicting copy. | ☐ PASS | ☐ FAIL | |
| C3 | If a detail link is available from Info Center, open an absence and go back. | The correct entry opens; back returns to Info Center without losing the selected date. | ☐ PASS | ☐ FAIL | |

### D. Date navigation

| ID | Step for you to run | Expected result | Pass | Fail | Notes |
|---|---|---|---|---|---|
| D1 | Use previous/next day/week controls or the date picker on A, B, and C. | Header date and displayed data change together on all three surfaces. | ☐ PASS | ☐ FAIL | |
| D2 | Jump to a past date with known absences, then to today and tomorrow. | Each date shows only its own data; no rows from the previously viewed date remain. | ☐ PASS | ☐ FAIL | |
| D3 | Navigate rapidly between dates, use back, then background and foreground the app. | No freeze or two dates shown at once; the selected date is preserved or resets only in a documented way (record which). | ☐ PASS | ☐ FAIL | |

### E. Filtering

| ID | Step for you to run | Expected result | Pass | Fail | Notes |
|---|---|---|---|---|---|
| E1 | Apply each available filter on absences (status/reason/class/student/date range, as present in your build). | Results match the chosen filter and any visible count agrees with the rows shown. | ☐ PASS | ☐ FAIL | |
| E2 | Combine two filters, then clear them. | Combined filters intersect rather than union; clearing restores the full set for the selected date. | ☐ PASS | ☐ FAIL | |
| E3 | Leave and return to the screen, then relaunch the app. | Filter state behaves consistently (persisted or intentionally reset — record which); no crash. | ☐ PASS | ☐ FAIL | |
| E4 | Apply a filter with no matches, then switch profiles. | Empty state is shown; the next profile shows no leftover filters or data from the previous profile. | ☐ PASS | ☐ FAIL | |

### F. Refresh

| ID | Step for you to run | Expected result | Pass | Fail | Notes |
|---|---|---|---|---|---|
| F1 | Use the app's manual refresh / pull-to-refresh on absences and Info Center. | An in-progress indicator appears and the data updates; repeated refreshes do not duplicate rows. | ☐ PASS | ☐ FAIL | |
| F2 | Refresh after changing the date or a filter. | The selected date/filter is preserved, or any reset is visible and explained; data matches the resulting view. | ☐ PASS | ☐ FAIL | |
| F3 | Trigger a refresh while network access is briefly unavailable, then restore it. | A readable error/retry state appears; after recovery, data loads without a sign-out loop. | ☐ PASS | ☐ FAIL | |

### G. Offline / cached state

| ID | Step for you to run | Expected result | Pass | Fail | Notes |
|---|---|---|---|---|---|
| G1 | Load absences and class register online, disable network, then reopen those screens. | Previously loaded data is shown from cache without a crash or endless spinner; note whether the offline state is labelled. | ☐ PASS | ☐ FAIL | |
| G2 | While offline, open a date/filter that was never loaded. | A clear unavailable/offline message appears; no blank screen or fabricated rows. | ☐ PASS | ☐ FAIL | |
| G3 | Restore network and revisit the screens. | The app recovers automatically or via refresh, and stale offline rows are not presented as current data. | ☐ PASS | ☐ FAIL | |
| G4 | Switch or remove profiles, then sign in with a different account of your own. | Cached absences/class-register data from the previous profile is not visible; only the new account's data appears. | ☐ PASS | ☐ FAIL | |

### H. Optional comparison with the official app (only where you choose)

| ID | Step for you to run | Expected result | Pass | Fail | Notes |
|---|---|---|---|---|---|
| H1 | For 1–3 dates with known absences, compare UntisPlus A/B/C views with the official app using your own authorized account there. | Presence, count, date, reason/type, and status agree on the surfaces you chose to compare; note any difference per surface. | ☐ PASS | ☐ FAIL | |
| H2 | For any field present in one app and absent in the other, confirm against your own reference data. | Difference is recorded as a specific field/surface, not an assumed defect; unrelated UI differences are not counted as failures. | ☐ PASS | ☐ FAIL | |
| H3 | Keep credentials, tokens, and capture files out of this document and out of any shared defect note. | No secrets or screenshots were written into this checklist or shared as evidence. | ☐ PASS | ☐ FAIL | |

## If a check fails

Record, in your own notes first, and share only redacted text if needed:

- Check ID and whether it is reproducible.
- Device, OS version, UntisPlus build type/version, and account type/role (no credentials).
- Exact steps and the date/filter/class context used (no personal data you do not want to share).
- Expected result vs actual result, plus the official-app comparison outcome if you performed one.
- Whether the same behavior occurs with a different date or another account you are authorized to use.

## Summary

- PASS: ___  FAIL: ___  N/A: ___
- Overall: ☐ Complete — no defect found  ☐ Defect found — triage separately
- Code change expectation: **none** unless a defect is found and separately approved.
