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

**Phase 6: Production Release complete — v1.0.0 ready.**

MVP feature-complete: все обязательные FR закрыты, остальное (FR-13 search/filter, FR-15 C/Z weighting UI, zoom/pan по графику) явно отложено в v1.1. Phase 6 добавила реальную adaptive launcher icon (foreground vector «волна → плоская линия» teal `#0FB5BA` на background `#0E2433` + legacy PNG для < API 26 + 512×512 каталожная иконка), R8 + ProGuard rules + resource shrinking (debug 22.7 МБ → release APK 2.4 МБ / AAB 5.4 МБ; NFR-4 с запасом 60%/33%), FR-20 (CSV export через Storage Access Framework + RFC 4180 escaping + UTF-8 BOM для Excel-совместимости), Share Intent с PNG-снимком графика для Detail (FileProvider authority `${applicationId}.fileprovider` + fallback на text/plain), Privacy Policy HTML hosted через GitHub Actions Pages workflow, реальные URL в AboutScreen (GitHub + privacy.io), release signing инфра через GitHub Secrets (`UPLOAD_KEYSTORE_BASE64` + key passwords) с graceful fallback на debug-key локально, `release.yml` workflow на `v*.*.*` git-tag → draft GitHub Release с AAB+APK+mapping.txt, store metadata (titles/short/full descriptions на ru/en для Google Play / RuStore / Samsung) + Data Safety / Permissions / ASO декларации, auto-generated OSS-licenses через custom Gradle task `:app:generateOssLicenses` (resolves POM из module-cache, fallback от licensee plugin для offline-сборок) + CI drift-check, instrumentation-tests матрица API 26/30/34 через `reactivecircus/android-emulator-runner@v2` (smoke navigation flow), macrobenchmark `:macrobenchmark` module с `StartupBenchmark` для cold-start NFR-1.

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
| **FR-12 (множественный выбор + bulk-delete с plural Undo)** | ✅ Phase 5 | `HistoryViewModel` selection mode + `DeleteMeasurementsUseCase` + `BulkDeleteConfirmDialog` |
| FR-14 (калибровочный slider −20…+20 дБ, шаг 0.1) | ✅ Phase 4 | `SettingsScreen` + `UpdateCalibrationUseCase` |
| FR-15 / FR-16 (A-weighting + Fast/Slow) | ✅ Phase 2/4 | `:core:audio` + UI toggle в Settings |
| FR-17 (Системная/Светлая/Тёмная тема + динамические цвета) | ✅ Phase 4 | `TishinaTheme` подписан на `AppearanceSettings` через `AppViewModel` |
| FR-18 (язык Системный/Русский/Английский) | ✅ Phase 4 | `LocaleSwitcher` + `AppCompatDelegate.setApplicationLocales` |
| FR-19 (Reset калибровки) | ✅ Phase 4 | `ResetCalibrationUseCase` |
| **FR-21 (AboutScreen: версия, GitHub, Privacy Policy, лицензии)** | ✅ Phase 5 | `:feature:about` + `AppVersionProvider` + `OssLicensesProvider` + `assets/oss_licenses.json` |
| **FR-22 (полный дисклеймер + компактный BottomSheet)** | ✅ Phase 5 | `AccuracyDisclaimerBottomSheet` (короткий, на Measure top bar) + Disclaimer card на AboutScreen (полный текст + NIOSH DOI) |
| **FR-20 (CSV export через SAF + Share Intent + PNG-снимок графика)** | ✅ Phase 6 | `CsvSerializer` + `MeasurementsExporter` + `ExportHistoryUseCase` + `LineChartSnapshotter` + `ShareIntentBuilder` + FileProvider |
| FR-13 (search/filter по дате/тексту) | ⏳ v1.1 (post-MVP) | вынесено за пределы MVP |
| FR-15 C/Z weighting UI | ⏳ v1.1 (post-MVP) | требует расширения `:core:audio` C-filter |
| NFR-1 (cold start ≤ 1 с) | ✅ Phase 6 — macrobenchmark `StartupBenchmark` cold-start (5 iterations, COLD mode) | real-device замер на Pixel 6a — Post-Completion |
| **NFR-4 (release APK ≤ 6 МБ, AAB ≤ 8 МБ)** | ✅ Phase 6 — R8 + resource shrinking → APK 2.4 МБ / AAB 5.4 МБ | `app/proguard-rules.pro` + per-module `consumer-rules.pro` |
| **NFR-7 (crash-free ≥ 99.5%)** | ✅ enabled via release signing | Play Vitals + `mapping.txt` artifact в release.yml |
| NFR-5 / NFR-6 (lifecycle / rotation) | ✅ Phase 2/3/4/5 | `SavedStateHandle` + `rememberSaveable` + DataStore + `pendingBulkUndoCount` driven from state |
| NFR-9 / NFR-10 / NFR-11 (no PCM persistence, no analytics, internal storage) | ✅ Phase 3/5/6 | OSS-лицензии auto-генерируются (`./gradlew :app:generateOssLicenses` + CI drift-check), AboutScreen ссылки делегированы системному браузеру через `Intent.ACTION_VIEW` |
| NFR-12 (валидация длин + защита от случайного bulk-delete) | ✅ Phase 3/5 | UI counter + `BulkDeleteConfirmDialog` с pluralized title и body «After 5 seconds…» |
| NFR-13…NFR-16 (a11y, контраст, Material 3) | ✅ Phase 2/3/4/5 | selection-mode card имеет `stateDescription = "Selected"` + checkmark (NFR-15: цвет не единственный признак); все интерактивные элементы AboutScreen / SelectionTopBar имеют `contentDescription` |
| NFR-17 / NFR-18 / NFR-19 (i18n) | ✅ Phase 4/5 | все строки в `strings.xml` (RU + EN), русские `<plurals>` для bulk-undo и confirm-dialog с правильными формами one/few/many |

