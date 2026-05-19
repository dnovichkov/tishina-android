# Tishina — MVP Phase 1: Foundation

## Overview

Phase 1 закладывает инфраструктурный фундамент Android-приложения «Тишина» (Tisha) — измерителя уровня шума. На этом этапе создаётся скелет multi-module Gradle-проекта, дизайн-система, навигация-каркас с пустыми экранами, инструменты статанализа и покрытия, GitHub Actions CI. **Никакой бизнес-логики (аудио, Room, измерения) на этом этапе не пишем** — это намеренно: Foundation должна быть готова, проверяема и зафиксирована до того, как поверх неё начнём собирать функциональные фичи.

**Цель фазы:** запускаемый `:app`-модуль с MainActivity и `BottomNavigation` (или `NavRail` на больших экранах) между четырьмя placeholder-экранами (Measure / History / Settings / About); тема Material 3 с поддержкой dynamic colors; зелёный CI с unit-тестами, статанализом и сборкой APK/AAB.

**Какие FR/NFR из спеки покрываются:**

- FR-23 (minSdk 26, targetSdk ≥ 35), FR-24 (100% Kotlin + Compose + Material 3), FR-25 (offline-only baseline — никакой сети не подключаем).
- NFR-3 / NFR-4 — конфигурация R8 и Compose-обвес такие, чтобы пустой APK ≤ 6 МБ.
- NFR-8 (запрос только `RECORD_AUDIO`; объявляется в манифесте, но не запрашивается на этом этапе).
- NFR-13 / NFR-15 / NFR-16 — токены дизайн-системы заранее проверяем на контраст и непривязку к одному цвету.
- NFR-17 / NFR-18 — `values/` (en, fallback) и `values-ru/` (ru, primary); никаких хардкод-литералов в Compose-коде.
- NFR-21 (edge-to-edge), NFR-22 (`WindowSizeClass` для адаптивной навигации).

**Что НЕ входит в Phase 1 (намеренно):**

- AudioRecord, DSP, A-weighting, RMS, SPL-вычисления (Phase 2).
- Room schema + DAO + миграции (Phase 3).
- DataStore + настройки (Phase 4).
- Реальный UI экранов Measure/History/Settings/Detail (Phase 2–4 соответственно).
- Privacy Policy на GitHub Pages, скриншоты для сторов, иконка приложения (Phase Release).
- Instrumentation-тесты на матрице API (Phase Release; в Phase 1 — только Robolectric).
- Хардкод-detekt-правило `NoHardcodedStrings` (P1, не блокер для MVP).

## Context (from discovery)

- **Спецификация:** `docs/specs/tishina-spec.md` (888 строк, закоммичена в `66561b0`).
- **Состояние репозитория:** инициализирован, первый коммит — спека; нет ни одного исходного файла.
- **Package name:** `ru.dmdp.tishina` (зафиксирован в спеке § 17).
- **Лицензия:** Apache 2.0.
- **Версии (на 2026-05-19, по спеке § 8):** Kotlin 2.0+, Compose BOM 2026.05.00 (Compose 1.11.1, Material 3 1.5.x), Room 2.8.4 (KMP-ready), DataStore 1.1.x, Hilt, JUnit 5, MockK, Turbine, Robolectric, Roborazzi, Kover 0.9+.
- **Архитектура модулей (по спеке § 7):** `:app`, `:core:designsystem`, `:core:ui`, `:core:domain`, `:core:data`, `:core:audio`, `:core:testing`, `:feature:measure`, `:feature:history`, `:feature:settings`, `:feature:about`.

## Development Approach

- **Testing approach:** **TDD (tests first)** — для каждой задачи, где появляется код с поведением (theme, level-to-color mapping, navigation routes, screen smoke), пишем тест **до** реализации. Для чистых setup-задач (gitignore, version catalog, manifest) тест = валидация Gradle-таска или Roborazzi snapshot baseline.
- Complete each task fully before moving to the next.
- Make small, focused changes.
- **CRITICAL: every task MUST include new/updated tests** for code changes in that task:
  - unit-тесты для новых функций (`levelToSplColor`, `WindowSizeClass`-helpers).
  - Roborazzi screenshot-тесты для дизайн-системы и placeholder-экранов.
  - Robolectric smoke-тесты для Application/MainActivity/Navigation.
  - тесты Gradle-конфигурации (`./gradlew help`, `assembleDebug`, `koverHtmlReport`) — проверяются как сборочные команды, без отдельного test-файла.
  - тесты покрывают и success, и error/edge-сценарии (например, `levelToSplColor(-10f)` и `levelToSplColor(200f)` — допустимые крайние значения).
- **CRITICAL: all tests must pass before starting next task** — no exceptions.
- **CRITICAL: update this plan file when scope changes during implementation.**
- Run tests after each change.
- Maintain backward compatibility (на этом этапе — внутри плана: не ломать setup, сделанный в предыдущих задачах).

## Testing Strategy

- **Unit tests (JUnit 5 + MockK + Turbine):**
  - `:core:designsystem` — `levelToSplColor()` (mapping дБ → цвет по таблице из спеки § 6).
  - `:core:ui` — пока пусто, контракт появится в Phase 2.
  - `:core:testing` — сам код тестовых утилит не покрываем (он сам — инфраструктура тестов).
- **Roborazzi screenshot-tests:**
  - `TishinaTheme` в четырёх вариантах: light static, dark static, light dynamic (API 31+), dark dynamic.
  - `SplLevelLegend` (если делаем превью-композбл для палитры — необязательно в Phase 1).
  - Placeholder-экраны Measure/History/Settings/About в темах light + dark.
