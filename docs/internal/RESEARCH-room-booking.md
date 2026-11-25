# Research: Room booking / availability (FOSS-only scope)

Date: 2026-11-26  
Branch/worktree: `research-room-booking` (isolated worktree only; nothing pushed).  
Status: **not implemented**. No verified booking endpoint or contract was found.

Scope note: the task references `docs/internal/PLAN-post-agent-next-phase.md`, which says this
item is parked because no booking API evidence was recovered. That plan file is **not present in
this worktree's branch**, so its exact wording was not re-read here; the brief above is the
statement of the parked item. All repo claims below are from this worktree snapshot only.

Research constraints observed: no accounts/credentials/logins, no school/Untis/proxy live
endpoints, no Play/F-Droid/Google accounts, no screenshots/UI dumps/screen capture. Only public
documentation pages were fetched, and their text was treated as untrusted data rather than
instructions. No booking network call or production code was added.

## 1. Current room finder / occupancy capability

The app already has a **read-only free-room finder**, not a booking feature. It shows which
saved rooms are free in the current school's time grid and lets the user open a room's timetable.

| Capability | Where | What it does |
| --- | --- | --- |
| Feature surface | `README.md:10`, `fastlane/metadata/android/en-US/full_description.txt:20-22` | Advertised as "room finder to search for free rooms"; no booking claim. |
| Screen | `app/src/main/java/com/deelabs/untisplus/ui/pages/roomfinder/RoomFinder.kt` | Compose screen with an hour selector (`:240-263`), room rows with loading/free/occupied icons and text (`:267-334`), and delete affordance for saved rooms. |
| View model | `app/src/main/java/com/deelabs/untisplus/ui/pages/roomfinder/RoomFinderViewModel.kt` | Builds hours from `user.timeGrid.days/units` (`:37-48`), preselects the current hour (`:64-73`), sorts rooms by consecutive free hours then long name (`:75-78`), adds/deletes saved rooms (`:84-90`). `onRoomClick` only **navigates** to `AppRoutes.Timetable(ElementType.ROOM, id)` (`:92-96`); it does not book. |
| Domain model | `shared/src/commonMain/kotlin/com/deelabs/untisplus/models/RoomFinder.kt` | `RoomFinderItem(entity, states: List<Boolean>)`; `freeHoursAt(index)` counts consecutive `false` (free) units from the selected hour (`:16-18`). |
| Occupancy derivation | `app/src/main/java/com/deelabs/untisplus/domain/GetRoomFinderItemsUseCase.kt` | Reads saved room IDs per user from `RoomFinderDao` (`:43`), resolves them from `masterDataRepository.rooms` (`:34`, `:46`), fetches a **room timetable** for the current week via `timetableRepository.timetableSource().get(...)` with `FromCache.IF_HAVE` and a 1-hour max age (`:68-80`), then maps periods to per-unit booleans: a unit is occupied if any non-`CANCELLED` period overlaps it (`:83-94`). Emits rooms incrementally as each fetch completes (`:49-60`, `:62-66`). |
| Persistence | `shared/src/commonMain/kotlin/com/deelabs/untisplus/data/database/entities/RoomFinderEntity.kt` | Per-user `(id, userId)` `rooms` table and DAO insert/delete/`getAllByUserId` (`:12-31`). This is a saved-room list, not a reservation store. |
| Master data | `api/src/commonMain/kotlin/com/deelabs/untisplus/api/model/untis/masterdata/Room.kt` | Read-only room fields: `id`, `name`, `longName`, department/colors, `active`, `displayAllowed`. |
| Underlying API | `shared/src/commonMain/kotlin/com/deelabs/untisplus/data/repository/TimetableRepository.kt:51-70`, `api/src/commonMain/kotlin/com/deelabs/untisplus/api/client/TimetableApi.kt:38-69`, `api/src/commonMain/kotlin/com/deelabs/untisplus/api/client/ApiClient.kt:134` | Uses JSON-RPC `getTimetable2017` with `ElementType.ROOM`. It is a **read** call. Other write methods in `TimetableApi`/`TimetableRepository` are lesson-topic and absence operations, not room booking. |
| Localisation | `app/src/main/res/values/strings.xml:4`, `:459`, `:462`; e.g. `values-de/strings.xml:37`, `:113-114` | Strings for "Free Rooms", "Room availability indicator", "This Room is occupied." |

