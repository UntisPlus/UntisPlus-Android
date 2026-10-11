# KMP / Compose Multiplatform Migration Plan — UntisPlus iOS

_Last updated: 2026-10-11. Status: planned, not started._

Goal: add a native **iOS app that replicates the Android app's features** by sharing the
Kotlin/Compose codebase via **Kotlin Multiplatform (KMP) + Compose Multiplatform (CMP)**.
Sideloaded to the owner's iPhone only (no App Store), with as little re-authoring as possible.

---

## 1. Goal & constraints

- **Feature parity** with the Android app on iOS (login/SSO, timetable + WeekView, imported
  `.ics` overlay, info center, room finder, settings, change notifications, etc.).
- **Shared Compose UI** (Compose Multiplatform), not a second native UI.
- **Sideload only** to one iPhone, free Apple ID, **no App Store**.
- **No full rewrite** — move/adapt code, don't re-author.
- Separate **iOS repo**; dedicated **shared** repo.

## 2. Locked decisions
- Shared code lives in a **dedicated `UntisPlus-Shared` repo** (KMP library). Both apps consume it.
- **Full parity before the first iOS build** (no feature work starts on iOS until Android+shared reach parity).
- No early toolchain smoke test (accepted risk — see §11).
- Android-only features are **approximated** on iOS, not dropped (see §10).