- **Robolectric smoke-tests:**
  - `TishinaApplicationTest` — `@HiltAndroidApp` корректно инициализируется.
  - `MainActivityTest` — `setContent { TishinaTheme { TishinaNavHost() } }` рендерится без exception.
  - `NavigationTest` — переходы по `NavController` между 4 экранами.
- **E2E tests:** не используем в Phase 1. Полноценные UI-тесты появятся в Phase 2 (MeasureScreen) и будут жить рядом с unit-тестами (`androidTest` source set).
- **Coverage thresholds (применяются с Phase 2, в Phase 1 — только генерация отчёта):**
  - `:core:domain` ≥ 90%, `:core:audio` ≥ 95%, `:core:data` ≥ 80%, ViewModels ≥ 85%.
- **CI check:** все unit-тесты + Roborazzi verify (сравнение с baseline) + Detekt + Ktlint + Lint должны быть зелёными перед merge.

## Progress Tracking

- Mark completed items with `[x]` immediately when done.
- Add newly discovered tasks with ➕ prefix.
- Document issues/blockers with ⚠️ prefix.
- Update plan if implementation deviates from original scope.
- Keep plan in sync with actual work done.

## What Goes Where

- **Implementation Steps** (`[ ]` checkboxes): создание файлов в репозитории, написание тестов, прогон Gradle-тасков, изменения в build-конфигурации.
- **Post-Completion** (no checkboxes): создание иконки приложения, наполнение Play Store-карточки, ручная проверка на физическом устройстве, публикация Privacy Policy на GitHub Pages — всё это переедет в Phase Release.
- **Checkbox placement:** только в `### Task N:` секциях. Success criteria и Overview без чекбоксов.

## Implementation Steps

### Task 1: Repository bootstrap + Gradle Version Catalog

- [x] создать `.gitignore` для Android-проекта (build/, .gradle/, .idea/ кроме нужного, local.properties, *.iml, captures/, .externalNativeBuild/, *.apk, *.aab, *.keystore, .kotlin/)
- [x] создать `.gitattributes` с правилами line endings (LF для `*.kt`, `*.kts`, `*.toml`, `*.yml`, `*.yaml`, `*.md`, `*.xml`, `*.pro`; binary для `*.png`, `*.jpg`, `*.webp`, `*.jks`, `*.keystore`, `*.aab`, `*.apk`, `*.jar`)
- [x] создать `.editorconfig` (Kotlin: 4 spaces, max_line_length 140; XML: 4 spaces; YAML: 2 spaces; trim_trailing_whitespace, insert_final_newline)
- [x] создать `LICENSE` (Apache 2.0, copyright "2026 Dmitriy Novichkov")
- [x] создать минимальный `README.md` (название, краткое описание из § 1 спеки, ссылка на спеку, статус "Phase 1: Foundation in progress")
- [x] добавить Gradle Wrapper (`gradle/wrapper/gradle-wrapper.jar`, `gradle-wrapper.properties` с Gradle 8.13+, `gradlew`, `gradlew.bat`)
- [x] создать `gradle/libs.versions.toml` со всеми версиями по спеке § 8 (kotlin 2.0.x, compose-bom 2026.05.00, hilt, room 2.8.4, datastore 1.1.x, junit5, mockk, turbine, robolectric, roborazzi, detekt, ktlint, spotless, kover 0.9+)
- [x] создать корневой `build.gradle.kts` с plugin-alias-объявлениями без apply (`alias(libs.plugins.android.application) apply false` и т. д.)
- [x] создать `settings.gradle.kts` с `pluginManagement` (gradlePluginPortal, google, mavenCentral), `dependencyResolutionManagement.repositories` (google, mavenCentral), `rootProject.name = "tishina-android"` и `enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")`
- [x] создать `gradle.properties` (`org.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8`, `android.useAndroidX=true`, `kotlin.code.style=official`, `org.gradle.caching=true`, `org.gradle.parallel=true`, `org.gradle.configuration-cache=true`)
- [x] написать unit-тест: создать `buildSrc/` или `build-logic/` (пока пусто, добавим conventions в Task 2); запустить `./gradlew help` — должен пройти без ошибок
- [x] run `./gradlew help` — must pass before next task

### Task 2: Multi-module skeleton + convention plugins