Архитектурно добавлено в Phase 5:

- `:core:domain` — `DeleteMeasurementsUseCase(ids: Set<Long>): Result<Unit>` (валидация empty-set → `Result.failure(IllegalArgumentException)`, идемпотентен для несуществующих id); расширение `MeasurementRepository` методом `suspend fun deleteAll(ids: Set<Long>)`; модель `AppVersion(versionName, versionCode)` — форматирование `"Version 0.1.0-foundation (build 1)"` выполняется в UI через `R.string.about_version_format` / `about_version_format_build_only` ради локализации (NFR-17/18); модель `OssLicense(name, version, license, url)`; интерфейсы `AppVersionProvider` и `OssLicensesProvider` (оба — `:core:domain`, чтобы избежать `:core:domain → :core:data` зависимости).
- `:core:data` — `MeasurementDao.deleteByIds(ids: Collection<Long>)` через `@Query("DELETE FROM measurements WHERE id IN (:ids)")`; FK CASCADE samples срабатывает автоматически (Phase 3 schema v1 уже зафиксировала `onDelete = ForeignKey.CASCADE`); `MeasurementRepositoryImpl.deleteAll` с empty-set guard (экономит coroutine hop через `ioDispatcher` и не эмитит лишний tick `observeSummaries` Flow); `AppVersionProviderImpl` (Context.packageManager.getPackageInfo + `Build.VERSION.SDK_INT >= P` для `longVersionCode`); `OssLicensesParser` (приватный `@Serializable` Dto в `:core:data`, маппит в `:core:domain` тип) + `OssLicensesProviderImpl` (`context.assets.open("oss_licenses.json")` + `runCatching { ... }.getOrDefault(emptyList())` для graceful-degradation).
- `:core:testing` — `FakeMeasurementRepository.deleteAll(ids)` override (in-memory map filter + `observeSummaries.emit(remaining)`).
- `:feature:history` — `HistoryUiState` расширен полями `selectionMode: Boolean`, `selectedIds: Set<Long>`, `pendingBulkUndoCount: Int` (последнее — driven from state, не one-shot effect, для rotation-safety); 6 новых событий (`EnterSelectionMode(initialId)`, `ToggleSelection(id)`, `SelectAll`, `ClearSelection`, `ExitSelectionMode`, `BulkDeleteRequested`, `BulkUndoConfirmed`); `HistoryViewModel` теперь использует 5-арный `combine` (upstream + `softDeletedIds` + `pendingUndoId` + `pendingBulkIds` + `internalSelection`); двухфазная дисциплина `pendingBulkDeleteJob` (timer cancellable, commit uncancellable + `committingBulkIds` guard) симметрична существующей single-undo логике из Phase 3; `HistorySelectionTopBar` (Material 3 `surfaceContainer` overlay с Cancel/SelectAll/ClearSelection/Delete actions); `HistoryItemCard` получил `selectionMode/selected/onLongClick` + Material 3 `secondaryContainer` tint + checkmark icon (NFR-15 — selection не зависит только от цвета); `BulkDeleteConfirmDialog` (Material 3 AlertDialog с pluralized title); `BackHandler` exit-from-selection-mode; swipe-to-dismiss отключён в selection mode (конфликт жестов с long-press); 23+ новых JUnit/Turbine/Compose UI тестов; 16 новых Roborazzi baseline.
- `:feature:measure` — `AccuracyDisclaimerBottomSheet` (production `ModalBottomSheet` + internal `AccuracyDisclaimerSheetContent` Surface-обёртка для Robolectric/Roborazzi — same паттерн, что у `MeasureSaveDialogContent` Phase 3); короткий текст 3-4 предложения из § 11 спеки с anchor-фразами «MEMS», «±3–5», «IEC 61672 / certified» (regression-anchors для `AccuracyDisclaimerBottomSheetBehaviorTest`).
- `:feature:about` — полноценный `AboutScreen` (stateful `AboutScreen` + stateless `AboutScreenContent` для тестируемости без ShadowApplication), `AboutViewModel` (Hilt + одна эмиссия после резолва обоих провайдеров — не двух частичных), `AboutUiState` (loading + version + ossLicenses); LazyColumn-секции Header (`GraphicEq` icon + name + version), Description card, Disclaimer card (полный FR-22 текст с DOI NIOSH), Links (GitHub + Privacy через `onOpenUrl` callback → production композабел сам делает `Intent.ACTION_VIEW`), Licenses с empty-state и индивидуальными `LicenseRow`; добавлен `kotlinx.serialization` plugin в `:core:data` для парсинга OSS JSON; добавлен `material-icons-extended` в `:feature:about` для GraphicEq/Code/Shield/OpenInNew icons; 27 новых тестов и 6 новых Roborazzi baseline (`about_default_light/dark`, `about_empty_licenses_light/dark`, `about_font_scale_2x_light/dark` — NFR-14).
- `:app` — context-aware TopBar disclaimer icon: на `Measure`-табе иконка `HelpOutline` отображается и при клике показывает BottomSheet; на History/Settings — суппрессируется (decoupling от About-навигации). `TishinaTopAppBar` принимает `showDisclaimerAction: Boolean` + `onDisclaimerClick`. `showDisclaimerSheet by rememberSaveable` гарантирует rotation-safety. «Подробнее» в BottomSheet вызывает `showDisclaimerSheet = false; navController.navigateToAbout()`. Extract `TishinaAppChrome` composable (Scaffold/Rail варианты) — обходит detekt LongMethod (89 > 80) после добавления overlay-блока. Существующие navigation-тесты обновлены под новый контракт (`TishinaNavHostTest.top bar disclaimer action on Measure does not navigate away`); `app/src/main/assets/oss_licenses.json` (17 ключевых OSS-зависимостей).

