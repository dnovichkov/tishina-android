# Tishina — MVP Phase 4: Settings + Calibration + Theme + Language

## Overview

Phase 4 заменяет `DefaultSettingsRepository`-stub из Phase 2/3 на полноценную DataStore-backed реализацию, добавляет SettingsScreen с измерительными настройками (калибровка, Fast/Slow), внешним видом (тема, динамические цвета) и языком приложения (системный / русский / английский). После завершения этой фазы у пользователя должен появиться полностью настраиваемый MVP-опыт: открыл Settings → подстроил калибровку → переключил тему → выбрал язык → вернулся на Measure → новые семплы идут через подкорректированный конвейер DSP, история фиксирует калибровку на момент замера.

**Цель фазы:**
- `:core:domain` — модели `AppearanceSettings`, `ThemeMode`, `AppLocale`; расширение `SettingsRepository` интерфейса для appearance + language + reset; новые use-cases (`UpdateCalibrationUseCase`, `ResetCalibrationUseCase`, `UpdateTimeWeightingUseCase`, `UpdateThemeModeUseCase`, `UpdateDynamicColorsUseCase`, `UpdateAppLocaleUseCase`); вспомогательные модели наблюдения (`ObserveAppSettingsUseCase`).
- `:core:data` — `SettingsRepositoryImpl` на `androidx.datastore:datastore-preferences` 1.1.x, Hilt-биндинг в `DataModule`, миграция из stub (просто провайдим новый импл).
- `:core:designsystem` — `TishinaTheme` принимает `ThemeMode` + `Boolean dynamicColors`; реализует `dynamicLightColorScheme`/`dynamicDarkColorScheme` на API 31+, static teal/neutral на API ≤30.
- `:core:ui` — переиспользуемые preference-компоненты `PreferenceCategory`, `ChoicePreference`, `SliderPreference`, `SwitchPreference` (Material 3, без сторонних libs).
- `:feature:settings` — `SettingsViewModel` (MVI lite), `SettingsScreen` с группами: «Измерение» (калибровка slider, time weighting, reset калибровки), «Внешний вид» (тема, динамические цвета), «Язык», подвал-ссылка на «О приложении» (placeholder; полноценный AboutScreen будет в Phase 5).
- `:feature:measure` — `MeasureViewModel` подписывается на `SettingsRepository.config` и использует свежий `MeasurementConfig` на каждый Start вместо `MeasurementConfig()` дефолта; калибровочный offset попадает в `MeasurementEntity.calibrationOffsetDb` на Save (уже зафиксировано Phase 3 в схеме v1, миграции не требуется).
- `:app` — навигация в SettingsScreen из MeasureTopBar (иконка-шестерёнка уже есть как destination в `TishinaNavigation`); `TishinaApp` подписывается на `appearance` и динамически пересобирает `TishinaTheme`; `MainActivity` применяет `AppCompatDelegate.setApplicationLocales(...)` на изменение локали.

**Стратегия calibration vs in-flight session (зафиксировано на планировании):** `MeasurementConfig` инжектится при `StartMeasurementUseCase.invoke(config)`. То есть текущий активный замер использует config, сделанный на момент Start. Если пользователь меняет калибровку во время замера — изменение применится только к следующему Start. Это естественное поведение (config — immutable seed сессии, как `SessionSeed` в Phase 2); hot-reload откладывается на v1.2 (требует AudioRecord-restart и переосмысления состояния DSP-фильтров, что разрушает Leq accumulator).

**Какие FR / NFR из спеки покрываются:**
- **FR-14** — Калибровочный offset: слайдер от −20,0 до +20,0 dB с шагом 0,1, с пояснением методики.
- **FR-16** — Выбор временного взвешивания: Fast (125 мс) — по умолчанию, Slow (1 с).
- **FR-17** — Темы: Системная / Светлая / Тёмная. Опция «Динамические цвета (Android 12+)».
- **FR-18** — Язык: Системный / Русский / Английский (через AppCompatDelegate + LocaleListCompat).
- **FR-19** — Кнопка «Сбросить калибровку» → возврат к 0,0 dB.
- **NFR-1** — Settings экран открывается лениво (Hilt + Compose navigation), не влияет на cold-start Measure-tab.
- **NFR-5** — `SettingsViewModel` восстанавливает состояние из DataStore при reСreate (через `Flow.stateIn` + persistent storage).
- **NFR-13 / NFR-14** — `contentDescription` для всех интерактивных preferences, поддержка масштабирования шрифта.
- **NFR-15 / NFR-16** — контраст ≥ 4.5:1, цвет не единственный носитель (иконка + текст для каждого preference).
- **NFR-17 / NFR-18 / NFR-19** — все строки через `strings.xml`, plurals для русского, числа через `NumberFormat(Locale)`.

**Что НЕ входит в Phase 4 (намеренно отложено):**
- **FR-15** (A/C/Z выбор) — спека прямо разрешает «Только A в MVP». C-weighting фильтр и UI-toggle уходят в v1.1 (P1).
- **FR-20** (экспорт CSV через Storage Access Framework) — P1, уходит в Phase 5 / Release вместе с другими data-фичами.
- **Impulse time-weighting** (35 мс / 1.5 с) — P2 по спеке, не MVP.
- **FR-21 / FR-22 (AboutScreen + дисклеймер)** — Phase 5 (Polish + About + bulk-delete).
- **FR-12 / FR-13 (bulk-delete + search/filter в History)** — Phase 5.
- **Auto-калибровка по эталону** (тихая комната, 30 dB) — P2, v1.2.
- **Pre-built калибровки под популярные модели** — v1.2.
- **Импорт/экспорт настроек** — v1.3.
- **Hot-reload калибровки активного замера** — v1.2 (требует AudioRecord-restart).
- **Share Intent**, **PNG-снимок графика**, **Zoom/pan по графику в Detail** — Phase 5 / Release.

## Context (from discovery)

**Состояние репозитория после Phase 3 (коммиты `ec30ea0`...`8172ba6`):**
- 13 модулей собираются; `:app:assembleDebug` зелёный.
- `:core:domain` содержит интерфейс `SettingsRepository` (только measurement-часть: `config`, `updateCalibrationOffset`, `updateFrequencyWeighting`, `updateTimeWeighting`) и stub-реализацию `DefaultSettingsRepository` (always-default, no-op setters). Phase 4 расширяет интерфейс новыми методами и моделями.
- `:core:domain/model/MeasurementConfig.kt` уже содержит `frequencyWeighting/timeWeighting/calibrationOffsetDb`. Используется в `StartMeasurementUseCase.invoke(config)`.
- `:core:data` собран Phase 3, содержит `TishinaDatabase`, `MeasurementDao`, `DataModule`. **Не содержит** DataStore-зависимости — добавим в Phase 4.
- `:feature:settings` — placeholder с `PlaceholderScreen`, переписываем полностью.
- `:feature:about` — placeholder, в Phase 4 не трогаем (Phase 5).
- `:core:designsystem` — содержит `TishinaTheme` (предположительно фиксированный M3 color scheme); расширяем для observe `AppearanceSettings`.
- `:core:ui` — содержит общие composables (`AppEmptyState`, `SplLineChart` после Phase 3 Task 6), добавим preference-компоненты.
- `:app/MainActivity.kt` — точка входа `AppCompatDelegate.setApplicationLocales(...)`.
- `MeasureViewModel` инжектится `StartMeasurementUseCase` + `SaveMeasurementUseCase`; **не инжектится** `SettingsRepository` напрямую. В Phase 4 добавляем `ObserveAppSettingsUseCase` (или прямую инжекцию `SettingsRepository`) и используем actual config вместо `MeasurementConfig()` дефолта.
- `MeasurementEntity` уже сохраняет `calibrationOffsetDb`/`weighting`/`timeWeighting` (Phase 3). Никаких миграций v1→v2 не нужно.