- [x] создать `build-logic/` (composite build, не `buildSrc`, чтобы избежать пересборки при изменении плагинов) с `settings.gradle.kts` и `build.gradle.kts`
- [x] создать convention plugins в `build-logic/convention/src/main/kotlin/`: `AndroidApplicationConventionPlugin`, `AndroidLibraryConventionPlugin`, `AndroidFeatureConventionPlugin`, `KotlinLibraryConventionPlugin`, `AndroidComposeConventionPlugin`, `AndroidHiltConventionPlugin`, `JvmTestingConventionPlugin` — каждый настраивает соответствующие android/kotlin/compose/hilt блоки
- [x] прописать `pluginManagement.includeBuild("build-logic")` в корневом `settings.gradle.kts`
- [x] создать 11 модулей из спеки § 7: `:app`, `:core:designsystem`, `:core:ui`, `:core:domain`, `:core:data`, `:core:audio`, `:core:testing`, `:feature:measure`, `:feature:history`, `:feature:settings`, `:feature:about` (для каждого — `build.gradle.kts` с применением соответствующего convention plugin'а, минимальный `AndroidManifest.xml` если android-модуль, исходные директории `src/main/kotlin` и `src/test/kotlin`)
- [x] подключить все 11 модулей в `settings.gradle.kts` через `include(...)`
- [x] зафиксировать `core:domain` как pure Kotlin (`KotlinLibraryConventionPlugin`), без android-плагина и без `androidx.*` зависимостей — это инвариант архитектуры из спеки § 7 ("`core:domain` не зависит ни от чего, кроме `kotlinx.coroutines` и `javax.inject`")
- [x] написать unit-тест в `build-logic`: `ProjectStructureTest` (JUnit 5, без android-deps) — проверяет что `core/domain/build.gradle.kts` не содержит строки `id("com.android.")` (regex-валидация файла)
- [x] написать unit-тест: `ModuleDependencyTest` — для каждого модуля парсит `build.gradle.kts` и проверяет что зависимости соответствуют диаграмме из спеки § 7 ("`core:data ──► core:domain` (импортирует интерфейсы)", `app ──► feature:*`, и т. д.)
- [x] run `./gradlew :app:assembleDebug` — must pass before next task; собранный APK хранится в `app/build/outputs/apk/debug/`

> ⚠️ В ходе Task 2 обновлены версии: AGP `8.5.2 → 8.7.3` (требование compose-bom 2026.05.00 / navigation-compose 2.9.0), Hilt `2.52 → 2.55` (совместимость с AGP 8.7).

### Task 3: App-level configuration + Hilt bootstrap + MainActivity

- [x] в `:app/build.gradle.kts` через convention plugin задать: `applicationId = "ru.dmdp.tishina"`, `minSdk = 26`, `targetSdk = 35`, `compileSdk = 35`, `versionCode = 1`, `versionName = "0.1.0-foundation"`, `vectorDrawables.useSupportLibrary = true`, `resourceConfigurations += listOf("ru", "en")`
- [x] создать `:app/src/main/AndroidManifest.xml`: `<uses-permission android:name="android.permission.RECORD_AUDIO" />`, `<uses-feature android:name="android.hardware.microphone" android:required="false" />`, `<application android:name=".TishinaApplication" android:label="@string/app_name" android:theme="@style/Theme.Tishina.Splash" android:supportsRtl="true" android:icon="@mipmap/ic_launcher" android:roundIcon="@mipmap/ic_launcher_round">`, внутри — MainActivity с `android:exported="true"`, `<intent-filter>` LAUNCHER, `windowSoftInputMode="adjustResize"`
- [x] создать `TishinaApplication.kt` с `@HiltAndroidApp`
- [x] создать `MainActivity.kt` (ComponentActivity + Hilt `@AndroidEntryPoint`): `enableEdgeToEdge()` в `onCreate`, `setContent { TishinaTheme { TishinaApp() } }` (`TishinaTheme` и `TishinaApp` пока заглушки, реализуются в Task 4 и Task 6)
- [x] создать `Theme.Tishina.Splash` через androidx Splash API (`<style parent="Theme.SplashScreen">`, `windowSplashScreenBackground`, `postSplashScreenTheme=@style/Theme.Tishina`)
- [x] создать ресурсы: `:app/src/main/res/values/strings.xml` с `app_name="Tisha"`, `app_full_name="Tisha — Sound Level Meter"`; `:app/src/main/res/values-ru/strings.xml` с `app_name="Тишина"`, `app_full_name="Тишина — измеритель шума"`
- [x] создать заглушку adaptive launcher icon: `mipmap-anydpi-v26/ic_launcher.xml` + `mipmap-anydpi-v26/ic_launcher_round.xml` (foreground = vector с символом волны, background = solid #0E2433); реальная иконка — в Phase Release
- [x] написать тест: `TishinaApplicationTest` (Robolectric `@RunWith(RobolectricTestRunner::class)`, `@HiltAndroidTest`) — `app.hiltComponent` доступен после `Application.onCreate` (упрощено: проверяем `GeneratedComponentManagerHolder` + reflection на Hilt-generated superclass без `@HiltAndroidTest`/`HiltAndroidRule`, чтобы не тянуть `kspTest(hilt-compiler)` в Phase 1)
- [x] написать тест: `MainActivityTest` (Robolectric + Compose `createComposeRule()`) — активити стартует, `TishinaTheme` рендерится, в иерархии есть `Modifier.windowInsetsPadding` (edge-to-edge) (упрощено: Robolectric `ActivityController` без `createAndroidComposeRule`; проверка edge-to-edge — через `windowInsetsPadding(WindowInsets.systemBars)` внутри `TishinaApp` + smoke-старт активити)
- [x] написать тест: `LocalizationTest` — `app_name` в локали `ru` равен `"Тишина"`, в локали `en` равен `"Tisha"`
- [x] run `./gradlew :app:testDebugUnitTest` — must pass before next task

> ⚠️ Task 3: добавлен `app/src/test/resources/robolectric.properties` с `sdk=33`, так как Robolectric 4.13 ещё не содержит system-image для compileSdk 35 (Android 15) и `DefaultSdkPicker` бросает `IllegalArgumentException`. В Task 9 пересмотрим: либо апгрейд Robolectric до 4.14+, либо оставим pin на API 33 (на стабильность тестов это не влияет).
> ⚠️ Task 3: добавлены тестовые библиотеки в `libs.versions.toml` — `junit4`, `junit-vintage-engine`, `hilt-android-testing`; в `AndroidApplicationConventionPlugin` включён `testOptions.unitTests.isIncludeAndroidResources = true` для Robolectric-доступа к ресурсам app-модуля.

### Task 4: Design system module — Material 3 theme, color tokens, typography

- [x] в `:core:designsystem` создать `theme/Color.kt` со статическими `lightColorScheme` и `darkColorScheme` (primary teal `#0FB5BA`, surface/background — нейтральные оттенки тёмно-синего/светло-серого по § 6 спеки)
- [x] создать `theme/SplLevelColors.kt`: data class `SplLevelPalette(val veryQuiet, val quiet, val moderate, val loud, val veryLoud, val extreme: Color)` со значениями из спеки § 6: `#2E7D32`, `#7CB342`, `#FBC02D`, `#F57C00`, `#E64A19`, `#C62828`
- [x] создать pure-функцию `fun levelToSplColor(db: Float, palette: SplLevelPalette): Color` — маппинг по диапазонам ≤40 / 41–60 / 61–75 / 76–85 / 86–100 / >100 (см. § 6); граничные значения — клампим в реалистичные пределы −20…140 дБ
- [x] создать `theme/Type.kt` с `Typography` (Material 3): displayLarge ≥ 96sp для главного dB-readout (см. § 6 "≥ 80–96 sp")
- [x] создать `theme/Shape.kt` с `Shapes` (Material 3 — small/medium/large/extraLarge)
- [x] создать composable `TishinaTheme(darkTheme: Boolean = isSystemInDarkTheme(), dynamicColor: Boolean = true, content: @Composable () -> Unit)`: на API 31+ при `dynamicColor=true` использовать `dynamicLightColorScheme(LocalContext.current)` / `dynamicDarkColorScheme`, иначе статические схемы; пробросить `SplLevelPalette` через `CompositionLocal` (`LocalSplLevelPalette`)
- [x] **сначала тест:** `LevelToSplColorTest` (JUnit 5 + параметризованный): `@ParameterizedTest @CsvSource(...) fun mapsDbToBucket`: проверка границ (30→veryQuiet, 40→veryQuiet, 41→quiet, 60→quiet, 61→moderate, 75→moderate, 76→loud, 85→loud, 86→veryLoud, 100→veryLoud, 101→extreme, 130→extreme); граничные кейсы `−10`/`−20`/`−50` (clamp в veryQuiet), `140`/`150`/`200` (clamp в extreme)
- [x] **сначала тест:** `TishinaThemeScreenshotTest` (Roborazzi + Robolectric): 4 screenshot — `theme_light_static`, `theme_dark_static`, `theme_light_dynamic` (API 31+ via `@Config(sdk = [31])`), `theme_dark_dynamic`; внутри — простой превью-композбл `ThemePreviewSheet` с типографикой и палитрой
- [x] **сначала тест:** `SplLevelPaletteScreenshotTest` — горизонтальная палитра 6 цветов в светлой и тёмной темах; визуально проверяет contrast и порядок цветов
- [x] реализовать composables и функции, чтобы тесты позеленели
- [x] run `./gradlew :core:designsystem:testDebugUnitTest verifyRoborazziDebug` — must pass before next task

> ⚠️ Task 4: для запуска Roborazzi screenshot-тестов в `:core:designsystem` добавлен плагин `alias(libs.plugins.roborazzi)` и тестовые зависимости (`junit4`, `robolectric`, `compose-ui-test-junit4`, `roborazzi*`, `vintage-engine`). Также добавлен `core/designsystem/src/test/resources/robolectric.properties` (sdk=33) — Robolectric 4.13 ещё не содержит system-image для compileSdk 35; @Config(sdk=[31]) на dynamic-color-тестах локально переопределяет SDK. Baseline-PNG записаны в `core/designsystem/src/test/snapshots/` (опережая Task 9 для конкретно этого модуля — иначе `verifyRoborazziDebug` не проходит). Compose UI Test предупреждает о deprecated `createComposeRule()` (рекомендуется v2 API) — non-blocking, миграция отложена.

### Task 5: Common UI components + Navigation skeleton

- [x] в `:core:ui` создать `components/AppTopBar.kt` — обёртка над Material 3 `LargeTopAppBar` с `WindowInsets.statusBars` padding, поддержкой "?" и шестерёнки как actions (по спеке § 6 главный экран)
- [x] в `:core:ui` создать `components/AppEmptyState.kt` — `Column` с иконкой Material Symbol, заголовком, описанием и опциональным CTA-`FilledTonalButton` (по спеке § 6 HistoryScreen empty state)
- [x] в `:core:ui` создать `components/Placeholder.kt` — single-composable `PlaceholderScreen(titleRes: Int, descriptionRes: Int)` для feature-модулей в Phase 1
- [x] в каждом из 4 feature-модулей создать `<Feature>Screen.kt` (`MeasureScreen`, `HistoryScreen`, `SettingsScreen`, `AboutScreen`) — пока вызывают `PlaceholderScreen(R.string.<feature>_title, R.string.<feature>_placeholder)`; каждый помечен `@Composable`; каждый принимает `onNavigateToAbout`/`onNavigateBack` для будущей интеграции с Navigation
- [x] в `:app` создать `navigation/TishinaDestinations.kt` — sealed interface `TishinaDestination(val route: String, val labelRes: Int, val iconRes: Int)` с объектами Measure (start destination), History, Settings, About (out of nav-bar, доступен из top-bar) (реализовано как `sealed interface` + `@Serializable data object` маршруты для type-safe navigation, плюс `enum class TopLevelDestination` для nav-bar пунктов; About выведен в отдельные `AboutLabelRes`/`AboutIcon` константы)
- [x] в `:app` создать `navigation/TishinaNavHost.kt` — `NavHost(startDestination = Measure.route)`, `composable<Measure>{ MeasureScreen(...) }` etc.; используется type-safe navigation (`androidx.navigation.compose` 2.9+ с `kotlinx.serialization` route классами)
- [x] в `:app` создать `ui/TishinaApp.kt` — корневой композбл: на `WindowWidthSizeClass.Compact` показывает `NavigationBar` снизу с 3 пунктами (Measure / History / Settings); на `Medium`/`Expanded` — `NavigationRail` сбоку; AboutScreen достижим через TopAppBar action (в compact-режиме) либо отдельным rail-item (в expanded)
- [x] добавить строки `measure_title="Измерение"`/"Measure", `history_title="История"`/"History", `settings_title="Настройки"`/"Settings", `about_title="О приложении"`/"About", и соответствующие `*_placeholder` (например, "В разработке" / "Coming soon") (живут в `:core:ui` res, чтобы быть доступными всем feature-модулям без дублирования)
- [x] **сначала тест:** `PlaceholderScreenTest` (Compose UI test через Robolectric `createComposeRule`) — `setContent { PlaceholderScreen(...) }`, проверка `onNodeWithText("В разработке").assertIsDisplayed()` (по дефолту Robolectric использует en-локаль — проверяем "Coming soon")
- [x] **сначала тест:** `AppEmptyStateScreenshotTest` (Roborazzi) — снимок light + dark
- [x] **сначала тест:** `TishinaNavHostTest` — `setContent { TishinaApp() }`, проверка что стартовый destination — Measure, клик по `NavigationBar` пункту "История" приводит к навигации на `History` (через `NavDestination.hasRoute(TishinaDestination.History::class)`)
- [x] **сначала тест:** `AdaptiveNavigationTest` — `WindowSizeClass.calculateFromSize(DpSize(360.dp, 640.dp))` показывает `NavigationBar`; `DpSize(720.dp, 1024.dp)` и `DpSize(960.dp, 1024.dp)` показывают `NavigationRail` (используем DpSize вместо `@Config(qualifiers)` — Robolectric `Activity.window` не подключён в чистом `createComposeRule`)
- [x] реализовать composables и навигационный граф
- [x] run `./gradlew testDebugUnitTest verifyRoborazziDebug` — must pass before next task

> ⚠️ Task 5: добавлены ресурсы локализации в `:core:ui` (`values/strings.xml` и `values-ru/strings.xml`) — feature-модули включают `:core:ui` транзитивно через `AndroidFeatureConventionPlugin`, поэтому строки доступны всем экранам без дублирования. Также подключён плагин `kotlin-serialization` и зависимость `kotlinx-serialization-json` в `:app` — это требование type-safe routes navigation-compose 2.9. Roborazzi-baseline для `AppEmptyState` записан (light + dark) и закоммичен в `core/ui/src/test/snapshots/`. Тестовые WindowSizeClass подаются через `WindowSizeClass.calculateFromSize(DpSize)` напрямую, минуя `calculateWindowSizeClass(activity)` — Robolectric `Activity.window` без `createAndroidComposeRule` не настраивается.

### Task 6: Testing infrastructure :core:testing

- [x] в `:core:testing/build.gradle.kts` подключить как `api`-зависимости: junit-jupiter-api/engine, mockk, turbine, robolectric, roborazzi, compose-ui-test-junit4 — чтобы тестовые модули фичей подключали один `testImplementation(projects.core.testing)` и получали полный набор
- [x] создать `rules/RoborazziTestRule.kt` — обёртка над `RoborazziRule` с дефолтной директорией `module/build/outputs/roborazzi/`, дефолтным `RoborazziOptions.CompareOptions(changeThreshold = 0.01)`, удобным шорткатом `captureRoboImage(name: String)` для composables (реализовано как `captureSnapshot(name)` extension + константы `SnapshotChangeThreshold` / `SnapshotDirectory`; имя отличается от `captureRoboImage`, чтобы не конфликтовать с одноимённой extension-функцией Roborazzi)
- [x] создать `rules/MainDispatcherRule.kt` — стандартная обёртка над `TestDispatcher` для замены `Dispatchers.Main` в JUnit 5 (`@BeforeEach setMain`, `@AfterEach resetMain`)
- [x] создать `composables/PreviewSheet.kt` — helper `@Composable fun PreviewSheet(name: String, content: @Composable () -> Unit)` оборачивает контент в `TishinaTheme` + контрастный фон + label, чтобы единообразно делать screenshot-фикстуры
- [x] создать `fakes/` — пустая директория с README "fake implementations will be added in Phase 2+"
- [x] добавить тест-маркер: `infrastructureSmoke` — пустой JUnit 5 тест `class InfrastructureSmokeTest { @Test fun moduleCompiles() = Unit }`, чтобы CI прогонял `:core:testing:testDebugUnitTest` и validating импорты
- [x] run `./gradlew :core:testing:testDebugUnitTest` — must pass before next task

> ⚠️ Task 6: в `:core:testing` подключены дополнительные плагины `tishina.android.compose` (PreviewSheet — composable) и `tishina.jvm.testing` (включает `useJUnitPlatform()` для InfrastructureSmokeTest); добавлены `api(projects.core.designsystem)` (для `TishinaTheme` внутри PreviewSheet), `api(libs.junit4)` и `api(libs.junit.vintage.engine)` (чтобы фичевые модули, подключающие `testImplementation(projects.core.testing)`, могли писать JUnit 4-стилевые тесты с `@RunWith(RobolectricTestRunner)` без дублирования зависимостей). `RoborazziOptions.CompareOptions(changeThreshold)` принимает `Float`, не `Double`; обёрнут `@OptIn(ExperimentalRoborazziApi::class)`.

### Task 7: Static analysis — Detekt, Ktlint, Android Lint + Code coverage Kover

- [x] создать `config/detekt/detekt.yml` (на основе `detekt --generateConfig`, с правилами: complexity threshold 20, нет хардкод-strings — заявляем как `// TODO: enable in P1`, default style, потому что custom правило для строк сложное и оставлено на P1)
- [x] подключить Detekt в convention-plugins: `detekt { config.setFrom(rootProject.file("config/detekt/detekt.yml")); buildUponDefaultConfig = true; toolVersion = libs.versions.detekt.get(); allRules = false }`; добавить task `detektAll` через `Detekt`-task с `setSource(files(...))` — рекурсивно обходит все Kotlin-файлы кроме build-генерированных
- [x] подключить Ktlint через Spotless: `spotless { kotlin { ktlint(libs.versions.ktlint.get()).editorConfigOverride(mapOf("android" to "true", "max_line_length" to "140")); target("**/*.kt"); targetExclude("**/build/**", "**/generated/**") } }`
- [x] подключить Android Lint в `AndroidApplicationConventionPlugin` и `AndroidLibraryConventionPlugin`: `lint { abortOnError = true; warningsAsErrors = false; baseline = file("lint-baseline.xml") }`
- [x] сгенерировать lint baseline для пустых модулей: `./gradlew lintDebug --baseline` (после disable broken Compose K2-detectors lint прошёл чисто — baseline-файлы не понадобились, как и предполагалось планом)
- [x] подключить Kover в convention plugins (через новый `QualityConventionPlugin` со skip для `:core:testing`)
- [x] в корневом `build.gradle.kts` собрать агрегирующий kover-report: `dependencies { kover(projects.app); kover(projects.core.designsystem); ... }`, исключения `kover.reports.filters { excludes { classes("*.BuildConfig", "*.databinding.*", "*Module", "*MainActivity*", "*_HiltModules*", "*Application*") } }`
- [x] **тест:** запустить `./gradlew detektAll spotlessCheck lintDebug` — должно пройти зелёным (изначально кодовая база чиста)
- [x] **тест:** запустить `./gradlew koverHtmlReportDebug koverXmlReportDebug` — HTML- и XML-отчёты создаются в `build/reports/kover/`
- [x] run all static checks + kover — must pass before next task

> ⚠️ Task 7: Compose runtime + lifecycle lint detectors crash with K1/K2-API IncompatibleClassChangeError on AGP 8.7.3 + Kotlin 2.0.21 (https://issuetracker.google.com/issues/336842138). Disabled 13 broken detectors (FlowOperatorInvokedInComposition, RememberInComposition, NullSafeMutableLiveData, ...) + `ignoreTestSources = true` в lint-конфиге; будем пересматривать при переходе на AGP 8.8+. Также Spotless применён на root `build.gradle.kts`, отключены release unit-tests через `KotlinAndroid.configureKotlinAndroid` (Roborazzi-snapshot'ы записаны только под debug; release-вариант не нужен в Phase 1). Пара минорных авто-форматирований Spotless'ом: коллапс multi-line enum constructor в `TishinaDestinations.kt`, сортировка импортов в трёх файлах, реструктура `MainDispatcherRule.kt`. dB-thresholds (40/60/75/85/100 f) в `levelToSplColor` вынесены в private const'ы для устранения MagicNumber.

### Task 8: GitHub Actions CI — static + unit

- [ ] создать `.github/workflows/ci.yml`: триггеры `push` на `main`/`develop`, `pull_request` на `main`/`develop`
- [ ] job `static-checks`: `actions/checkout@v4`, `actions/setup-java@v4` (zulu 17), `gradle/actions/setup-gradle@v3` (кэш Gradle home + local build cache), команда `./gradlew detektAll spotlessCheck lintDebug --no-daemon`
- [ ] job `unit-tests` (`needs: static-checks`): прогон `./gradlew testDebugUnitTest verifyRoborazziDebug koverXmlReportDebug --no-daemon`; upload артефактов: `roborazzi-failure-images` (`module/build/outputs/roborazzi/`), `kover-xml-report` (`build/reports/kover/reportDebug.xml`), `unit-test-reports` (`*/build/reports/tests/`)
- [ ] job `build` (`needs: static-checks`): `./gradlew :app:assembleDebug :app:bundleDebug --no-daemon`; upload `debug-apk` + `debug-aab`
- [ ] (опционально, отметить как `if: ${{ secrets.CODECOV_TOKEN != '' }}`) шаг `codecov/codecov-action@v4` в `unit-tests` для загрузки покрытия — token подгружается из repo secrets
- [ ] создать `.github/dependabot.yml` (npm/gradle/github-actions ecosystems, weekly schedule) — низкий приоритет, добавить если время позволит
- [ ] создать `.github/workflows/README.md` с описанием workflow и ожидаемых artifacts
- [ ] **тест:** запушить feature-ветку → workflow выполняется зелёным; PR в `main` запускает все три job'а параллельно (static-checks → unit-tests + build); если хоть один step падает — статус PR красный
- [ ] **тест локально:** `act -j static-checks` (если установлен `nektos/act`) — опционально, для офлайн-валидации
- [ ] run CI on a test branch — must be green before next task

### Task 9: Verify acceptance criteria

- [ ] verify all requirements from Overview are implemented: `:app` собирается и запускается; есть 4 placeholder-экрана; навигация работает; темы light/dark/dynamic применяются; ru/en локализация в `app_name`
- [ ] verify edge cases are handled: `levelToSplColor` обрабатывает экстремальные значения; навигация выживает rotation (Robolectric `@Config(qualifiers="land")` для одного nav-теста)
- [ ] run full test suite (unit tests): `./gradlew testDebugUnitTest verifyRoborazziDebug` — 100% зелёных
- [ ] run linter (`./gradlew detektAll spotlessCheck lintDebug`) — все warnings/errors устранены или явно подавлены в baseline
- [ ] verify test coverage report генерируется: `./gradlew koverHtmlReportDebug` (пороги не enforced в Phase 1, только проверяем что отчёт собирается)
- [ ] verify APK size ≤ 6 МБ (NFR-4): `Get-ChildItem app/build/outputs/apk/debug/*.apk | Select-Object Length` — на этом этапе ожидаем 4–5 МБ
- [ ] verify все 11 модулей перечислены в `settings.gradle.kts` и собираются: `./gradlew projects` показывает корректное дерево
- [ ] зафиксировать baseline Roborazzi screenshots (`./gradlew recordRoborazziDebug`) и закоммитить PNG в `*/src/test/snapshots/`
- [ ] обновить `README.md`: статус Phase 1 → "Foundation complete", добавить badges (CI status, Kover, license)
- [ ] run `./gradlew clean build testDebugUnitTest verifyRoborazziDebug detektAll spotlessCheck lintDebug` — финальный smoke-прогон, всё зелёное

## Technical Details

### Структура каталогов после Phase 1

```
tishina-android/
├── .editorconfig
├── .gitattributes
├── .gitignore
├── .github/
│   ├── dependabot.yml
│   └── workflows/
│       ├── README.md
│       └── ci.yml
├── LICENSE
├── README.md
├── build.gradle.kts                       # root: alias-declarations
├── settings.gradle.kts                    # includes 11 modules
├── gradle.properties
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/
├── gradlew  /  gradlew.bat
├── config/detekt/detekt.yml
├── build-logic/
│   ├── settings.gradle.kts
│   └── convention/
│       ├── build.gradle.kts
│       └── src/main/kotlin/
│           ├── AndroidApplicationConventionPlugin.kt
│           ├── AndroidLibraryConventionPlugin.kt
│           ├── AndroidFeatureConventionPlugin.kt
│           ├── AndroidComposeConventionPlugin.kt
│           ├── AndroidHiltConventionPlugin.kt
│           ├── JvmTestingConventionPlugin.kt
│           └── KotlinLibraryConventionPlugin.kt
├── docs/
│   ├── plans/
│   │   └── 2026-05-19-tishina-foundation.md
│   └── specs/
│       └── tishina-spec.md
├── app/
│   ├── build.gradle.kts
│   ├── lint-baseline.xml
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── kotlin/ru/dmdp/tishina/
│       │   │   ├── TishinaApplication.kt
│       │   │   ├── MainActivity.kt
│       │   │   ├── ui/TishinaApp.kt
│       │   │   └── navigation/
│       │   │       ├── TishinaDestinations.kt
│       │   │       └── TishinaNavHost.kt
│       │   └── res/
│       │       ├── mipmap-anydpi-v26/
│       │       ├── values/strings.xml
│       │       ├── values/themes.xml
│       │       └── values-ru/strings.xml
│       └── test/
│           ├── kotlin/ru/dmdp/tishina/
│           │   ├── TishinaApplicationTest.kt
│           │   ├── MainActivityTest.kt
│           │   ├── LocalizationTest.kt
│           │   ├── TishinaNavHostTest.kt
│           │   └── AdaptiveNavigationTest.kt
│           └── snapshots/...
├── core/
│   ├── designsystem/{build.gradle.kts, src/main/kotlin/.../theme/{Color, SplLevelColors, Type, Shape, Theme}.kt, src/test/...}
│   ├── ui/{Placeholder, AppTopBar, AppEmptyState}.kt
│   ├── domain/                            # pure Kotlin — пусто
│   ├── data/                              # пусто
│   ├── audio/                             # пусто
│   └── testing/{rules/{RoborazziTestRule, MainDispatcherRule}.kt, composables/PreviewSheet.kt, fakes/README.md, InfrastructureSmokeTest.kt}
└── feature/
    ├── measure/{MeasureScreen.kt}
    ├── history/{HistoryScreen.kt}
    ├── settings/{SettingsScreen.kt}
    └── about/{AboutScreen.kt}
```

### Версии (фиксируются в `libs.versions.toml`)

| Группа | Артефакт | Версия |
|---|---|---|
| kotlin | `org.jetbrains.kotlin.android` plugin | 2.0.21 (стабильная K2) |
| android-gradle-plugin | `com.android.application` | 8.5.0+ |
| compose-bom | `androidx.compose:compose-bom` | 2026.05.00 |
| material3 | `androidx.compose.material3:material3` | через BOM → 1.5.x |
| activity-compose | `androidx.activity:activity-compose` | 1.10.x |
| lifecycle | `androidx.lifecycle:*` | 2.9.x |
| navigation-compose | `androidx.navigation:navigation-compose` | 2.9.x (type-safe routes) |
| windowsizeclass | `androidx.compose.material3:material3-window-size-class` | через BOM |
| hilt | `com.google.dagger:hilt-android` | 2.52 |
| hilt-navigation-compose | `androidx.hilt:hilt-navigation-compose` | 1.2.x |
| junit5 | `org.junit.jupiter:junit-jupiter-{api,engine}` | 5.11.x |
| mockk | `io.mockk:mockk` | 1.13.x |
| turbine | `app.cash.turbine:turbine` | 1.2.x |
| robolectric | `org.robolectric:robolectric` | 4.13+ |
| roborazzi | `io.github.takahirom.roborazzi:roborazzi` + `roborazzi-compose` | 1.30.x |
| detekt | `io.gitlab.arturbosch.detekt` plugin | 1.23.x |
| spotless | `com.diffplug.spotless` plugin | 6.25.x |
| ktlint | (через Spotless) | 1.4.x |
| kover | `org.jetbrains.kotlinx.kover` plugin | 0.9.x |

### Параметры компиляции

- JDK toolchain: 17 (zulu / temurin).
- Kotlin compiler args: `-Xjvm-default=all`, `-opt-in=kotlin.RequiresOptIn`, `-opt-in=androidx.compose.material3.ExperimentalMaterial3Api` (минимально).
- AGP: `coreLibraryDesugaring` НЕ нужен для minSdk 26.
- `composeOptions.kotlinCompilerExtensionVersion` — определяется автоматически через Compose BOM + Kotlin 2.0 plugin (`org.jetbrains.kotlin.plugin.compose`).
- ProGuard/R8 — настраивается для release-build, для Phase 1 — debug only (`isMinifyEnabled = false`).

### Convention plugins — общий контракт

- `AndroidLibraryConventionPlugin` → `android { compileSdk = 35; defaultConfig.minSdk = 26; compileOptions/kotlinOptions JDK 17 }`.
- `AndroidComposeConventionPlugin` → `buildFeatures.compose = true; implementation(composeBom + foundation + material3 + ui-tooling-preview)`.
- `AndroidHiltConventionPlugin` → KSP + hilt-android + hilt-compiler.
- `AndroidFeatureConventionPlugin` → объединяет Library + Compose + Hilt + `implementation(projects.core.designsystem); implementation(projects.core.ui); implementation(projects.core.domain); testImplementation(projects.core.testing)`.
- `KotlinLibraryConventionPlugin` → чистый Kotlin/JVM 17, без android-deps.
- `JvmTestingConventionPlugin` → JUnit 5 platform, MockK, Turbine — подключается через `testImplementation(projects.core.testing)`.

### Тестовая стратегия в Phase 1 — сводно

| Слой | Что тестируем | Чем |
|---|---|---|
| `:core:designsystem` | `levelToSplColor` (param), `TishinaTheme` light/dark/dynamic | JUnit 5 + Roborazzi (Robolectric) |
| `:core:ui` | `PlaceholderScreen`, `AppEmptyState`, `AppTopBar` визуально | Roborazzi snapshot |
| `:app` | Application/Activity smoke, Navigation routes, Adaptive layout | Robolectric + Compose UI Test |
| `:feature:*` | Заглушки рендерятся | Compose UI Test через Robolectric |
| `:core:domain/data/audio` | Ничего (пусто) | — |
| `:core:testing` | Один smoke-тест, что модуль компилируется | JUnit 5 |
| `build-logic` | `ProjectStructureTest`, `ModuleDependencyTest` | JUnit 5 |

## Post-Completion

*Items requiring manual intervention or external systems — no checkboxes, informational only.*

**Manual verification** (после завершения Phase 1):

- Установить debug APK на физическое Android-устройство (API 26 минимум, желательно API 33+ с dynamic colors); убедиться, что приложение запускается, темы light/dark/dynamic переключаются с системой, навигация работает между 4 экранами; убедиться, что edge-to-edge корректно отображается (статус-бар прозрачный, контент уезжает под него).
- Проверить, что smartphone-Settings → Apps → Tishina → Permissions показывает только Microphone (даже без запроса на этом этапе — оно объявлено в манифесте).
- Прогнать APK через `bundletool` для AAB локально, чтобы оценить размер итогового бинарника на разных density-конфигурациях.

**External system updates** (отложено в Phase Release):

- Создать adaptive launcher icon (foreground SVG/vector с волной, background solid `#0E2433`) + 512×512 PNG для каталога RuStore/Samsung — Phase Release.
- Резервирование пакета `ru.dmdp.tishina` в Google Play Console и RuStore Console — после первой успешной debug-сборки, не блокирует Phase 1.
- Создание GitHub Pages-сайта `https://<user>.github.io/tishina-android/privacy/` с черновиком Privacy Policy — Phase Release.
- Подключение Codecov (если решим использовать) — после первого PR с покрытием, не блокирующее.
- Опубликовать репозиторий на GitHub (push origin) — пользовательское действие; план не диктует момент, рекомендуется после Task 1 для возможности проверить CI.

**Что переходит в Phase 2 (Audio Engine + Measure):**

- `:core:audio` — `AudioRecord`-обёртка с `AudioSource.UNPROCESSED`/fallback, ring-buffer, DSP-фильтры (DC-block + A-weighting IIR-bi-quad по IEC 61672-1), `RmsCalculator`, `SplCalculator`, `Flow<SoundSample>` API.
- `:core:domain` — `Measurement`/`SoundSample`/`Settings` модели, репозиторий-интерфейсы (`AudioRepository`, `MeasurementRepository`, `SettingsRepository`), use-cases `StartMeasurementUseCase`, `StopMeasurementUseCase` (без сохранения — сохранение в Phase 3).
- `:feature:measure` — `MeasureViewModel` (MVI lite — `UiState`/`UiEvent`/`UiEffect`), реальная `MeasureScreen` с readout, gauge, графиком, статистикой, FAB Start/Pause, rationale-диалог для `RECORD_AUDIO`.
- Расширение CI: артефакт `RmsCalculator`-точности (sin-wave fixtures), Spectral test `AWeightingFilter` против опорной кривой IEC 61672-1 (точки 31.5/125/1000/8000/16000 Гц, допуск ±0.3 дБ).
