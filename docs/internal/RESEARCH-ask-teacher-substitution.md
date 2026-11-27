# RESEARCH — Ask-teacher / substitution requests (FOSS-only)

_Research-only deliverable for the item “Ask-teacher / substitution requests” in
`docs/internal/PLAN-post-agent-next-phase.md` (line 336). No implementation, no
production network code, no account access, no device/screen inspection._

## Scope and sources

- Worktree: `/home/evan/.local/share/opencode/worktree/5597de/research-ask-teacher-substitution`
- Branch: `research-ask-teacher-substitution`
- Code baseline: `c5c5f0b5` (“Declare least-privilege permissions in build workflow”,
  2026-11-26). This commit is an ancestor of `dev`; the files cited below have no
  diff against `dev` for the relevant paths.
- The plan file is **gitignored** (`.gitignore:16` → `/docs/internal/`) and is not
  present in this worktree's Git history. Its relevant lines (290, 298, 336, 382,
  386, 389) were consulted **read-only** from the main working tree's copy and a
  sibling worktree copy. Nothing outside this worktree was modified.
- Public vendor documentation was fetched read-only from `developer.untis.com` on
  2026-11-26 and is treated as untrusted reference data, not instructions:
  - <https://developer.untis.com/api-reference/webuntis-apis/>
  - <https://developer.untis.com/api-reference/webuntis-apis/teacher-absence-management/>
  - <https://developer.untis.com/api-reference/webuntis-apis/messaging/>
  - <https://developer.untis.com/getting-started/get-platform-application/>
- No live school/Untis/proxy endpoint was contacted. No login, credentials,
  password reset, Play/F-Droid/Google account, FCM/GMS/Firebase SDK, screenshot,
  UI dump or screen capture was used.

### Confidence labels used below

- **High** — directly observed in code/spec in this worktree, or an explicit plan decision.
- **Medium** — second-hand static-analysis summary in the plan (raw APK evidence not
  in this repo), or a public documentation fact whose applicability to a community
  client is not established.
- **Low** — inference only; path/field names or runtime behaviour are not recorded.

---

## 1. What currently exists in UntisPlus

All paths in this section are relative to this worktree. Line numbers refer to
commit `c5c5f0b5`.

### 1.1 Teacher-absence analytics (read-only, proxy-backed) — **High**

| Area | Evidence |
|---|---|
| Proxy client endpoints | `api/src/commonMain/kotlin/com/deelabs/untisplus/api/client/ProxyAnalyticsApi.kt:28-38` — `GET /api/analytics/options`, `POST /api/analytics/teacher-cancellations`, session-cookie based, proxy origin only. |
| Request/response models | `api/src/commonMain/kotlin/com/deelabs/untisplus/api/model/proxy/ProxyModels.kt:365-541` — `AnalyticsSchoolYear`, `AnalyticsTarget`, `AnalyticsOptions`, `AnalyticsReportRequest`, `AnalyticsCounts`, `AnalyticsBucket`, `AnalyticsTeacher`, `AnalyticsWindow`, `AnalyticsLessonChange`, `AnalyticsReport` (includes `missedTeachers`, `coverTeachers`, `lessonChanges`). |
| Serialization detail | `api/src/commonMain/kotlin/com/deelabs/untisplus/api/client/ApiClient.kt:114-121` — `ANALYTICS_JSON` with `explicitNulls = false`; `ProxyModels.kt:400-426` documents `additionalProperties: false` on the proxy request schema. |
| Repository/gating | `shared/src/commonMain/kotlin/com/deelabs/untisplus/data/repository/ProxyRepository.kt:92-94` (`teacherAbsencesSupported` from cached compatibility snapshot), `:174-182` (`analyticsOptions()`, `teacherCancellations()`), `:70-94` (cached `/me` permissions/capabilities). |
| UI/state | `app/src/main/java/com/deelabs/untisplus/ui/pages/teacherabsences/TeacherAbsencesViewModel.kt` (report modes `personal`/`teacher`/`class`/`room`, target/data-source selection, school-year vs custom range, max 6 years, multi-year report loop, `requestFor()` at `:365-428`); `TeacherAbsences.kt` (setup + report dashboard). |
| Tests | `app/src/test/java/com/deelabs/untisplus/ui/pages/teacherabsences/TeacherAbsencesYearTest.kt`; `api/src/jvmTest/kotlin/com/deelabs/untisplus/api/client/ProxyAnalyticsApiTest.kt` (MockEngine, path/body assertion, parsed report rows). |