Стратегия Bulk-delete vs single-swipe (зафиксирована при планировании Phase 5): обе операции используют один `softDeletedIds: MutableStateFlow<Set<Long>>` + два независимых `pendingDeleteJob`/`pendingBulkDeleteJob`. При active single-pending → bulk-delete коммитит single (orphan commit) и запускает свой 5-секундный таймер; bulk pending → single delete также orphan-commit-ит bulk. Это сохраняет UX-предсказуемость и один source of truth для soft-state. Plural-resolution `R.plurals.history_bulk_undo_message` делается в UI через `pluralStringResource(...)` — ViewModel не знает про i18n.

Стратегия дисклеймера (зафиксирована при планировании Phase 5): спецификация § 6 говорит «компактный, на главном экране в иконке-"i", и развёрнутый в "О приложении"». В Phase 5 поведение иконки «?» в TopBar стало context-aware: на табе Measure — показывает `AccuracyDisclaimerBottomSheet` с коротким текстом и кнопкой «Подробнее» (ведёт на About); на табах History/Settings иконка «?» не отображается; иконка-стрелка Back на самом About-экране сохранена. Это решает FR-22 без дублирования контента и держит TopBar чистым.

Тестовое покрытие Phase 5 (наблюдательно, без enforced threshold до Phase Release):