**Capability summary:** availability is inferred locally from timetable periods; there is no
booking, reservation, confirmation, cancellation, or booking-list UI/state anywhere in the repo.

## 2. Static evidence in this repo for booking/reservation endpoints

| Finding | Evidence in this worktree | Confidence |
| --- | --- | --- |
| No booking/reservation JSON-RPC method is implemented. | `api/.../client/ApiClient.kt:123-139` lists all method constants: `getTimetable2017`, `getUserData2017`, `getHomeWork2017`, `getExams2017`, `getStudentAbsences2017`, absence/topic writes, etc. No `book*`/`reservation*` method. `TimetableApi.kt` exposes only timetable, period-data, lesson-topic and absence calls. | **High** (complete constant list in this snapshot). |
| No booking/room-reservation REST client or model exists. | `api/src/commonMain/kotlin/.../client/` contains `TimetableApi`, `UserDataApi`, `MessagesApi`, `OfficeHoursApi`, `ClassRegApi`, proxy clients for homework flags, absence notes, status, subscriptions, calendar, timetable changes/stream, analytics. None is a booking client; `api/.../model/request/` and `model/response/` have no booking DTOs. | **High**. |
| `BOOK` / `RESERVATION` strings are colour codes, not endpoints. | `api/.../model/untis/enumeration/AppColorType.kt:3-24`: `RESERVATION(3)`, `BOOK(4)`, `STORNO(5)`, etc. These are Untis period/app colour types. | **High** (positive identification; do not misread as API evidence). |
| Official external timetable spec contains the word "booking" only as a field description. | `api/spec/untis-extern/untis-extern-timetable-api-v3.yaml:912`: "Booking purpose or if no booking then lesson description (DB field Lesson.text)." Its only paths are `GET /api/rest/extern/v1|v2|v3/timetable` (`:47`, `:169`, `:225`), with a `room` filter (`:83-90`, `:191`, `:247`). | **High** that this file documents timetable reads only. |
| The proxy contract has no room-booking endpoint. | `docs/PROXY-API.md`: homework flags (§3), absence notes (§4), webhook/ntfy subscriptions (§5), response enrichments (§6), timetable changes/SSE (§7), `/me`, `/status`, `/healthz` (§8), calendar tokens (§9), explicit out-of-scope admin surfaces (§10), implementation checklist (§11). No booking path is specified. | **High** that no contract exists here; **medium** as evidence that no backend capability exists (the doc could simply omit it). |
| Office-hour registration is explicitly read-only. | `shared/src/commonMain/kotlin/com/deelabs/untisplus/domain/GetOfficeHourRegistrationsUseCase.kt:12`: "Read-only retrieval of an office hour's timeslots. Never books anything." | **High** for office hours; not general room booking. |
| Bounded text search for booking terms. | Repo-wide (`.md`, `api/`, `shared/src/`) searches for `booking|reservation|book|reserv` produced only the colour enum, the timetable field description, the office-hour comment, and a DB migration column named `bookmarks` (`shared/.../MigrationSpecs.kt:10`). | **Medium-high**: strong negative result for the searched patterns; does not prove an undocumented server capability is absent. |

**Static conclusion:** the plan's parking rationale is consistent with this branch snapshot.
Confidence that *this app has no booking integration*: **high**. Confidence that *no booking API
exists anywhere in Untis/WebUntis*: **low** from repo evidence alone; that question needs public
documentation (below), and even that is bounded.

## 3. Public-source findings

### 3.1 Official Untis sources (public product/help/developer documentation)

These pages are official Untis-owned documentation/marketing. They establish that **room booking
is a real WebUntis product feature**, but they do not provide a public booking API contract.

- **Untis Booking product page** — <https://www.untis.at/en/products/webuntis/booking>  
  Describes booking rooms/resources, reservations that admins confirm/reject, room lists,
  availability at a glance, room changes, appointment search, booking list, and reports with a
  permissions system. No endpoint, method name, payload or auth model is published there.  
  Confidence: **high** that the UI product feature exists; **none** for an API.
- **Untis Help Center: "Untis Termin: Räume buchen"** — <https://help.untis.at/hc/de/articles/7629076982812-WebUntis-Termin-R%C3%A4ume-buchen>  
  User-facing steps: search free rooms under *Buchen | Raum buchen*, filter by resource/department,
  optionally set recurrence, choose a room, add class/subject, and *Speichern*; the booking then
  appears under *Buchen | Meine Buchungen* and in the relevant timetables. Again, UI-only; no API
  details.  
  Confidence: **high** for UI workflow; **none** for wire protocol.