This is **analytics over cancellation/substitution outcomes**. It has no create,
accept or decline operation and no request lifecycle state.

### 1.2 Notification targets and delivery (FOSS paths already present) — **High**

| Area | Evidence |
|---|---|
| Persisted stream target | `app/src/main/proto/settings.proto:63-64` — `timetableChangeStreamTargetType` (default `PERSONAL`) and `timetableChangeStreamTargetId`; `:83-84` — opt-in + Wi-Fi-only flags. Generated code rule: edit the `.proto`, never generated sources. |
| Teacher target type | `api/src/commonMain/kotlin/com/deelabs/untisplus/api/model/proxy/ProxyModels.kt:74-94` — `ProxyStreamTargetType` includes `TEACHER("TEACHER", needsId = true)` with `validated()` requiring a positive id; covered by `api/src/jvmTest/kotlin/com/deelabs/untisplus/api/client/ProxyTimetableApiTest.kt:20-22`. |
| Webhook/ntfy target keys | `ProxyModels.kt:143-235` — `ProxySubscriptionTarget` carries `teacherId`, `classId`, `personId`, `personal`, `roomId`, `subjectId`; `ProxyWebhookRequest`/`ProxyNtfyRequest` mirror those keys; `docs/PROXY-API.md:215-318` (§5 self-service subscriptions, privileged school-wide/teacher targets). |
| Repository + settings UI | `ProxyRepository.kt:203-219` (`notificationOptions`, webhook/ntfy CRUD + test); `app/src/main/java/com/deelabs/untisplus/ui/pages/settings/ChangeSubscriptionsViewModel.kt:17-80`; `.../settings/fragments/SettingsCategoryChangeSubscriptions.kt:279` maps `teacher` options to `ProxySubscriptionTarget(teacherId = it.id)`; `SettingsCategoryNotifications.kt` (break/first-lesson alarms, timetable-change opt-in, stream target picker). |
| Delivery mechanism | `app/src/main/java/com/deelabs/untisplus/services/TimetableChangeService.kt:36-46` — foreground `dataSync` SSE service against the proxy stream, 15 s reconnect, battery/Wi-Fi aware; `app/src/main/java/com/deelabs/untisplus/workers/TimetableChangeWorker.kt` — 15-minute WorkManager backstop; `TimetableChangeNotifications.kt` — channels/notification IDs; `NotificationReceiver.kt` — local message-of-day notification. No FCM/GMS code exists. |
| Teacher-target tests | `api/src/jvmTest/kotlin/com/deelabs/untisplus/api/client/ProxySubscriptionsApiTest.kt:123-166` (ntfy create with `teacherId = 123`, test/delete paths). |

These are the natural FOSS transport for any future substitution-request alerts:
teacher-scoped proxy/SSE + WorkManager backstop + webhook/ntfy, **not** an imitation
of official push (plan line 382, 385, 389).

### 1.3 Absences, class register, messages and substitution *display* — **High**

- Student absences: `api/src/commonMain/kotlin/com/deelabs/untisplus/api/client/AbsenceApi.kt:24-49`
  (`getStudentAbsences2017`); `ApiClient.kt:123-139` lists the supported JSON-RPC
  methods (`createImmediateAbsence2017`, `deleteAbsence2017`,
  `getStudentAbsences2017`, `submitAbsencesChecked2017`, etc. — no ask-teacher or
  substitution-request method); `api/.../model/untis/absence/StudentAbsence.kt:7-26`
  includes `manualNotificationStatus`, `autoNotificationStatus`, `studentOfAge`,
  plus proxy-injected `note`/`derived`.
