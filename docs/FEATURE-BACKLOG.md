# FEATURE BACKLOG — Community research (Untis ecosystem)

_Last updated: 2026-10-11. Compiled from a GitHub sweep of ~20 Untis-related projects (~1,040 issues scanned) plus each project's README/feature docs._

Purpose: a "go through" list of candidate features, grounded in what users of other Untis
projects actually ask for, with sources and the current status in UntisPlus.

Status legend: ✅ have · ⚠️ partial · ❌ not yet.

Source legend: `BT` BetterUntis · `nU` ninocss/UntisPlus (Flutter) · `EP` EigenPlan ·
`Sit` project-sitnu (watchOS) · `iOS` BetterUntis-iOS · `Scr` Scriptable Untis widget ·
`HA` homeassistant-WebUntis · `CLI` webuntis-scraper · `OU` OpenUntis ·
`Legacy` BetterUntis-Legacy · SDKs = untis-rs / untis4j / node-webuntis / SchoolUtils.

---

## 1. What each project does differently

| Project | Platform | Standout / unique approach |
|---|---|---|
| **BetterUntis** (our base) | Android/Kotlin | Compose WeekView, Info Center (messages/homework/exams/absences), RoomFinder, multi-profile, auto-mute, per-item colors, teacher/room/class/subject plans, change notifications, Material 3, F-Droid + Play |
| **ninocss/UntisPlus** (Flutter, *name twin*) | Android/iOS | Privacy-first, local-only; multi-account by design (account-scoped widgets/alarms/credentials/caches); offline-first Hive cache; absence + change center (deduplicated cancellations/room/teacher/subject/time changes); home-screen widgets; on-device AI assistant; free-room finder; per-day custom alarms; secure storage; dynamic color |
| **EigenPlan** (Lasslos) | Android/Flutter | Minimal but polished distribution: Play Store + IzzyOnDroid, reproducible builds, two-tier tests (offline + live API), filter-by-class only, custom colors |
| **project-sitnu** | Apple Watch | Watch-first: many complications (small/large/circular/rectangular/corner/XL), offline, login via user/pw/secret/QR, "time until end of lesson" complication |
| **BetterUntis-iOS** | iOS/SwiftUI | Native SwiftUI, Keychain multi-account, Core Data offline cache, background refresh, QR URL parsing, 15+ API fallback methods for old servers |
| **Scriptable Untis widget** (kaiser-jan) | iOS widget | Configurable small/medium widgets; notifies on changes + exams + grades; grades view; conditional fetch (start of lesson / outside hours) for battery; visual config editor + JSON config; self-updating |
| **homeassistant-WebUntis** | Home Assistant | 30-day calendar entity; lesson/start/end/next-lesson sensors for wake-up automations; change notifications (cancel/room/teacher/swap); iOS Live Activities timetable; homework list + bundled Lovelace card (Bald fällig / Noch nicht abgeschlossen / Verpasst + print); `get_timetable`/`count_lessons` services; filters |
| **webuntis-scraper** | Terminal (CLI) | `--now` "what lesson am I in" + Waybar module; day/oneline/week table with cancels, subs, exams highlighted; `--changes --notify` desktop notifications; `--live` auto-redraw; JSON for every run; HTTP + Playwright browser fallback (SSO/2FA/WAF); `--profile` multi-account |
| **OpenUntis** (FLOSS legacy) | Android | Proxied WebUntis domain for IP/UA privacy (self-hostable), anti-feature transparency, F-Droid |
| **untis-ics-sync / untis-to-calendar / ics-webuntis** | Server | Serve an ICS feed when the school disabled Untis' calendar API; Docker/Redis; configurable date window; maintenance-notice injection |
| **webuntis-alarm** | Middleware (Rust) | Tiny `/tomorrow` endpoint returning the first lesson → drives an alarm automation |
| **SDKs** (untis-rs, untis4j, node-webuntis, SchoolUtils) | Libraries | School search + read-only JSON-RPC; building blocks for other apps |
| **Untis4Garmin / WebUntis-AppleWatch / ioBroker.webuntis** | Wearables/smarthome | Other device ecosystems |