## 3. Target device & signing
- Device: **iPhone SE (2nd gen)**, iOS **27.0** (A13, 4.7" screen — UI must be responsive/tight).
- Sideload: **SideStore** (on-device auto-refresh, weekly) + **StikDebug** local VPN (available via
  AltStore PAL in the EU). One-time computer setup for the pairing file.
- Free Apple ID limits: **3 apps, 10 App IDs/week, 7-day expiry**, Developer Mode required (iOS 16+).
- Alternative: paid Apple Developer Program ($99/yr) → 1-year profiles, no weekly refresh.
- No APNs on the free account → iOS change notifications use **local notifications + background refresh**.

## 4. Repo topology (3 repos)
```
UntisPlus-Shared      KMP library — canonical shared code
├── commonMain        models, api, data, domain, ui (Compose)
├── androidMain       Android `actual`s
└── iosMain           iOS `actual`s

UntisPlus-Android     this repo — keeps shipping the Android app
└── app               depends on UntisPlus-Shared

UntisPlus-iOS         thin Swift/Xcode wrapper
├── iosApp            entry point (only after parity)
└── Shared            git submodule → UntisPlus-Shared
```
Wiring:
- Android: git submodule + Gradle **composite build** (`includeBuild`) or `include` with a mapped `projectDir`.
- iOS: Xcode build phase runs `./gradlew :shared:embedAndSignAppleFrameworkForXcode`; shared UI boots via
  `ComposeUIViewController { App() }`.
- Keep `:shared` isolated from day one so extraction stays clean.

## 5. Tooling: Xcode vs. xtool
- **Xcode (macOS on the Intel Mac)** — the primary path. Required for the Kotlin/Native iOS framework
  build (`embedAndSignAppleFrameworkForXcode`) and for the simulator.
- **[xtool](https://xtool.sh)** (`xtool-org/xtool`, ~5k★) — cross-platform Xcode replacement that builds a
  **SwiftPM** package into an iOS app, signs it, and installs over USB (`usbmuxd`) from **Linux**.
  - Needs: Swift 6.x Linux toolchain, `usbmuxd`, and an **`Xcode.xip`** you download from Apple to
    generate the Darwin Swift SDK (`swift sdk list` → `darwin`). No simulator; no App Extensions;
    App Store upload not implemented.
  - **KMP/CMP caveat:** xtool currently can **sign** KMP apps but **not build** them — its SwiftPM build
    model doesn't yet integrate with the Kotlin/Native Gradle build (xtool issue #28, still open).
    So the Kotlin/Native framework still needs the macOS/Kotlin toolchain.
  - **Use for us:** optional. If useful, xtool can package/sign/install the **Swift wrapper** from Linux,
    but the shared framework build stays on macOS. Revisit issue #28 if Linux-only builds become possible.
- Related tools: `xcross.sh` (Flutter equivalent), `xtool dev generate-xcode-project` (escape hatch to open
  the package in Xcode on the Mac).

## 6. Migration surface (measured in this repo)
| Area | Amount | KMP action |
|---|---|---|
| App size | 203 `.kt` files / ~24,000 LOC | move to commonMain |
| **Hilt** | 127 annotations in **63 files**; 52 `dagger.hilt` + 61 `javax.inject` imports | → **Koin 4** |
| Room | 18 `@Entity` files, 3 `@Dao` files | → Room KMP 2.8 |
| `java.time` | 115 imports | → `kotlinx-datetime` |
| WorkManager | ~14 files | → `expect/actual` (BGAppRefreshTask) |
| Settings | protostore/protobuf-lite, 16 files | → shared multiplatform store |
| Widgets | Glance, 5 files (**disabled** in manifest) | defer |
| Accompanist | 3 files | → CMP equivalents |
| Coil | 2 files (Coil 2.7) | → Coil 3 (KMP) |
| `android.*` | 174 imports | gate via `expect/actual` |
| `:api` | Ktor 3.1.3, OpenAPI (`jvm-ktor`, `java8` dt) | → OpenAPI `multiplatform` + `kotlinx-datetime` |
| `:material-color-utils` | pure Kotlin + error-prone (JVM) | → KMP (drop error-prone) |
Already KMP-friendly: Ktor, `kotlinx-serialization`, `universal-cache` (crocodile8), `colormath` (ajalt).
Dead dep: **Fuel** (0 usages) — remove.

## 7. Phase 1 — Shared logic core
1. **`:api` → KMP**: OpenAPI generator `library=multiplatform, dateLibrary=kotlinx-datetime`; replace the
   custom `java.time` `DateTime` serializer; add `ktor-client-darwin`.
2. **`:material-color-utils` → KMP**.
3. **Hilt → Koin 4** across the app (also unblocks newer Kotlin/KSP; Hilt's annotation processor can't read
   newer Kotlin metadata).
4. **Room → Room KMP 2.8**: `@ConstructedBy`, `expect fun getDatabaseBuilder()`, `BundledSQLiteDriver`,
   DAOs → `suspend` + `Flow` (drop `LiveData`), transactions via `useWriterConnection`.
5. **Settings**: replace protostore/protobuf-lite with a shared store (DataStore + serialized model) behind
   an interface.
6. **Coil 2 → 3**; **remove Fuel**; replace Accompanist.
7. Move models/api/data/domain into `commonMain`; `expect/actual` for Android specifics.
8. Verify Android builds + runs after every step.

## 8. Phase 2 — Shared Compose UI (full parity)
- Move every screen to `commonMain`: login, timetable/WeekView, info center (messages/homework/exams/
  absences/office hours), room finder, settings, imported calendars.
- `org.jetbrains.compose.*` artifacts; JetBrains `navigation-compose` + `lifecycle-viewmodel-compose`; Koin DI.
- `expect/actual` inventory: **SSO WebView** (Android `WebView` ↔ iOS `WKWebView`) ← highest risk,
  **QR scanner** (ZXing ↔ `AVFoundation`), files, permissions, clipboard, notifications, system UI.
- Android must stay green after each screen.

## 9. Phase 3 — iOS repo & app (after parity)
- Create `UntisPlus-iOS`; add `UntisPlus-Shared` submodule; Xcode project; `ComposeUIViewController { App() }`;
  Koin init from Swift.
- Implement iOS `actual`s; build + sign on the Intel Mac (Xcode); install over USB; then move under
  **SideStore** for auto-refresh.
- Optional: xtool for Linux-side packaging/install of the wrapper (see §5).

## 10. Phase 4 — Approximating Android-only features on iOS
| Android feature | iOS best-effort |
|---|---|
| **Auto-mute (DND)** | Can't set Focus programmatically → local notification + documented **Shortcuts/Focus automation** the user enables once. |
| **Change monitoring service** | `BGAppRefreshTask` polling the proxy `/api/timetable/stream` + **local notifications** (no APNs). |
| **Widgets (Glance)** | Deferred; later a native **WidgetKit** widget (Swift, not shared). Currently disabled on Android anyway. |
| **`.ics` VIEW/SEND intents** | iOS **share-sheet** document type + custom URL scheme. |
| **Exact-alarm scheduling** | iOS local notifications (no exact-alarm API). |

## 11. Risks & mitigations
| Risk | Impact | Mitigation |
|---|---|---|
| **Hilt → Koin** breadth (63 files) | Large mechanical change; unblocks everything else | Do first; keep Android green; code outside `*.di` mostly untouched |
| **SSO WebView** port | Highest-risk single feature (cookie/session handling) | Isolate behind an interface early; prototype WKWebView actual in Phase 2 |
| **Settings store** replacement | Shared settings UI depends on it | Abstract behind an interface; migrate data once |
| **Room KMP DAO rewrites** | 18 entities/3 DAOs to `suspend`+`Flow` | Incremental; commit schema JSON |
| **No early iOS smoke test** (chosen) | Toolchain issues discovered only after parity | Accept; if it bites, do a minimal `xtool`/Xcode "hello world" framework build immediately |
| **Intel Mac / iOS 27 toolchain** | Late discovery of incompatibility | Keep Xcode current for the macOS version; xtool as fallback |
| **Free-account signing churn** | Weekly refresh | SideStore auto-refresh; consider $99/yr for 1-year profiles |

## 12. Effort (part-time, with agent help)
| Phase | Estimate |
|---|---|
| 1. Shared core + Hilt→Koin + Room/data | 3–6 weeks |
| 2. Shared CMP UI | 3–5 weeks |
| 3. iOS repo + app + actuals | 2–3 weeks |
| 4. Approximations/polish | 2–4 weeks |
| **Total** | **~2.5–4.5 months** |

## 13. Parity checklist (shared/iOS)
- [ ] API client (`:api` → KMP)
- [ ] Hilt → Koin
- [ ] Room KMP
- [ ] `java.time` → `kotlinx-datetime`
- [ ] Settings store (protostore replacement)
- [ ] Login (password/secret/QR)
- [ ] SSO seamless login (WKWebView)
- [ ] Timetable week view (+ imported `.ics` overlay)
- [ ] Info center: messages / homework / exams / absences / office hours
- [ ] Room finder
- [ ] Settings screens
- [ ] Change notifications (local)
- [ ] Auto-mute (Shortcuts approximation)
- [ ] `.ics` share-sheet import
- [ ] SideStore auto-refresh set up

## 14. Progress log
- 2026-10-11 — Phase 1 started on branch `feature/kmp-shared-core`:
  - Added an in-repo `:shared` Kotlin Multiplatform module (targets: `android`, `jvm`, iOS
    `arm64`/`simulatorArm64`/`x64`; the Apple targets are disabled on non-macOS hosts). This is the
    seed of the future `UntisPlus-Shared` repo — it can be extracted to its own repository once its
    modules are free of JVM-only dependencies.
  - Moved the self-contained, JVM-free `Base32` utility into `commonMain`; `:api` now depends on
    `:shared`.
  - Removed the unused Fuel dependency.
  - `./gradlew :app:assembleDebug` verified green.
  - Converted `:api` to Kotlin Multiplatform: sources moved from `src/main/kotlin` →
    `src/commonMain/kotlin` and `src/test/kotlin` → `src/jvmTest/kotlin`; `jvmMain`/`androidMain`
    (JCA `Mac`) and `iosMain` (CoreCrypto `CCHmac`) `expect`/`actual` implementations for SHA-1
    HMAC added in `HmacSha1.kt` (the iOS actual is unverified — no macOS host yet).
  - Replaced `java.time` with `kotlinx-datetime` throughout `:api` (`SerializableTypes` typealiases,
    `LocalDate`/`LocalTime`/`LocalDateTime`/`DayOfWeek` serializers, `Auth`, `TimeGrid`, `Day`,
    `StudentExemption` and the client request signatures). The OpenAPI generator keeps
    `dateLibrary=java8` but maps `OffsetDateTime`/`LocalDateTime`/`LocalDate` onto the kotlinx-datetime
    serializers via `typeMappings`, so generated code is free of `java.*` references. Generator output
    moved to `api/build/openapi` (was colliding with AGP's `build/generated`).
  - `:api` uses `api(libs.kotlinx.datetime)` so the date types are visible to consumers.
  - Kept the Android app on `java.time` internally and convert at the ~20 boundary sites using
    kotlinx-datetime's JVM interop (`toKotlinLocalDate/Time`, `toJavaLocalDate/Time/DateTime`). This
    keeps the UI formatting code (java.time `DateTimeFormatter`) unchanged for now.
  - Migrated `:api`'s serializer tests to kotlinx-datetime. `./gradlew :app:assembleDebug`,
    `:app:testDebugUnitTest` verified green. `:api:jvmTest` passes (38/38) under `TZ=UTC`; one
    empty-string fallback test is timezone-sensitive as before.
  - Added `shared/.gitignore` (`/build`) so the new module's build output is not committed.
  - Converted `:material-color-utils` (§7 item 2) to Kotlin Multiplatform: the 11 non-empty Java
    sources (`utils/{MathUtils,ColorUtils}.java`, `hct/{Cam16,Hct,HctSolver,ViewingConditions}.java`,
    `palettes/{CorePalette,TonalPalette}.java`, `blend/Blend.java`, `scheme/Scheme.java`,
    `score/Score.java`) were manually translated to `commonMain` Kotlin with identical behavior and
    package names; `src/main/java` was deleted. The Google errorprone annotations on `Scheme.java`
    were dropped (no runtime effect).
  - Added a **golden test** (`SchemeGoldenTest`, JVM) that asserts `Scheme.light`/`dark`/
    `lightContent`/`darkContent` for 8 ARGB seeds (incl. the app's default theme colours) against a
    snapshot captured from the original Java implementation (`jvmTest/resources/golden.txt`, 1152
    values). Route: `Scheme.dark`/`light` → `CorePalette` → `TonalPalette` → `Hct` → `HctSolver`
    → `Cam16` → `ColorUtils`/`MathUtils`, so the test pins the whole pipeline. Other entry points
    (`Blend`, `Score`, `Hct` mutators) were ported 1:1 but aren't asserted yet.
  - `:material-color-utils:jvmTest` green; `./gradlew :app:assembleDebug` and
    `:app:testDebugUnitTest` green after the conversion.