- Absence CRUD and class register: `shared/.../repository/TimetableRepository.kt:28-34,102-131`
  (`postAbsence`, `deleteAbsence`, `postAbsencesChecked`);
  `shared/.../repository/InfoCenterRepository.kt:30,125-139` (`absencesSource()`);
  `app/.../ui/pages/infocenter/fragments/Absences.kt`, `AbsenceFilterDialog.kt`;
  `app/.../ui/pages/classregister/ClassRegisterScreens.kt`,
  `ClassRegisterViewModels.kt`.
- Messages are **read-only**: `app/src/main/java/com/deelabs/untisplus/data/repository/MessagesRepository.kt:12-65`
  exposes `messageSource`, `messagesSource`, `messagesSentSource`,
  `messagesDraftsSource` only. `api/spec/untis-intern/untis-rest.yaml:50-280`
  describes `getMessages`, `getMessage`, `getMessagesSent`, `getMessagesDrafts`,
  `getMessagesStatus`, `getMessagesPermissions`, `getMessagesRecipientsStaticPersons`
  (the latter three are spec-only in the app). No send/create message operation is
  wired in this repo.
- Substitution *rendering* only: `api/.../model/untis/timetable/PeriodText.kt:9`
  (`substitution: String` shown in details),
  `app/.../ui/pages/timetable/details/TimetableItemDetailsDialog.kt:390-392`,
  plus setting `timetableSubstitutionsIrregular` (`settings.proto:54`) and strings
  `preference_timetable_substitutions_irregular*`. These display a substitution
  already published by the school; they do not request or decide one.

### 1.4 What does **not** exist — **High** (negative evidence in this worktree)

- No `OPEN_ASK_TEACHER`, `askTeacher`, `AskTeacher`, `substitutionRequest` or
  `SubstitutionRequest` client/model/repository/UI symbol (repo-wide grep found
  only the unrelated German string “Vertretungsstunden als irregulär behandeln”
  and the spec enum below).
- No route, DTO, state machine, permission check or local persistence for
  create/accept/decline of substitution requests.
- No dedicated teacher-role screen, no own-absence request form.
- `api/spec/untis-intern/untis-rest.yaml:744-753` contains a `HomeSectionCell.type`
  enum value `SUBSTITUTION_REQUESTS` (alongside `TEACHER_ABSENCES`,
  `STUDENT_ABSENCES`, `STUDENT_ABSENCES_ADMINISTRATION`), but no generated model is
  used by the app and no operation uses it. It is a spec literal, not a feature.

---

## 2. Static evidence for request / accept / decline flows

