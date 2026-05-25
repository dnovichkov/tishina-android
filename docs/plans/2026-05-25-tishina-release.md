# Tishina — MVP Phase 6: Production Release

## Overview

Phase 6 закрывает все production-readiness требования и делает приложение `Тишина` готовым к подаче в Google Play, RuStore и Samsung Galaxy Store. После завершения этой фазы у MVP появляется фирменная иконка, R8-уменьшенный release-AAB (целевой размер ≤ 6 МБ), реализованный FR-20 (экспорт CSV через Storage Access Framework), share Intent с PNG-снимком графика для Detail (P1 из Phase 5), реальный хостинг Privacy Policy на GitHub Pages, release signing инфраструктура через GitHub Secrets, отдельный `release.yml` workflow, инструменты ASO и заполненные store metadata templates, а также Robolectric → real emulator переход для матрицы API 26/30/34 + macrobenchmark cold-start для подтверждения NFR-1. Все плейсхолдер-URL из Phase 5 заменяются на финальные.

**Цель фазы:**
- `:app` — реальная adaptive launcher icon (foreground vector «волна → плоская линия» + background `#0E2433`), 512×512 PNG для каталогов RuStore / Samsung, full ProGuard/R8 rules с keep-инструкциями для Hilt / Compose / kotlinx-serialization / Room / DataStore, `isMinifyEnabled = true` + `isShrinkResources = true` для release buildType, debug-only suffix `.debug` для параллельной установки, динамический versionName через git-tag, versionCode из `github.run_number`, signing config через GitHub Secrets + правильный фиксированный keystore для CI/CD; финальные URL для AboutScreen (`about_github_url`, `about_privacy_url`) с реальными значениями.
- `:feature:history` — `ExportHistoryUseCase(filter: ExportFilter)` для отбора замеров под экспорт; в `HistoryScreen` контекстное меню «Export» с открытием Storage Access Framework picker (`ActivityResultContracts.CreateDocument("text/csv")`), который сериализует выбранные measurements (либо все) в CSV (заголовки на текущей локали, UTF-8 BOM для Excel-совместимости).
- `:feature:history` — расширенный `DetailScreen`: action «Share» в TopAppBar открывает `Intent.ACTION_SEND` с текстом (название, дата, метрики) + URI на PNG-снимок графика (через `FileProvider` из app cache); fallback на text-only share для устройств без поддержки image share.
- `:core:data` — `CsvSerializer` + `MeasurementCsvFormat` (POJO с column-mapping), `MeasurementsExporter` (combines DAO+Csv), правила escaping (`,`, `"`, `\n`); опциональный bundle samples-таблицы для каждого замера.
- `:feature:measure` — переиспользуемый `SplLineChart` уже живёт в `:core:ui` после Phase 3/4; новая утилита `LineChartSnapshotter` рендерит график в `android.graphics.Bitmap` через Compose `Canvas.captureToImage()` (Compose UI Test API публичный, но для production используется `Bitmap.createBitmap` + draw из `DrawScope`).
- `:core:about` — `AboutViewModel` подписывается на новый `AppLinksProvider` (DI-обёртка над strings.xml), который собирает URL из `BuildConfig` либо runtime-flags для разных flavors (placeholder vs production); auto-generated OSS-licenses через Gradle task `generateOssLicenses` (использует `LicenseeTask` из cashapp/licensee или custom скрипт парсящий `dependencies` метаданные).
- `:app` — `release.yml` workflow на git-tag `v*.*.*`: cache-friendly Gradle setup, `assembleRelease bundleRelease`, signing через decoded keystore из base64-secret, upload AAB+APK+mapping.txt в GitHub Releases через `softprops/action-gh-release@v2`, generate release notes из commits-между-тегами; опциональный nightly.yml.
- `:app` — `instrumentation-tests` GitHub Actions job на матрице API 26/30/34 через `reactivecircus/android-emulator-runner@v2` (один e2e flow: запуск → permission → measure 3 с → save → история → bulk-delete → undo → AboutScreen → CSV export); конфигурация через `:app/src/androidTest/` (новая директория — пока пустая, Phase 1-5 шли через Robolectric `src/test/`).
- `:app` — macrobenchmark module `:macrobenchmark` (новый Gradle-module, не feature) с `MainActivity` cold-start baseline-profile generation; CI-job собирает baseline profile и зашивает в `:app/src/main/baseline-prof.txt` через `androidx.baselineprofile` плагин; результат подтверждает NFR-1 (`≤ 1 с` до интерактива на Pixel 6a).
- `app/src/main/store-metadata/` — структура с локализованными `title.txt` / `short_description.txt` / `full_description.txt` для Google Play (ru, en), RuStore (ru), Samsung (en); скриншот-плейсхолдеры (8 для Play, ≤10 для RuStore); ASO-ключи в полном описании; чек-лист Data Safety / Permissions declaration / IARC 3+.
- `docs/privacy/index.html` — статичная HTML Privacy Policy для хостинга на GitHub Pages (`gh-pages` branch автоматически или через workflow-publish из `docs/privacy/`); CNAME-настройка опциональна.
- `README.md` — финальный production-status, FR-таблица 100% MVP, инструкции по локальной сборке release (`./gradlew bundleRelease`), пометка «v1.0.0».

**Стратегия TDD для Phase 6 (зафиксировано на планировании):** TDD обязателен для бизнес-логики (CSV serializer, share Intent payload builder, OSS-licenses parser, AppLinksProvider) — тесты пишутся перед реализацией. Для чистого config/infra (ProGuard rules, release.yml workflow, signing config, store metadata templates, иконка vector drawable, Privacy Policy HTML) — validation через успешную сборку `bundleRelease`, успешный run `release.yml` в dry-run/draft-режиме, manual inspection PNG-рендеров иконки в Android Studio Asset Studio preview, плюс Roborazzi screenshot-тесты обновлённого header `AboutScreen` (icon visual regression). Это согласуется с проектной memory ([[feedback_tdd_default]]) — глобальное TDD остаётся, но контракт уточняется для не-кодовых артефактов.

**Стратегия release signing (зафиксировано на планировании):** **Play App Signing** — Google управляет финальным app-signing ключом; мы храним только upload-key. Локально genereated keystore (`tishina-upload-key.jks`) шифруется в base64 и заливается в GitHub Secrets как `UPLOAD_KEYSTORE_BASE64`. `release.yml` декодирует на disk во время build, удаляет после. Пароли через secrets: `UPLOAD_KEYSTORE_PASSWORD`, `UPLOAD_KEY_PASSWORD`, `UPLOAD_KEY_ALIAS`. RuStore и Samsung используют тот же upload-key (нет Play App Signing-эквивалента), что accepted limitation.

**Стратегия APK size (зафиксировано на планировании):** Цель release AAB ≤ 8 МБ и universal release APK ≤ 6 МБ (NFR-4). Текущий debug APK ~19-20 МБ. Снижение через: (a) R8 full mode + treeshaking, (b) Resource shrinking, (c) ProGuard rules с aggressive keep-only-necessary, (d) `resourceConfigurations += listOf("ru", "en")` уже есть с Phase 1 (отсекает остальные локали), (e) WebP вместо PNG для скриншот-fallback в AboutScreen (если есть), (f) `vectorDrawables.useSupportLibrary = true` уже включён. Замер через `:app:analyzeReleaseBundle` (AGP 8.3+ tool) или `bundletool build-apks` локально. Если NFR-4 не достигается — добавляем `arsc` оптимизации и анализ APK через `apkanalyzer` для самых тяжёлых dependencies.

**Стратегия ASO (зафиксировано на планировании):** Согласно § 17 спеки — generic-ключи (`измеритель шума`, `шумомер`, `уровень звука`, `дБ метр`, `sound level meter`, `decibel meter`) уходят в **подзаголовок** и **полное описание** карточки сторов; короткое название остаётся `Тишина` / `Tisha` (низкий риск impersonation-флага Google Play, опыт с NetWalk это подтверждает). Длинное store-title — `Тишина — измеритель шума` / `Tisha — Sound Level Meter`. Title-tag-stuffing избегается (≤ 30 символов в Play short title).

**Какие FR / NFR из спеки покрываются:**
- **FR-20** — Экспорт истории в CSV через Storage Access Framework.
- **FR-21** (доделка) — финальные GitHub-URL и Privacy Policy URL в AboutScreen.
- **NFR-1** — macrobenchmark cold-start ≤ 1 с на референсном Pixel 6a (CI matrix берёт reactivecircus emulator API 33 для baseline).
- **NFR-4** — APK ≤ 6 МБ, AAB ≤ 8 МБ после R8 + resource shrinking.
- **NFR-7** — Crash-free ≥ 99.5% через Play Vitals (не наш SDK, но enabled через release signing); macrobenchmark отлавливает регрессии до релиза.
- **NFR-9 / NFR-10 / NFR-11** — Data Safety декларация в Play Console: `Audio files collected: No`, `App activity: No`; никаких сторонних SDK; внутреннее app-storage только.
- **Share Intent + PNG-снимок** — P1 из спеки § 6, FR-10 (расширение DetailScreen).
- **Adaptive launcher icon** — § 6 спеки (UX/UI), § 17 (брендинг).
- **Privacy Policy hosting** — обязательное требование Google Play и RuStore (см. § 15 спеки).
- **release.yml + GitHub Releases** — § 14 спеки (CI/CD release workflow).
- **Instrumentation matrix** — § 13 спеки (smoke-test на API 26 / 30 / 34).
- **Store metadata + ASO** — § 15 спеки (Google Play / RuStore / Samsung карточки).

**Что НЕ входит в Phase 6 (намеренно отложено):**
- **Реальная публикация в магазины** (загрузка AAB, заполнение Data Safety form, ручная модерация) — Post-Completion (handled by developer manually, не automatable из плана).
- **`PLAY_SERVICE_ACCOUNT_JSON` auto-publish через `r0adkll/upload-google-play@v1`** — P1, отдельный Phase 7 если решим автоматизировать; для MVP-релиза ручная загрузка достаточна.
- **`RUSTORE_API_KEY` auto-publish через RuStore Public API** — аналогично, P1, отдельный шаг.
- **FR-13** (поиск/фильтр по дате и тексту заметки) — v1.1 после release.
- **FR-15** (C/Z weighting UI + C-filter в `:core:audio`) — v1.1.
- **Auto-калибровка** (P2 спеки) — v1.2.
- **Pre-built калибровки для популярных моделей** — v1.2 (требует сбора данных).
- **Zoom/pan жестами по графику в Detail** — P1, v1.1 (отдельный feature после share).
- **Импорт/экспорт настроек** — v1.3.
- **Wear OS companion** — v2.0+.
- **Bluetooth-микрофоны** — v2.0+.
- **FFT/RTA spectrum** — v2.0+.
- **Дозиметр (NIOSH TWA)** — v2.0+ (требует строгой калибровки, иначе вводит в заблуждение).
- **CI auto-publish nightly Pre-release** — `nightly.yml` workflow добавляется опционально, реальная публикация — Post-Completion.