- **Untis developer docs: Room Management** — <https://developer.untis.com/api-reference/webuntis-apis/room-management/>  
  Says "Read-only access to room master-data"; exactly two endpoints: `GET getRoomById` and
  `GET getRooms`. It mentions room-booking *systems* as a use case for the data, not a booking API.  
  Confidence: **high** that the documented Room Management API cannot create bookings.
- **Untis developer docs: Platform APIs overview / Which API to Choose / API overview** —
  <https://developer.untis.com/api-reference/webuntis-apis/>,
  <https://developer.untis.com/api-reference/which-api-to-choose/>,
  <https://developer.untis.com/api-reference/api-overview/>  
  The API catalogue lists Room Management, Timetable, Lessons, Exams, Absences, Messaging,
  Timetable Event Push, etc. "Read room information" maps to **Room Management (read-only)**. The
  version table marks Room Management v1 "Current (read-only)" and lists **no Room Booking API**.
  API access goes through a central gateway (`https://api.webuntis.com/` production,
  `https://api.integration.webuntis.dev/` integration) and requires an approved platform
  application; the public "Try it out" sandbox client is read-only.  
  Confidence: **high** that no booking API is publicly documented in that catalogue as of this
  date; **not proof** that an internal/permission-gated endpoint cannot exist.
- **Untis developer docs: Timetable Event Push** — <https://developer.untis.com/api-reference/webuntis-apis/timetable-event-push/>  
  The one write-ish timetable API has `POST updatePlatformCalendarEvents` and `DELETE
  deletePlatformCalendar`, described as pushing a **platform calendar app's** events into
  WebUntis. It is not documented as a room-booking/conflict-checked reservation API.  
  Confidence: **medium-high** that it is not a usable booking substitute.

### 3.2 Community / reverse-engineered sources (not official)

These are third-party client libraries and docs. They are useful as bounded negative evidence but
are not a contract and must never be treated as authorisation to call an endpoint.

- **webuntis (NodeJS wrapper)** — <https://webuntis.noim.me/>  
  States: "In case you need the Untis API Spec (pdf), you need to email Untis directly and ask. I
  am (legally) not allowed to publish it", and "I am not affiliated with Untis GmbH." The
  documented examples cover login variants and timetable reads; no booking method is listed.  
  Confidence: **medium** that the public community API surface is read-focused; the statement
  about the PDF is a community claim, not an official licence grant.
- **python-webuntis Session docs** — <https://python-webuntis.readthedocs.io/en/latest/session.html>  
  Documents `rooms()`, `timetable()`, `timetable_extended()`, `statusdata()`, `exams()`,
  `class_reg_events()`, etc. No booking/reservation method appears.  
  Confidence: **medium** (bounded absence in one popular wrapper).
- **HexDocs `webuntis` / `untis` Elixir clients** — <https://webuntis.hexdocs.pm/json-rpc.html>,
  <https://untis.hexdocs.pm/json-rpc.html>  
  Document JSON-RPC methods `getTimetable2017`, `getHomeWork2017`, `getMessagesOfDay2017`,
  `getStudentAbsences2017`, `getUserData2017`; no booking method. Also notes that a user account
  is required for requests and that some methods use the mobile endpoint.  
  Confidence: **medium** (community reverse-engineered method lists, not official).
- **Search coverage and limits**: targeted public searches for `WebUntis API room booking
  endpoint`, `bookRoom`, `createBooking`, `getFreeRooms`, `saveBooking`, and site-restricted
  `developer.untis.com` booking queries surfaced only the official Room Management read-only page,
  the product/help pages above, and generic JSON-RPC wrappers. The primary web-search integration
  returned HTTP 402; DuckDuckGo HTML was used instead, and the last query hit a bot challenge.
  This is a **bounded negative search**, not proof of nonexistence.

**Public-source conclusion:** a booking *product* is documented, but no public, stable, verified
booking *endpoint* was found. Official docs explicitly keep room APIs read-only and gate the
platform APIs behind an approved application. Community material is read-focused and does not
supply a booking contract.

## 4. Decision: do not implement room booking yet

**Decision (as of 2026-11-26): keep the item parked. Implement no booking network call, client,
model, UI affordance, or production endpoint against an unproven target.** Continue to ship only
the existing inferred-availability room finder described in §1.

Reasons:

1. **No verified contract.** There is no confirmed request method/URL, auth model, required
   permission, request body, success body, error body, conflict semantics, or idempotency rule.
   Guessing a JSON-RPC method or REST path risks writes to a shared school resource.
2. **Official public APIs do not provide it.** The documented Room Management API is read-only;
   the platform gateway needs an approved application (credentials/registration), which is
   outside the research constraints and incompatible with the current login model.
3. **FOSS constraints.** The project promises a fully FOSS build with no proprietary Google
   components, ads, analytics or tracking SDKs (`README.md:34`, `docs/play/STORE_LISTING.md:28`,
   `fastlane/.../full_description.txt:11-16`, `:32-38`). The default proxy and Untis service are
   disclosed as Anti-Features in F-Droid metadata (`docs/play/README.md:47-50`,
   `docs/play/DATA_SAFETY.md:35`). A booking integration must not add a proprietary SDK or an
   undisclosed data flow.
4. **Provider gating is mandatory.** Per `docs/PROXY-API.md:13-14`, `:44-45`, `:591-592`, custom
   proxy endpoints only exist for UntisPlus/custom-provider users, never stock Untis. Even if a
   booking backend were added, the app would have to gate it on the provider and hide it on stock
   Untis; it must not silently fall back to an unproven school endpoint.
5. **Write safety.** Booking changes other people's resource availability and may need admin
   approval/reservation confirmation. A read-only availability display is safe; an unverified
   write is not.

**Reopen condition** (all must be true before code): a concrete public/official endpoint or a
formally documented FOSS backend contract exists; auth/permission and error semantics are known;
the data flow passes the FOSS/Anti-Feature disclosure review; and the feature is gated so it never
targets stock Untis or unverified hosts. This research did not find such a contract.

## 5. Seeded/local fixture tests to write if a future endpoint is confirmed

All of these can be written with the existing local test layout and **no live endpoint**:

- **Wire-contract tests** in `api/src/jvmTest/kotlin/...` following `UserDataTest.kt` and
  `ProxyTimetableApiTest.kt`: seed JSON fixtures for booking create/list/cancel responses and run
  `Json.decodeFromString`/`encodeToString` round-trips; assert URL/method/body construction with
  a `MockEngine` or pure URL-builder checks. Never point at a real school/Untis/proxy host.
- **Method/path/auth tests**: assert the exact confirmed JSON-RPC method or REST path, school/user
  scoping, cookie/token handling, and that no `auth` body is sent where the contract says cookie
  only (pattern: `docs/PROXY-API.md:55-68`, `ProxyCookieStorageTest.kt`).
- **Error mapping tests**: seeded `400/401/403/404/409/429/5xx` bodies → re-login, hide
  affordance, no-op, conflict message, retry/back-off; assert roll-back on non-2xx and no PII in
  logs (pattern: `WidgetTimetableLoader.kt:70` comment).
- **Occupancy mapping tests** in `app/src/test/java/...` and/or `shared/src/commonTest/...`:
  seed `RoomEntity` + timegrid `Day`/`Unit` + `Period` + booking fixtures; verify `states`,
  `freeHoursAt`, cancelled-period exclusion, overlapping/adjacent bookings, day/week rollover,
  reservation states (pending/confirmed/rejected) and whether each counts as occupied. This
  extends the logic currently in `GetRoomFinderItemsUseCase.kt:83-94` and `RoomFinder.kt:16-18`.
- **Repository/use-case tests** with fake `RoomFinderDao`, `TimetableRepository` and confirmed
  booking data source: seeded multi-user rooms (`RoomFinderEntity(userId=...)`) must stay scoped;
  a booking write must occur only on an explicit confirmed user action, never from screen entry,
  hour selection or `onRoomClick` (which today only navigates: `RoomFinderViewModel.kt:92-96`).
- **Idempotency/deduplication tests**: double submit, repeated polling with unchanged version,
  cancellation of an already-cancelled booking, and stale/conflicting local state.
- **Regression guard**: a test asserting that when the feature is disabled or the provider is
  stock Untis, no booking call is constructed and no booking UI is shown; also a static/CI check
  (`scripts/check-foss.sh`) remains green so no proprietary dependency is introduced.

Suggested fixture shape (local only, no real identifiers): `Room(id=1, longName="Test Room A")`,
`Period(id=..., start/end..., is=[CANCELLED] or [REGULAR])`, booking record with synthetic room
id, start/end, state `BOOK`/`RESERVATION`, and a fixed clock for the "current hour" selection.