**Зафиксированные версии:**
- Kotlin 2.0.21 + K2; AGP 8.7.3; Compose BOM 2026.05.00; Hilt 2.55; Room 2.8.4.
- AppCompat — нужно подтвердить наличие 1.7.x в `libs.versions.toml` (для `AppCompatDelegate.setApplicationLocales`); если нет — добавляем.
- DataStore Preferences 1.1.x — **новая зависимость** (alias `androidx.datastore.preferences` в `libs.versions.toml`).
- JUnit 5.11.x + MockK 1.13.x + Turbine 1.2.x + Robolectric 4.13 + Roborazzi 1.30.x.

**Источники истины:**
- Спецификация `docs/specs/tishina-spec.md`:
  - § 4 — FR-14, FR-16, FR-17, FR-18, FR-19.
  - § 6 — UX SettingsScreen (Material 3 Preference-стиль, группы «Измерение» / «Внешний вид» / «Данные» / «О приложении»; чек-лист цветовой палитры; teal `#0FB5BA`).
  - § 7 — архитектура (Clean Architecture, MVI lite, поток данных от Settings к Measure).
  - § 8 — DataStore (Preferences) 1.1.x как замена SharedPreferences (ACID, async, Flow API).
  - § 11 — методология калибровки (offset −20…+20 дБ, пояснение про сравнение с эталонным шумомером).
  - § 13 — стратегия тестирования (DataStore-репо ≥ 80%, ViewModels ≥ 85%).
- Завершённые планы Phase 1–3 — образец TDD-формата.

## Development Approach

- **Testing approach:** **TDD (tests first)** — глобальное правило проекта ([[feedback_tdd_default]]) и принципиальная политика, выбранная на планировании. Для каждой задачи с поведением (DataStore round-trip, ViewModel state-машина, theme observer, locale applier) **тест пишется первым**, реализация — после того как тест зафиксировал контракт. Чистые setup-задачи (build.gradle.kts dependencies, Hilt-модули) валидируются успешной сборкой + наличием класса в DI-графе.
- Complete each task fully before moving to the next.
- Make small, focused changes.
- **CRITICAL: every task MUST include new/updated tests** for code changes in that task:
  - JUnit 5 unit-тесты для domain-моделей, use-cases (с `FakeSettingsRepository` или `mockk<SettingsRepository>`).
  - Robolectric + DataStore preference round-trip (создаём временный `DataStore<Preferences>` в `tempDir`, проверяем persistence через `.first()`).
  - Turbine-тесты `StateFlow` для `SettingsViewModel`, обновлений `MeasureViewModel` (заменяем static config на observe).
  - Roborazzi screenshot-тесты для `SettingsScreen` (light + dark, все 3 темы, обе локали).
  - Compose UI-тесты через `createComposeRule()` для интерактивных сценариев (drag slider → emit `UpdateCalibration`, click reset → emit `ResetCalibration`).
  - Тесты покрывают success **и** error/edge: out-of-range калибровка (−21 / +21), null/empty значения, локаль не из enum, конкурентные updates.
- **CRITICAL: all tests must pass before starting next task** — no exceptions.
- **CRITICAL: update this plan file when scope changes during implementation.**
- Run tests after each change (`./gradlew :feature:settings:testDebugUnitTest`, `:core:data:testDebugUnitTest` и т. д. локально быстрее, чем полный build).
- Maintain backward compatibility: не ломаем интерфейс `MeasureScreen()` / `HistoryScreen()` / `DetailScreen()` без default параметров — same pattern как в Phase 2/3.

## Testing Strategy

### Unit tests (JUnit 5 + MockK + Turbine)

| Слой | Что тестируется | Целевое покрытие |
|---|---|---|
| `:core:domain` — `AppearanceSettings`, `ThemeMode`, `AppLocale` | конструкторы, equality, copy, граничные значения (calibration −20.0/0.0/+20.0/−20.1/+20.1) | ≥ 90% |
| `:core:domain` — расширенный `SettingsRepository` + новые use-cases | `UpdateCalibrationUseCase` (валидация −20..+20, проброс в repository); `ResetCalibrationUseCase` (вызывает `updateCalibrationOffset(0f)`); `UpdateThemeModeUseCase`, `UpdateDynamicColorsUseCase`, `UpdateAppLocaleUseCase`, `UpdateTimeWeightingUseCase`; `ObserveAppSettingsUseCase` (combine `config` + `appearance`) — через `mockk<SettingsRepository>` плюс `FakeSettingsRepository` для UI-тестов | ≥ 90% |
| `:core:data` — `SettingsRepositoryImpl` | round-trip всех 6 ключей через DataStore (in-memory `PreferenceDataStoreFactory` в tmp-dir); пустой DataStore возвращает дефолты; некорректные строки enum'ов fallback на дефолт; `config` Flow эмитит на любое изменение; `appearance` Flow эмитит независимо | ≥ 90% |
| `:core:designsystem` — `TishinaTheme` | parametric-test для всех (ThemeMode × Dynamic on/off × API ≥31 vs ≤30) — корректная color-scheme выбирается; system follow при `ThemeMode.System` берёт `isSystemInDarkTheme()` | ≥ 85% |
| `:feature:settings` `SettingsViewModel` | начальная загрузка дефолтов; событие `ChangeCalibration(15.0f)` → use-case вызван; `ChangeCalibration(-25f)` → `ShowSnackbar(settings_calibration_out_of_range)`; `ResetCalibration` → use-case вызван; `ChangeThemeMode`/`ChangeDynamicColors`/`ChangeAppLocale`/`ChangeTimeWeighting` — каждое эмитит соответствующий effect | ≥ 85% |
| Composables UI | smoke-рендеринг + screenshot baselines + интерактивное поведение через `createComposeRule` (slider drag, choice select, switch toggle) | Roborazzi + Compose UI Test |

### Robolectric instrumentation-стиль тесты (под `src/test/`)

- `SettingsRepositoryImplTest` (`@RunWith(RobolectricTestRunner)`) — реальный `PreferenceDataStoreFactory.create(file = tmpFile)`, проверяет:
  - дефолтные значения при пустом store;
  - `updateCalibrationOffset(+3.5f) → config.first().calibrationOffsetDb == 3.5f`;
  - повторное `updateCalibrationOffset` эмитит новое значение в `config`;
  - `updateThemeMode(Dark)` → `appearance.first().themeMode == Dark`;
  - конкурентные `update*` калькулируются последовательно (preferences atomicity);
  - повреждённый file → handler возвращает defaults (`corruptionHandler`).
- `SettingsScreenComposeUiTest` — `createComposeRule()`:
  - Calibration slider: setValue(7.5f) → callback `ChangeCalibration(7.5f)`;
  - Reset button click → `ResetCalibration`;
  - ThemeMode chips: click "Тёмная" → `ChangeThemeMode(Dark)`;
  - DynamicColors switch: toggle → `ChangeDynamicColors(false)`;
  - Locale radio: click "English" → `ChangeAppLocale(English)`.
- `TishinaThemeObserverTest` — `createComposeRule()` + `FakeSettingsRepository`:
  - старт со светлой темой → меняем на тёмную → пересборка с правильными цветами;
  - dynamicColors=true на API ≥31 → используется `dynamicLightColorScheme`;
  - dynamicColors=true на API ≤30 → fallback на static (Robolectric `@Config(sdk = 30)`).
- `LocaleSwitcherTest` — Robolectric + `AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("ru"))` → проверка `LocaleManagerCompat.getApplicationLocales(application).toLanguageTags() == "ru"`.

### Roborazzi screenshot тесты

- `SettingsScreenScreenshotTest` — `setting_default_light/dark`, `setting_calibration_15db_light/dark`, `setting_dark_theme_selected_light/dark`, `setting_english_locale_light/dark` (8 baseline).
- `CalibrationSliderScreenshotTest` — отдельный композбл в 4 вариантах (`0db`, `+15db`, `-15db`, `at_boundary_+20db`); light + dark (8 baseline).
- `PreferenceCategoryScreenshotTest` — секция с 3 preferences; light + dark (2 baseline).
- `ChoicePreferenceScreenshotTest` — три chips (System/Light/Dark); selected vs not; light + dark (4 baseline).