### Our uniquifiers (nobody else in the list has these)
- Own proxy backend: recon (teacher/room/subject without credentials), change stream, webhook + ntfy, calendar tokens, REST + JSON-RPC dual, API-shutdown insulation.
- Imported external `.ics` overlay on the week view — unique in this ecosystem.
- SSE push + seamless SSO (iServ/Office365).

---

## 2. Candidate feature list ("go through")

### A. Home-screen / glance
- [ ] ❌ Android home-screen **widget** (small: next lessons) — `EP`/`Scr`/`nU`/`BT#88`
- [ ] ❌ Widget variant showing **cancelled lessons + next exam** — `Scr`
- [ ] ❌ **Wear OS** app / complication ("time until end of lesson") — `BT#61`/`Sit`
- [ ] ❌ **Live Activity / ongoing notification** of the current lesson — `HA`
- [ ] ❌ Quick-settings **tile / app shortcut** to the timetable — `BT#88`

### B. Notifications & alarms
- [ ] ⚠️ Change notifications (cancel/room/teacher/swap) — push exists, no per-type tuning — `HA`/`Scr`/`CLI`
- [ ] ❌ **"Next lesson" alarm / wake-up** ("Wecker") — `HA#57`/`webuntis-alarm`
- [ ] ❌ **Notification before first lesson** with room — `BT#24`
- [ ] ❌ **Homework** notification — `BT#200`
- [ ] ❌ **Exam reminder** notifications — `Scr`/`HA`
- [ ] ❌ **Grades-added** notification — `Scr`
- [ ] ❌ **Messages-of-the-day** notification — `BT#301`
- [ ] ❌ Customizable / localizable **notification templates** — `HA#174`
- [ ] ✅ Auto-mute

### B2. Backend / proxy (our differentiator — keep extending)
- [x] ✅ Change stream + SSE + webhook + ntfy
- [x] ✅ Calendar subscription tokens
- [x] ✅ Recon (teacher/room/subject)
- [x] ✅ REST + JSON-RPC dual transport

### C. Timetable viewing & navigation
- [ ] ❌ **Day ↔ week quick toggle** — `BT#197`
- [ ] ❌ **Detailed day view** — `BT#490`
- [ ] ❌ **Hide/filter specific classes & subjects** — `BT#102`/`#157`, `HA#152`
- [ ] ❌ **Grey out everything behind / past lessons** — `nU#161`
- [ ] ⚠️ Highlight irregular/swap lessons as **substitutions** — `BT#242`/`#17`, `CLI`, `HA#53`
- [ ] ❌ Mark **room/teacher changed** badge — `OU#26`
- [ ] ❌ **Strike-through** for cancelled lessons — `BT#409`
- [ ] ❌ Custom **week range / last day of week** (Mon–Sun) — `BT#161`/`#439`
- [ ] ❌ Limit **horizontal blocks** / disable zoom — `BT#331`/`#375`
- [ ] ❌ **Combine/sum consecutive lessons** — `Sit#22`
- [ ] ⚠️ Offline **timetable cache** — `BT#119`/`nU#162`/`iOS` (verify current state)

### D. Customization
- [ ] ⚠️ **Custom subject colors** (per subject, all views) — `BT#329`/`#420`, `EP`, `nU`
- [ ] ❌ **Color-code subjects that have homework** — `BT#404`
- [ ] ❌ **Rename subjects / own display name** ("Eigener Stundenplan") — `BT#120`, `HA#133`
- [ ] ❌ **Themes / per-role colors** — `CLI#45`
- [ ] ⚠️ More **icons for settings** — `BT#341`
- [ ] ❌ Teacher **abbreviation fixes** — `nU#30`

### E. Data & Info Center
- [ ] ❌ **Grades view** — `Scr`
- [ ] ❌ **Homework list/todo** (Bald fällig / offen / verpasst) + mark done — `HA#140`, `BT#304`, `nU`
- [ ] ❌ **Exams tab** in Info Center — `HA#100`, `iOS`
- [ ] ⚠️ **Absence totals** (days, missed lessons, excused) — `CLI`/`HA`
- [ ] ❌ **Submit your own absences** — `BT#376`, `nU`
- [ ] ❌ **Attendance / lesson documentation** — `OU#13`
- [ ] ⚠️ **Lesson topic/contents** in details — `WebUntis#36`
- [ ] ⚠️ **Message attachments** view/download — `nU#167`, `BT#467`
- [ ] ❌ **Parent-account** attendance/grade handling — `HA#282`/`#372`

