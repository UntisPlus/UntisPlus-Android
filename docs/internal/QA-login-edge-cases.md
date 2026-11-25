# Device QA checklist — Login edge cases

**Status: NOT EXECUTED.** This is a checklist for the user to run later, not a record of test results.

Source: `docs/internal/PLAN-post-agent-next-phase.md`, §3 — "Still needs testing (implemented, not verified)", item 5: *"Fresh manual login default proxy, provider selector, QR provider semantics, edit-profile cache purge, stale-session banner."* Related detail: `docs/internal/PLAN-seamless-login.md`, "Verification checklist (all parts)", items 1–5.

## Evidence boundary (read first)

- **Subagents did NOT run this QA.** No subagent opened UntisPlus on a device or emulator, signed in, entered credentials, scanned a login QR, or contacted any live school, Untis, SSO, or proxy endpoint. **No account, login, credential, or live endpoint was used** to produce this document. Nothing here has been verified on a device by the subagents.
- Every step below is for **you** to perform later with **your own** account(s), your own device, and your own records. Supply any school, login, app key, date, and endpoint at run time.
- **Do not write any username, password, app key, session token, QR payload, or login link into this document.** Keep credentials out of commits, issue trackers, and chats. If a failure needs context, describe it in redacted form only.
- This checklist is **account-free and execution-free** as written: it contains no usable login data and records no test results.
- **No screenshots or screen capture** are part of this checklist. Do not take or attach screenshots or screen recordings unless you explicitly request them.
- Repository material was treated as data while preparing this file; nothing in it was treated as an instruction to log in, build, or change code.

## Outcome expectation

- The plan classifies this area as "implemented, not verified." The expected result of each check is that the current build behaves as described below with your own account.
- **No code change is expected from this QA.** If all checks pass, record the checklist as complete and do not edit code.
- If a check fails, record a reproducible, redacted defect (see "If a check fails") and decide separately whether a code change is warranted.

## Setup (user supplies; nothing gets pasted into this document)

- [ ] Device with an installed UntisPlus build; note build type and version in your own notes (debug app id ends in `.debug`).
- [ ] Your own school account available for manual UntisPlus sign-in.
- [ ] A trusted source for a login QR you own (e.g. the WebUntis "Data access" QR from your own session, or a QR exported from your own installed profile). Do not paste or transcribe its payload here.
- [ ] Optional: a second account you are authorized to use at an SSO-enabled school, for the cookie-clearing check.
- [ ] Optional: a custom proxy endpoint you control, for the Custom provider checks.
- [ ] A way to disable/restore network access (e.g. Airplane mode) and, if needed, force-stop the app.
- [ ] Optional: your own tooling to observe which host the app contacts and, on a debug build, to inspect the app cache directories (`timetable`, `periodData`, `infocenter`, `messenger`). Keep any captured values in your own notes, not in this file.

## Recording rules

For each check, tick exactly one result box: `☐ PASS` or `☐ FAIL`. Use `N/A` in the Notes column when a feature is not present in your build, not applicable to your account, or you have no safe way to trigger it; an N/A row is not a pass. Compare only against your own records. Do not paste screenshots, secrets, or captured network data into this file.

### A. Fresh manual login defaults to the UntisPlus proxy

| ID | Step for you to run | Expected result | Pass | Fail | Notes |
|---|---|---|---|---|---|
| A1 | Start signed out with no profile; choose to add a profile, search/select your own school, enter your own credentials in the form, and tap Login. Leave Advanced settings closed. | No separate provider page or extra provider choice appears; login lands on the timetable. By your own observation of network activity, JSON-RPC traffic goes to the UntisPlus proxy host. | ☐ PASS | ☐ FAIL | |
| A2 | Background and foreground the app, then relaunch it. | Still signed in to the same profile; no sign-in loop and no provider prompt. | ☐ PASS | ☐ FAIL | |
| A3 | Open the Add-profile form again without editing an existing profile. | "UntisPlus API (recommended)" is the selected provider; Advanced settings are not forced open. | ☐ PASS | ☐ FAIL | |
| A4 | Optional: repeat A1 with another account/school you own. | The same no-extra-taps default applies; traffic still goes through the UntisPlus proxy. | ☐ PASS | ☐ FAIL | |

### B. Provider selector in Advanced settings