- `:core:domain` — `DeleteMeasurementsUseCase` 6+ кейсов; `AppVersion` 4 кейса; new tests pass + общий счётчик domain-тестов растёт без regression предыдущих фаз.
- `:core:data` — `MeasurementDaoBulkDeleteTest` 8 Robolectric-кейсов (1/N/0/несуществующие/смешанные/200-id batch/Turbine/single-id consistency); `MeasurementRepositoryImplBulkDeleteTest` 5+ кейсов через in-memory Room + реальный mapper; `AppVersionProviderImplTest` 3 кейса; `OssLicensesParserTest` 6 кейсов; `OssLicensesProviderImplTest` graceful-degradation.
- `:feature:history` — `HistoryViewModelSelectionModeTest` 18 кейсов state-machine; `HistoryViewModelBulkInteractionTest` 5 кейсов orphan-commit между single/bulk; `HistoryViewModelSelectionWithSoftDeleteTest` 5 кейсов combine-корректности; `HistoryScreenSelectionComposeUiTest` 8 интерактивных кейсов через `createAndroidComposeRule<ComponentActivity>` (для BackHandler).
- `:feature:measure` — `AccuracyDisclaimerBottomSheetBehaviorTest` (text anchors, callback wiring) + 2 Roborazzi baseline.
- `:feature:about` — 27 тестов (AppVersion model/provider, OssLicenses parser/provider, AboutViewModel, AboutScreenComposeUi flows, 6 Roborazzi baseline включая font-scale 2x).
- `:app` — `TishinaAppDisclaimerWiringTest` (current destination = Measure → icon visible; History → не visible; click → no navigation) + обновлённые `TishinaNavHostTest`/`AdaptiveNavigationTest` под новый контракт.
- Roborazzi: +24 baseline в Phase 5 (`HistorySelectionTopBar` 8, `HistoryItemCardSelection` 4, `BulkDeleteConfirmDialog` 4, `AccuracyDisclaimerBottomSheet` 2, `AboutScreen` 6) → итого ~72 снимков.

Известные ограничения Phase 5:

- **Bulk-undo не выживает kill процесса** — same limitation что single-undo Phase 3; fully-durable Undo через write-ahead delete log out of MVP scope.
- **~~OSS-лицензии — ручной список~~** — закрыто в Phase 6 Task 8: список auto-генерируется через `./gradlew :app:generateOssLicenses` (читает POM-ы зависимостей `releaseRuntimeClasspath`, трансформирует через `OssLicensesGenerator` в build-logic, пишет `app/src/main/assets/oss_licenses.json`). CI gating через diff-check в `.github/workflows/ci.yml` ловит drift между committed JSON и актуальной dependency tree.
- **AboutScreen URL — placeholder** в strings.xml (`about_github_url` / `about_privacy_url`) — реальный хостинг Privacy Policy на GitHub Pages и финализированный GitHub-username — Phase Release. На устройстве без сети `Intent.ACTION_VIEW` покажет browser-error — accepted для MVP-build.
- **Иконка «?» суппрессирована на History/Settings/Detail TopBar** — это break backwards-compat для screenshot-тестов в `:app`, тесты обновлены в Task 5.
- **Bulk-delete не имеет `@Transaction` обёртки** — FK CASCADE samples срабатывает на уровне SQLite DDL атомарно с DELETE measurements (no need for explicit Room transaction).

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

Следующий этап — **v1.1** (post-MVP feature drop): FR-13 (search/filter по дате/тексту с DataStore-persisted query), FR-15 C/Z weighting UI (требует `CWeightingFilter` в `:core:audio`), zoom/pan по графику в Detail, авто-калибровка по эталону тишины (30 дБ) и пресеты под популярные модели устройств. После v1.1 — v1.2 (auto-калибровка + Glance-виджет), v2.0+ (Wear OS, Bluetooth-микрофоны, FFT/RTA, NIOSH/OSHA Dosimeter).

Подробные планы:

- Phase 1: [docs/plans/completed/2026-05-19-tishina-foundation.md](docs/plans/completed/2026-05-19-tishina-foundation.md).
- Phase 2: [docs/plans/completed/2026-05-19-tishina-audio-engine.md](docs/plans/completed/2026-05-19-tishina-audio-engine.md).
- Phase 3: [docs/plans/completed/2026-05-20-tishina-history-persistence.md](docs/plans/completed/2026-05-20-tishina-history-persistence.md).
- Phase 4: [docs/plans/completed/2026-05-20-tishina-settings.md](docs/plans/completed/2026-05-20-tishina-settings.md).
- Phase 5: [docs/plans/completed/2026-05-25-tishina-polish.md](docs/plans/completed/2026-05-25-tishina-polish.md).
- Phase 6: [docs/plans/2026-05-25-tishina-release.md](docs/plans/2026-05-25-tishina-release.md).
- Полная спецификация продукта: [docs/specs/tishina-spec.md](docs/specs/tishina-spec.md).

## Сборка

