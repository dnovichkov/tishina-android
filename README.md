# Тишина (Tisha) — Android Sound Level Meter

[![CI](https://img.shields.io/badge/CI-pending--push-lightgrey?logo=githubactions)](.github/workflows/ci.yml)
[![Coverage](https://img.shields.io/badge/Kover-report--local-blue)](./app/build/reports/kover/htmlDebug/index.html)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](./LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![minSdk](https://img.shields.io/badge/minSdk-26-3DDC84?logo=android&logoColor=white)](https://developer.android.com)

> Badges CI/Coverage станут «живыми» после push в GitHub remote (см. Post-Completion в плане Phase 1) — замените `OWNER/REPO` в URL на ваш.

Открытое Android-приложение для измерения уровня окружающего шума. Работает офлайн, без рекламы и трекеров; данные хранятся локально на устройстве.

- **Платформа:** Android 8.0 (API 26) и выше.
- **Стек:** 100% Kotlin, Jetpack Compose, Material 3, Hilt, Room, DataStore.
- **Лицензия:** Apache 2.0 (см. [LICENSE](LICENSE)).
- **Спецификация:** [docs/specs/tishina-spec.md](docs/specs/tishina-spec.md).

## Статус

**Phase 3: Persistence + History complete.**

К инфраструктуре Phase 1 и audio pipeline Phase 2 добавлен полноценный persistence-слой (Room) и замкнутый CRUD-цикл: измерить → сохранить → найти в History → открыть Detail → отредактировать заметку → удалить с возможностью Undo.

Покрытые FR/NFR из [спецификации](docs/specs/tishina-spec.md):

| FR / NFR | Статус | Где |
|---|---|---|
| FR-2 (runtime permission) | ✅ Phase 2 | `:feature:measure` |
| FR-3 / FR-4 / FR-5 / FR-7 (Start/live/gauge/Pause/Reset) | ✅ Phase 2 | `:feature:measure` |
| **FR-6 (Save dialog с валидацией)** | ✅ Phase 3 | `MeasureSaveDialog` + `SaveMeasurementUseCase` |
| **FR-8 (List orderBy createdAt DESC)** | ✅ Phase 3 | `MeasurementDao.observeSummaries` |
| **FR-9 (карточка с date/title/avg/duration/sparkline)** | ✅ Phase 3 | `HistoryItemCard` + `SparklineChart` |
| **FR-10 (Detail с полным графиком + inline-edit заметки)** | ✅ Phase 3 | `DetailScreen` + `DetailViewModel` |
| **FR-11 (swipe-delete с Snackbar Undo 5s)** | ✅ Phase 3 | `HistoryViewModel.scheduleDelete` |
| FR-15 / FR-16 (A-weighting + Fast) | ✅ Phase 2 | `:core:audio` |
| FR-12 / FR-13 (bulk + search) | ⏳ v1.1 | out of MVP |
| FR-14, FR-17…FR-19 (Settings UI) | ⏳ Phase 4 | планируется |
| FR-20 (CSV export) | ⏳ Phase Release | планируется |
| **NFR-1 (cold start ≤ 1 с)** | ✅ Phase 3 — lazy Room init | замер на эмуляторе → Phase Release |
| NFR-5 / NFR-6 (lifecycle / rotation) | ✅ Phase 2/3 | `SavedStateHandle` + `rememberSaveable` |
| **NFR-9 / NFR-11 (no PCM persistence, internal storage)** | ✅ Phase 3 | в Room сохраняются только агрегаты + 5 Гц-семплы dB(A) |
| **NFR-12 (валидация длин)** | ✅ Phase 3 | UI counter + use-case `Result.failure` + SQLite trigger |
| NFR-13…NFR-16 (a11y, контраст, Material 3) | ✅ Phase 2/3 | swipe-delete сопровождается trash-иконкой, не только цветом |

Архитектурно добавлено в Phase 3:

- `:core:domain` — модели `MeasurementSummary` / `MeasurementDetails` / `NewMeasurement` (immutable, `MAX_TITLE_LENGTH=80`, `MAX_NOTE_LENGTH=200`); интерфейс `MeasurementRepository`; use-cases `SaveMeasurementUseCase` / `GetMeasurementsUseCase` / `GetMeasurementByIdUseCase` / `DeleteMeasurementUseCase` / `UpdateMeasurementNoteUseCase`.
- `:core:data` — Room 2.8.4 база `TishinaDatabase` (version=1, exported schema коммитится в `core/data/schemas/`); `MeasurementEntity` + `SampleEntity` с FK ON DELETE CASCADE; `MeasurementDao` с `@Transaction insertWithSamples`, slim projection `MeasurementSummaryRow`, `loadSparklinePreview(LIMIT 20)`, `MeasurementWithSamples` (`@Relation` POJO); mapper Entity↔Domain с defensive enum fallback; `MeasurementRepositoryImpl` (`@IoDispatcher` + `flowOn`); `DataModule` (`@Binds` repository, `@Provides` database/DAO/dispatcher); SQLite-триггеры `LENGTH_GUARD_CALLBACK` для CHECK constraint'ов (Room 2.8 не поддерживает CHECK в `@Entity`).
- `:core:ui` — `SplLineChart` + `SplStatsRow` переехали сюда из `:feature:measure` (общий компонент для Measure + Detail); `core_ui_spl_*` test-tags; screenshot-baselines перенесены.
- `:core:testing` — `FakeMeasurementRepository` с `seed(...)`, monotonic id generator, `MutableStateFlow<List<MeasurementSummary>>`.
- `:feature:measure` — `MeasureViewModel` накапливает downsampled-до-5-Гц RAM-буфер; новые события `SaveDialogConfirmed(title, note)` / `SaveDialogDismissed`; effect `ShowSaveDialog`; composable `MeasureSaveDialog` (Material 3 AlertDialog + Surface-variant для тестов; counter с error-цветом при overflow; `rememberSaveable`); Save FAB активируется после первого семпла.
- `:feature:history` — `HistoryViewModel` с soft-delete стратегией (combine 3 flows, 5-секундное Undo-окно через `pendingDeleteJob`); `HistoryScreen` (LazyColumn + `SwipeToDismissBox` + Snackbar Undo + empty state с CTA); `HistoryItemCard` (text/stats колонки + относительная дата через `DateUtils.getRelativeTimeSpanString` + мини-`SparklineChart`); `DetailViewModel` (декодирует id из `SavedStateHandle.toRoute<DetailRoute>()`; ShowSnackbar+NavigateBack при `null`); `DetailScreen` (полный `SplLineChart` + `SplStatsRow` + `MetadataCard` + `NoteCard` с inline-edit + `DeleteConfirmDialog`); `HistoryUseCaseModule` (`@Provides` для 4 use-cases).
- `:app` — `TishinaNavHost` добавлен `composable<DetailRoute>`; `historyContent` / `detailContent` слоты с default'ами (паттерн `measureContent` из Phase 2) для unit-тестируемости без Hilt-graph; `TishinaColdStartTest` фиксирует FR-1 invariant'ы reflection-ассертами.

Стратегия хранения sample-потока (зафиксирована при планировании Phase 3): RAM → одной транзакцией на Save. `MeasureViewModel` накапливает downsampled-точки 5 Гц в обычный `MutableList<SoundSample>`. Нажатие Save → `SaveMeasurementUseCase` выполняет одну атомарную Room-транзакцию (вставка `MeasurementEntity` + N `SampleEntity` ссылающихся на её id). Reset/Pause/cancel — ничего не пишется. Аудио (PCM) в файл по-прежнему не сохраняется (NFR-9).

Тестовое покрытие (наблюдательно, без enforced threshold до Phase Release):

- `:core:domain` — 100% INSTRUCTION (цель ≥ 90%); 5 новых use-cases + 3 новых модели полностью покрыты JUnit 5.
- `:core:data` — DAO (13 Robolectric-тестов, in-memory Room), mapper round-trip, `MeasurementRepositoryImpl` (Robolectric + Turbine + параллельные сохранения); экспорт schema v1 проверяется тестом наличия файла.
- `:feature:measure` — расширенный охват RAM-буфера (5 Гц downsampling, Pause/Reset), `SaveDialog` flow (7 кейсов), валидация в Compose UI Test (6 кейсов).
- `:feature:history` — `HistoryViewModel` (7 кейсов soft-delete + Undo timer), `DetailViewModel` (12 кейсов inline-edit + delete), Compose UI Test для нажатий по карточкам.
- Roborazzi: +14 baseline в Phase 3 (`MeasureSaveDialog` 6, `HistoryEmptyState` 2, `HistoryList` 2, `HistoryItemCard` 6, `DetailScreen` 6) → итого 48 снимков.

Известные ограничения Phase 3:

- Soft-delete Undo не переживает kill процесса — fully-durable Undo требует write-ahead delete log, overkill для MVP.
- `MigrationTestHelper` в Robolectric не работает с Room 2.8 KMP-driver (известный bug — открывает БД по абсолютному пути и падает в `inMemoryDatabaseBuilder`-режиме). Schema v1 экспортируется и коммитится, helper будет активирован в androidTest на эмуляторе в Phase 4 при v1→v2.
- `DetailScreen` Compose UI Test (TextField counter) недоступен под Robolectric из-за `AppNotIdleException` Material 3 AlertDialog + focus animations — покрытие через `DetailViewModelTest` (12 кейсов unit) + instrumentation в Phase Release.

Следующий этап — **Phase 4: Settings + DataStore** (реальный `SettingsRepository` через DataStore Preferences, `SettingsScreen` UI для калибровки/A-C-Z/Fast-Slow/тем/языка, `CWeightingFilter` в `:core:audio`).

Подробные планы:

- Phase 1: [docs/plans/completed/2026-05-19-tishina-foundation.md](docs/plans/completed/2026-05-19-tishina-foundation.md).
- Phase 2: [docs/plans/completed/2026-05-19-tishina-audio-engine.md](docs/plans/completed/2026-05-19-tishina-audio-engine.md).
- Phase 3: [docs/plans/2026-05-20-tishina-history-persistence.md](docs/plans/2026-05-20-tishina-history-persistence.md).
- Полная спецификация продукта: [docs/specs/tishina-spec.md](docs/specs/tishina-spec.md).

## Сборка

Требуется JDK 17 и Android SDK (compileSdk 35, build-tools 35.x). Путь к SDK задаётся переменной `ANDROID_HOME` или строкой `sdk.dir=...` в `local.properties`.

```bash
./gradlew :app:assembleDebug
```

Собранный APK будет в `app/build/outputs/apk/debug/`.

## Тестирование

```bash
./gradlew :build-logic:convention:test :core:domain:test testDebugUnitTest verifyRoborazziDebug
```

`:core:domain` — pure-Kotlin JVM-модуль, для него правильная задача `test`, не `testDebugUnitTest`. Отчёт Kover для этого модуля собирается отдельной командой `./gradlew :core:domain:koverHtmlReport`.

Обновление baseline-PNG для Roborazzi (после намеренного изменения UI):

```bash
./gradlew recordRoborazziDebug
```

## Качество кода

```bash
./gradlew detektAll spotlessCheck lintDebug
./gradlew koverHtmlReportDebug
```

HTML-отчёт о покрытии: `build/reports/kover/htmlDebug/index.html`. Авто-фикс форматирования: `./gradlew spotlessApply`.

## Известные особенности

- Размер debug-APK ~17.9 МБ. NFR-4 (≤ 6 МБ) применим к release-сборке после включения R8/resource shrinking — отложено до Phase Release.
- При прогоне `clean` + Kover в одном invocation возможна гонка `kover-agent.args FileNotFoundException`. Workaround: разделить на два прогона — `./gradlew clean assembleDebug -x test`, затем `./gradlew testDebugUnitTest verifyRoborazziDebug koverXmlReportDebug`.
- После `clean` Spotless может выдать stale config-cache. Workaround: удалить `.gradle/configuration-cache/` и повторить.
- Robolectric 4.13 не поддерживает API 35; для unit-тестов SDK зафиксирован на 33 через `src/test/resources/robolectric.properties` в `:app`, `:core:designsystem`, `:core:ui`, `:core:audio`, `:feature:measure`.
- `MeasureScreen` использует `hiltViewModel()`, поэтому навигационные тесты в `:app` подменяют его на пустой stub через параметр `measureContent` у `TishinaApp`/`TishinaNavHost`, не нагружая Hilt-граф.

## Контрибьюция

Проект на ранней стадии. Issues и pull requests приветствуются.