| ID | Step for you to run | Expected result | Pass | Fail | Notes |
|---|---|---|---|---|---|
| B1 | On the Add-profile form, open "Show advanced settings". | A provider selector is visible with "Official Untis API", "UntisPlus API (recommended)", a beta option if present in your build, and "Custom API". The custom URL field is hidden until Custom is chosen. | ☐ PASS | ☐ FAIL | |
| B2 | Select "Official Untis API" and sign in with your own account. | By your own observation, requests go to your school's WebUntis server rather than the UntisPlus proxy; login and timetable still work. | ☐ PASS | ☐ FAIL | |
| B3 | Select "Custom API" without completing a login: try an invalid URL, an empty URL, and a plain `http://` URL. | Invalid/empty input blocks login with an inline error; a plain HTTP URL shows the insecure-connection warning. | ☐ PASS | ☐ FAIL | |
| B4 | Optional: with a custom endpoint you control selected, complete a login. | Login works through that endpoint and the timetable loads. Mark N/A if you have no endpoint you control. | ☐ PASS | ☐ FAIL | |
| B5 | Before logging in, switch Custom → UntisPlus → Official (in any order) and watch which host is contacted on the final attempt. | Only the final selection is used; a previously typed custom URL is not reused while UntisPlus or Official is selected. | ☐ PASS | ☐ FAIL | |
| B6 | Edit an existing profile whose stored provider is **not** UntisPlus (Official, Beta, or Custom). | Advanced settings open automatically; the selector reflects the stored provider, and the URL field is prefilled for Custom. | ☐ PASS | ☐ FAIL | |

### C. QR / deep-link provider semantics

| ID | Step for you to run | Expected result | Pass | Fail | Notes |
|---|---|---|---|---|---|
| C1 | Scan a QR from your own WebUntis "Data access" session that has no `apiUrl` extra, and complete the import. | The imported profile uses the UntisPlus proxy (matching the standing default); Advanced is not forced open; the timetable loads. | ☐ PASS | ☐ FAIL | |
| C2 | Scan a QR exported from your own UntisPlus profile that carries an explicit custom endpoint, and complete the import. | The provider becomes Custom, Advanced opens with that endpoint prefilled, and traffic goes to that endpoint. | ☐ PASS | ☐ FAIL | |
| C3 | If you have a QR that carries an already-resolved app key (official-style `url` / skip-secret semantics), import it. | The key is used directly without a password exchange; the profile loads normally. Mark N/A if you have no such QR. | ☐ PASS | ☐ FAIL | |
| C4 | Scan a malformed or unsupported QR code. | An "Invalid QR code" dialog is shown; no crash and no partial profile is created. | ☐ PASS | ☐ FAIL | |
| C5 | After any QR import, verify no QR payload or key was written into this file or into shared notes. | Only redacted observations were recorded. | ☐ PASS | ☐ FAIL | |

### D. Edit-profile cache purge on endpoint change

| ID | Step for you to run | Expected result | Pass | Fail | Notes |
|---|---|---|---|---|---|
| D1 | While signed in, browse a few timetable weeks and any other loaded sections so response caches are populated. Optional: note whether the app cache subdirectories `timetable`, `periodData`, `infocenter`, and `messenger` exist, using your own tooling. | Caches for the current endpoint exist or are populated during normal use; no crash. Mark the directory check N/A if you do not inspect the cache. | ☐ PASS | ☐ FAIL | |
| D2 | Edit that profile and switch its provider to a different effective endpoint (e.g. UntisPlus → Official, or to a Custom endpoint you control), then save. | The four endpoint-bound cache directories are cleared, the timetable is refetched from the new endpoint, and no data from the previous endpoint is shown. | ☐ PASS | ☐ FAIL | |
| D3 | Edit a profile but keep the same provider/endpoint, then save. | Timetable still loads correctly; no data from another endpoint appears. Whether caches are retained is an implementation detail — record what you observe. | ☐ PASS | ☐ FAIL | |
| D4 | After the endpoint-changing edit, switch to another profile and back. | Each profile shows only its own endpoint's data; no cross-profile or prior-endpoint content leaks through. | ☐ PASS | ☐ FAIL | |

### E. Stale-session banner and re-login