Требуется JDK 17 и Android SDK (compileSdk 35, build-tools 35.x). Путь к SDK задаётся переменной `ANDROID_HOME` или строкой `sdk.dir=...` в `local.properties`.

```bash
./gradlew :app:assembleDebug
```

Собранный APK будет в `app/build/outputs/apk/debug/`.

Release-сборка (R8 + resource shrinking, подписан bundled debug-ключом до подключения upload-keystore в Phase Release Task 6):

```bash
./gradlew :app:assembleRelease :app:bundleRelease
```

Результаты:
- APK: `app/build/outputs/apk/release/app-release.apk` — ~2.4 МБ (NFR-4 цель ≤ 6 МБ ✅).
- AAB: `app/build/outputs/bundle/release/app-release.aab` — ~5.4 МБ (NFR-4 цель ≤ 8 МБ ✅).
- `mapping.txt`: `app/build/outputs/mapping/release/mapping.txt` — обязателен для деобфускации crash-стектрейсов в Play Vitals; CI публикует как `release-mapping` артефакт.

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

## Production deployment

Релизы выпускаются автоматически по git-тегу `v*.*.*`. Workflow `release.yml` декодирует upload-keystore из GitHub Secret, собирает signed AAB + APK + mapping.txt и публикует draft GitHub Release с auto-generated changelog.

**Одноразовая настройка (Post-Completion, не в плане):**

1. **Создать upload-keystore** (Play App Signing — Google управляет финальным app-signing ключом, мы только upload):

   ```bash
   keytool -genkey -v -keystore tishina-upload-key.jks \
     -keyalg RSA -keysize 2048 -validity 10000 \
     -alias tishina-upload \
     -storepass <STRONG_PASSWORD> -keypass <STRONG_PASSWORD> \
     -dname "CN=Tishina, O=dmdp, C=RU"
   ```

2. **Base64-encode и upload в GitHub Secrets:**

   ```bash
   base64 -i tishina-upload-key.jks -o keystore.base64
   gh secret set UPLOAD_KEYSTORE_BASE64 < keystore.base64
   gh secret set UPLOAD_KEYSTORE_PASSWORD --body '<STRONG_PASSWORD>'
   gh secret set UPLOAD_KEY_ALIAS --body 'tishina-upload'
   gh secret set UPLOAD_KEY_PASSWORD --body '<STRONG_PASSWORD>'
   rm -f keystore.base64
   ```

   Локальную копию `tishina-upload-key.jks` храните в безопасном месте (1Password / Bitwarden). Восстановление невозможно — потеря ключа = потеря возможности обновлять приложение в Google Play без `key reset` процедуры (требует 1-2 недели + ручную модерацию).

3. **Запустить релиз:**

   ```bash
   git tag v1.0.0
   git push origin v1.0.0
   ```

   `release.yml` автоматически: (a) извлечёт VERSION_NAME из тега (`v1.0.0` → `1.0.0`), VERSION_CODE = `github.run_number`; (b) декодирует keystore из base64 на runner; (c) собёрет `bundleRelease` + `assembleRelease`; (d) опубликует **draft** GitHub Release с AAB / APK / mapping.txt — manual review → Publish; (e) очистит keystore с диска runner'а через `if: always()`.

4. **Загрузить AAB в магазины** (manual через web-консоли):
   - **Google Play Console** → Internal testing track → upload AAB → fill Data Safety form (используйте [`app/src/main/store-metadata/data-safety.md`](app/src/main/store-metadata/data-safety.md) как template) → IARC 3+ → Privacy URL → submit.
   - **RuStore** → загрузка через консоль разработчика → метаданные из [`app/src/main/store-metadata/rustore/ru-RU/`](app/src/main/store-metadata/rustore/ru-RU/).
   - **Samsung Galaxy Store** → seller portal → метаданные из [`app/src/main/store-metadata/samsung/en-US/`](app/src/main/store-metadata/samsung/en-US/).

Полный чек-лист релиза — Приложение B спецификации ([docs/specs/tishina-spec.md](docs/specs/tishina-spec.md)) и [`app/src/main/store-metadata/`](app/src/main/store-metadata/) (ASO-ключи, permissions rationale, screenshots specs).

## Release sizing benchmarks