### Coverage thresholds

- Применяем пороги, заявленные в спеке § 13:
  - `:core:domain` ≥ 90% INSTRUCTION (новые модели + use-cases).
  - `:core:data` ≥ 80% (`SettingsRepositoryImpl`).
  - `:feature:settings` `SettingsViewModel` ≥ 85%.
  - `:core:designsystem` ≥ 85% (новая theme-observer логика).
- Kover XML-репорт публикуется как CI-артефакт; пороги **не** enforcement-фейлят PR в Phase 4 — это будет включено в Phase Release.

### E2E tests

- Полный UI flow тесты ("открыть Settings → подкрутить калибровку → переключить тему → выбрать английский → вернуться на Measure → запустить замер → сохранить → калибровка зафиксирована в записи") — в `:app/src/test/` как Robolectric Compose UI test, используя `FakeSettingsRepository` + `FakeMeasurementRepository` через Hilt test bindings или TestNavHost напрямую. Реальный instrumentation на эмуляторе API 26/30/34 — Phase Release.

## Progress Tracking

- Mark completed items with `[x]` immediately when done.
- Add newly discovered tasks with `➕` prefix.
- Document issues/blockers with `⚠️` prefix.
- Update plan if implementation deviates from original scope.
- Keep plan in sync with actual work done.

## What Goes Where

- **Implementation Steps** (`[ ]` checkboxes): код Kotlin/Compose/DataStore, build.gradle.kts изменения, тесты JUnit/Robolectric/Roborazzi, прогон Gradle-команд (`testDebugUnitTest`, `verifyRoborazziDebug`, `detektAll`, `spotlessCheck`, `lintDebug`, `assembleDebug`), KSP-генерация Hilt.
- **Post-Completion** (no checkboxes): ручная проверка на физическом устройстве (полный поток калибровки, переключение темы в живую, проверка локали), наполнение Play Store-карточки скриншотами с настроенным теме/языком, бэта-релиз `v0.4.0-settings` — переедет в Phase Release.
- **Checkbox placement:** только в `### Task N:` секциях. Success criteria и Overview без чекбоксов.

## Implementation Steps

### Task 1: Domain — AppearanceSettings + расширение SettingsRepository + новые use-cases

- [x] **сначала тест:** `AppearanceSettingsTest`, `ThemeModeTest`, `AppLocaleTest` — конструкторы immutable классов, equality, copy, проверка enum-значений (System/Light/Dark для ThemeMode; System/Russian/English для AppLocale); дефолтные значения (System theme, dynamicColors=true, System locale)
- [x] **сначала тест:** `UpdateCalibrationUseCaseTest` (JUnit 5 + mockk<SettingsRepository>): счастливый путь `invoke(+3.5f)` → `repository.updateCalibrationOffset(+3.5f)`; out-of-range (`-20.1f`, `+20.1f`) → `Result.failure(IllegalArgumentException)`; граничные значения `-20.0f`, `+20.0f`, `0.0f` — допустимы; precision: `3.55f` округляется до `3.5f` (шаг 0.1) — `Result.success` с округлённым значением
- [x] **сначала тест:** `ResetCalibrationUseCaseTest` — `invoke()` вызывает `repository.updateCalibrationOffset(0f)`; success path; идемпотентность (повторный reset тоже успешен)
- [x] **сначала тест:** `UpdateTimeWeightingUseCaseTest` — `invoke(SLOW)` → `repository.updateTimeWeighting(SLOW)`; invoke(FAST) тоже работает
- [x] **сначала тест:** `UpdateThemeModeUseCaseTest`, `UpdateDynamicColorsUseCaseTest`, `UpdateAppLocaleUseCase` — каждый вызывает соответствующий repository-метод
- [x] **сначала тест:** `ObserveAppSettingsUseCaseTest` — combine `config` + `appearance` → `AppSettingsSnapshot(config, appearance)`; эмиссия при изменении любого из двух flows
- [x] создать `core/domain/src/main/kotlin/ru/dmdp/tishina/core/domain/model/AppearanceSettings.kt`:
  - `enum class ThemeMode { System, Light, Dark }`
  - `enum class AppLocale(val tag: String) { System(""), Russian("ru"), English("en") }` (пустой tag для System означает "follow OS")
  - `data class AppearanceSettings(val themeMode: ThemeMode = ThemeMode.System, val dynamicColors: Boolean = true, val locale: AppLocale = AppLocale.System)`
  - `data class AppSettingsSnapshot(val config: MeasurementConfig, val appearance: AppearanceSettings)` — комбинированный snapshot для подписок
  - companion object: `const val CALIBRATION_MIN_DB = -20.0f`, `const val CALIBRATION_MAX_DB = 20.0f`, `const val CALIBRATION_STEP_DB = 0.1f`