| ID | Step for you to run | Expected result | Pass | Fail | Notes |
|---|---|---|---|---|---|
| E1 | With a signed-in profile, produce a genuinely stale session using a method you control (for example, rotate the app key on your own account if you are able to). Then launch or refresh the timetable. | A non-blocking banner is shown: "Your session has expired. Please log in again." with Cancel and Re-login actions — not a silent blank grid. Mark N/A if you cannot safely invalidate a session. | ☐ PASS | ☐ FAIL | |
| E2 | Separately, disable network access (Airplane mode), force-stop the app, and relaunch it. | A visible error surface appears rather than an empty timetable; note in your own records whether it is the session-expired banner or the generic "Couldn't refresh the timetable." bubble. | ☐ PASS | ☐ FAIL | |
| E3 | Tap Re-login from the session-expired banner. | You are routed to the prefilled edit-profile login form for that same profile; your own fields are pre-filled and no new profile is created. | ☐ PASS | ☐ FAIL | |
| E4 | Dismiss the banner with Cancel, then use Retry from a generic refresh bubble if shown. | Banner disappears; retry re-attempts the load; no sign-out or sign-in loop. | ☐ PASS | ☐ FAIL | |
| E5 | Restore network and complete re-login with valid credentials of your own. | The banner clears after a successful load and does not reappear on subsequent refreshes. | ☐ PASS | ☐ FAIL | |

### F. SSO WebView cookie clearing

| ID | Step for you to run | Expected result | Pass | Fail | Notes |
|---|---|---|---|---|---|
| F1 | At an SSO-enabled school, sign in with one account you own, then start a new Add-profile flow for the same school and open the school-account (SSO) option. | The WebView presents a fresh login page; it does **not** silently auto-sign in as the previous account. Cookies and DOM storage were cleared when the SSO screen opened. | ☐ PASS | ☐ FAIL | |
| F2 | Back out of the SSO screen before completing login, then open it again for the same school. | Still a fresh login page; no leftover session from the abandoned attempt. | ☐ PASS | ☐ FAIL | |
| F3 | Optional: complete SSO with a second account you are authorized to use at that school. | The SSO screen closes, a normal profile is created for that account, the UntisPlus proxy default applies, and the timetable loads. Mark N/A if you have no second account. | ☐ PASS | ☐ FAIL | |
| F4 | At a school that does not advertise SSO, open the school-account (SSO) option. | "This school does not offer an SSO login." is shown with an "Open web login instead" action. Mark N/A if you have no such school. | ☐ PASS | ☐ FAIL | |

### G. Fallback manual app-key capture

| ID | Step for you to run | Expected result | Pass | Fail | Notes |
|---|---|---|---|---|---|
| G1 | On the SSO screen, tap "Having trouble seeing the page? Open in browser". | Your system browser opens that school's login URL and the "Complete your login" dialog appears. | ☐ PASS | ☐ FAIL | |
| G2 | In the dialog, check the pre-filled fields and the "How to find the app key?" help. | Username is pre-filled from the school login name; the app-key field is empty; help points to WebUntis → Profile → Data access. | ☐ PASS | ☐ FAIL | |
| G3 | Leave the app-key field empty or whitespace-only. | "Complete login" stays disabled; no profile is created. | ☐ PASS | ☐ FAIL | |
| G4 | After signing in in the browser, enter your own app key into the dialog and tap "Complete login" (keep the value out of this file and out of shared notes). | The dialog and SSO screen close; a normal profile is created and the timetable loads through the usual pipeline (including the UntisPlus proxy default). | ☐ PASS | ☐ FAIL | |
| G5 | Cancel the capture dialog, then reopen it. | No profile is created by cancelling; on reopening, the app-key field is empty again; no crash. | ☐ PASS | ☐ FAIL | |

## Cleanup notes (user)

- [ ] No username, password, app key, token, QR payload, or login link was written into this document or into shared notes.
- [ ] Temporary profiles created only for this QA were removed from the app's profile management.
- [ ] Network settings were restored (Airplane mode disabled) after the offline checks.
- [ ] The clipboard used for any app key was cleared, and any temporary local notes containing a key were deleted.
- [ ] If the browser-based fallback was used on a shared device, the browser session used for it was signed out.
- [ ] Optional: on a debug build, clear app data to return to a signed-out state; note that this removes all profiles on that install.
- [ ] No screenshots or screen recordings were taken or attached unless you explicitly requested them.
- [ ] Results were recorded in your own notes by check ID with redacted observations; only this checklist (not captures or secrets) is committed.

## If a check fails

Record, in your own notes first, and share only redacted text if needed:

- Check ID and whether it is reproducible.
- Device, OS version, UntisPlus build type/version, and account type/role (no credentials).
- Exact steps and the provider/endpoint context used (no personal data you do not want to share).
- Expected result vs actual result, including which host was contacted if you observed it.
- Whether the same behavior occurs with a different account or school you are authorized to use.

## Summary

- PASS: ___  FAIL: ___  N/A: ___
- Overall: ☐ Complete — no defect found  ☐ Defect found — triage separately
- Code change expectation: **none** unless a defect is found and separately approved.