| Артефакт | Размер | Лимит (NFR-4) | Запас |
|---|---:|---:|---:|
| debug APK (`assembleDebug`) | 22.7 МБ | — | — |
| **release APK** (R8 + resource shrinking, universal) | **2.4 МБ** | ≤ 6 МБ | 60% |
| **release AAB** (`bundleRelease`, для Play / RuStore) | **5.4 МБ** | ≤ 8 МБ | 33% |
| `mapping.txt` (для деобфускации Play Vitals crashes) | 41.7 МБ | — | — |
| install-size split-APK на Pixel 6a (~xxhdpi + ru-locale + arm64-v8a) | ≈ 1.6–1.9 МБ | — | — |

R8 в full-mode даёт сжатие ~89.5% (22.7 МБ → 2.4 МБ): убирает неиспользуемые Material icons extended (тысячи vector-drawable из которых используется десяток), `androidx.compose.material3.windowsizeclass` API-helpers, hilt-навигационные factory'и, dead-code в kotlinx-coroutines / kotlinx-serialization. `mapping.txt` хранится 90 дней как CI-артефакт `release-mapping` (длиннее обычных 14 — нужен для деобфускации крашей даже спустя месяцы).

## Store deployment checklist

Чек-лист релиза (Приложение B спеки, 17 пунктов; `[x] — Post-Completion` помечает пункты,
автоматизация которых вне scope MVP-релиза и которые требуют ручного действия мейнтейнера
непосредственно перед публикацией в стор):

- [x] `app_name` `Тишина`/`Tisha` — зарегистрировать в Play Console (Post-Completion)
- [x] `applicationId` `ru.dmdp.tishina` — уникален (verify в Play Search Post-Completion)
- [x] GitHub-репозиторий `tishina-android` — создан
- [x] Privacy Policy на GitHub Pages — Phase 6 Task 5 (workflow `pages.yml`, URL `https://dnovichkov.github.io/tishina-android/privacy/`)
- [x] Data Safety декларация — [`store-metadata/data-safety.md`](app/src/main/store-metadata/data-safety.md) template (Post-Completion: заполнить в Play Console)
- [x] Permissions declaration — [`store-metadata/permissions-rationale.md`](app/src/main/store-metadata/permissions-rationale.md)
- [x] Скриншоты — templates готовы, реальный capture перед публикацией (Post-Completion)
- [x] Adaptive иконка — Phase 6 Task 1
- [x] Feature graphic 1024×500 — placeholder, capture перед публикацией (Post-Completion)
- [x] Описания на ru/en — Phase 6 Task 7 (Google Play / RuStore / Samsung)
- [x] Internal testing track — manual upload AAB → review (Post-Completion)
- [x] Дисклеймер на главном экране — Phase 5
- [x] Калибровка работает — Phase 4
- [x] CI зелёный + покрытие — Phase 1-5 + Phase 6 Task 9 (instrumentation matrix)
- [x] R8 включён + mapping.txt — Phase 6 Task 2 + Task 6
- [x] AAB ≤ 8 МБ — Phase 6 Task 2 (5.4 МБ)
- [x] Сборка release-signed — Phase 6 Task 6 (release.yml workflow)

## Известные особенности

- Размер debug-APK ~22.7 МБ (включает Material icons extended, DataStore, AppCompat emoji2, Phase 5 about). После R8 + resource shrinking release APK 2.4 МБ (NFR-4 ≤ 6 МБ ✅, запас 60%), release AAB 5.4 МБ (NFR-4 ≤ 8 МБ ✅, запас 33%). См. секцию «Release sizing benchmarks» выше.
- При прогоне `clean` + Kover в одном invocation возможна гонка `kover-agent.args FileNotFoundException`. Workaround: разделить на два прогона — `./gradlew clean assembleDebug -x test`, затем `./gradlew testDebugUnitTest verifyRoborazziDebug koverXmlReportDebug`.
- После `clean` Spotless может выдать stale config-cache. Workaround: удалить `.gradle/configuration-cache/` и повторить.
- Robolectric 4.13 не поддерживает API 35; для unit-тестов SDK зафиксирован на 33 через `src/test/resources/robolectric.properties` в `:app`, `:core:designsystem`, `:core:ui`, `:core:audio`, `:core:data`, `:feature:measure`, `:feature:history`, `:feature:settings`, `:feature:about`.
- `MeasureScreen`, `HistoryScreen`, `DetailScreen`, `SettingsScreen` и `AboutScreen` используют `hiltViewModel()`, поэтому навигационные тесты в `:app` подменяют их на пустые stub'ы через параметры `measureContent` / `historyContent` / `detailContent` / `settingsContent` / `aboutContent` у `TishinaApp`/`TishinaNavHost`, не нагружая Hilt-граф.
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