| # | Evidence | Where | What it actually says | Confidence |
|---|---|---|---|---|
| E1 | Official app 6.8.3 has “own-absence and substitution-request activities”; literal `OPEN_ASK_TEACHER`; “accept/decline path literals” | Plan line 290, attributed to Feature M/C/S + API E5 (DEX literal scan). Raw APK evidence IDs are not in this repo. | Third-hand summary of static strings/classes. Establishes that the official client has some ask-teacher UI and accept/decline-related literals. Does **not** give endpoint paths, HTTP methods, DTO fields, roles or state transitions. | **Medium** for presence of literals; **Low** for any API shape. |
| E2 | Ask-teacher accept/decline included in the raw API-path list | Plan line 298, attributed to API E5. | Path literals were observed during static analysis, but the plan does not transcribe them. Deriving routes from these names would be guessing. | **Medium** for string presence; **Low** for route semantics. |
| E3 | `HomeSectionCell.type` enum value `SUBSTITUTION_REQUESTS` | `api/spec/untis-intern/untis-rest.yaml:744-753` in this worktree. | A home-page section type exists in the checked-in REST spec. No endpoint, client method or UI consumes it. | **High** for the spec literal; **Low** for runtime meaning. |
| E4 | Absence notification-status fields | `api/.../model/untis/absence/StudentAbsence.kt:19-21`; plan line 386. | `manualNotificationStatus` / `autoNotificationStatus` are observed DTO status values (e.g. `NOT_NOTIFIED`, `NO_AUTO_PARENT_NOTIFICATION_AND_NO_ATTEMPT`). The plan explicitly says these are **status flags, not teacher FCM actions**. | **High** for fields; **High** for the plan's “document only” decision. |
| E5 | Official teacher push requires an authenticated profile; FCM is non-FOSS | Plan lines 288, 368-376, 380-386 (teacher-notification research outcome). | No evidence of teacher push without a saved profile; official transport is Firebase/FCM. Decision: do not replicate GMS/Firebase; extend the proxy/webhook/ntfy path. | **High** as plan decision; **Medium** as second-hand APK fact. |
| E6 | FOSS-only gating rule for this item | Plan lines 336, 356-364, 382, 389, 399. | “Teacher-absence analytics only; no request or accept/decline flow”; “Medium/large; needs API/model work and a dedicated teacher account”; implement only behind a real FOSS proxy/local data source; defer until notification QA lands. | **High**. |
| E7 | Public Untis Platform docs (fetched 2026-11-26) | `developer.untis.com/api-reference/webuntis-apis/teacher-absence-management/` and `/messaging/`; `/getting-started/get-platform-application/`. | Public API surface documents **Teacher Absence Management v1** (GET by id/list, POST create, PUT update, DELETE delete) and **Messaging v1** (`POST messages`, send-only, platform token or OIDC user token). **No public substitution-request accept/decline API is documented.** Platform APIs require an Untis-configured platform application via the integration-partner process. | **High** that docs say this; **Medium** that it fully reflects the official app's private internals. |
| E8 | No corresponding client code in UntisPlus | Repo-wide grep over this worktree. | No request/accept/decline implementation exists; only analytics and substitution display. | **High**. |

**Overall assessment:** there is credible static evidence that the official app has
an ask-teacher/substitution-request surface, but the repo/plan preserve only
*names and strings*, not a usable contract. Any concrete endpoints, request bodies,
status values or permission rules would be invented. Confidence that a community
FOSS client could implement true parity against the official private flow is
currently **Low**; confidence that a proxy-owned equivalent could be designed is
**Medium** (design only, unverified).

---

## 3. Concrete API/model work that would be needed (proposal only)

The following is a **design sketch**, not an implementation and not a claim about
any live endpoint. Nothing here has been added to the codebase.

### 3.1 Preferred FOSS route: define the contract on the untis-proxy

The proxy already owns session auth, capability gating, teacher-scoped targets and
read-only analytics. A substitution-request feature should therefore be a new
proxy-owned surface rather than a reverse-engineered private Untis endpoint:

- `GET /api/substitution-requests/options?school=…` — allowed target teachers,
  periods/classes, reasons and the caller's permitted actions.
- `GET /api/substitution-requests?school=…&from=…&to=…&status=…` — filtered list.
- `POST /api/substitution-requests?school=…` — create a request.
- `POST /api/substitution-requests/{id}/accept?school=…` and
  `POST /api/substitution-requests/{id}/decline?school=…` — decisions, with a
  reason/version field to avoid lost updates.

All paths/verbs are **illustrative**. The proxy must perform authorization;
the client must only render actions returned by `options` and must treat a `403`
as “not permitted”, not as a UI bug.

### 3.2 Models (`:api` module)

New serializable models in
`api/src/commonMain/kotlin/com/deelabs/untisplus/api/model/proxy/` (likely a new
file, e.g. `ProxySubstitutionRequests.kt`):

- `ProxySubstitutionRequest`: id, date, period id / start-end, school class,
  subject, absent teacher, requested cover teacher (nullable), reason, note,
  status, actor/role, created/updated timestamps, `version`/ETag.
- `ProxySubstitutionRequestRequest` and `ProxySubstitutionDecisionRequest`:
  create/decline payloads; nullable fields omitted, matching the proxy's
  `additionalProperties: false` convention (`ApiClient.ANALYTICS_JSON` pattern,
  `ApiClient.kt:114-121`).
- `ProxySubstitutionRequestOptions`: available teachers/periods/reasons and
  allowed action flags per request.