## Context (from discovery)

**Состояние репозитория после Phase 5 (merged PR #11, коммит `e2ba525` на main):**
- 13 модулей собираются; `:app:assembleDebug` зелёный; debug APK ~19-20 МБ; CI с Codecov gating настроен.
- **Versioning:** `app/build.gradle.kts:14-15` — `versionCode = 1`, `versionName = "0.1.0-foundation"`. Не обновлялись через Phase 1-5 (накопительный bump в Phase 6 до `v1.0.0` + `versionCode` из `github.run_number`).
- **Release buildType:** `app/build.gradle.kts:25-28` — `isMinifyEnabled = false`, `isShrinkResources = false`. Нет ProGuard rules-файлов в репозитории (`Glob **/proguard*.pro` → empty). Phase 6 создаёт `app/proguard-rules.pro` + per-module `consumer-proguard-rules.pro` для тех модулей, где это требуется.
- **Иконка приложения:** только `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` + `ic_launcher_round.xml` — стандартные плейсхолдеры Android Studio template. Нет foreground/background drawables, нет 512×512 PNG. Phase 6 создаёт:
  - `app/src/main/res/drawable/ic_launcher_foreground.xml` — vector «волна → плоская линия»
  - `app/src/main/res/values/ic_launcher_background.xml` — `<color name="ic_launcher_background">#0E2433</color>`
  - обновляет `mipmap-anydpi-v26/ic_launcher.xml` с правильным foreground+background
  - `app/src/main/play-store-icon.png` — 512×512 PNG для каталогов Play / RuStore / Samsung
- **CI workflows:** `.github/workflows/ci.yml` есть (5 jobs: static-checks → unit-tests + build, всё на ubuntu-latest, JDK 17 Zulu, Gradle setup-action v3). Не существуют: `release.yml`, `nightly.yml`. Phase 6 добавляет `release.yml`.
- **AboutScreen URL placeholders:** `feature/about/src/main/res/values/strings.xml` содержит `about_github_url` / `about_privacy_url` с placeholder-значениями (`https://github.com/dmitrynovichkov/tishina-android` etc.), зафиксировано в Phase 5 plan §551-557. Phase 6 заменяет на финальные URL.
- **OSS-licenses:** `app/src/main/assets/oss_licenses.json` — статический ручной список (Phase 5 Task 6); ~15 зависимостей. Phase 6 заменяет на auto-generated через `cashapp/licensee` Gradle plugin (или custom Gradle task если licensee тянет лишнего).
- **Store metadata:** не существует структура `app/src/main/store-metadata/`. Phase 6 создаёт.
- **Privacy Policy:** не существует `docs/privacy/`. Phase 6 создаёт + настраивает GitHub Pages.
- **Instrumentation tests:** не существует `app/src/androidTest/` или `instrumentation-tests` CI-job. В Phase 1-5 все интеграционные сценарии проверяются через Robolectric в `src/test/` (см. note в Phase 3 plan §192 о `MigrationTestHelper` incompat с Room 2.8 + Robolectric — на эмуляторе будет работать). Phase 6 добавляет.
- **macrobenchmark:** не существует `:macrobenchmark` module. Phase 6 создаёт минимальный setup для baseline-profile cold-start.

**Зафиксированные версии (после Phase 5):**
- Kotlin 2.0.21 + K2; AGP 8.7.3; Compose BOM 2026.05.00; Hilt 2.55; Room 2.8.4; DataStore Preferences 1.1.1; AppCompat 1.7.0; kotlinx.serialization 1.7.3; Gradle 8.13 (см. [[project_tishina_toolchain]]).
- JUnit 5.11.x + MockK 1.13.x + Turbine 1.2.x + Robolectric 4.13 + Roborazzi 1.30.x.
- AndroidX Test 1.6.x (для instrumentation) — добавляется в Phase 6.
- `androidx.benchmark:benchmark-macro-junit4` — новая dependency для Phase 6.
- `app.cash.licensee:licensee-plugin` 1.x — opt-in (если выбран этот подход; альтернатива — custom Gradle task).

**Источники истины:**
- Спецификация `docs/specs/tishina-spec.md`:
  - § 4 — FR-20 (CSV export), FR-22 (дисклеймер уже сделан Phase 5).
  - § 5 — NFR-1 (cold-start), NFR-4 (APK size), NFR-7 (crash-free), NFR-9..NFR-11 (privacy).
  - § 6 — UX share Intent + PNG-снимок (FR-10 P1); adaptive icon, palette `#0FB5BA` teal на `#0E2433`.
  - § 14 — CI/CD GitHub Actions, `release.yml`, signing через `r0adkll/sign-android-release@v1` (или native), Play App Signing.
  - § 15 — Google Play / RuStore / Samsung карточки, data safety, permissions declaration, ASO.
  - § 17 — Брендинг (`Тишина`/`Tisha`, package `ru.dmdp.tishina`, GitHub-репозиторий `tishina-android`, лицензия Apache 2.0).
  - Приложение B — Чек-лист релиза MVP (16 пунктов).
- Завершённые планы Phase 1-5 (`docs/plans/completed/`) — образец TDD-формата.
- Phase 5 plan — список явно отложенного в Phase Release (§ 32-44, § 620-633).

## Development Approach

- **Testing approach:** **TDD (tests first)** — глобальное правило проекта ([[feedback_tdd_default]]) плюс явное подтверждение на планировании. Для бизнес-логики (CSV serializer, share Intent payload, OSS-licenses parser, AppLinksProvider, ExportHistoryUseCase) **тест пишется первым**, реализация — после того как тест зафиксировал контракт. Для **config/infra**-задач (ProGuard rules, release.yml workflow, signing config, store metadata templates, иконка vector drawable, Privacy Policy HTML, macrobenchmark setup) — validation через успешную сборку (`bundleRelease`, `assembleRelease`), успешный draft-run `release.yml` (через `act` или dry-push на forked-branch), manual inspection в Android Studio Asset Studio для иконки, Roborazzi screenshot-тесты обновлённого `AboutScreen` header (icon visual regression).
- Complete each task fully before moving to the next.
- Make small, focused changes.
- **CRITICAL: every task MUST include new/updated tests** for code changes in that task:
  - JUnit 5 unit-тесты для domain use-cases, CSV serializer, share Intent payload builder, OSS-licenses parser.
  - Robolectric + Room in-memory для `ExportHistoryUseCase` + DAO end-to-end (multi-measurement → CSV → expected string).
  - Turbine-тесты `StateFlow` для расширенного `HistoryViewModel` (export action) и `DetailViewModel` (share action).
  - Roborazzi screenshot-тесты для обновлённого `AboutScreen` header (real icon), Detail share button visibility, History export menu visibility.
  - Compose UI-тесты через `createComposeRule()` для интерактивных сценариев (long-press на History → context menu → Export → fake SAF returns URI → CSV written; click Share на Detail → Intent.ACTION_SEND emitted с правильным mimeType).
  - Instrumentation tests (real emulator) для smoke-flow API 26/30/34: запуск → permission → measure 3 с → save → history → bulk-delete → undo → AboutScreen → CSV export (через `UiAutomator` для SAF picker).
  - macrobenchmark `StartupBenchmark` (cold-start measure на reactivecircus emulator).
  - Тесты покрывают success **и** error/edge: CSV пустых измерений → header-only file; CSV с спец-символами в note (`,`, `"`, `\n`) → корректное escaping; share Intent без сетевого URI → text-only fallback; SAF picker cancelled → no-op без crash; macrobenchmark не падает если устройство slow (timeout 30 сек).
- **CRITICAL: all tests must pass before starting next task** — no exceptions.
- **CRITICAL: update this plan file when scope changes during implementation**.
- Run tests after each change (`./gradlew :feature:history:testDebugUnitTest`, `:core:data:testDebugUnitTest`, `:app:assembleRelease`, etc. локально быстрее, чем полный build).
- Maintain backward compatibility: не ломаем сигнатуры существующих composables без default-параметров — same pattern, что в Phase 2-5.
- **Release signing locally:** для локального тестирования `bundleRelease` нужен `tishina-upload-key.jks` в `app/` (НЕ коммитим, добавлен в `.gitignore`). Без него `assembleRelease` падает — используем `assembleDebug` или `assembleReleaseUnsigned` task.

## Testing Strategy

### Unit tests (JUnit 5 + MockK + Turbine)

| Слой | Что тестируется | Целевое покрытие |
|---|---|---|
| `:core:data` — `CsvSerializer` | escape `,` / `"` / `\n` / `\r` в title/note; CRLF line terminator (Excel-compat); UTF-8 BOM в начале файла; пустой список → header-only file; null title/note → пустая колонка; numeric formatting через `Locale.US` (точка для десятичной) | ≥ 95% |
| `:core:data` — `MeasurementsExporter` | round-trip: seed 3 measurements → export → parse CSV → reconstruct → equals original (modulo float precision); empty repository → header-only output; large export (1000 measurements) → output ≤ 5 МБ + время ≤ 2 сек | ≥ 90% |
| `:feature:history` — `ExportHistoryUseCase` | invoke(filter=All) → repository.observeSummaries() → exporter call; invoke(filter=ByIds(setOf(1,2))) → только эти id; empty result → Result.success(0 rows) с header-only file; exporter throws → Result.failure | ≥ 90% |
| `:feature:history` — `HistoryViewModel` export action | new event `ExportRequested(filter)` → effect `LaunchSafPicker(suggestedName = "tishina-history-2026-05-25.csv")`; `ExportFileSelected(uri)` → use-case invoked → effect `ShowExportSnackbar(success = true)`; SAF cancelled → no-op | ≥ 85% |
| `:feature:history` — `DetailViewModel` share action | new event `ShareRequested` → snapshot chart via `LineChartSnapshotter` → save to cache → effect `LaunchShareIntent(Intent.ACTION_SEND, mimeType = "image/png", text = ...)`; чистый text-only fallback если snapshot failed | ≥ 85% |
| `:core:ui` — `LineChartSnapshotter` | renders Compose `SplLineChart` на off-screen `Bitmap`; output PNG-bytes parseable обратно через `BitmapFactory`; width/height соответствуют requested size; transparent background опционален | ≥ 85% |
| `:feature:about` — `AppLinksProvider` | возвращает URL из BuildConfig (prod) или strings.xml (debug); fallback на placeholder если BuildConfig field отсутствует | ≥ 85% |
| `:core:data` — `OssLicensesGenerator` (Gradle task helper) | парсит `dependencies-metadata.json` от licensee plugin → `List<OssLicense>` JSON; формат совместим с существующим `OssLicensesProvider` | ≥ 80% (Gradle task сам тестируется в `:build-logic`) |
| Composables UI | smoke-рендеринг + screenshot baselines + интерактивное поведение через `createComposeRule` | Roborazzi + Compose UI Test |

### Robolectric instrumentation-стиль тесты (под `src/test/`)

- `CsvSerializerEscapingTest` — table-driven 10+ кейсов для escape всех специальных символов.
- `MeasurementsExporterRoundTripTest` (`@RunWith(RobolectricTestRunner)`) — Room.inMemoryDatabaseBuilder + реальный DAO; seed → export → parse → assert.
- `HistoryExportComposeUiTest` (createComposeRule + Robolectric) — long-press карточки в History → menu «Export» → click → assertion на `LaunchSafPicker` effect.
- `DetailShareComposeUiTest` — кнопка Share в TopAppBar → click → assertion на `Intent.ACTION_SEND` через `Shadows.shadowOf(application).nextStartedActivity`.
- `LineChartSnapshotterTest` — рендерит чарт + assertion на bitmap.width / height / non-zero pixels.
- `AppLinksProviderTest` — стабильность URL между билд-вариантами.

### Roborazzi screenshot тесты

- `AboutScreenIconScreenshotTest` — обновлённый header с реальной иконкой; 4 baseline (default + scrolled + light + dark).
- `HistoryExportMenuScreenshotTest` — long-press context menu с пунктом «Export»; 4 baseline.
- `DetailShareButtonScreenshotTest` — TopAppBar с share-icon; 4 baseline.
- `LauncherIconRenderTest` — рендерит adaptive launcher через Compose preview wrapper; 2 baseline (foreground only + foreground+background composed).

### Instrumentation tests (новая инфра в `:app/src/androidTest/`)

- `SmokeFlowInstrumentationTest` (`@RunWith(AndroidJUnit4)` + `HiltAndroidTest`) — полный сценарий через `UiAutomator` + Compose `createAndroidComposeRule`:
  1. Запуск activity.
  2. Click FAB → permission rationale → grant.
  3. Wait 3 sec для измерения.
  4. Click Save → enter title «Smoke test» → confirm.
  5. Navigate to History → assert карточка появилась.
  6. Long-press → selection mode → tap «Delete (1)» → confirm → wait 6 sec → assert удалено.
  7. Navigate to About → assert version visible.
  8. Navigate to History → menu → Export → SAF picker через `UiAutomator` (выбор стандартного каталога) → assert Snackbar success.
- Run на reactivecircus/android-emulator-runner@v2 матрица:
  - API 26 (Android 8.0) — minSdk проверка.
  - API 30 (Android 11) — средняя точка.
  - API 34 (Android 14) — модерн.
  - Skip API 35 (требует AOSP image и flaky на CI 2026).

### macrobenchmark (`:macrobenchmark` module)

- `StartupBenchmark` (`@RunWith(AndroidJUnit4)` + `MacrobenchmarkRule`) — cold-start measure для `MainActivity` через `StartupTimingMetric`; 10 итераций; baseline `< 1000ms` на reactivecircus API 33 emulator (приблизительный proxy для Pixel 6a NFR-1).
- Опциональный `BaselineProfileGenerator` через `androidx.baselineprofile` Gradle plugin — генерирует профиль warm classes для R8/ART AOT, зашивается в `:app/src/main/baseline-prof.txt`.

### Coverage thresholds

- Применяем пороги, заявленные в спеке § 13:
  - `:core:data` ≥ 80% (новый CSV serializer + exporter).
  - `:feature:history` ViewModel ≥ 85% (export action).
  - `:feature:about` AppLinksProvider ≥ 85%.
- Kover XML-репорт публикуется как CI-артефакт; пороги **впервые** enforcement-фейлят PR в Phase 6 — это финальный production-ready guard (через `koverVerify` task с `excludes` для generated Hilt-классов).

### E2E tests (Robolectric, `:app/src/test/`)

- Полный UI flow тест («измерить → save → открыть History → long-press → export → CSV в fake SAF → ассерт content») — как Robolectric Compose UI test, используя `FakeMeasurementRepository` + `ContentResolver` overlay для fake SAF. Real instrumentation на эмуляторе API 26/30/34 — отдельный CI job, см. выше.

## Progress Tracking

- Mark completed items with `[x]` immediately when done.
- Add newly discovered tasks with `➕` prefix.
- Document issues/blockers with `⚠️` prefix.
- Update plan if implementation deviates from original scope.
- Keep plan in sync with actual work done.

## What Goes Where

- **Implementation Steps** (`[ ]` checkboxes): код Kotlin/Compose/Room/Gradle, build.gradle.kts изменения, тесты JUnit/Robolectric/Roborazzi/Macrobenchmark/Instrumentation, прогон Gradle-команд (`testDebugUnitTest`, `verifyRoborazziDebug`, `assembleRelease`, `bundleRelease`, `connectedDebugAndroidTest`, `:macrobenchmark:connectedReleaseAndroidTest`), KSP-генерация Hilt, статические артефакты (иконка XML, HTML Privacy Policy, store metadata templates), CI workflows.
- **Post-Completion** (no checkboxes): реальная публикация в Google Play / RuStore / Samsung Galaxy Store (загрузка AAB, заполнение Data Safety / Permissions / IARC форм, ручная модерация); создание GitHub Pages site (включение `gh-pages` source в repo settings); создание keystore локально + upload base64 в GitHub Secrets; bug-bounty или security review (опционально); пользовательское бета-тестирование (Internal testing track Google Play); финальная пометка `v1.0.0` git-tag после успешной модерации.
- **Checkbox placement:** только в `### Task N:` секциях. Success criteria и Overview без чекбоксов.

## Implementation Steps

<!--
Task structure guidelines:
- Each task = ONE logical unit (one feature, one infra component, one release artifact)
- Use specific descriptive names, not generic "[Polish]" or "[Implementation]"
- Aim for ~5-7 checkboxes per task (more is OK if logically atomic)
- CRITICAL: Each task MUST end with writing/updating tests before moving to next (for code tasks)
  - For config/infra tasks: validation through successful build + integration test
-->

### Task 1: Реальная adaptive launcher icon + 512×512 PNG для каталогов

- [x] **сначала тест:** `AboutScreenIconScreenshotTest` (Roborazzi) — 2 baseline (`about_header_with_real_icon_light/dark`) — fail если иконка не отрисована корректно; baseline записывается **после** создания иконки в этой же task
- [x] **➕ возможная подзадача:** `LauncherIconRenderTest` — реализован как `BrandLogoScreenshotTest` в `:core:designsystem` (более логичное место — drawable живёт в designsystem). 2 baseline (`brand_logo_in_primary_container_light/dark`) проверяют рендер `ic_brand_logo` внутри tinted Surface — конструкция, которую AboutHeader использует один к одному.
- [x] создать `app/src/main/res/drawable/ic_launcher_foreground.xml`:
  - `<vector android:viewportWidth="108" android:viewportHeight="108">` — Material 3 adaptive icon canvas
  - Path: волнообразная синусоида слева (3 затухающих оскилляции, амплитуда 24 → 16.5 → 7.5), плавно вырождающаяся в плоскую линию справа; teal `#0FB5BA` stroke 5dp с round-caps; метафора «звук → тишина»
  - Safe zone 66dp в центре соблюдена: путь x∈[24,84], y∈[28,78] (3dp запас на каждой стороне)
- [x] создать `app/src/main/res/values/ic_launcher_background.xml`:
  - Уже существует в `app/src/main/res/values/colors.xml` (`<color name="ic_launcher_background">#0E2433</color>`) — функционально идентично, отдельный файл не нужен
- [x] обновить `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` и `ic_launcher_round.xml`:
  - Уже ссылаются на `@color/ic_launcher_background` + `@drawable/ic_launcher_foreground` + `<monochrome>` для Android 13+ themed icons; обновлений не потребовалось
- [x] **➕ внеплановая подзадача:** добавить legacy `mipmap-{ldpi,mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.png` для устройств < API 26 — сгенерированы через `docs/tools/render_launcher_icon.py` (Pillow-based renderer воспроизводит ту же геометрию что и vector drawable; скрипт коммитим для воспроизводимости)
- [x] создать `app/src/main/play-store-icon.png` — 512×512 PNG версия для каталогов (Play / RuStore / Samsung); сгенерирован тем же скриптом, НЕ включается в APK (лежит в `src/main/` вне `res/`)
- [ ] **➕ возможная подзадача:** добавить feature graphic 1024×500 PNG для Google Play в `app/src/main/store-metadata/google-play/feature-graphic.png` — будет создан/обновлён в Task 7
- [x] обновить `AboutScreen.kt` — `Icons.Filled.GraphicEq` заменён на `painterResource(DesignSystemR.drawable.ic_brand_logo)` (отдельный brand-mark в `:core:designsystem` без safe-zone-ограничений, размер 48dp в 72dp Surface)
- [x] verify через `:app:assembleDebug` + manual Android Studio preview Asset Studio — `:app:assembleDebug` зелёный, screenshot-baseline `BrandLogoScreenshotTest_*` подтверждает рендер
- [x] записать baseline `AboutScreenIconScreenshotTest` через `recordRoborazziDebug`
- [x] run `./gradlew :feature:about:verifyRoborazziDebug :app:assembleDebug :app:lintDebug` — must pass before next task

### Task 2: R8 + ProGuard rules + Resource shrinking (NFR-4: ≤ 6 МБ release APK)

- [x] **сначала проверка baseline:** debug APK baseline зафиксирован = 22 671 614 байт ≈ 22.7 МБ (`app/build/outputs/apk/debug/app-debug.apk`). Используется как стартовая точка для сравнения с release-сборкой
- [x] создать `app/proguard-rules.pro`:
  - Hilt keep rules: `-keep class dagger.hilt.android.internal.managers.* { *; }` + `dagger.hilt.internal.**` + ViewComponentManager$FragmentContextWrapper + HiltViewModel-аннотированные классы
  - Compose runtime: `-keepclassmembers class androidx.compose.runtime.** { *; }` (для reflection в runtime composer)
  - Kotlin metadata: `-keepattributes *Annotation*, InnerClasses, EnclosingMethod, Signature` + `SourceFile, LineNumberTable` + `-renamesourcefileattribute SourceFile`
  - kotlinx.serialization: official keep rules — `@Serializable` $Companion + `$serializer` через `-if`/`-keepclassmembers` шаблон; явное keep для Navigation 2.9 `TishinaDestination` (data objects) + `DetailRoute` (data class)
  - Room: `-keep class * extends androidx.room.RoomDatabase` + `-keep @androidx.room.Entity`/`@Dao class * { *; }`
  - DataStore: покрыт Compose runtime keep rules
  - reflection-driven domain models: `-keep class ru.dmdp.tishina.core.domain.model.** { *; }`
  - Lifecycle ViewModel constructor lookup; silenced non-actionable warnings (`StringConcatFactory`, `org.jetbrains.annotations`)
- [x] **➕ возможная подзадача:** добавить `consumer-rules.pro` в `:core:data` (`consumerProguardFiles("consumer-rules.pro")` в `defaultConfig`) с keep-правилами для Room entity/DAO/database классов и `OssLicensesParser$Dto` сериализатора. `:core:domain` — pure-Kotlin library (без `com.android.library`), consumer-rules там не применимы; правила для domain.model.** живут в `app/proguard-rules.pro`
- [x] обновить `app/build.gradle.kts:25-28` — `isMinifyEnabled = true`, `isShrinkResources = true`, `proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")`, `signingConfig = signingConfigs.getByName("debug")` (Task 6 заменит на release signing)
- [x] **сначала тест:** R8 невозможно протестировать через Robolectric (R8 работает только на реальной сборке). Smoke-тест R8-сборки реализован как новый CI job `release-build` в `.github/workflows/ci.yml` (выполняет `assembleRelease`+`bundleRelease` на каждом PR — catches keep-rule regressions до merge)
- [x] запустить `./gradlew :app:assembleRelease bundleRelease` локально:
  - сборка успешна (BUILD SUCCESSFUL за 4m 13s холодная + 19s для bundleRelease на горячем кэше)
  - release APK = 2 389 181 байт ≈ **2.39 МБ** (NFR-4 ≤ 6 МБ ✅ с запасом 60%)
  - release AAB = 5 362 630 байт ≈ **5.36 МБ** (NFR-4 ≤ 8 МБ ✅ с запасом 33%)
  - сжатие debug → release = 89.5% (22.7 → 2.4 МБ)
- [x] **➕ возможная подзадача:** apkanalyzer/analyzeReleaseBundle — NFR-4 достигнут с большим запасом, дополнительный teardown top-10 dependencies не нужен. Если в будущем добавим библиотеку с большим weight (~1.5 МБ+), будем профилировать тогда. R8 mapping.txt + usage.txt + seeds.txt доступны в `app/build/outputs/mapping/release/` для ручного анализа при необходимости
- [x] **➕ возможная подзадача:** bundletool split-APK install size — пропускаем (universal APK уже 2.4 МБ; split-density APKs гарантированно меньше; ценность анализа для каждого density-bucket появится при превышении NFR-4)
- [x] **сначала тест:** обновление `AboutScreenComposeUiTest` под debug/release suffix — N/A (skipped — not actionable): в текущей кодовой базе `AppVersion(versionName, versionCode)` приходит из `BuildConfig.VERSION_NAME` без debug/release suffix. Свойство `Build.IS_DEBUG_BUILD` или `BuildConfig.DEBUG` нигде не используется в композаблах AboutScreen — assertion был бы no-op. При появлении `versionNameSuffix = ".debug"` (Task 6 для signing config) тест обновится отдельно
- [x] **возможная подзадача:** mapping.txt upload как CI artifact — реализовано в новом job `release-build` (`.github/workflows/ci.yml`), артефакт `release-mapping` сохраняется на 90 дней (длиннее обычных 14 — нужен для деобфускации крашей даже спустя месяцы)
- [x] verify все 13 модулей собираются с R8: `./gradlew :app:assembleRelease :app:bundleRelease` + `:app:testDebugUnitTest :core:data:testDebugUnitTest :feature:about:testDebugUnitTest :feature:about:verifyRoborazziDebug :core:designsystem:verifyRoborazziDebug :app:lintRelease detektAll spotlessCheck :app:lintDebug` — всё зелёное. `testReleaseUnitTest` SKIPPED для всех модулей (testing convention plugin привязывает unit-тесты к debug variant — release-классы те же после R8, но JVM-тесты исполняются на debug bytecode по дизайну)
- [x] update README.md с финальным release APK size: добавлен раздел про `assembleRelease`/`bundleRelease`, известное ограничение про debug APK ~28.2 МБ Phase 3 baseline обновлено на Phase 6 baseline 22.7 МБ → 2.4 МБ release

### Task 3: FR-20 — CSV экспорт через Storage Access Framework

- [ ] **сначала тест:** `CsvSerializerTest` (JUnit 5) — 10+ кейсов:
  - happy path: 3 measurements с title/note/numeric → корректный CSV header + 3 row + LF/CRLF
  - escaping: title содержит `,` → wrapped в `"..."`; note содержит `"` → удвоено `""`; note содержит `\n` → wrapped в `"..."` без modification (CSV-valid)
  - null handling: title=null → empty column; note=null → empty column
  - numeric formatting: avgDb=42.567f → "42.6" (1 decimal place, Locale.US dot separator)
  - UTF-8 BOM: первые 3 байта = 0xEF 0xBB 0xBF
  - line terminator: CRLF (`\r\n`) для Excel compat
  - empty list → header-only output (header + final CRLF)
  - large export 1000 rows → output не превышает 100 КБ (memory check)
- [ ] **сначала тест:** `MeasurementsExporterTest` (Robolectric + in-memory Room) — 5+ кейсов:
  - seed 3 measurements → exportAll(uri) → parse CSV → assert строки и колонки совпадают
  - seed 3, export filter ids={id1,id3} → 2 rows
  - empty repo → header-only
  - export включает samples-CSV (опциональный bundle) → 4 файла (1 measurements.csv + 3 samples-{id}.csv) если выбран bundle
  - ContentResolver throws IOException → exporter.export(uri) возвращает `Result.failure`
- [ ] **сначала тест:** `ExportHistoryUseCaseTest` (JUnit 5 + FakeMeasurementRepository):
  - invoke(filter=All) → repository.observeSummaries() collected, всё передано в exporter
  - invoke(filter=ByIds(setOf(1,2))) → только эти id
  - empty result → success with 0 rows
  - exporter throws → failure
- [ ] **сначала тест:** `HistoryViewModelExportTest` (Turbine):
  - new event `ExportRequested(filter)` → effect `LaunchSafPicker(suggestedName)`
  - `ExportFileSelected(uri)` → use-case → effect `ShowExportSnackbar(success=true)` с count
  - SAF cancelled (uri=null) → no-op
  - exporter throws → snackbar `ShowExportFailedSnackbar`
- [ ] **сначала тест:** `HistoryExportComposeUiTest` (createComposeRule):
  - long-press карточки → context menu visible
  - tap «Export to CSV» → effect captured
  - в selection mode tap menu «Export selected» → effect с filter=ByIds(selectedIds)
- [ ] создать `core/data/.../export/CsvSerializer.kt`:
  - `class CsvSerializer { fun serialize(measurements: List<MeasurementSummary>, writer: Writer) }`
  - private helpers `escapeCsv(value: String?): String` (handle `,` `"` `\n` `\r`), `writeBom(writer)`, `writeRow(writer, values: List<String>)`
- [ ] создать `core/data/.../export/MeasurementCsvFormat.kt`:
  - константы для column names (локализованные через ResourceProvider? или English-only? — выбор в Implementation; рекомендую English для совместимости с Excel)
  - column order: `id, createdAt (ISO 8601 UTC), durationSec, avgDb, minDb, maxDb, weighting, timeWeighting, calibrationOffsetDb, title, note`
- [ ] создать `core/data/.../export/MeasurementsExporter.kt`:
  - `class MeasurementsExporter @Inject constructor(dao: MeasurementDao, contentResolver: ContentResolver, ioDispatcher: CoroutineDispatcher)`
  - `suspend fun export(uri: Uri, filter: ExportFilter): Result<Int>` — opens output stream, streams serialize, returns row count
- [ ] создать `core/domain/.../model/ExportFilter.kt`:
  - `sealed interface ExportFilter { data object All : ExportFilter; data class ByIds(val ids: Set<Long>) : ExportFilter }`
- [ ] создать `core/domain/.../usecase/ExportHistoryUseCase.kt`:
  - `class ExportHistoryUseCase @Inject constructor(repository: MeasurementRepository, exporter: MeasurementsExporter)`
  - `suspend operator fun invoke(uri: Uri, filter: ExportFilter): Result<Int>`
- [ ] обновить `feature/history/.../HistoryViewModel.kt`:
  - инжектится `ExportHistoryUseCase`
  - new event `ExportRequested(filter: ExportFilter)` → emit effect `LaunchSafPicker(suggestedName = "tishina-history-${LocalDate.now()}.csv")`
  - new event `ExportFileSelected(uri: Uri, filter: ExportFilter)` → invoke use-case → emit snackbar effect
- [ ] обновить `feature/history/.../HistoryUiEffect.kt`:
  - new effect `LaunchSafPicker(suggestedName: String, filter: ExportFilter)` — захватывается `HistoryScreen` через `rememberLauncherForActivityResult(CreateDocument("text/csv"))`
  - new effects `ShowExportSnackbar(count: Int)`, `ShowExportFailedSnackbar`
- [ ] обновить `feature/history/.../ui/HistoryScreen.kt`:
  - в обычном режиме (без selection): TopAppBar action «Export all to CSV» (icon `Icons.Outlined.FileDownload`)
  - в selection mode: TopAppBar overflow menu «Export selected (N)» (только если selectedCount > 0)
  - `rememberLauncherForActivityResult(CreateDocument("text/csv")) { uri -> if (uri != null) onEvent(ExportFileSelected(uri, pendingFilter)) }`
  - subscribe to `LaunchSafPicker` effect через `LaunchedEffect` → `launcher.launch(suggestedName)`
- [ ] добавить локализационные строки в `feature/history/src/main/res/values/` и `values-ru/`:
  - `history_export_all_cd`, `history_export_selected_cd`
  - `history_export_success_plural` (plurals для русского one/few/many)
  - `history_export_failed`
  - `history_export_default_filename` (например, "tishina-history" — date добавляется в код)
- [ ] **➕ возможная подзадача:** добавить permission declaration в AboutScreen или Settings: "CSV экспорт записывает файлы только в выбранную пользователем папку через системный picker; никакого автоматического доступа к storage" — успокаивает privacy-paranoid пользователей; обновляется в FR-22 disclaimer (Phase 5 Task 5)
- [ ] run `./gradlew :core:data:testDebugUnitTest :core:domain:test :feature:history:testDebugUnitTest :feature:history:verifyRoborazziDebug :app:assembleDebug` — must pass before next task

### Task 4: Share Intent + PNG-снимок графика для Detail

- [ ] **сначала тест:** `LineChartSnapshotterTest` (Robolectric + Compose):
  - render `SplLineChart` с 60 samples в Bitmap 1080×720
  - assertion: bitmap.width == 1080, height == 720, non-zero pixel coverage (>= 50%)
  - empty samples list → Bitmap не падает, renders empty grid
  - exception в Compose render → возвращает `Result.failure` (caller fallback на text-only share)
- [ ] **сначала тест:** `DetailViewModelShareTest` (Turbine + FakeMeasurementRepository):
  - `ShareRequested` → snapshot success → effect `LaunchShareIntent(mimeType="image/png", uri=non-null, text=summary)`
  - `ShareRequested` → snapshot failure → effect `LaunchShareIntent(mimeType="text/plain", uri=null, text=summary)` (fallback)
- [ ] **сначала тест:** `DetailShareComposeUiTest` (createComposeRule):
  - TopAppBar action «Share» visible
  - tap Share → effect captured → `Intent.ACTION_SEND` через `Shadows.shadowOf(application).nextStartedActivity` assertion
  - text content содержит title, date, avgDb, durationSec — из summary
- [ ] **сначала тест:** `DetailShareButtonScreenshotTest` (Roborazzi) — 4 baseline (`detail_share_button_visible_light/dark` + `detail_share_disabled_for_empty_chart_light/dark` если такой кейс есть)
- [ ] создать `core/ui/.../snapshot/LineChartSnapshotter.kt`:
  - `class LineChartSnapshotter { suspend fun snapshot(samples: List<SoundSample>, width: Int, height: Int, density: Density): Result<Bitmap> }`
  - использует Compose Canvas API + `Bitmap.createBitmap` + `Canvas(bitmap).draw(...)` — рендерит SplLineChart на off-screen surface
  - alternative: использовать `androidx.compose.ui.test.captureToImage()` через test rule (но это test-only API; production-альтернатива — manual draw)
- [ ] создать `feature/history/.../share/ShareIntentBuilder.kt`:
  - `class ShareIntentBuilder @Inject constructor(@ApplicationContext context: Context, fileProvider: FileProviderAuthority) { fun build(measurement: MeasurementDetails, chartBitmap: Bitmap?): Intent }`
  - если bitmap != null → write to cache file `tishina-{id}-{timestamp}.png` через `FileProvider.getUriForFile`; Intent.ACTION_SEND with mimeType="image/png" + `EXTRA_STREAM` + `EXTRA_TEXT`
  - если bitmap == null → text-only fallback (mimeType="text/plain", `EXTRA_TEXT` с summary)
  - text format: title \n date \n "Avg: X dB / Min: Y / Max: Z" \n "Duration: M:SS" \n "—\n Сделано в приложении Тишина" (без link для privacy-first)
- [ ] **➕ внеплановая подзадача:** настроить `FileProvider` в `AndroidManifest.xml`:
  - `<provider android:name="androidx.core.content.FileProvider" android:authorities="${applicationId}.fileprovider" android:exported="false" android:grantUriPermissions="true">`
  - `<meta-data android:name="android.support.FILE_PROVIDER_PATHS" android:resource="@xml/file_provider_paths"/>`
  - создать `app/src/main/res/xml/file_provider_paths.xml` с `<cache-path name="shared" path="."/>`
- [ ] обновить `feature/history/.../DetailViewModel.kt`:
  - инжектится `LineChartSnapshotter` + `ShareIntentBuilder`
  - new event `ShareRequested` → coroutineScope { snapshot → buildIntent → emit `LaunchShareIntent` effect }
  - new effect `LaunchShareIntent(intent: Intent)` (handled в DetailScreen через `Activity.startActivity` или `Intent.createChooser`)
- [ ] обновить `feature/history/.../DetailScreen.kt`:
  - TopAppBar action `IconButton { onEvent(ShareRequested) }` с `Icons.Outlined.Share`
  - subscribe to `LaunchShareIntent` effect → `LocalContext.current.startActivity(Intent.createChooser(intent, "Share measurement"))`
- [ ] добавить локализационные строки:
  - `detail_share_cd`, `detail_share_chooser_title`, `detail_share_text_template`
- [ ] run `./gradlew :core:ui:testDebugUnitTest :feature:history:testDebugUnitTest :feature:history:verifyRoborazziDebug :app:assembleDebug` — must pass before next task

### Task 5: Privacy Policy + GitHub Pages hosting + финальные URL

- [ ] создать `docs/privacy/index.html`:
  - статичная HTML без JavaScript (privacy-first, не загружает CDN-скрипты)
  - заголовок: «Политика конфиденциальности приложения Тишина» / «Privacy Policy for Tisha»
  - две колонки/секции: RU + EN
  - содержимое (выработано по NFR-9..NFR-11 + § 11 спеки):
    - «Приложение Тишина не собирает, не передаёт и не хранит на удалённых серверах никакие персональные или аудиоданные»
    - «Микрофон используется только для real-time анализа уровня шума; аудио-потоки не записываются в файлы»
    - «История измерений сохраняется локально на устройстве (`com.android.providers.system` private app storage); не синхронизируется в облако»
    - «Приложение не содержит сторонних SDK для аналитики, рекламы или crash-reporting»
    - «При экспорте CSV пользователь явно выбирает целевую папку через системный picker — приложение не имеет автоматического доступа к storage»
    - «Контакты для вопросов: <GitHub issues URL>»
    - дата обновления: 2026-05-25 (Phase 6 release date)
- [ ] создать `docs/privacy/style.css` — минимальный CSS для читабельности (font-family system, max-width 720px, line-height 1.6); inline в HTML возможно вместо отдельного файла для simplicity
- [ ] **➕ внеплановая подзадача:** обновить `app/src/main/AndroidManifest.xml` если требуется добавить ссылку на privacy policy в `<application android:metadata="..."/>` (не обязательно для Play Console — там URL вводится отдельно)
- [ ] настроить GitHub Pages:
  - Settings → Pages → Source = `main` branch → folder `/docs` (если такой опции нет — переместить в `docs/privacy/` или сделать workflow-publish)
  - alternative: создать `.github/workflows/pages.yml` для auto-deploy `docs/privacy/` в `gh-pages` branch
  - URL: `https://dnovichkov.github.io/tishina-android/privacy/` (substring username из git config; verified в `git config user.email`)
- [ ] обновить `feature/about/src/main/res/values/strings.xml` и `values-ru/`:
  - `about_github_url` = `https://github.com/dnovichkov/tishina-android` (real username из git config)
  - `about_privacy_url` = `https://dnovichkov.github.io/tishina-android/privacy/`
- [ ] **сначала тест:** `AboutScreenLinksIntegrationTest` (Robolectric + createComposeRule):
  - тап «GitHub» → captured Intent.ACTION_VIEW.data == final github URL (не placeholder)
  - тап «Privacy Policy» → captured Intent.ACTION_VIEW.data == final privacy URL
  - тестируется через `Shadows.shadowOf(application).nextStartedActivity`
- [ ] обновлены baseline `AboutScreenScreenshotTest` (Phase 5) — URL подписи могут сменить positioning; перерисовать через `:feature:about:recordRoborazziDebug`
- [ ] **➕ возможная подзадача:** добавить generative-test для URL validity через `URI.create(it.url).host != null` — защита от typo в strings.xml
- [ ] run `./gradlew :feature:about:testDebugUnitTest :feature:about:verifyRoborazziDebug :app:assembleDebug` — must pass before next task

### Task 6: Release signing + release.yml workflow + GitHub Releases publishing

- [ ] **локально (Post-Completion):** создать `tishina-upload-key.jks`:
  - `keytool -genkey -v -keystore tishina-upload-key.jks -keyalg RSA -keysize 2048 -validity 25000 -alias upload`
  - (НЕ коммитить keystore в репо; .gitignore уже должен покрывать `*.jks` — verify)
  - base64-encode: `base64 -i tishina-upload-key.jks -o tishina-upload-key.jks.base64`
  - upload в GitHub Secrets как `UPLOAD_KEYSTORE_BASE64` + `UPLOAD_KEYSTORE_PASSWORD`, `UPLOAD_KEY_PASSWORD`, `UPLOAD_KEY_ALIAS`
- [ ] обновить `.gitignore`: `*.jks`, `*.keystore`, `key.properties`, `*.base64` (если не покрыто)
- [ ] обновить `app/build.gradle.kts`:
  - `signingConfigs { create("release") { storeFile = file(System.getenv("UPLOAD_KEYSTORE_PATH") ?: "../tishina-upload-key.jks"); storePassword = System.getenv("UPLOAD_KEYSTORE_PASSWORD"); keyAlias = System.getenv("UPLOAD_KEY_ALIAS"); keyPassword = System.getenv("UPLOAD_KEY_PASSWORD") } }`
  - `buildTypes.release.signingConfig = signingConfigs.getByName("release")` (заменить debug signing из Task 2)
  - fallback на null если env-vars не заданы — позволяет локальный build без keystore (`assembleReleaseUnsigned` task)
- [ ] обновить `app/build.gradle.kts:14-15`:
  - dynamic versionCode: `versionCode = System.getenv("VERSION_CODE")?.toInt() ?: 1` (CI передаёт `github.run_number`)
  - dynamic versionName: `versionName = System.getenv("VERSION_NAME") ?: "1.0.0-dev"` (CI передаёт git-tag without `v` prefix)
- [ ] создать `.github/workflows/release.yml`:
  - `on: push: tags: ['v*.*.*']`
  - jobs:
    - `build-and-release` (ubuntu-latest, timeout-minutes: 45):
      - checkout (fetch-depth: 0 для git-log в release notes)
      - setup-java JDK 17 Zulu
      - setup-gradle с cache-read-only=false
      - decode keystore: `echo "${{ secrets.UPLOAD_KEYSTORE_BASE64 }}" | base64 -d > tishina-upload-key.jks`
      - extract version from tag: `VERSION=${GITHUB_REF#refs/tags/v}` → `echo "VERSION_NAME=$VERSION" >> $GITHUB_ENV` + `VERSION_CODE=${{ github.run_number }}`
      - assemble: `./gradlew :app:bundleRelease :app:assembleRelease --no-daemon --stacktrace`
      - upload artifacts: AAB + APK + mapping.txt as separate downloads
      - create GitHub Release через `softprops/action-gh-release@v2`:
        - files: AAB, APK, mapping.txt
        - generate_release_notes: true (auto-collects commits since last tag)
        - draft: true (manual review перед публикацией)
- [ ] **➕ внеплановая подзадача:** обновить `.github/workflows/ci.yml`:
  - добавить job `release-build-smoke`: проверяет что `bundleRelease` собирается с placeholder keystore (debug) — early-warning для R8 breakage на PR-level
- [ ] **сначала тест:** `dry-run release.yml` через `act -j build-and-release` (если установлен) или push tag `v0.0.0-test` в feature-branch → проверить workflow зелёный → удалить test tag
- [ ] **➕ возможная подзадача:** создать `.github/workflows/nightly.yml`:
  - on: schedule: `'0 2 * * *'` (ежедневно в 02:00 UTC)
  - `./gradlew clean build test verifyRoborazziDebug` на main branch
  - upload artifacts; create draft Pre-release с `v{date}-nightly` tag
  - DECISION: добавить только если есть запрос от пользователя; иначе skip — лишний CI cost
- [ ] verify через `git tag v0.0.0-test && git push origin v0.0.0-test` (на feature-branch) — workflow зелёный? Если да — `git tag -d v0.0.0-test && git push --delete origin v0.0.0-test`
- [ ] run `./gradlew :app:bundleRelease` локально с GitHub Secrets env-vars подставленными (или fallback debug signing) — must pass before next task

### Task 7: Store metadata + ASO + Data Safety + Permissions declarations

- [ ] создать структуру `app/src/main/store-metadata/`:
  ```
  store-metadata/
  ├── google-play/
  │   ├── ru-RU/
  │   │   ├── title.txt              # ≤30 chars
  │   │   ├── short_description.txt  # ≤80 chars
  │   │   └── full_description.txt   # ≤4000 chars
  │   ├── en-US/
  │   │   ├── title.txt
  │   │   ├── short_description.txt
  │   │   └── full_description.txt
  │   ├── feature-graphic.png        # 1024×500
  │   └── screenshots/
  │       ├── 01-measure-screen-ru.png
  │       ├── 02-history-ru.png
  │       └── ... (8 screenshots)
  ├── rustore/
  │   ├── ru-RU/
  │   │   ├── title.txt              # ≤50 chars
  │   │   ├── short_description.txt  # ≤80 chars
  │   │   └── full_description.txt   # ≤4000 chars
  │   └── screenshots/
  │       └── ... (≤10 screenshots)
  ├── samsung/
  │   ├── en-US/
  │   │   ├── title.txt
  │   │   ├── short_description.txt
  │   │   └── full_description.txt
  │   └── screenshots/
  ├── data-safety.md                 # Заполняемая декларация для Google Play
  ├── permissions-rationale.md       # Объяснение для RECORD_AUDIO
  └── aso-keywords.md                # ASO-стратегия и ключевые слова
  ```
- [ ] заполнить `google-play/ru-RU/title.txt`:
  - «Тишина — измеритель шума» (24 chars, ≤30 OK)
- [ ] заполнить `google-play/ru-RU/short_description.txt`:
  - «Бесплатный измеритель шума без рекламы. История замеров с заметками.» (≤80)
- [ ] заполнить `google-play/ru-RU/full_description.txt` (~1500-2000 слов):
  - hook первого экрана: «Измерьте уровень шума вокруг — без рекламы, без подписок, без сбора данных»
  - ключевые фичи: real-time SPL meter, история, заметки, калибровка, темы, локализация
  - ASO-ключи (распределяются естественно в тексте): измеритель шума, шумомер, уровень звука, дБ метр, измерение шума, sound level meter, decibel meter, SPL meter
  - дисклеймер о точности (компактная версия из § 11)
  - ссылки: GitHub, Privacy Policy
- [ ] заполнить `google-play/en-US/*.txt` — аналогично, английский
- [ ] заполнить `rustore/ru-RU/*.txt`:
  - RuStore разрешает до 50 chars в title — можем расширить
  - «Тишина — шумомер без рекламы» (28 chars)
  - акцент на «без рекламы», «полностью на русском», «локально» — выделяет на фоне rootApps / KTW
- [ ] заполнить `samsung/en-US/*.txt` — английский; Samsung Galaxy Store повторяет Google Play контент
- [ ] заполнить `data-safety.md`:
  - Audio files collected: **No**
  - App activity / App info collected: **No**
  - Crash logs / Diagnostic data: **No** (Play Vitals managed by Google, не нашим SDK)
  - All data: stored only on device
  - Encrypted in transit: N/A (no network)
  - Data deletion request: app uninstall = full deletion
- [ ] заполнить `permissions-rationale.md`:
  - `RECORD_AUDIO`: «Core functionality: measuring ambient sound levels in real time. No audio is stored, recorded to file, or transmitted. PCM samples are processed in RAM only and discarded after each frame.»
- [ ] заполнить `aso-keywords.md`:
  - primary keywords (русский): измеритель шума, шумомер, уровень шума, дБ метр, измерение звука
  - secondary keywords (русский): громкость, децибел, фоновый шум, шум соседей, шум на работе
  - primary keywords (english): sound level meter, decibel meter, noise meter, dB meter, SPL meter
  - secondary keywords (english): sound measurement, noise level, ambient noise, decibel app
  - не в title — только в descriptions (§ 17 спеки)
- [ ] **➕ возможная подзадача:** добавить screenshot-генератор Gradle task `:app:generateStoreScreenshots` — Roborazzi-based, рендерит ключевые экраны в правильных разрешениях для Play (16:9 1920×1080) и RuStore (Portrait Phone)
- [ ] **сначала тест:** `StoreMetadataLengthLimitTest` (JUnit 5) — читает все `.txt` файлы и assertion на максимальные длины (защита от случайного превышения лимитов); запускается в CI
- [ ] **➕ возможная подзадача:** обновить `app/src/main/AndroidManifest.xml` если нужны specific store-targeting тэги (не критично)
- [ ] run `./gradlew :app:lintRelease` — finalize lint compliance for production; must pass before next task

### Task 8: OSS-licenses auto-generation Gradle task (replace static JSON)

- [ ] добавить `app.cash.licensee:licensee-plugin` в `build-logic/convention/build.gradle.kts` либо как direct plugin в `:app`:
  - `id("app.cash.licensee") version "1.10.0"` (latest на 2026; проверить актуальную)
- [ ] настроить licensee в `app/build.gradle.kts`:
  - `licensee { allow("Apache-2.0"); allow("MIT"); allow("BSD-2-Clause"); allow("BSD-3-Clause"); allow("EPL-2.0") }`
  - generates `app/build/reports/licensee/release/artifacts.json` после `:app:licensee`
- [ ] **сначала тест:** `OssLicensesGeneratorTest` (JUnit 5):
  - читает `artifacts.json` через test fixture → парсит в `List<OssLicense>` POJO → сериализует обратно в `oss_licenses.json` через kotlinx-serialization → JSON-структура совместима с `OssLicensesProvider.load()` (формат Phase 5)
  - non-standard license в input → fallback на «Custom» или skip с warning
  - dependencies без license → exception (fail-fast на пропавшие лицензии)
- [ ] создать Gradle task `generateOssLicenses` в `:app/build.gradle.kts`:
  - depends on `:app:licenseeRelease`
  - reads `artifacts.json` → transforms → writes to `app/src/main/assets/oss_licenses.json`
  - регистрируется как input для `assembleRelease` task — auto-runs перед release build
- [ ] обновить CI `ci.yml`:
  - добавить step `./gradlew :app:generateOssLicenses` перед `assembleDebug`
  - assert что `app/src/main/assets/oss_licenses.json` не drift с auto-generated (git diff check)
- [ ] **➕ возможная подзадача:** добавить `gradlew licenseeRelease verifyOssLicenses` task chain в pre-commit hook через Spotless или husky-style
- [ ] обновить `feature/about/.../OssLicensesProvider.kt` (если нужны schema changes для new fields like `dependency_group_id` или `dependency_version`)
- [ ] **сначала тест:** обновить `AboutScreenComposeUiTest` — после auto-generation количество licenses должно быть consistent с Phase 5 (~15 entries); assertion на presence ключевых: Kotlin, Compose, Hilt, Room
- [ ] run `./gradlew :app:generateOssLicenses :app:assembleDebug :feature:about:testDebugUnitTest :feature:about:verifyRoborazziDebug` — must pass before next task

### Task 9: Instrumentation tests матрица API 26/30/34 + macrobenchmark cold-start

- [ ] создать `app/src/androidTest/` directory + `kotlin/` subdir
- [ ] обновить `app/build.gradle.kts`:
  - `defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"` (уже есть)
  - dependencies block: `androidTestImplementation(libs.androidx.test.runner)`, `androidx.test.ext.junit`, `androidx.test.espresso.core`, `androidx.compose.ui.test.junit4`, `hilt.android.testing`
  - `androidTestImplementation(libs.hilt.android.testing)` + `kspAndroidTest(libs.hilt.android.compiler)` (для @HiltAndroidTest)
- [ ] добавить в `gradle/libs.versions.toml`:
  - `androidx-test-runner = "1.5.2"`, `androidx-test-ext-junit = "1.1.5"`, `androidx-test-espresso = "3.5.1"` (или актуальные на 2026)
  - alias-блок аналогично существующим
- [ ] **сначала тест:** `app/src/androidTest/.../SmokeFlowInstrumentationTest.kt`:
  - `@HiltAndroidTest` + `@UninstallModules(DataModule::class)` + custom binding `FakeMeasurementRepository`
  - `@get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)`
  - `@get:Rule(order = 1) val composeRule = createAndroidComposeRule<MainActivity>()`
  - test: `fun smoke_full_user_flow() { ... }` — выполняет 8-step сценарий из Testing Strategy section
  - использует `UiAutomator` для SAF picker interaction (системный диалог вне Compose hierarchy)
- [ ] **сначала тест:** создать `:macrobenchmark` Gradle module:
  - `:macrobenchmark/build.gradle.kts` — `id("com.android.test")` + macrobenchmark deps
  - `:macrobenchmark/src/main/AndroidManifest.xml` — declared as instrumented test app
  - `:macrobenchmark/src/main/kotlin/.../StartupBenchmark.kt`:
    - `@RunWith(AndroidJUnit4)` + `@get:Rule val benchmarkRule = MacrobenchmarkRule()`
    - `@Test fun startup() = benchmarkRule.measureRepeated(packageName = "ru.dmdp.tishina", metrics = listOf(StartupTimingMetric()), iterations = 10, startupMode = StartupMode.COLD) { pressHome(); startActivityAndWait() }`
- [ ] обновить `settings.gradle.kts` — добавить `include(":macrobenchmark")`
- [ ] обновить `.github/workflows/ci.yml`:
  - добавить job `instrumentation-tests`:
    - `runs-on: ubuntu-latest`
    - `strategy.matrix.api-level: [26, 30, 34]`
    - `steps.uses: reactivecircus/android-emulator-runner@v2`
    - `with.api-level: ${{ matrix.api-level }}`, `with.script: ./gradlew :app:connectedDebugAndroidTest`
    - timeout: 90 минут (эмулятор start медленный)
    - `needs: [unit-tests]` (запускается после быстрых проверок)
- [ ] добавить job `macrobenchmark` в ci.yml (только на main branch, post-merge):
  - emulator API 33 (стандарт для macrobenchmark)
  - `./gradlew :macrobenchmark:connectedReleaseAndroidTest`
  - upload baseline-prof.txt как artifact для review перед мержем в `:app`
- [ ] **➕ возможная подзадача:** установить `androidx.baselineprofile` Gradle plugin для auto-generation baseline profile и встройки в `:app` через `:macrobenchmark:generateBaselineProfile`
- [ ] verify локально (нужен Android emulator API 33 установлен): `./gradlew :app:connectedDebugAndroidTest` — passes
- [ ] verify smoke test работает на API 26 (Robolectric NOT эквивалент real emulator — найдём что-то новое; проявляются permission edge-cases, SAF differences и т.п.)
- [ ] run final CI dry-run via push на feature-branch → wait green → must pass before next task

### Task 10: Final acceptance + version v1.0.0 + README + memory update

- [ ] **критерии приёмки (FR-чек)** — авто-проверка через test suite + manual verification (Post-Completion):
  - FR-20: `CsvSerializerTest` + `MeasurementsExporterRoundTripTest` + `HistoryExportComposeUiTest` зелёные; в Detail open → no Export menu (FR-20 only через History — accepted scope simplification)
  - NFR-4: `:app:bundleRelease` size ≤ 8 МБ; universal APK ≤ 6 МБ — assertion в README + CI artifact size badge
  - NFR-1: `:macrobenchmark:StartupBenchmark` median cold-start ≤ 1000 ms на emulator API 33 — captured в CI report
- [ ] обновить `app/build.gradle.kts:14-15`:
  - versionCode = (CI-managed через `github.run_number`); locally fallback `1`
  - versionName = (CI-managed через git-tag); locally fallback `"1.0.0-dev"`
  - tag и push: `git tag v1.0.0 && git push origin v1.0.0` (Post-Completion после успешного manual review)
- [ ] обновить `README.md`:
  - повысить статус с «Phase 5: Polish + About + Bulk-delete complete» до «Phase 6: Production Release complete — v1.0.0 ready»
  - в FR-таблице FR-20 переведён в «✅ Phase 6»; FR-13 / FR-15 явно как «v1.1 (post-release)»
  - добавить секцию «Production deployment» с краткой инструкцией: создание keystore, GitHub Secrets, push v*.*.* tag → автоматический Release
  - добавить секцию «Release sizing benchmarks»: текущий debug ~19-20 МБ, release ~5-6 МБ (R8), AAB ~6-7 МБ
  - добавить секцию «Store deployment checklist» — ссылка на Приложение B спеки + `store-metadata/data-safety.md`
  - обновить FR/NFR таблицу финальным статусом — все основные FR/NFR закрыты или явно перенесены в v1.1+
- [ ] обновить memory `project_tishina.md`:
  - добавить Phase 6 в «Завершённые фазы»
  - обновить «Следующая запланированная фаза» → «v1.1: FR-13 Search/Filter + FR-15 C/Z weighting + zoom/pan в Detail + auto-calibration» (после реальной публикации в стор и feedback)
  - финальный status: «MVP feature-complete, готов к публичному релизу»
- [ ] **➕ внеплановая подзадача:** проверить spec-compliance Приложения B (16 пунктов):
  - app_name `Тишина`/`Tisha` зарезервирован в Play Console — Post-Completion
  - package `ru.dmdp.tishina` уникален — Post-Completion (проверка через Play Search)
  - GitHub-репозиторий `tishina-android` — уже создан
  - Privacy Policy на GitHub Pages — Task 5 ✓
  - Data Safety декларация — Task 7 ✓ (template создан, реальное заполнение в Play Console — Post-Completion)
  - Permissions declaration — Task 7 ✓
  - Скриншоты — Task 7 (templates) + Post-Completion (реальный capture)
  - Adaptive иконка — Task 1 ✓
  - Feature graphic 1024×500 — Task 7 (placeholder)
  - Описания на ru/en — Task 7 ✓
  - Internal testing track — Post-Completion
  - Дисклеймер на главном экране — Phase 5 ✓
  - Калибровка работает — Phase 4 ✓
  - CI зелёный + покрытие — Phase 1-5 ✓ + Task 9 added thresholds
  - R8 включён + mapping.txt — Task 2 + Task 6 ✓
  - AAB ≤ 8 МБ — Task 2 ✓
  - Сборка release-signed — Task 6 ✓
- [ ] запустить полный test suite — `./gradlew clean test verifyRoborazziDebug detektAll lintDebug spotlessCheck :app:assembleRelease :app:bundleRelease :app:generateOssLicenses :app:connectedDebugAndroidTest :macrobenchmark:connectedReleaseAndroidTest` → должно быть BUILD SUCCESSFUL
- [ ] verify все 14 модулей (13 production + 1 macrobenchmark) собираются и проходят все типы тестов
- [ ] verify finalize APK/AAB sizes — release APK ≤ 6 МБ + release AAB ≤ 8 МБ — финальный артефакт прикладывается к этому task в качестве evidence
- [ ] коммит финального статуса в HEAD: `feat: Phase 6 Task 10 — verify acceptance + README + version v1.0.0`
- [ ] Post-Completion (после merge PR):
  - tag `v1.0.0` → push → автоматический run `release.yml` → draft GitHub Release с AAB/APK/mapping.txt
  - manual review draft release → publish
  - upload AAB в Google Play Console (Internal testing track) → fill Data Safety / Permissions / IARC / Privacy URL → review → submit
  - аналогично RuStore + Samsung Galaxy Store (см. § 15 спеки)
  - финальный пометка memory: project_tishina_status = «released»

*Note: ralphex автоматически переносит завершённый план в `docs/plans/completed/` после прохождения всех чекбоксов.*

## Technical Details

### Структура каталогов после Phase 6 (новое относительно Phase 5)

```
tishina-android/
├── .github/
│   └── workflows/
│       ├── ci.yml                                  # +instrumentation-tests job, +macrobenchmark job, +release-build-smoke
│       ├── release.yml                             # новый — on git-tag v*.*.*
│       └── nightly.yml                             # опциональный
├── app/
│   ├── build.gradle.kts                            # +signingConfigs, +R8/shrinkResources, +versionCode/Name dynamic, +licensee plugin
│   ├── proguard-rules.pro                          # новый — Hilt/Compose/Room/Serialization keep rules
│   └── src/
│       ├── main/
│       │   ├── assets/
│       │   │   └── oss_licenses.json               # auto-generated (was manual in Phase 5)
│       │   ├── AndroidManifest.xml                 # +FileProvider declaration
│       │   ├── res/
│       │   │   ├── drawable/
│       │   │   │   └── ic_launcher_foreground.xml  # новый — реальная иконка
│       │   │   ├── mipmap-anydpi-v26/
│       │   │   │   ├── ic_launcher.xml             # updated с реальными foreground/background
│       │   │   │   └── ic_launcher_round.xml       # updated аналогично
│       │   │   ├── mipmap-{ldpi..xxxhdpi}/
│       │   │   │   └── ic_launcher.png             # новый — legacy fallback PNG
│       │   │   ├── values/
│       │   │   │   └── ic_launcher_background.xml  # новый — color resource
│       │   │   └── xml/
│       │   │       └── file_provider_paths.xml     # новый — для share Intent
│       │   ├── play-store-icon.png                 # новый — 512×512 для каталогов
│       │   └── store-metadata/                     # новый — структура (см. Task 7)
│       │       ├── google-play/
│       │       ├── rustore/
│       │       ├── samsung/
│       │       ├── data-safety.md
│       │       ├── permissions-rationale.md
│       │       └── aso-keywords.md
│       └── androidTest/                            # новая директория
│           └── kotlin/ru/dmdp/tishina/
│               └── SmokeFlowInstrumentationTest.kt # новый
├── core/
│   ├── data/
│   │   └── src/main/kotlin/ru/dmdp/tishina/core/data/
│   │       └── export/                             # новая директория
│   │           ├── CsvSerializer.kt
│   │           ├── MeasurementCsvFormat.kt
│   │           └── MeasurementsExporter.kt
│   ├── domain/
│   │   └── src/main/kotlin/ru/dmdp/tishina/core/domain/
│   │       ├── model/
│   │       │   └── ExportFilter.kt                 # новый
│   │       └── usecase/
│   │           └── ExportHistoryUseCase.kt         # новый
│   └── ui/
│       └── src/main/kotlin/ru/dmdp/tishina/core/ui/
│           └── snapshot/
│               └── LineChartSnapshotter.kt         # новый
├── feature/
│   ├── history/
│   │   └── src/main/kotlin/ru/dmdp/tishina/feature/history/
│   │       ├── HistoryUiEvent.kt                   # +ExportRequested, +ExportFileSelected
│   │       ├── HistoryUiEffect.kt                  # +LaunchSafPicker, +ShowExportSnackbar
│   │       ├── HistoryViewModel.kt                 # +export logic
│   │       ├── DetailUiEvent.kt                    # +ShareRequested
│   │       ├── DetailUiEffect.kt                   # +LaunchShareIntent
│   │       ├── DetailViewModel.kt                  # +share logic
│   │       ├── share/
│   │       │   └── ShareIntentBuilder.kt           # новый
│   │       └── ui/
│   │           ├── HistoryScreen.kt                # +export menu/button
│   │           └── DetailScreen.kt                 # +share TopBar action
│   └── about/
│       └── src/main/res/values{,-ru}/strings.xml   # final URLs (replaces placeholders)
├── macrobenchmark/                                 # новый module
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── kotlin/ru/dmdp/tishina/macrobenchmark/
│           └── StartupBenchmark.kt
├── docs/
│   └── privacy/                                    # новый — для GitHub Pages
│       ├── index.html
│       └── style.css                               # опциональный
├── README.md                                       # updated — Phase 6 status, deployment instructions
└── settings.gradle.kts                             # +include(":macrobenchmark")
```

### CSV формат экспорта

Файл `tishina-history-{YYYY-MM-DD}.csv`, UTF-8 + BOM, CRLF line endings:

```csv
id,createdAt,durationSec,avgDb,minDb,maxDb,weighting,timeWeighting,calibrationOffsetDb,title,note
1,2026-05-25T14:32:11Z,180,42.3,28.5,68.7,A,FAST,0.0,"Office room","Air conditioner on"
2,2026-05-25T15:10:00Z,60,55.8,40.2,72.1,A,SLOW,1.5,"Subway",
3,2026-05-25T16:00:00Z,300,38.9,25.0,55.0,A,FAST,-1.0,,"Quiet"" room with ""quotes"""
```

Опционально (bundle mode): дополнительные файлы `tishina-samples-{id}.csv`:

```csv
tOffsetMs,db
0,42.3
200,42.8
400,43.1
...
```

### Share Intent payload

```kotlin
Intent(Intent.ACTION_SEND).apply {
    type = "image/png"
    putExtra(Intent.EXTRA_STREAM, fileProviderUri)
    putExtra(Intent.EXTRA_TEXT, """
        ${measurement.title ?: "Замер"}
        ${formatDate(measurement.createdAtEpochMs)}

        Среднее: ${"%.1f".format(measurement.avgDb)} дБ(А)
        Мин: ${"%.1f".format(measurement.minDb)} дБ(А)
        Макс: ${"%.1f".format(measurement.maxDb)} дБ(А)
        Длительность: ${formatDuration(measurement.durationMs)}

        —
        Измерено в приложении Тишина
    """.trimIndent())
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
}
```

### Release.yml ключевые шаги

```yaml
name: Release
on:
  push:
    tags: ['v*.*.*']

jobs:
  build-and-release:
    runs-on: ubuntu-latest
    timeout-minutes: 45
    steps:
      - uses: actions/checkout@v4
        with: { fetch-depth: 0 }

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with: { distribution: zulu, java-version: 17 }

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v3

      - name: Decode upload keystore
        run: |
          echo "${{ secrets.UPLOAD_KEYSTORE_BASE64 }}" | base64 -d > tishina-upload-key.jks

      - name: Extract version from tag
        run: |
          VERSION=${GITHUB_REF#refs/tags/v}
          echo "VERSION_NAME=$VERSION" >> $GITHUB_ENV
          echo "VERSION_CODE=${{ github.run_number }}" >> $GITHUB_ENV

      - name: Build signed release
        env:
          UPLOAD_KEYSTORE_PATH: ${{ github.workspace }}/tishina-upload-key.jks
          UPLOAD_KEYSTORE_PASSWORD: ${{ secrets.UPLOAD_KEYSTORE_PASSWORD }}
          UPLOAD_KEY_ALIAS: ${{ secrets.UPLOAD_KEY_ALIAS }}
          UPLOAD_KEY_PASSWORD: ${{ secrets.UPLOAD_KEY_PASSWORD }}
        run: ./gradlew :app:bundleRelease :app:assembleRelease

      - name: Upload artifacts
        uses: actions/upload-artifact@v4
        with:
          name: release-artifacts
          path: |
            app/build/outputs/bundle/release/*.aab
            app/build/outputs/apk/release/*.apk
            app/build/outputs/mapping/release/mapping.txt

      - uses: softprops/action-gh-release@v2
        with:
          files: |
            app/build/outputs/bundle/release/*.aab
            app/build/outputs/apk/release/*.apk
            app/build/outputs/mapping/release/mapping.txt
          generate_release_notes: true
          draft: true
```

### ProGuard ключевые keep-правила

```proguard
# Kotlin metadata
-keepattributes *Annotation*, InnerClasses, EnclosingMethod, Signature

# Hilt
-keep class * extends dagger.hilt.android.internal.managers.* { *; }
-keep class * extends androidx.hilt.* { *; }
-keep @dagger.hilt.* class *

# Compose runtime
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.* class *
-keepclassmembers class * { @androidx.room.* <methods>; }

# kotlinx-serialization
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-keepclassmembers class kotlinx.serialization.json.** { *; }

# Tishina domain models (used for serialization in OSS-licenses)
-keep class ru.dmdp.tishina.core.domain.model.** { *; }
-keep class ru.dmdp.tishina.feature.about.OssLicense { *; }
```

### Известные ограничения и допущения

- **Реальная публикация в магазины — Post-Completion**. План создаёт всю инфраструктуру (signing, артефакты, метаданные), но загрузка AAB в Play Console / RuStore / Samsung требует manual interaction (заполнение Data Safety form в Web UI, ручная модерация). Это accepted limitation.
- **Privacy Policy URL — placeholder username `dnovichkov`**. Будет валидирован через `git config user.email` (`dmitry.novichkov@gmail.com` из system context → `dnovichkov` или похожий). Финальный username фиксируется в Task 5.
- **Instrumentation matrix API 26/30/34** — не покрывает API 35 (требует AOSP image, который flaky на reactivecircus action в 2026). FR-23 (targetSdk ≥ 35 политика Google Play 2025-2026) проверяется через успешный `assembleRelease` с `targetSdk = 35` без реального теста на эмуляторе 35.
- **Auto-publish в Play через `r0adkll/upload-google-play@v1`** — отложен, требует service account JSON в secrets + initial manual app creation в Play Console. Phase 6 готовит signed AAB; auto-publish — Phase 7 если нужно.
- **macrobenchmark на emulator** — не proxy для Pixel 6a реального устройства (CDD требование NFR-1). Emulator API 33 fastest baseline, на Pixel 6a фактически быстрее. NFR-1 валидируется на real device — Post-Completion.
- **`combinedClickable` semantics** — `Modifier.combinedClickable(onLongClick = ...)` для long-press на History используется с Phase 5; в Phase 6 добавляется правый TopAppBar overflow menu с «Export» action — ortogonal.
- **`pluralStringResource` для русского** — три формы (`one`/`few`/`many`) уже работают в Phase 5; Phase 6 добавляет plurals для `history_export_success` (e.g., «1 замер сохранён» / «2 замера сохранены» / «5 замеров сохранены»).
- **CSV форматирование чисел через `Locale.US`** — гарантирует `.` decimal separator для совместимости с Excel/LibreOffice в любой локали; альтернатива `Locale.getDefault()` сломает CSV в русской локали (запятая как separator конфликтует с column separator). Решение зафиксировано в Task 3.
- **Share PNG snapshot через `Bitmap.createBitmap` + Canvas draw** — production-API; Compose `captureToImage()` ограничен test scope. Альтернатива — render через `ComposeView.draw(Canvas)` через `WindowManager`-attached invisible view (overhead, hidden complexity). Решение: manual draw через DrawScope в `LineChartSnapshotter`.
- **FileProvider authority** — `${applicationId}.fileprovider` → `ru.dmdp.tishina.fileprovider`. Manifest declaration через placeholder. Не конфликтует с release-build (debug-suffix `.debug` если используется → `ru.dmdp.tishina.debug.fileprovider`, что accepted).

## Post-Completion

*Items requiring manual intervention or external systems — no checkboxes, informational only.*

**Manual verification** (после завершения Phase 6 implementation):

- Установить release-signed APK на физическое Android-устройство (Pixel 6a / Snapdragon 7-gen — целевая device); выполнить полный пользовательский сценарий:
  - cold-start: проверить FR-1 (≤ 1 с до интерактива через secunds watch + adb logcat marker)
  - запуск → tab Measure → permission → измерение 10 сек → save → история → bulk-delete всех → undo → CSV export через системный SAF picker → verify файл в выбранной папке
  - открыть Detail замера → Share → выбрать Telegram/WhatsApp/Email → проверить, что PNG-снимок графика + текст приходят корректно
  - переключить тему / язык / dynamic colors → проверить иконку с новой палитрой
  - проверить, что adaptive icon корректно отрисовывается на устройствах с different launcher shapes (round / squircle / teardrop)
- TalkBack: пройти весь сценарий с включённым TalkBack; озвучивание новых элементов (Export menu, Share button) — корректное
- Размер шрифта 200% → проверить, что новый export menu и share TopBar не overflow
- Cold-start замер на реальном Pixel 6a через `adb shell am start-activity -W -n ru.dmdp.tishina/.MainActivity` — verify NFR-1
- Проверить debug + release builds на API 26 emulator + API 34 emulator (если instrumentation matrix CI был skip из-за времени)

**External system updates** (deferred):

- Создать `tishina-upload-key.jks` локально (1 раз, не пересоздаваемый), сохранить копию в безопасном месте (1Password / Bitwarden); upload base64 в GitHub Secrets
- Зарезервировать `Тишина` / `Tisha` в Google Play Console (требует $25 one-time developer fee — Post-Completion)
- Создать RuStore developer аккаунт (бесплатно для физлица; верификация через Госуслуги для РФ-резидентов)
- Создать Samsung Galaxy Store seller аккаунт (требует D-U-N-S номер или ИП — отдельный шаг)
- Включить GitHub Pages для `tishina-android` репо: Settings → Pages → Source = `main` branch → folder `/docs` (или создать `gh-pages` branch через `peaceiris/actions-gh-pages@v3` workflow); финальный URL `https://dnovichkov.github.io/tishina-android/privacy/`
- Зафиксировать `app_name` / `applicationId` уникальность через ручной поиск в Google Play / RuStore
- Загрузить AAB в Google Play Console Internal testing track → пройти ручную модерацию (~1 час)
- Загрузить AAB в RuStore → пройти ручную модерацию (~1 час)
- Загрузить AAB в Samsung Galaxy Store → пройти ручную модерацию (2-7 рабочих дней)
- Заполнить Data Safety form в Play Console (используя `store-metadata/data-safety.md` как template)
- Заполнить Permissions declaration для `RECORD_AUDIO` (using `permissions-rationale.md`)
- Заполнить IARC Age Rating questionnaire → ожидаемо 3+ (Everyone)
- Создать 8 скриншотов 1920×1080 для Google Play (Measure / History / Detail / Settings / About / Selection mode / Bulk delete / Share) через `:app:generateStoreScreenshots` Roborazzi task (если был создан в Task 7) или manually через эмулятор
- Создать ≤10 скриншотов для RuStore (Portrait Phone preset)
- Опубликовать промежуточный Pre-release `v1.0.0-rc1` на GitHub Releases для бета-тестеров — собрать feedback за 1-2 недели перед публичным релизом
- Финальная пометка memory `project_tishina.md` → `status: released v1.0.0`

**Что может быть в Phase 7+ (post-MVP, после первого релиза):**

- FR-13 — Search/Filter в History (P1)
- FR-15 — C/Z weighting UI + C-filter в `:core:audio` (P1)
- Zoom/pan по графику в Detail (P1)
- Auto-калибровка по эталонной комнате (P2)
- Pre-built калибровки под популярные модели (Pixel 6a, Galaxy S23, Redmi Note 13)
- ML-based калибровка (research, v1.2+)
- Wear OS companion (v2.0+)
- Bluetooth-микрофоны (v2.0+)
- FFT/RTA spectrum (v2.0+)
- NIOSH/OSHA Dosimeter (TWA, projected dose) — v2.0+
- Виджет на главном экране (Glance, v1.2)
- Импорт/экспорт настроек (v1.3)
- CI auto-publish в Google Play через `r0adkll/upload-google-play@v1` (Phase 7)
- RuStore auto-publish через RuStore Public API (Phase 7)
- nightly.yml workflow для daily Pre-release
- F-Droid manifest и публикация через offizielle Reproducible Builds repository (v1.5+, opt-in)

*Note: ralphex автоматически переносит завершённый план в `docs/plans/completed/` после прохождения всех чекбоксов.*