- [x] обновить `core/domain/.../repository/SettingsRepository.kt`:
  - оставить `val config: Flow<MeasurementConfig>` (используется уже Phase 2/3 stub'ом)
  - добавить `val appearance: Flow<AppearanceSettings>`
  - оставить `suspend fun updateCalibrationOffset(db: Float)`, `suspend fun updateFrequencyWeighting(weighting: FrequencyWeighting)` (Phase 4 не вызывает, но интерфейс готов к v1.1), `suspend fun updateTimeWeighting(weighting: TimeWeighting)`
  - добавить `suspend fun updateThemeMode(mode: ThemeMode)`, `suspend fun updateDynamicColors(enabled: Boolean)`, `suspend fun updateAppLocale(locale: AppLocale)`
  - добавить `suspend fun resetCalibration()` (равно `updateCalibrationOffset(0f)`, но имеет смысл иметь отдельный метод для тестирования reset-кнопки)
- [x] обновить `core/domain/.../repository/DefaultSettingsRepository.kt`:
  - добавить `override val appearance: Flow<AppearanceSettings> = flowOf(AppearanceSettings())`
  - добавить no-op реализации новых suspend-методов
  - этот stub остаётся доступным для модулей которые не хотят тянуть `:core:data` (например, screenshot-тестам `:core:designsystem`)
- [x] создать use-cases в `core/domain/.../usecase/`:
  - `UpdateCalibrationUseCase.kt` — `operator fun invoke(db: Float): Result<Float>` — валидация диапазона, округление до шага 0.1, проксирование
  - `ResetCalibrationUseCase.kt` — `suspend operator fun invoke() = repository.resetCalibration()`
  - `UpdateTimeWeightingUseCase.kt`, `UpdateThemeModeUseCase.kt`, `UpdateDynamicColorsUseCase.kt`, `UpdateAppLocaleUseCase.kt` — простые пробросы
  - `ObserveAppSettingsUseCase.kt` — `operator fun invoke(): Flow<AppSettingsSnapshot> = combine(repository.config, repository.appearance) { c, a -> AppSettingsSnapshot(c, a) }`
- [x] обновить `core/testing/src/main/kotlin/ru/dmdp/tishina/core/testing/fakes/FakeSettingsRepository.kt`:
  - in-memory `MutableStateFlow<MeasurementConfig>` + `MutableStateFlow<AppearanceSettings>`
  - все setter-методы обновляют соответствующий StateFlow
  - `seed(config: MeasurementConfig? = null, appearance: AppearanceSettings? = null)` для предзаполнения тестов
- [x] реализовать модели, расширенный интерфейс, обновлённый stub, use-cases, fake — чтобы все тесты позеленели
- [x] run `./gradlew :core:domain:test :core:testing:testDebugUnitTest` — must pass before next task

### Task 2: Data — SettingsRepositoryImpl на DataStore Preferences + DI

- [x] обновить `gradle/libs.versions.toml`:
  - добавить `datastore-preferences = "1.1.1"` (стабильная версия на 2026-05; перепроверить актуальную)
  - alias: `androidx-datastore-preferences = { module = "androidx.datastore:datastore-preferences", version.ref = "datastore-preferences" }`
- [x] обновить `core/data/build.gradle.kts`:
  - добавить `implementation(libs.androidx.datastore.preferences)`
  - убедиться что test-зависимости включают coroutines-test (нужно для `runTest` с datastore)
- [x] **сначала тест:** `SettingsRepositoryImplTest` (`@RunWith(RobolectricTestRunner)`) — 12+ тестов:
  - empty DataStore → `config.first() == MeasurementConfig()` (все дефолты)
  - `updateCalibrationOffset(+3.5f)` → `config.first().calibrationOffsetDb == 3.5f`
  - `updateCalibrationOffset(+3.5f)` затем `updateCalibrationOffset(-1.2f)` → последнее значение остаётся
  - `updateTimeWeighting(SLOW)` → `config.first().timeWeighting == SLOW`
  - `updateFrequencyWeighting(A)` → `config.first().frequencyWeighting == A` (Phase 4 UI не использует, но интерфейс работает)
  - `updateThemeMode(Dark)` → `appearance.first().themeMode == Dark`
  - `updateDynamicColors(false)` → `appearance.first().dynamicColors == false`
  - `updateAppLocale(English)` → `appearance.first().locale == English`
  - `resetCalibration()` после `updateCalibrationOffset(+5f)` → `config.first().calibrationOffsetDb == 0f`
  - Turbine: подписка на `config` → setter → новая эмиссия (verify reactive)
  - Turbine: подписка на `appearance` → setter → новая эмиссия (verify reactive)
  - corruption handler: повреждённый file → defaults возвращаются (через `corruptionHandler` в `PreferenceDataStoreFactory.create`)
  - неизвестное enum-значение в preferences (симулируем через прямую запись `preferencesOf("theme_mode" to "Unknown")`) → fallback на `ThemeMode.System`
- [x] создать `core/data/src/main/kotlin/ru/dmdp/tishina/core/data/settings/SettingsKeys.kt`:
  - `internal object SettingsKeys`
  - `val CALIBRATION_OFFSET_DB = floatPreferencesKey("calibration_offset_db")`
  - `val FREQUENCY_WEIGHTING = stringPreferencesKey("frequency_weighting")`
  - `val TIME_WEIGHTING = stringPreferencesKey("time_weighting")`
  - `val THEME_MODE = stringPreferencesKey("theme_mode")`
  - `val DYNAMIC_COLORS = booleanPreferencesKey("dynamic_colors")`
  - `val APP_LOCALE = stringPreferencesKey("app_locale")`
- [x] создать `core/data/.../settings/SettingsRepositoryImpl.kt`:
  - `@Singleton class SettingsRepositoryImpl @Inject constructor(private val dataStore: DataStore<Preferences>, @IoDispatcher private val ioDispatcher: CoroutineDispatcher) : SettingsRepository`
  - `override val config: Flow<MeasurementConfig> = dataStore.data.map { prefs -> MeasurementConfig(frequencyWeighting = prefs[FREQUENCY_WEIGHTING]?.toFrequencyWeightingSafe() ?: FrequencyWeighting.A, timeWeighting = prefs[TIME_WEIGHTING]?.toTimeWeightingSafe() ?: TimeWeighting.FAST, calibrationOffsetDb = prefs[CALIBRATION_OFFSET_DB] ?: 0f) }.flowOn(ioDispatcher).distinctUntilChanged()`
  - `override val appearance: Flow<AppearanceSettings> = dataStore.data.map { prefs -> AppearanceSettings(themeMode = prefs[THEME_MODE]?.toThemeModeSafe() ?: ThemeMode.System, dynamicColors = prefs[DYNAMIC_COLORS] ?: true, locale = prefs[APP_LOCALE]?.toAppLocaleSafe() ?: AppLocale.System) }.flowOn(ioDispatcher).distinctUntilChanged()`
  - setter-методы используют `dataStore.edit { prefs -> prefs[KEY] = value }`
  - private helpers `String.toThemeModeSafe()` / `String.toAppLocaleSafe()` / `String.toFrequencyWeightingSafe()` / `String.toTimeWeightingSafe()` — defensive fallback на дефолт для неизвестных значений
- [x] обновить `core/data/.../di/DataModule.kt`:
  - удалить регистрацию `DefaultSettingsRepository` (если она там есть из Phase 2)
  - `@Binds @Singleton fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository`
  - `@Provides @Singleton fun provideSettingsDataStore(@ApplicationContext context: Context): DataStore<Preferences>` — через `PreferenceDataStoreFactory.create(corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }, produceFile = { context.preferencesDataStoreFile("tishina_settings") })`
- [x] **➕ внеплановая подзадача (возможно):** если `DefaultSettingsRepository` явно биндится в `:app/di/SettingsModule.kt` или другом модуле Phase 2 — найти и убрать, заменить на новый biding из `DataModule`. Использовать `Grep` для поиска `DefaultSettingsRepository` и `provideSettingsRepository`. Проверено: `DefaultSettingsRepository` существует только в `:core:domain` как stub (для screenshot-тестов `:core:designsystem`/`:core:ui`, которые не должны тянуть `:core:data`). Внешних Hilt-биндингов на stub нет — единственный biding `SettingsRepository` теперь идёт от `DataModule.bindSettingsRepository(SettingsRepositoryImpl)`.
- [x] реализовать impl + DataStore + DI — все тесты позеленели
- [x] `./gradlew :core:data:testDebugUnitTest :core:data:detektAll :core:data:lintDebug :app:assembleDebug` — SUCCESSFUL; `:core:data:spotlessCheck` — SUCCESSFUL; Hilt-граф валиден (assembleDebug без MissingBinding ошибок); `:feature:measure:assembleDebug` тоже должен собраться — `MeasureViewModel` пока продолжает использовать stub или дефолт (изменим в Task 8)

### Task 3: SettingsViewModel + UI state machine

- [ ] **сначала тест:** `SettingsViewModelTest` (Turbine + FakeSettingsRepository + StandardTestDispatcher) — 15+ тестов:
  - empty repository → state.first() с дефолтами (calibration=0, timeWeighting=FAST, theme=System, dynamicColors=true, locale=System)
  - seed repository с `calibrationOffset=+5f, themeMode=Dark` → state.first() отражает эти значения
  - event `ChangeCalibration(+3.5f)` → use-case вызван с `+3.5f` → state эмитит новое значение
  - event `ChangeCalibration(-25f)` → use-case возвращает `Result.failure` → effect `ShowSnackbar(settings_calibration_out_of_range)` без изменения state
  - event `ResetCalibration` → use-case вызван → state эмитит calibrationOffset=0f
  - event `ChangeTimeWeighting(SLOW)` → repository.updateTimeWeighting(SLOW) → state эмитит SLOW
  - event `ChangeThemeMode(Dark)` → state эмитит Dark
  - event `ChangeDynamicColors(false)` → state эмитит false
  - event `ChangeAppLocale(English)` → state эмитит English + effect `ApplyAppLocale(English)` (для применения в MainActivity)
  - consecutive events: rapid `ChangeCalibration(+1f)`, `(+2f)`, `(+3f)` → final state = `+3f` (последний выигрывает)
  - подписка остаётся активной пока ViewModel живой; `WhileSubscribed(5000)` корректно конфигурирован
  - rotation simulation: state восстанавливается из repository, не из SavedStateHandle (DataStore — single source of truth)
- [ ] создать `feature/settings/src/main/kotlin/ru/dmdp/tishina/feature/settings/SettingsUiState.kt`:
  - `data class SettingsUiState(val calibrationOffsetDb: Float = 0f, val timeWeighting: TimeWeighting = TimeWeighting.FAST, val themeMode: ThemeMode = ThemeMode.System, val dynamicColors: Boolean = true, val locale: AppLocale = AppLocale.System, val loading: Boolean = true)`
- [ ] создать `feature/settings/.../SettingsUiEvent.kt`:
  - `sealed interface SettingsUiEvent`
  - `data class ChangeCalibration(val db: Float) : SettingsUiEvent`
  - `data object ResetCalibration : SettingsUiEvent`
  - `data class ChangeTimeWeighting(val weighting: TimeWeighting) : SettingsUiEvent`
  - `data class ChangeThemeMode(val mode: ThemeMode) : SettingsUiEvent`
  - `data class ChangeDynamicColors(val enabled: Boolean) : SettingsUiEvent`
  - `data class ChangeAppLocale(val locale: AppLocale) : SettingsUiEvent`
- [ ] создать `feature/settings/.../SettingsUiEffect.kt`:
  - `sealed interface SettingsUiEffect`
  - `data class ShowSnackbar(val messageRes: Int) : SettingsUiEffect`
  - `data class ApplyAppLocale(val locale: AppLocale) : SettingsUiEffect` — поднимаем эффект в MainActivity для AppCompatDelegate (Task 7)
- [ ] создать `feature/settings/.../SettingsViewModel.kt`:
  - `@HiltViewModel class SettingsViewModel @Inject constructor(private val observeAppSettings: ObserveAppSettingsUseCase, private val updateCalibration: UpdateCalibrationUseCase, private val resetCalibration: ResetCalibrationUseCase, private val updateTimeWeighting: UpdateTimeWeightingUseCase, private val updateThemeMode: UpdateThemeModeUseCase, private val updateDynamicColors: UpdateDynamicColorsUseCase, private val updateAppLocale: UpdateAppLocaleUseCase) : ViewModel()`
  - `val state: StateFlow<SettingsUiState>` через `observeAppSettings().map { snapshot -> SettingsUiState(...) }.stateIn(viewModelScope, WhileSubscribed(5000), SettingsUiState())`
  - `private val _effects = Channel<SettingsUiEffect>(capacity = Channel.BUFFERED)`
  - `val effects: Flow<SettingsUiEffect> = _effects.receiveAsFlow()`
  - `fun onEvent(event: SettingsUiEvent) = when (...)` — каждый event делает соответствующий use-case вызов
- [ ] создать `feature/settings/.../di/SettingsUseCaseModule.kt`:
  - `@Module @InstallIn(ViewModelComponent::class) object SettingsUseCaseModule`
  - `@Provides fun provideObserveAppSettingsUseCase(repo: SettingsRepository) = ObserveAppSettingsUseCase(repo)`
  - аналогично для всех 6 use-case'ов
- [ ] реализовать ViewModel + state + events + effects + DI — все тесты зелёные
- [ ] `./gradlew :feature:settings:testDebugUnitTest :feature:settings:detektAll :feature:settings:lintDebug` — SUCCESSFUL

### Task 4: SettingsScreen scaffolding + общие preference-компоненты

- [ ] **сначала тест:** `PreferenceCategoryScreenshotTest` (Roborazzi) — category с заголовком + 3 child preferences; light + dark (2 baseline)
- [ ] **сначала тест:** `ChoicePreferenceScreenshotTest` — 3 chips (System/Light/Dark); selected=middle vs first vs last; light + dark (4 baseline)
- [ ] **сначала тест:** `SliderPreferenceScreenshotTest` — slider с value=0, value=+15, value=-15, value=+20 (граница); light + dark (8 baseline)
- [ ] **сначала тест:** `SwitchPreferenceScreenshotTest` — switch on vs off; light + dark (4 baseline)
- [ ] **сначала тест:** `SettingsScreenScreenshotTest` — `setting_default_light/dark` (полный экран с дефолтами); 2 baseline
- [ ] **сначала тест:** `SettingsScreenComposeUiTest` (createComposeRule):
  - empty repository → loading indicator → дефолтные значения
  - клик "Назад" → onNavigateBack callback
  - TopAppBar title корректно отображается
- [ ] создать `core/ui/src/main/kotlin/ru/dmdp/tishina/core/ui/preferences/PreferenceCategory.kt`:
  - `Composable fun PreferenceCategory(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit)`
  - `Column { Text(title, style = labelLarge, color = primary), content() }`
- [ ] создать `core/ui/.../preferences/ChoicePreference.kt`:
  - `Composable fun <T> ChoicePreference(title: String, options: List<T>, optionLabels: List<String>, selectedOption: T, onOptionSelected: (T) -> Unit, modifier: Modifier = Modifier)`
  - Material 3 SegmentedButton или FilterChip row
  - `contentDescription` для каждой опции
- [ ] создать `core/ui/.../preferences/SliderPreference.kt`:
  - `Composable fun SliderPreference(title: String, value: Float, onValueChange: (Float) -> Unit, valueRange: ClosedFloatingPointRange<Float>, steps: Int, valueFormatter: (Float) -> String, onResetClick: (() -> Unit)? = null, modifier: Modifier = Modifier)`
  - Material 3 Slider + Text current value + optional Reset IconButton
  - `contentDescription` "Слайдер калибровки, текущее значение N дБ"
- [ ] создать `core/ui/.../preferences/SwitchPreference.kt`:
  - `Composable fun SwitchPreference(title: String, subtitle: String? = null, checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier)`
  - row с text-column + Material 3 Switch
- [ ] переписать `feature/settings/.../SettingsScreen.kt`:
  - `@Composable fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel(), onNavigateBack: () -> Unit = {}, modifier: Modifier = Modifier)`
  - `SettingsScreenContent` без VM-параметра (тестируемая версия)
  - `Scaffold` с `TopAppBar(title = "Настройки", navigationIcon = ArrowBack)`
  - `LazyColumn` с группами `PreferenceCategory`:
    - «Измерение»: SliderPreference (калибровка) + Button "Сбросить калибровку" + ChoicePreference (Fast/Slow)
    - «Внешний вид»: ChoicePreference (тема) + SwitchPreference (динамические цвета)
    - «Язык»: ChoicePreference (System/Russian/English)
    - **Footer:** plain text ссылка/кнопка "О приложении" (placeholder action до Phase 5)
  - подписка на `viewModel.effects` через `LaunchedEffect` для отображения Snackbar (`SnackbarHostState`) и эмиссии `ApplyAppLocale` наверх в MainActivity (но в Phase 4 Task 4 мы только готовим UI; реальное применение локали — Task 7)
- [ ] добавить локализационные строки в `core/ui/src/main/res/values/` и `values-ru/`:
  - `settings_title`, `settings_back_cd`
  - `settings_category_measurement`, `settings_category_appearance`, `settings_category_language`
  - `settings_calibration_title`, `settings_calibration_description`, `settings_calibration_format` ("%.1f дБ"), `settings_calibration_reset`, `settings_calibration_out_of_range`
  - `settings_time_weighting_title`, `settings_time_weighting_fast`, `settings_time_weighting_slow`, `settings_time_weighting_description`
  - `settings_theme_title`, `settings_theme_system`, `settings_theme_light`, `settings_theme_dark`
  - `settings_dynamic_colors_title`, `settings_dynamic_colors_description`
  - `settings_language_title`, `settings_language_system`, `settings_language_russian`, `settings_language_english`
  - `settings_about_link`
- [ ] реализовать components + SettingsScreen — все тесты зелёные
- [ ] `./gradlew :core:ui:testDebugUnitTest :core:ui:verifyRoborazziDebug :feature:settings:testDebugUnitTest :feature:settings:verifyRoborazziDebug` — SUCCESSFUL

### Task 5: Measurement settings — Calibration slider + Reset + Time weighting

- [ ] **сначала тест:** `SettingsScreenCalibrationTest` (Compose UI Test + createComposeRule + state hoisting):
  - initial calibration=0 → slider position и value text "0.0 дБ"
  - drag slider to position representing +5.5 → onEvent(ChangeCalibration(+5.5f)) callback
  - drag slider beyond max (+20) → slider clamped to +20, callback с +20f
  - click Reset button → onEvent(ResetCalibration)
  - long slider drag (множественные value changes) → throttled callbacks (опционально, если используем `debounce`)
- [ ] **сначала тест:** `SettingsScreenTimeWeightingTest`:
  - initial timeWeighting=FAST → chip "Быстрый (125 мс)" выделен
  - click chip "Медленный (1 с)" → onEvent(ChangeTimeWeighting(SLOW))
- [ ] **сначала тест:** `SettingsScreenScreenshotTest` (расширяем из Task 4):
  - `setting_calibration_15db_light/dark` (2 baseline)
  - `setting_calibration_negative_8db_light/dark` (2 baseline)
  - `setting_calibration_at_max_light/dark` (2 baseline)
  - `setting_time_weighting_slow_selected_light/dark` (2 baseline)
- [ ] **➕ возможно:** добавить debounce 100ms на slider drag (избежать большого числа DataStore writes). Решение: использовать `var localValue` (mutableStateOf, не сохраняется), отправка onEvent только на `onValueChangeFinished`. Это natural pattern для Material 3 Slider
- [ ] обновить `SettingsScreenContent` (или вынести в `MeasurementSettingsSection`):
  - подкомпонент `CalibrationPreference(value: Float, onValueChange: (Float) -> Unit, onReset: () -> Unit)`:
    - `var localValue by remember(value) { mutableStateOf(value) }` — для smooth drag без debouncing
    - `Slider(value = localValue, onValueChange = { localValue = it }, onValueChangeFinished = { onValueChange(localValue) }, valueRange = -20f..20f, steps = 400)`
    - текст текущего значения форматируется через `String.format(Locale.current, "%.1f", value)` + локализованная единица "дБ"
    - кнопка "Сбросить калибровку" под слайдером (FilledTonalButton)
    - описание методики снизу: `Text(stringResource(R.string.settings_calibration_description), style = bodySmall)`
  - подкомпонент `TimeWeightingPreference(value: TimeWeighting, onValueChange: (TimeWeighting) -> Unit)`:
    - SegmentedButtonRow с 2 кнопками "Быстрый (125 мс)" / "Медленный (1 с)"
    - описание под выбором: "Быстрый — для динамичных звуков; Медленный — для усреднённых уровней"
- [ ] реализовать + проверить UI tests — все тесты зелёные
- [ ] `./gradlew :feature:settings:testDebugUnitTest :feature:settings:verifyRoborazziDebug` — SUCCESSFUL

### Task 6: Appearance — Theme + Dynamic Colors + наблюдение в TishinaTheme

- [ ] **сначала тест:** `TishinaThemeTest` (`@RunWith(RobolectricTestRunner)` + `createComposeRule`) — parametric/table-driven 8+ кейсов:
  - `ThemeMode.Light + dynamicColors=false + API≥31` → static light scheme
  - `ThemeMode.Dark + dynamicColors=false + API≥31` → static dark scheme
  - `ThemeMode.Light + dynamicColors=true + API≥31` → `dynamicLightColorScheme(LocalContext)`
  - `ThemeMode.Dark + dynamicColors=true + API≥31` → `dynamicDarkColorScheme(LocalContext)`
  - `ThemeMode.System + isSystemInDarkTheme()=true + dynamicColors=false` → static dark
  - `ThemeMode.System + isSystemInDarkTheme()=false + dynamicColors=false` → static light
  - `dynamicColors=true + API=30` (через `@Config(sdk=30)`) → static (no fallback crash)
  - проверяемо через `MaterialTheme.colorScheme.primary == expected_color`
- [ ] **сначала тест:** `SettingsScreenThemeTest` (Compose UI Test):
  - initial themeMode=System → chip "Системная" выделен
  - click "Тёмная" → onEvent(ChangeThemeMode(Dark))
  - initial dynamicColors=true → switch выключенный
  - click switch → onEvent(ChangeDynamicColors(false))
- [ ] **сначала тест:** `SettingsScreenScreenshotTest` (расширяем):
  - `setting_dark_theme_selected_light/dark` (2 baseline)
  - `setting_dynamic_colors_off_light/dark` (2 baseline)
- [ ] обновить `core/designsystem/.../TishinaTheme.kt`:
  - сигнатура: `@Composable fun TishinaTheme(themeMode: ThemeMode = ThemeMode.System, dynamicColors: Boolean = true, content: @Composable () -> Unit)`
  - логика выбора `useDarkTheme`:
    - `val useDarkTheme = when (themeMode) { System -> isSystemInDarkTheme(); Light -> false; Dark -> true }`
  - логика выбора colorScheme:
    - `val colorScheme = when { dynamicColors && Build.VERSION.SDK_INT >= 31 && useDarkTheme -> dynamicDarkColorScheme(LocalContext.current); dynamicColors && Build.VERSION.SDK_INT >= 31 && !useDarkTheme -> dynamicLightColorScheme(LocalContext.current); useDarkTheme -> TishinaDarkColorScheme; else -> TishinaLightColorScheme }`
  - `MaterialTheme(colorScheme = colorScheme, typography = TishinaTypography, content = content)`
- [ ] обновить `app/.../TishinaApp.kt`:
  - инжектится `ObserveAppSettingsUseCase` (через ViewModel-обёртку или прямо в Compose через `hiltViewModel<AppViewModel>()`)
  - подписывается на `appearance` и передаёт в `TishinaTheme`
  - `val appearance by viewModel.appearance.collectAsStateWithLifecycle(initialValue = AppearanceSettings())`
  - `TishinaTheme(themeMode = appearance.themeMode, dynamicColors = appearance.dynamicColors) { ... }`
  - **➕ возможная подзадача:** если `TishinaApp` пока не имеет VM — создать `AppViewModel @HiltViewModel constructor(observeAppSettings: ObserveAppSettingsUseCase)`. Если уже имеет — расширить
- [ ] подкомпонент `AppearanceSettingsSection` в SettingsScreen:
  - ChoicePreference для ThemeMode (3 chips: Системная/Светлая/Тёмная)
  - SwitchPreference для dynamic colors:
    - на API ≤30 — switch отображается но disabled с подписью "Доступно на Android 12+"
    - на API ≥31 — обычный switch
- [ ] реализовать TishinaTheme + AppearanceSection + AppViewModel — все тесты зелёные
- [ ] `./gradlew :core:designsystem:testDebugUnitTest :feature:settings:testDebugUnitTest :feature:settings:verifyRoborazziDebug :app:assembleDebug` — SUCCESSFUL

### Task 7: Language switching через AppCompatDelegate

- [ ] **сначала тест:** `LocaleSwitcherTest` (Robolectric):
  - `LocaleSwitcher.apply(AppLocale.Russian)` → `LocaleManagerCompat.getApplicationLocales(application).toLanguageTags() == "ru"`
  - `LocaleSwitcher.apply(AppLocale.English)` → "en"
  - `LocaleSwitcher.apply(AppLocale.System)` → `LocaleListCompat.getEmptyLocaleList()` применён → `LocaleListCompat.toLanguageTags() == ""`
  - идемпотентность: повторный apply с тем же locale не меняет состояние
- [ ] **сначала тест:** `SettingsScreenLanguageTest` (Compose UI Test):
  - initial locale=System → chip "Системный" выделен
  - click "Русский" → onEvent(ChangeAppLocale(Russian))
  - click "English" → onEvent(ChangeAppLocale(English))
- [ ] **сначала тест:** `MainActivityLocaleEffectTest` — `MainActivity` подписывается на `effects` от `SettingsViewModel`, при получении `ApplyAppLocale(Russian)` вызывает `AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("ru"))`. Альтернатива: применять локаль в `SettingsViewModel` напрямую (но это нарушит "ViewModel не должен трогать Android API" — поэтому через effect наверх)
- [ ] добавить AppCompat в `gradle/libs.versions.toml` (если ещё нет):
  - `androidx-appcompat = "1.7.0"`
  - alias: `androidx-appcompat = { module = "androidx.appcompat:appcompat", version.ref = "androidx-appcompat" }`
- [ ] обновить `app/build.gradle.kts`:
  - добавить `implementation(libs.androidx.appcompat)` если отсутствует
- [ ] обновить `app/src/main/AndroidManifest.xml`:
  - в `<application>` добавить `android:appCategory="productivity"` (необязательно, но улучшает категоризацию)
  - **обязательно** для per-app language настройки: создать `app/src/main/res/xml/locales_config.xml` со списком `<locale android:name="ru"/>` + `<locale android:name="en"/>` и привязать через `android:localeConfig="@xml/locales_config"` в `<application>` (требование Android 13+)
- [ ] создать `app/src/main/kotlin/ru/dmdp/tishina/app/locale/LocaleSwitcher.kt`:
  - `object LocaleSwitcher`
  - `fun apply(locale: AppLocale)`:
    - `AppCompatDelegate.setApplicationLocales(when (locale) { AppLocale.System -> LocaleListCompat.getEmptyLocaleList(); else -> LocaleListCompat.forLanguageTags(locale.tag) })`
- [ ] обновить `MainActivity.kt`:
  - в `setContent` (или одно из `LaunchedEffect`) подписаться на `SettingsViewModel.effects` (через `AppViewModel` или `hiltViewModel()` локально на NavGraph SettingsScreen с навигационной обёрткой)
  - на каждый `ApplyAppLocale(locale)` эффект вызвать `LocaleSwitcher.apply(locale)` — это вызывает recreate activity и применение строковых ресурсов
  - **альтернативный паттерн:** применить в `SettingsViewModel.init` или внутри `onEvent(ChangeAppLocale)` через прямую инжекцию `Application` контекста + `AppCompatDelegate`. Мы выберем effect-pattern (ViewModel platform-agnostic)
- [ ] реализовать LocaleSwitcher + MainActivity подписку + locales_config.xml — все тесты зелёные
- [ ] `./gradlew :app:testDebugUnitTest :feature:settings:testDebugUnitTest :app:assembleDebug` — SUCCESSFUL

### Task 8: Wire to MeasureViewModel — observe config from Settings

- [ ] **сначала тест:** `MeasureViewModelSettingsIntegrationTest` (расширяем `MeasureViewModelStartTest`):
  - инжектируем `FakeSettingsRepository` с seed `MeasurementConfig(calibrationOffsetDb=+5f, timeWeighting=SLOW)`
  - событие `Start` → `StartMeasurementUseCase.invoke(MeasurementConfig(timeWeighting=SLOW, calibrationOffsetDb=+5f, ...))` — config взят из repository, не дефолт
  - изменение config во время активного замера → текущая сессия не прерывается; новый config будет использован только на следующий Start
  - после Save → MeasurementEntity содержит calibrationOffsetDb=+5f, timeWeighting="SLOW" (используя in-memory FakeMeasurementRepository для проверки .save() вызова)
- [ ] обновить `MeasureViewModel.kt`:
  - инжектится `ObserveAppSettingsUseCase` или прямо `SettingsRepository`
  - private `currentConfig: StateFlow<MeasurementConfig> = settingsRepository.config.stateIn(viewModelScope, Eagerly, MeasurementConfig())`
  - в `handleStartRequested` (или эквивалентном методе) использовать `currentConfig.value` вместо `MeasurementConfig()` default
  - **CRITICAL:** не подменять config в середине активного замера. `SessionSeed` (если он есть с Phase 2) фиксирует config на момент Start
- [ ] обновить `feature/measure/.../di/MeasureUseCaseModule.kt`:
  - добавить provide для `ObserveAppSettingsUseCase` если ViewModel использует use-case (не прямую repo-инжекцию)
- [ ] обновить существующие тесты `MeasureViewModelStartTest`/`PauseResetTest`/`SaveFlowTest` — добавить параметр `settingsRepository = FakeSettingsRepository()` где требуется
- [ ] реализовать integration + проверить все тесты `:feature:measure` зелёные
- [ ] `./gradlew :feature:measure:testDebugUnitTest :feature:measure:verifyRoborazziDebug :app:assembleDebug` — SUCCESSFUL

### Task 9: Verify acceptance + README + coverage report

- [ ] **критерии приёмки (FR-чек):**
  - FR-14: калибровочный slider −20..+20 с шагом 0.1 работает, значение сохраняется в DataStore, применяется к новым замерам, фиксируется в `MeasurementEntity.calibrationOffsetDb` на Save (✓ ручная проверка через лог DataStore)
  - FR-16: переключатель Fast/Slow работает, влияет на DSP-фильтрацию (применяется на следующий Start)
  - FR-17: 3 темы переключаются мгновенно; динамические цвета на API≥31 включают Material You; на API≤30 опция показана как disabled или скрыта
  - FR-18: смена локали → `AppCompatDelegate.setApplicationLocales` → recreate Activity → новые строки. Системный — следует ОС
  - FR-19: кнопка "Сбросить калибровку" → значение возвращается в 0.0 дБ
- [ ] **NFR-чек:**
  - NFR-1: Settings экран открывается лениво (не влияет на Measure cold-start)
  - NFR-13: `contentDescription` присутствуют (Compose UI test проверка)
  - NFR-14: Шрифт масштабируется до 200% без overflow (Roborazzi с font scale)
  - NFR-17: все строки в `strings.xml` (Detekt-правило `NoHardcodedStrings` зелёное)
- [ ] добавить screenshot-тесты в матрицу: SettingsScreen в `font-scale=2.0` light + dark (2 baseline) — проверка NFR-14
- [ ] обновить `README.md` (если есть):
  - раздел "Features" — добавить "Калибровка, темы, языки"
  - раздел "Architecture" — упомянуть DataStore Preferences для Settings
  - раздел "Development" — обновить команды если что-то изменилось
- [ ] запустить полный test suite:
  - `./gradlew test` — все unit-тесты
  - `./gradlew verifyRoborazziDebug` — все screenshot baselines
  - `./gradlew detekt ktlintCheck lint` — линтеры
  - `./gradlew koverXmlReportDebug` — coverage report, проверка порогов
- [ ] обновить `MEMORY.md` (через update memory protocol) — добавить пометку что Phase 4 завершена; если открыты новые архитектурные ноты — добавить
- [ ] `./gradlew test verifyRoborazziDebug detekt ktlintCheck lint :app:assembleDebug` — SUCCESSFUL; все тесты зелёные; coverage отчёты доступны

## Technical Details

### Поток данных Settings → Measure (после Phase 4)

```
SettingsScreen (UI)
  └─► SettingsViewModel.onEvent(ChangeCalibration(+3.5f))
        └─► UpdateCalibrationUseCase(+3.5f)
              └─► SettingsRepository.updateCalibrationOffset(+3.5f)
                    └─► DataStore<Preferences>.edit { it[CALIBRATION_OFFSET_DB] = +3.5f }
                          └─► DataStore.data Flow эмитит новое значение
                                └─► SettingsRepository.config: Flow<MeasurementConfig> эмитит обновлённый config
                                      ├─► SettingsViewModel.state эмитит обновлённый calibrationOffsetDb (UI rerender)
                                      └─► MeasureViewModel.currentConfig: StateFlow эмитит новое значение
                                            └─► Следующий Start использует новый config
```

### Поток данных Theme → UI

```
SettingsScreen (UI)
  └─► SettingsViewModel.onEvent(ChangeThemeMode(Dark))
        └─► UpdateThemeModeUseCase(Dark)
              └─► SettingsRepository.updateThemeMode(Dark)
                    └─► DataStore.data эмитит новое значение
                          └─► SettingsRepository.appearance эмитит обновлённый AppearanceSettings
                                ├─► SettingsViewModel.state эмитит новый themeMode
                                └─► AppViewModel.appearance (в `TishinaApp`) эмитит → TishinaTheme пересобирается
                                      └─► MaterialTheme.colorScheme переключается на dark
```

### Поток данных Locale → Recreate Activity

```
SettingsScreen (UI)
  └─► SettingsViewModel.onEvent(ChangeAppLocale(English))
        ├─► UpdateAppLocaleUseCase(English) → DataStore (persistence)
        └─► _effects.trySend(ApplyAppLocale(English))
              └─► MainActivity.collectAsEffect → LocaleSwitcher.apply(English)
                    └─► AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en"))
                          └─► Activity.recreate() автоматически
                                └─► Все строки перерендериваются на английском
```

### Версии и зависимости

| Группа | Артефакт | Версия | Статус |
|---|---|---|---|
| DataStore | `androidx.datastore:datastore-preferences` | 1.1.1 | **новая** — добавляем в `:core:data` |
| AppCompat | `androidx.appcompat:appcompat` | 1.7.0 | проверить если уже есть; если нет — добавить в `:app` (нужен для `AppCompatDelegate.setApplicationLocales`) |

Все остальные версии остаются как после Phase 3.

### Convention plugin & Hilt wiring

- `:core:data` уже подключает `tishina.android.library` + `tishina.android.hilt` + `tishina.jvm.testing`. Добавляем `implementation(libs.androidx.datastore.preferences)`.
- `:feature:settings` подключает `tishina.android.feature` (предположительно создан в Phase 1) + `tishina.android.hilt` (для `@HiltViewModel`). Уже подключён, изменения не требуются.
- `DataModule` биндится в `SingletonComponent`:
  - `@Binds @Singleton fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository` — **заменяет** stub-bidning из Phase 2
  - `@Provides @Singleton fun provideSettingsDataStore(@ApplicationContext context: Context): DataStore<Preferences>`
- `:feature:settings` `SettingsUseCaseModule` (`@InstallIn(ViewModelComponent::class)`):
  - `@Provides fun provideObserveAppSettingsUseCase(repo: SettingsRepository)`
  - `@Provides fun provideUpdateCalibrationUseCase(repo: SettingsRepository)`
  - аналогично для остальных 5 use-case'ов

### Хранилище DataStore — design notes

- Имя файла: `tishina_settings` (Preferences DataStore автоматически добавляет `.preferences_pb`).
- Расположение: `context.preferencesDataStoreFile("tishina_settings")` — внутри app-private storage (NFR-11 ✓).
- `corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }` — defensive (NFR-7: crash-free sessions ≥ 99.5%).
- Не используем `migrations` потому что это первая версия store-а (нечего мигрировать).

### Известные ограничения и допущения

- **Hot-reload калибровки активного замера не поддерживается** — задокументировано в Overview. Изменения config применяются при следующем Start.
- **AppCompatDelegate.setApplicationLocales требует Activity.recreate()** — пользователь увидит мгновенное мигание/перезапуск экрана при смене локали. Это стандартное поведение AppCompat 1.6+; альтернатива (replace-strings without recreate) требует кастомного `ContextWrapper` и осложняет архитектуру.
- **Dynamic colors fallback на API ≤30** — switch disabled, описание "Доступно на Android 12+". Это chosen UX — спека § 6 это и подразумевает.
- **Migration test for v1 → v2** не возникает в Phase 4: колонки `calibrationOffsetDb`/`weighting`/`timeWeighting` уже существуют в Room schema v1 (зафиксировано в Phase 3). Реальная миграционная инфра будет тестироваться когда добавим колонку в одной из будущих фаз (например, dose-метрика в v2.0).
- **`DefaultSettingsRepository` stub остаётся в `:core:domain`** — для screenshot-тестов `:core:designsystem` и `:core:ui` которые не должны тянуть `:core:data`. Hilt-граф `:app` использует `SettingsRepositoryImpl`.

## Post-Completion

*Items requiring manual intervention or external systems — no checkboxes, informational only.*

**Manual verification** (после завершения Phase 4):

- Установить debug APK на физическое Android-устройство (API 26+ и желательно API 33+); выполнить полный пользовательский сценарий:
  - запуск → tab Measure → 30 секунд замера в тихой комнате → отметить показания (например, 25 дБ);
  - открыть Settings → подкрутить калибровку до +5 дБ → вернуться в Measure → проверить что показания выросли на +5 дБ (например, 30 дБ);
  - переключить тему на «Тёмная» → проверить что весь UI стал тёмным включая историю и Detail;
  - на API≥31 (Pixel 6+, Android 12+) включить «Динамические цвета» → проверить что primary color сменился на оттенок текущих обоев;
  - на API≤30 (Android 11 или ниже) проверить что переключатель «Динамические цвета» disabled;
  - переключить язык на «English» → проверить что все строки перевелись, кроме записей пользователя (заметки в History);
  - вернуть язык на «Системный» → следует системному выбору;
  - выполнить тестовый замер → сохранить → открыть Detail → проверить что заметка отображается на новой локали интерфейса, а сам контент заметки (русский ввод) остаётся;
  - сбросить калибровку → значение возвращается в 0.0 дБ → проверка что значение применилось.
- Проверить TalkBack: на каждом preference (slider, choice, switch) объявляется метка + текущее значение; интерактивные элементы доступны.
- Проверить ротацию во время изменения slider'а калибровки: значение сохраняется (DataStore single source of truth + recomposition).
- Проверить настройку «Размер шрифта» в системных настройках → 200% → SettingsScreen остаётся читаемым без overflow.
- Замерить cold start (`adb shell am start-activity -W -n ru.dmdp.tishina/.MainActivity`) и убедиться, что FR-1 ≤ 1 с не регрессировал (DataStore ленивая инициализация через Hilt provider).

**External system updates** (отложено в Phase Release / Phase 5):

- Опубликовать промежуточный Pre-release на GitHub Releases (`v0.4.0-settings`) для бета-тестеров.
- Снять скриншоты для Play Store / RuStore с тёмной темой, dynamic colors включёнными, английской локалью (8-10 разнообразных вариантов).
- Документировать в README раздел "Калибровка" — рекомендация сравнить с эталонным шумомером или другим Android-устройством для устранения inter-device variance ±3-5 дБ.

**Что переходит в Phase 5 (Polish + About + Bulk-delete):**

- `:feature:about` — полноценный `AboutScreen` с дисклеймером, версией, ссылкой на GitHub, OSS-лицензиями (FR-21, FR-22).
- Дисклеймер на главном экране Measure (иконка `?` в TopAppBar открывает короткий popup-дисклеймер).
- `:feature:history` — bulk-выбор + bulk-удаление (FR-12).
- `:feature:history` — поиск/фильтр по дате и тексту заметки (FR-13, P1).
- Экспорт CSV через Storage Access Framework (FR-20, P1).
- Share Intent (поделиться замером с PNG-снимком графика).
- Zoom/pan по графику в Detail.

**Что переходит в Phase Release (CI/CD + Distribution):**

- Реальный adaptive launcher icon (foreground SVG/vector с волной).
- Privacy Policy на GitHub Pages.
- R8 + ProGuard rules + size optimization до ≤ 6 МБ.
- Instrumentation-тесты на матрице emulator API 26/30/34 (полный e2e сценарий).
- Hilt `@HiltAndroidTest` + `@UninstallModules` инфра.
- Релиз в Google Play / RuStore / Samsung Galaxy Store.
- macrobenchmark cold-start замер для подтверждения FR-1.
- CI/CD: GitHub Actions ci.yml + release.yml + nightly.yml.

*Note: ralphex автоматически переносит завершённый план в `docs/plans/completed/` после прохождения всех чекбоксов.*