### F. Accounts / security / privacy
- [ ] ⚠️ **Multi-account** — make fully account-scoped (widgets/alarms/caches) — `nU`
- [ ] ❌ **Multiple children** workflow — `OU#35`, `HA#299`
- [x] ✅ Seamless **SSO / no-password** login — `HA#111`/`#299`
- [x] ✅ **2FA/TOTP** — `BT#239`
- [x] ✅ **QR login** — `Sit`/`iOS`
- [ ] ❌ **Proxied/privacy domain** option — `OU`
- [ ] ❌ **App lock / biometric** (if not present)

### G. Distribution & trust
- [ ] ❌ **Publish signing fingerprint** — `BT#506` (cheap win)
- [ ] ❌ **Reproducible builds** — `EP#23`
- [ ] ❌ **FLOSS / de-Googled** build story — `Legacy#6`
- [ ] ❌ Play Store **inclusion** explored — `OpenUntis#3` (13👍)
- [x] ✅ F-Droid/Obtainium via `master` releases
- [ ] ❌ **Open-source the demo/backend API** docs — `BT#403`

### H. Integrations / export
- [x] ✅ Calendar subscription tokens / ICS export
- [x] ✅ Imported external `.ics` overlay on the timetable
- [ ] ❌ Documented ICS export → Google/Outlook calendar flow — `untis-ics-sync`
- [ ] ❌ Documented Home Assistant / automation recipes (webhook/ntfy) — `HA`
- [ ] ❌ JSON output / scripting story — `CLI`
- [ ] ❌ Wearable integrations (Garmin/Apple Watch) — `Untis4Garmin`

### I. Emerging / differentiators
- [ ] ❌ **On-device AI assistant** — `nU#30` (14 comments)
- [ ] ❌ **Free-room finder** ("which rooms are free now") — `nU`
- [ ] ❌ **Live Activity / ongoing current-lesson view** — `HA`

---

## 3. Suggested order (impact vs. effort)

1. **Publish signing fingerprint** (`BT#506`) — minutes.
2. **Day ↔ week toggle + detailed day view** — self-contained UI, high votes.
3. **Android "next lessons" widget** — biggest unfilled want; also unlocks the alarm.
4. **Substitution highlighting + per-type notification tuning** — we already have the change data.
5. **Hide/filter classes & subjects** — client-side.
6. **Homework list + exams tab + notification** — data mostly fetched already.
7. **Custom subject colors** (full, per-subject) — closes `BT#329`.
8. **Grades view** — differentiator only Scriptable has today.

---

## 4. Raw demand signals (highest score = reactions*3 + comments)

| Score | Source | # | Title |
|---|---|---|---|
| 42 | BetterUntis | #158 | Teacher names (closed) |
| 42 | OpenUntis | #3 | Google Play Store inclusion (open, 13👍) |
| 35 | BetterUntis | #102 | Hiding certain classes (open, 9👍) |
| 33 | BetterUntis | #61 | Wear Support (open, 30💬) |
| 23 | BetterUntis | #157 | Filter by Subjects (closed) |
| 22 | BetterUntis | #373 | Support for Messages (closed) |
| 21 | BetterUntis | #197 | Quick day/week view switch (open, 6👍) |
| 19 | homeassistant | #53 | Highlight substitutions/room changes in calendar |
| 18 | BetterUntis | #457 | Notification if lesson is cancelled (open, 6👍) |
| 15 | BetterUntis | #329 | Custom subject colors (open, 5👍) |
| 15 | BetterUntis | #88 | Widgets and Shortcuts (closed) |
| 14 | ninocss/UntisPlus | #30 | Native AI Assistant Integration (open) |
| 11 | BetterUntis-Legacy | #6 | FLOSS version (open) |
| 10 | BetterUntis | #404 | Color coding for subjects with homework (open) |
| 10 | BetterUntis | #376 | Submitting own absences (open) |
| 11 | EigenPlan | #23 | Reproducible Builds (open) |

_Regenerate this list by re-running the GitHub sweep; source snapshot taken 2026-10-11._