- A status enum (`PENDING`, `ACCEPTED`, `DECLINED`, `CANCELLED`) with an
  `UNKNOWN`/fallback path so new server values do not break parsing
  (`ignoreUnknownKeys = true`, `ApiClient.kt:107-112`).
- Collections/wrappers for list responses and created-id responses.

### 3.3 Client and repository

- `api/.../client/ProxySubstitutionRequestsApi.kt`: thin `ApiClient` subclass using
  the existing `proxyUrl(baseUrl, path, school)` helper and the same JSESSIONID
  session handling as `ProxyAnalyticsApi`/`ProxyAbsenceApi`. **No production code is
  written in this research task.**
- `shared/.../repository/ProxyRepository.kt`: add methods with `withSession { … }`
  and a compatibility gate analogous to `teacherAbsencesSupported`
  (`ProxyRepository.kt:92-94`), driven by cached `/me` capabilities rather than
  hard-coded role assumptions. Alternatively a dedicated repository with a
  `CachedSource` (as `InfoCenterRepository.kt:125-139` does) if the list is large.
- Domain layer (`:app`): a read/list use case plus guarded create/accept/decline
  use cases that check the capability/permissions snapshot and surface conflict
  (`409`/stale `version`) as recoverable UI state.

### 3.4 UI, strings and notifications

- Role-aware screen: students/parents see the resulting substitution only;
  teachers see their own requests and only the actions granted by `options`.
- Strings in `app/src/main/res/values/strings.xml` only; do not hand-edit
  translations (Crowdin), per `AGENTS.md`.
- Optional local notifications must reuse the existing FOSS path: opt-in,
  deduplicated by `(requestId, status, version)`, delivered through the current
  channels / teacher-scoped webhook or ntfy targets. No FCM/GMS/Firebase.
- If persisted state is needed, extend `app/src/main/proto/settings.proto` with
  **new field numbers** (never renumber); no Room schema change is expected
  unless requests are cached in the database.

### 3.5 Alternative public-API route (informational, blocked)

The public Untis Platform docs expose Teacher Absence Management CRUD and a
send-only Messaging API, with no substitution-request decision API. They require
an Untis-configured platform application and integration-partner onboarding
(`developer.untis.com/getting-started/get-platform-application/`). If this ever
becomes viable, edit the checked-in YAML under `api/spec/untis-intern/` (never
`api/build/openapi/…`) and follow the plan's SSO/authorization decision. For a
community FOSS build this is **not** an available path today.

---

## 4. Why a dedicated teacher account or seeded fixtures are required — blocked now

A read-only analytics view cannot validate a multi-role workflow:

1. **Two roles are involved.** A teacher creates an own-absence/substitution
   request; another teacher or an administrator accepts/declines it; students
   see the outcome. A single student profile cannot exercise creation or a
   decision, and cannot reveal which actions the server permits.
2. **Server-owned facts are unknowable statically.** Real teacher ids, class and
   period mappings, availability of the school module, capability strings from
   `/me`, status values, concurrency/version behaviour and error codes all come
   from the live school/proxy session. The plan preserves only literals (E1/E2);
   guessing them would risk writing to the wrong endpoint or mutating real data.
3. **A real teacher account is needed for end-to-end verification** of
   authorisation, status transitions, cross-role visibility and notification
   delivery. Seeded fixtures can prove parsing/state logic, but not endpoint
   compatibility or permissions.
4. **The current ground rules forbid obtaining that account.** The plan's
   reverse-engineering rules are: no account access, no live endpoint testing,
   no password-reset abuse (plan lines 14-18, 356-364), and this task additionally
   forbids logins, credentials, proxy/Untis endpoints, device-screen inspection
   and proprietary SDK replication. Therefore live teacher-account validation is
   **blocked under the current no-account rule**.
5. **Consequence:** the item stays deferred, behind a compatibility/capability
   gate, with no shipped claim of ask-teacher parity. Work that can proceed
   without accounts is limited to the fixture unit tests in §5.

---

## 5. Seeded/local-fixture unit-test plan (no accounts, no screens)

