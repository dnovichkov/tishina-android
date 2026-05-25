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

**Phase 4: Settings + Calibration + Theme + Language complete.**

К persistence-слою Phase 3 добавлены пользовательские настройки на DataStore Preferences: калибровочный slider −20…+20 дБ с шагом 0.1, переключатель Fast/Slow, выбор темы (Системная / Светлая / Тёмная), Material You (динамические цвета на Android 12+), переключение языка интерфейса (Системный / Русский / English) через `AppCompatDelegate.setApplicationLocales`. Settings-изменения мгновенно прокидываются в `MeasureViewModel` и применяются к следующему запуску замера; калибровочный offset фиксируется в `MeasurementEntity.calibrationOffsetDb` на Save.

Покрытые FR/NFR из [спецификации](docs/specs/tishina-spec.md):

| FR / NFR | Статус | Где |
|---|---|---|
| FR-2 (runtime permission) | ✅ Phase 2 | `:feature:measure` |
| FR-3 / FR-4 / FR-5 / FR-7 (Start/live/gauge/Pause/Reset) | ✅ Phase 2 | `:feature:measure` |
| FR-6 (Save dialog с валидацией) | ✅ Phase 3 | `MeasureSaveDialog` + `SaveMeasurementUseCase` |
| FR-8 (List orderBy createdAt DESC) | ✅ Phase 3 | `MeasurementDao.observeSummaries` |
| FR-9 (карточка с date/title/avg/duration/sparkline) | ✅ Phase 3 | `HistoryItemCard` + `SparklineChart` |
| FR-10 (Detail с полным графиком + inline-edit заметки) | ✅ Phase 3 | `DetailScreen` + `DetailViewModel` |
| FR-11 (swipe-delete с Snackbar Undo 5s) | ✅ Phase 3 | `HistoryViewModel.scheduleDelete` |
| **FR-14 (калибровочный slider −20…+20 дБ, шаг 0.1)** | ✅ Phase 4 | `SettingsScreen` + `UpdateCalibrationUseCase` |
| FR-15 / FR-16 (A-weighting + Fast/Slow) | ✅ Phase 2/4 | `:core:audio` + UI toggle в Settings |
| **FR-17 (Системная/Светлая/Тёмная тема + динамические цвета)** | ✅ Phase 4 | `TishinaTheme` подписан на `AppearanceSettings` через `AppViewModel` |
| **FR-18 (язык Системный/Русский/Английский)** | ✅ Phase 4 | `LocaleSwitcher` + `AppCompatDelegate.setApplicationLocales` |
| **FR-19 (Reset калибровки)** | ✅ Phase 4 | `ResetCalibrationUseCase` |
| FR-12 / FR-13 (bulk + search) | ⏳ Phase 5 | планируется |
| FR-20 (CSV export) | ⏳ Phase Release | планируется |
| FR-21 / FR-22 (AboutScreen + дисклеймер) | ⏳ Phase 5 | планируется |
| NFR-1 (cold start ≤ 1 с) | ✅ Phase 3 — lazy Room init; Phase 4 — lazy DataStore | замер на эмуляторе → Phase Release |
| NFR-5 / NFR-6 (lifecycle / rotation) | ✅ Phase 2/3/4 | `SavedStateHandle` + `rememberSaveable` + DataStore single source |
| NFR-9 / NFR-11 (no PCM persistence, internal storage) | ✅ Phase 3 | в Room сохраняются только агрегаты + 5 Гц-семплы dB(A) |
| NFR-12 (валидация длин) | ✅ Phase 3 | UI counter + use-case `Result.failure` + SQLite trigger |
| NFR-13…NFR-16 (a11y, контраст, Material 3) | ✅ Phase 2/3/4 | preference-компоненты Settings, font-scale 2x проверен Roborazzi |
| NFR-17 / NFR-18 / NFR-19 (i18n) | ✅ Phase 4 | все строки в `strings.xml` (RU + EN), формат чисел через локаль |

Архитектурно добавлено в Phase 4:

- `:core:domain` — модели `AppearanceSettings` / `ThemeMode` / `AppLocale` / `AppSettingsSnapshot` (immutable, `CALIBRATION_MIN_DB=-20f`, `CALIBRATION_MAX_DB=20f`, `CALIBRATION_STEP_DB=0.1f`); расширенный `SettingsRepository` интерфейс (`appearance: Flow<AppearanceSettings>` + 4 новых setter'а + `resetCalibration`); use-cases `UpdateCalibrationUseCase` (валидация диапазона + округление до 0.1 + `Result<Float>`), `ResetCalibrationUseCase`, `UpdateTimeWeightingUseCase`, `UpdateThemeModeUseCase`, `UpdateDynamicColorsUseCase`, `UpdateAppLocaleUseCase`, `ObserveAppSettingsUseCase` (combine `config` + `appearance`).
- `:core:data` — `SettingsRepositoryImpl` на `androidx.datastore:datastore-preferences` 1.1.1 (имя файла `tishina_settings`, `ReplaceFileCorruptionHandler { emptyPreferences() }`, defensive enum-fallback на дефолты); `SettingsKeys` (6 типизированных ключей: `floatPreferencesKey` для калибровки + `stringPreferencesKey` для enum'ов + `booleanPreferencesKey` для динамических цветов); `DataModule` биндит `SettingsRepository` → `SettingsRepositoryImpl` (заменяет stub из Phase 2).
- `:core:designsystem` — `TishinaTheme(themeMode, dynamicColors, content)` как primary API; внутри `when (themeMode)` → `useDarkTheme`; на API ≥ S guarded ветка `dynamicLightColorScheme`/`dynamicDarkColorScheme(LocalContext.current)`; на API ≤ 30 безопасный fallback на static. Сохранён deprecated boolean-overload (`darkTheme, dynamicColor`) для совместимости с уже-зафиксированными Roborazzi-baseline других модулей.
- `:core:ui` — переиспользуемые preference-компоненты в `core/ui/preferences/`: `PreferenceCategory`, `ChoicePreference<T>` (Material 3 `FilterChip` в `FlowRow` — устойчивее экспериментального `SegmentedButton`), `SliderPreference` (локальный `mutableFloatStateOf` для smooth drag + commit на `onValueChangeFinished` — natural debouncing, нет лишних DataStore writes), `SwitchPreference` (`Modifier.toggleable(role = Role.Switch)` — TalkBack-friendly).
- `:feature:settings` — переписан с placeholder'а: `SettingsViewModel` (MVI lite: `SettingsUiState` + `SettingsUiEvent` + `SettingsUiEffect.ApplyAppLocale/ShowSnackbar`); `SettingsScreen` с группами «Measurement» / «Appearance» / «Application language» + footer «About the app» (placeholder до Phase 5); `SettingsUseCaseModule` (`@InstallIn(ViewModelComponent::class)` + `@ViewModelScoped` — тонкий граф); локализационные строки в RU + EN; Roborazzi screenshot-baselines (18 шт., включая font-scale 2x для NFR-14).
- `:feature:measure` — `MeasureViewModel` теперь инжектится `SettingsRepository` напрямую и подписывается через `stateIn(SharingStarted.Eagerly, MeasurementConfig())` — первый Start уже видит реальное DataStore-значение, а не initial fallback. Активный `MeasurementConfig` снимается snapshot'ом в `startCollecting()` **только при fresh-session** (`sessionCount == 0L`); Resume после Pause сохраняет старый snapshot (одна сессия = один config, иначе `Save` записал бы offset, не соответствующий pre-pause семплам). `Reset` сбрасывает snapshot обратно к `MeasurementConfig()`, чтобы следующий Start re-сэмплировал DataStore.
- `:app` — `MainActivity` инжектится `AppViewModel @HiltViewModel` (через `by viewModels()` — ComponentActivity-scoped); `AppViewModel` подписан на `ObserveAppSettingsUseCase` с `SharingStarted.Eagerly` (никакого flash-of-default-light на первом frame); `TishinaTheme(themeMode, dynamicColors)` пересобирается реактивно; `LocaleSwitcher` (object с `apply(AppLocale)` + публичным `toLocaleListCompat(...)` для unit-тестируемости); `TishinaNavHost.settingsContent` slot (тот же паттерн что `measureContent`/`historyContent`); `AndroidManifest.xml`: `android:localeConfig="@xml/locales_config"` + `app/src/main/res/xml/locales_config.xml` (требование Android 13+ для появления «Язык приложения» в системных Settings).

Стратегия calibration vs in-flight session (зафиксирована при планировании Phase 4): `MeasurementConfig` инжектится в `StartMeasurementUseCase.invoke(config)` как immutable seed сессии. Если пользователь меняет калибровку во время активного замера — изменение применится только к следующему Start. Hot-reload откладывается на v1.2 (требует AudioRecord-restart и переосмысления состояния DSP-фильтров, что разрушает Leq accumulator).

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

Следующий этап — **Phase 5: Polish + About + Bulk-delete** (полноценный `AboutScreen` с дисклеймером, версией и OSS-лицензиями (FR-21 / FR-22); bulk-выбор/удаление в History (FR-12); поиск и фильтр по дате/тексту (FR-13); `CWeightingFilter` в `:core:audio` (FR-15); CSV-export через Storage Access Framework (FR-20); Share Intent + PNG-снимок графика).

Подробные планы:

- Phase 1: [docs/plans/completed/2026-05-19-tishina-foundation.md](docs/plans/completed/2026-05-19-tishina-foundation.md).
- Phase 2: [docs/plans/completed/2026-05-19-tishina-audio-engine.md](docs/plans/completed/2026-05-19-tishina-audio-engine.md).
- Phase 3: [docs/plans/completed/2026-05-20-tishina-history-persistence.md](docs/plans/completed/2026-05-20-tishina-history-persistence.md).
- Phase 4: [docs/plans/completed/2026-05-20-tishina-settings.md](docs/plans/completed/2026-05-20-tishina-settings.md).
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

- Размер debug-APK ~28.2 МБ (Phase 3 baseline, +10.3 МБ к Phase 2 — Room runtime, KSP-generated DAO, extended Material icons). NFR-4 (≤ 6 МБ) применим к release-сборке после включения R8/resource shrinking — отложено до Phase Release.
- При прогоне `clean` + Kover в одном invocation возможна гонка `kover-agent.args FileNotFoundException`. Workaround: разделить на два прогона — `./gradlew clean assembleDebug -x test`, затем `./gradlew testDebugUnitTest verifyRoborazziDebug koverXmlReportDebug`.
- После `clean` Spotless может выдать stale config-cache. Workaround: удалить `.gradle/configuration-cache/` и повторить.
- Robolectric 4.13 не поддерживает API 35; для unit-тестов SDK зафиксирован на 33 через `src/test/resources/robolectric.properties` в `:app`, `:core:designsystem`, `:core:ui`, `:core:audio`, `:core:data`, `:feature:measure`, `:feature:history`, `:feature:settings`.
- `MeasureScreen` и `SettingsScreen` используют `hiltViewModel()`, поэтому навигационные тесты в `:app` подменяют их на пустые stub'ы через параметры `measureContent` / `settingsContent` у `TishinaApp`/`TishinaNavHost`, не нагружая Hilt-граф.
- Phase 4 добавила DataStore Preferences (~250 КБ) и AppCompat 1.7.0 (~600 КБ + транзитивный `emoji2-views-helper` ~1.5 МБ); пересмотр debug-APK baseline отложен до Phase Release вместе с R8.
- Пользовательские настройки хранятся в app-private DataStore-файле `tishina_settings.preferences_pb`. При повреждении файла применяется `ReplaceFileCorruptionHandler` → возврат к дефолтам. Очистка настроек: системные настройки → Тишина → Очистить данные.
- `MainActivity` extends `ComponentActivity` (а не `AppCompatActivity`) — Compose-first. Для FR-18 на Android 12 и ниже Activity вручную оборачивает `attachBaseContext` персистентным локалем (`AppCompatDelegate.getApplicationLocales`) и сама вызывает `recreate()` после смены языка; на Android 13+ это делает системный `LocaleManager`.

## Калибровка

Микрофоны Android-устройств различаются между моделями на ±3–5 дБ. Для точных замеров рекомендуется откалибровать приложение под конкретное устройство:

1. **Эталон.** Возьмите либо профессиональный шумомер (Class 2 IEC 61672), либо другое Android-устройство с известной корректной калибровкой.
2. **Условия.** Стабильный шумовой источник (вентилятор, белый шум 60–70 дБ из соседней комнаты), оба прибора в одной точке на расстоянии ≤ 5 см друг от друга.
3. **Сравнение.** Запустите замер на 30+ секунд, сравните Leq.
4. **Коррекция.** Откройте Settings → Калибровка и подвиньте slider на разницу (если эталон показал 65 дБ, а Тишина — 62 дБ, выставьте +3.0 дБ).

Калибровочный offset фиксируется на момент Start и сохраняется в каждой записи (`MeasurementEntity.calibrationOffsetDb`); изменение калибровки во время активного замера применится только к следующему Start. Сброс — кнопка «Сбросить калибровку» возвращает offset к 0.0 дБ.

Автокалибровка по эталону тишины (30 дБ) и пресеты под популярные модели запланированы на v1.2.

## Контрибьюция

Проект на ранней стадии. Issues и pull requests приветствуются.