All tests use synthetic data with redacted/fake ids; no school names, keys,
cookies, credentials or live URLs. No UI dump, screenshot or device inspection.

### 5.1 `:api:test` — client contract tests with Ktor `MockEngine`

Follow the established pattern in
`api/src/jvmTest/kotlin/com/deelabs/untisplus/api/client/ProxyAnalyticsApiTest.kt:22-31`
and `ProxySubscriptionsApiTest.kt:123-166`:

- `options` GET hits `/api/substitution-requests/options` and appends `?school=…`.
- `list` GET hits `/api/substitution-requests` and parses an array of requests
  from a fixture JSON file.
- `create` POST serializes only allowed fields; unset nullable fields are absent;
  `teacherId` is a number, status strings use the agreed enum values.
- `accept` / `decline` POST target `/api/substitution-requests/{id}/accept` and
  `/decline`; assert method, path, id, `school` parameter and decision body.
- Error cases: `403` → permission error, `409` → conflict/stale version,
  `422` → validation error, malformed JSON → parse failure; unknown fields are
  ignored.
- Fixture files: new directory `api/src/jvmTest/resources/substitution-requests/`
  with synthetic payloads (list, single detail, options, error bodies). Existing
  tests inline JSON; files are preferable once more than one case exists.
- Keep deterministic date/time parsing (set `TZ=UTC` where a test touches
  serializer defaults). Note the pre-existing `:api:test` serializer quirks in
  `AGENTS.md`; new tests should not depend on those failures.

### 5.2 `:app:testDebugUnitTest` — repository/domain/state tests with fakes

Using JUnit 5 + MockK (already available per `AGENTS.md`) and a fake repository,
not Android screens:

- DTO → domain mapping from a fixture: status, absent/cover teacher names, class,
  date range, actor role, version.
- Permission gating from a cached `/me` fixture: student profile never gets
  accept/decline actions; teacher profile gets only the actions listed in the
  `options` fixture; capability `supported = false` hides the whole feature.
- State machine tests: `PENDING → ACCEPTED` / `PENDING → DECLINED`; invalid
  transitions are rejected locally; stale `version` produces a conflict state
  rather than a silent overwrite; double-submit is suppressed while in flight.
- List filtering/sorting (by date, status, teacher) as pure functions — same
  style as `app/src/test/java/com/deelabs/untisplus/ui/pages/InfoCenterStateTest.kt`
  and `TeacherAbsencesYearTest.kt`.
- Notification dedupe: identical `(requestId, status, version)` produces one
  notification event; no notification when the feature is opted out.
- ViewModel tests (if a ViewModel is introduced): loading / error / retry states
  using a test coroutine dispatcher and a mocked `ProxyRepository`; assert only
  state, never a rendered view.

### 5.3 Explicitly out of scope for this test plan

- Instrumented/connected tests, `connectedDebugAndroidTest`, screenshots, UI
  hierarchies, logcat capture of screens, or any device interaction.
- Any test that constructs a real HTTP client against a proxy/Untis host, or
  that stores a real key/cookie.
- Any FCM/GMS/Firebase/proprietary SDK stub or replication.

### 5.4 Commands (local, offline-safe)

```sh
./gradlew :api:test --tests 'com.deelabs.untisplus.api.client.ProxySubstitutionRequestsApiTest'
TZ=UTC ./gradlew :app:testDebugUnitTest --tests 'com.deelabs.untisplus.ui.pages.substitution.*'
```

Pass/fail of these suites would establish **fixture-level** correctness only.
Live compatibility with any real school or official-client behaviour remains
unverified and blocked by the no-account rule (§4).

---

## 6. Recommendation

Keep the plan's ordering: defer this item until notification QA lands; do not
implement against guessed private endpoints; do not claim official-app parity.
If implementation is later authorized, start with the proxy-owned contract in §3,
land the fixture suite in §5 first, and gate the feature behind cached
proxy capabilities. A dedicated teacher account or an explicitly approved seeded
local proxy fixture is a prerequisite for anything beyond fixture-level testing.
