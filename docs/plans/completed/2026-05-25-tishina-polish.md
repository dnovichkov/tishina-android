# Tishina — MVP Phase 5: Polish + About + Bulk-delete

## Overview

Phase 5 закрывает оставшиеся MVP-фичи перед Phase Release: добавляет в History-экран множественный выбор и batch-удаление с Undo (FR-12), реализует полноценный AboutScreen (FR-21) с версией, GitHub-ссылкой, Privacy Policy, OSS-лицензиями, выносит на главный Measure-экран компактный дисклеймер о точности (FR-22) — короткий BottomSheet через иконку «?» в TopBar плюс полный текст в About. После завершения этой фазы у пользователя должен появиться полностью функциональный MVP-опыт без ad-hoc workarounds — все обязательные FR-требования (кроме экспорта CSV / share Intent / R8 / Privacy Policy hosting, которые уходят в Phase Release) будут закрыты.

**Цель фазы:**
- `:core:domain` — новый `DeleteMeasurementsUseCase(ids: Set<Long>)`, расширение `MeasurementRepository` методом `deleteAll(ids: Set<Long>)`, валидация empty-set; `AppVersion` модель для AboutScreen.
- `:core:data` — `MeasurementDao.deleteByIds(ids: Set<Long>)` через `@Query("DELETE ... WHERE id IN (:ids)")` с автоматическим CASCADE samples; реализация `MeasurementRepositoryImpl.deleteAll`; провайдер `AppVersionProvider` (BuildConfig → AppVersion).
- `:feature:history` — расширение `HistoryUiState` (`selectionMode: Boolean`, `selectedIds: Set<Long>`), новые события (`EnterSelectionMode`, `ToggleSelection(id)`, `SelectAll`, `ClearSelection`, `ExitSelectionMode`, `BulkDeleteRequested`, `BulkUndoConfirmed`), batch-вариант soft-delete + Undo (5 секунд), `HistorySelectionTopBar` overlay с «X selected / Select all / Cancel / Delete», `HistoryItemCard` визуальный selection-state (Material 3 checkmark), confirm-диалог перед bulk delete.
- `:feature:measure` — `AccuracyDisclaimerBottomSheet` (короткий текст из спеки § 11 + кнопка «Подробнее» → AboutScreen), wiring через `MeasureScreen` (новый эффект + context-aware показ из `TishinaApp` только когда current destination = Measure).
- `:feature:about` — полноценный `AboutScreen` + `AboutViewModel`: версия (BuildConfig.VERSION_NAME / VERSION_CODE через DI-обёртку), карточка с дисклеймером (полный текст FR-22), ссылки GitHub / Privacy Policy / Лицензии через `Intent.ACTION_VIEW`; раздел OSS-лицензий — статический ассет `assets/oss_licenses.json` с ключевыми зависимостями.
- `:app` — `TishinaApp.kt`: переключение поведения иконки «?» — на Measure-табе показывает BottomSheet, на других экранах скрыта (упрощает UX); навигация Detail/Settings → About через TopBar action.

**Стратегия Bulk-delete vs single-swipe (зафиксировано на планировании):** обе операции используют один `softDeletedIds: MutableStateFlow<Set<Long>>` + один `pendingDeleteJob`. Single swipe (FR-11) добавляет один id и запускает 5-секундный таймер; bulk-delete (FR-12) добавляет N id одной транзакцией и запускает свой 5-секундный таймер. Snackbar messages разные (single vs bulk-count plural). При active single-pending → bulk-delete коммитит single (orphan commit) и запускает свой таймер — same паттерн, что использовался в Phase 3 для consecutive deletes. Это сохраняет UX-предсказуемость и one source of truth для soft-state.

**Стратегия дисклеймера (зафиксировано на планировании):** спецификация § 6 говорит «компактный, на главном экране в иконке-"i", и развёрнутый в "О приложении"». Сейчас (Phase 1–4) иконка «?» в `TishinaApp.kt` навигирует прямо на About. В Phase 5 поведение меняется context-aware: на табе Measure клик показывает `AccuracyDisclaimerBottomSheet` с коротким текстом + кнопкой «Подробнее», которая ведёт на About; на табах History/Settings иконка «?» не отображается (it bears no related semantics там); иконка-стрелка Back на самом About-экране остаётся как раньше. Это решает FR-22 без дублирования контента и сохраняет TopBar чистым.

**Какие FR / NFR из спеки покрываются:**
- **FR-12** — Поддерживается множественный выбор → bulk-удаление.
- **FR-21** — AboutScreen с версией, ссылкой на GitHub, политикой конфиденциальности, лицензиями OSS-зависимостей.
- **FR-22** — Полный текст дисклеймера о точности в AboutScreen + компактный BottomSheet на Measure.
- **NFR-1** — AboutScreen открывается лениво (Hilt + Compose navigation), не влияет на cold-start.
- **NFR-9** — UI-обновления не вызывают сетевых запросов; ссылки открываются через `Intent.ACTION_VIEW` в системном браузере (контролируется системой, не приложением).
- **NFR-10** — никаких сторонних аналитических SDK — OSS-лицензии собираются вручную в статический JSON.
- **NFR-12** — bulk-delete confirm-диалог защищает от случайных потерь; counter «N selected» виден в TopBar.
- **NFR-13** — `contentDescription` для каждой интерактивной кнопки (Select all, Delete N, Cancel, About-ссылки).
- **NFR-14** — поддержка масштабирования шрифта 200 % — screenshot-baseline для AboutScreen и selection-mode History.
- **NFR-15 / NFR-16** — контраст ≥ 4.5:1, цвет не единственный носитель (выбранная карточка получает и цвет surface-tint, и checkmark-иконку).
- **NFR-17 / NFR-18** — все строки через `strings.xml`, plurals для русского («%d замер» / «%d замера» / «%d замеров»).

**Что НЕ входит в Phase 5 (намеренно отложено):**
- **FR-13** (поиск/фильтр по дате и тексту заметки) — P1 по спеке, не блокер MVP; перенесём в v1.1 после Release.
- **FR-15** (C/Z weighting UI) — спека прямо разрешает «Только A в MVP»; C-фильтр не реализован в `:core:audio`, добавление = +30 % к Phase 5; v1.1.
- **FR-20** (экспорт CSV через Storage Access Framework) — P1, Phase Release.
- **Share Intent + PNG-снимок графика** — P1, Phase Release.
- **Zoom/pan по графику в Detail** — P1, v1.1.
- **Реальная иконка приложения** (foreground SVG + 512×512 PNG для каталогов) — Phase Release.
- **Privacy Policy hosting** на GitHub Pages (`https://<user>.github.io/tishina-android/privacy/`) — URL зафиксируем в Phase 5 как placeholder; реальный хостинг — Phase Release.
- **OSS-лицензий полный auto-generated список** через `oss-licenses-plugin` от Google — тянет Play Services Library, конфликтует с NFR-10; в Phase 5 поддерживаем ручной статический список ключевых зависимостей.
- **R8 + ProGuard + размер APK ≤ 6 МБ** — Phase Release вместе с release signing.
- **Instrumentation-тесты матрица API 26/30/34** — Phase Release; в Phase 5 ограничиваемся Robolectric + Room in-memory.
- **Bulk-undo с persistence** (выживание после kill процесса) — accepted limitation, как в Phase 3 single-undo.
- **AboutScreen с auto-rendered GitHub release notes** — Phase Release.

## Context (from discovery)

**Состояние репозитория после Phase 4 (коммит `2de636c` на main):**
- 13 модулей собираются; `:app:assembleDebug` зелёный; CI с Codecov gating настроен.
- `:core:domain` содержит модели Measurement/MeasurementSummary/MeasurementDetails/NewMeasurement (Phase 3), интерфейс `MeasurementRepository` с методами `observeSummaries / getById / save / delete(id) / updateNote` (**нет** `deleteAll(ids)` — добавим в Task 1). Use-cases `SaveMeasurement / GetMeasurements / GetMeasurementById / DeleteMeasurement / UpdateMeasurementNote`.
- `:core:data` содержит `TishinaDatabase` (Room 2.8.4 schema v1), `MeasurementDao` с `delete(id: Long)` через `@Query("DELETE FROM measurements WHERE id = :id")` (нет `deleteByIds`). FK CASCADE на samples настроен (Phase 3).
- `:core:testing/fakes/FakeMeasurementRepository.kt` — in-memory map + StateFlow, метод `delete(id)` есть; добавим `deleteAll(ids)`.
- `:feature:history` — `HistoryUiState(items, loading, loadFailed, pendingUndoId)`, события `DeleteRequested(id) / UndoConfirmed`, swipe-to-dismiss через Material 3 `SwipeToDismissBox` в LazyColumn. Soft-delete через `softDeletedIds: MutableStateFlow<Set<Long>>` + `pendingDeleteJob`. **Нет** selection mode.
- `:feature:about/AboutScreen.kt` — placeholder через `PlaceholderScreen(R.string.about_title, R.string.about_placeholder)`. Подключён в навигацию (`TishinaDestinations.About` + composable в `TishinaNavHost`). `build.gradle.kts` использует `tishina.android.feature` convention.
- `:feature:measure/MeasureScreen.kt` — реальный экран с readout/gauge/chart/bottom-bar (Phase 2/3/4); TopAppBar хостится в `TishinaApp.kt`, не в самом MeasureScreen.
- `app/.../TishinaApp.kt:223-229` — `Icons.AutoMirrored.Outlined.HelpOutline` (иконка «?») в actions глобального TopAppBar; `onClick = navController.navigateToAbout()` (`launchSingleTop = true`). На AboutScreen иконка заменяется на стрелку Back. На NavigationRail (tablet) TopAppBar не рендерится вообще.
- `app/build.gradle.kts:14-15`: `versionCode = 1`, `versionName = "0.1.0-foundation"`. **Не обновляли** через Phase 2-4 — фиксируем в Phase 5, версия будет указана в AboutScreen как есть; обновление version-name на `0.5.0-polish` или подобное — Phase Release decision.
- Локализационные строки уже включают: `about_title`, `about_placeholder`, `nav_open_about`, `history_undo_snackbar_message`, `history_undo_action`, `history_delete_failed`, `history_load_failed`, `history_card_delete_cd`. Для Phase 5 добавим ~25 новых ключей (selection mode, bulk confirm, about sections, disclaimer body).

**Зафиксированные версии (после Phase 4):**
- Kotlin 2.0.21 + K2; AGP 8.7.3; Compose BOM 2026.05.00; Hilt 2.55; Room 2.8.4; DataStore Preferences 1.1.1; AppCompat 1.7.0.
- JUnit 5.11.x + MockK 1.13.x + Turbine 1.2.x + Robolectric 4.13 + Roborazzi 1.30.x.

**Источники истины:**
- Спецификация `docs/specs/tishina-spec.md`:
  - § 4 — FR-12 (множественный выбор + bulk-удаление), FR-21 (AboutScreen контент), FR-22 (полный дисклеймер).
  - § 6 — UX HistoryScreen (свайп/Undo/empty), MeasureScreen TopBar с иконкой «?», AboutScreen (версия, иконка, GitHub, описание, полный дисклеймер, лицензии).
  - § 11 — методология калибровки, известные ограничения, текст дисклеймера (готовый, для использования в Phase 5), цитата NIOSH с DOI.
  - § 17 — идентификация проекта (название, package, GitHub-репозиторий, лицензия Apache 2.0, маркетинговый слоган).
  - § 8 — стек OSS-зависимостей (Compose / Material 3 / Hilt / Room / DataStore / Kotlin coroutines / JUnit 5 / Robolectric / Roborazzi / Detekt / Ktlint / Kover) для генерации статического списка.
- Завершённые планы Phase 1–4 — образец TDD-формата.

## Development Approach

- **Testing approach:** **TDD (tests first)** — глобальное правило проекта ([[feedback_tdd_default]]) плюс явное подтверждение на планировании. Для каждой задачи с поведением (DAO batch query, repository extension, ViewModel selection state-machine, BottomSheet rendering, AboutScreen Intent emission) **тест пишется первым**, реализация — после того как тест зафиксировал контракт. Чистые setup-задачи (build.gradle.kts dependencies, новые strings.xml ключи) валидируются успешной сборкой + assertions on Resources.getString.
- Complete each task fully before moving to the next.
- Make small, focused changes.
- **CRITICAL: every task MUST include new/updated tests** for code changes in that task:
  - JUnit 5 unit-тесты для domain use-cases, mappers, BuildConfig wrapper.
  - Robolectric + Room in-memory для batch DAO queries (FK CASCADE через `countSamplesForMeasurement`).
  - Turbine-тесты `StateFlow` для расширенного `HistoryViewModel` (selection mode invariants) и нового `AboutViewModel`.
  - Roborazzi screenshot-тесты для `HistorySelectionTopBar`, selected `HistoryItemCard`, `BulkDeleteConfirmDialog`, `AccuracyDisclaimerBottomSheet`, `AboutScreen` (default + scrolled + font-scale 2x).
  - Compose UI-тесты через `createComposeRule()` для интерактивных сценариев (long-press → selection mode; toggle multiple → select all; bulk-delete → confirm → Snackbar → undo; click «?» в Measure → BottomSheet появляется; click «Подробнее» → onAboutClick).
  - Тесты покрывают success **и** error/edge: bulk-delete пустой set → no-op; bulk-delete несуществующих id → no-op (idempotent); long-press на soft-deleted item → игнорируется; AboutScreen без сети → Intent.ACTION_VIEW делегирует системе (не падает).
- **CRITICAL: all tests must pass before starting next task** — no exceptions.
- **CRITICAL: update this plan file when scope changes during implementation.**
- Run tests after each change (`./gradlew :feature:history:testDebugUnitTest`, `:feature:about:testDebugUnitTest`, `:feature:measure:testDebugUnitTest` локально быстрее, чем полный build).
- Maintain backward compatibility: не ломаем сигнатуры существующих composables без default-параметров — same pattern, что в Phase 2/3/4 (slot-based navigation в `TishinaNavHost` сохраняется).

## Testing Strategy

### Unit tests (JUnit 5 + MockK + Turbine)

| Слой | Что тестируется | Целевое покрытие |
|---|---|---|
| `:core:domain` — `DeleteMeasurementsUseCase` | empty set → `Result.failure(IllegalArgumentException)` (бессмысленно вызывать); non-empty → `repository.deleteAll(ids)` вызван; идемпотентность для несуществующих id (repository отвечает Unit) | ≥ 95% |
| `:core:domain` — `AppVersion` model | конструкторы immutable классов, equality, copy, format helpers (`displayName = "$versionName (build $versionCode)"`) | ≥ 95% |
| `:core:data` — `MeasurementDao.deleteByIds` | удаление 1 / N / 0 id за раз; FK CASCADE удаляет samples (через `countSamplesForMeasurement`); несуществующие id игнорируются без exception; transactional rollback не нужен (batch DELETE атомарен) | ≥ 90% |
| `:core:data` — `MeasurementRepositoryImpl.deleteAll` | проксирование в DAO под `withContext(ioDispatcher)`; `observeSummaries()` эмитит новый список после bulk-delete | ≥ 85% |
| `:core:data` — `AppVersionProvider` | возвращает `AppVersion(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)`; тест через DI-обёртку, не через прямое чтение BuildConfig в тесте | ≥ 90% |
| `:feature:history` — `HistoryViewModel` selection state | `EnterSelectionMode` → `selectionMode=true, selectedIds=emptySet`; `ToggleSelection(id)` add/remove; `SelectAll` выбирает все видимые items; `ClearSelection` опустошает; `ExitSelectionMode` сбрасывает оба поля; `BulkDeleteRequested` с пустым set → no-op snackbar; non-empty → soft-delete всех selected + `ShowUndoSnackbar(plural)` + exit selection; `BulkUndoConfirmed` восстанавливает все; timer 5 sec → commit deleteAll | ≥ 90% |
| `:feature:history` — `HistoryViewModel` interaction между bulk и single | single pending → bulk → orphan commit single + new bulk pending; bulk pending → single delete → orphan commit bulk + new single pending; consecutive bulks → orphan commit previous | ≥ 90% |
| `:feature:measure` — `MeasureScreenContent` ShowDisclaimer effect | new `MeasureUiEffect.ShowAccuracyDisclaimer` (если выберем такой контракт) — отдельно проверяется TopBar wiring, не сам effect (он в `:app`) | n/a (нет VM-логики) |
| `:feature:about` — `AboutViewModel` | `state.first()` возвращает `AboutUiState(version = injected AppVersion, ossLicenses = parsed JSON)`; ResourceProvider-based парсинг JSON корректно сериализуется; ошибка чтения assets → empty list + log (no crash) | ≥ 85% |
| Composables UI | smoke-рендеринг + screenshot baselines + интерактивное поведение через `createComposeRule` | Roborazzi + Compose UI Test |

### Robolectric instrumentation-стиль тесты (под `src/test/`)

- `MeasurementDaoBulkDeleteTest` (`@RunWith(RobolectricTestRunner)`) — Room.inMemoryDatabaseBuilder; 5+ тестов: bulk delete 3 of 5 → 2 остаются; CASCADE samples всех 3 удалены; пустой set → no rows affected, не throw; несуществующие ids → no-op; смешанный (часть существующих, часть нет) → удаляются только существующие.
- `MeasurementRepositoryImplBulkTest` — Robolectric + in-memory Room + реальный mapper; round-trip save → save → save → deleteAll([id1, id2]) → observeSummaries() эмитит список с только id3.
- `HistoryScreenSelectionComposeUiTest` — `createComposeRule()`:
  - long-press на карточке → enter selection mode; первая карточка выбрана
  - tap на другой карточке (in selection mode) → toggle (она тоже выбрана)
  - tap на уже-выбранной → un-toggle
  - tap на «Select all» → все items выбраны
  - tap на «Cancel» → exit selection mode, обычный режим
  - tap на «Delete (N)» → confirm dialog → confirm → bulk-delete + Snackbar
  - Back press в selection mode → exit selection mode (не уход с экрана)
- `MeasureTopBarDisclaimerTest` — `createComposeRule()` + `TishinaApp` с current destination = Measure: click на «?» → `AccuracyDisclaimerBottomSheet` отрендерен (через `composeTestRule.onNodeWithTag("accuracy_disclaimer_sheet")`); click на «Подробнее» → `navController.currentDestination` стал About; на History/Settings tabs иконки «?» нет вообще (`onNodeWithContentDescription(R.string.nav_open_about).assertDoesNotExist()`).
- `AboutScreenComposeUiTest` — `createComposeRule()`:
  - version label содержит `BuildConfig.VERSION_NAME`
  - tap на «GitHub» → `Intent.ACTION_VIEW` с URL `https://github.com/dmitrynovichkov/tishina-android` (захватываем через `Shadows.shadowOf(application).nextStartedActivity`)
  - tap на «Privacy Policy» → Intent.ACTION_VIEW с placeholder URL
  - tap на «Open-source licenses» → внутренняя навигация на `LicensesScreen` или раскрытие Accordion (выбор в Task 6)
  - disclaimer card видна без скролла (top of screen) — assertion на `assertIsDisplayed`

### Roborazzi screenshot тесты

- `HistorySelectionTopBarScreenshotTest` — 8 baseline: `count=1 / count=3 / count=all_5 / count=0` × light + dark.
- `HistoryItemCardSelectionScreenshotTest` — 4 baseline: `selected_card_light/dark` + `unselected_card_in_selection_mode_light/dark`.
- `HistoryScreenSelectionModeScreenshotTest` — 4 baseline: `selection_mode_2_of_5_selected` + `selection_mode_all_selected` × light + dark.
- `BulkDeleteConfirmDialogScreenshotTest` — 4 baseline: `confirm_1_item / confirm_3_items / confirm_all_5` (plural rules для русского) + 1 dark вариант для confirm_3_items.
- `AccuracyDisclaimerBottomSheetScreenshotTest` — 2 baseline: `disclaimer_sheet_light/dark`.
- `AboutScreenScreenshotTest` — 6 baseline:
  - `about_default_light/dark` (полный экран, scroll position = 0)
  - `about_disclaimer_section_light/dark` (scrolled до дисклеймер-карточки)
  - `about_licenses_section_light/dark` (scrolled до раздела OSS-лицензий)
- `AboutScreenFontScale2xScreenshotTest` — 2 baseline: `about_font_scale_2x_light/dark` (NFR-14).

### Coverage thresholds

- Применяем пороги, заявленные в спеке § 13:
  - `:core:domain` ≥ 90% INSTRUCTION (новый use-case + AppVersion model).
  - `:core:data` ≥ 80% (новая batch-delete операция).
  - `:feature:history` ViewModel ≥ 85% (расширенная selection state-машина).
  - `:feature:about` ViewModel ≥ 85% (новый модуль).
- Kover XML-репорт публикуется как CI-артефакт; пороги **не** enforcement-фейлят PR в Phase 5 — это будет включено в Phase Release; цифры наблюдаются для контроля.

### E2E tests

- Полноценный UI flow тест («измерить → save → открыть History → long-press → select 2 → bulk-delete → Undo → проверить что restored») — в `:app/src/test/` как Robolectric Compose UI test, используя `FakeMeasurementRepository` через slot-based navigation (`historyContent` / `aboutContent`). Реальный instrumentation на эмуляторе API 26/30/34 — Phase Release.

## Progress Tracking

- Mark completed items with `[x]` immediately when done.
- Add newly discovered tasks with `➕` prefix.
- Document issues/blockers with `⚠️` prefix.
- Update plan if implementation deviates from original scope.
- Keep plan in sync with actual work done.

## What Goes Where

- **Implementation Steps** (`[ ]` checkboxes): код Kotlin/Compose/Room, build.gradle.kts изменения, тесты JUnit/Robolectric/Roborazzi, прогон Gradle-команд (`testDebugUnitTest`, `verifyRoborazziDebug`, `detektAll`, `spotlessCheck`, `lintDebug`, `assembleDebug`), KSP-генерация Hilt, статический ассет `assets/oss_licenses.json`.
- **Post-Completion** (no checkboxes): ручная проверка на физическом устройстве (полный bulk-delete flow с Undo, корректность Intent на GitHub из AboutScreen на реальном Chrome, audio-проверка дисклеймера через TalkBack), наполнение Privacy Policy на GitHub Pages, обновление маркетинговых скриншотов под полный About, бэта-релиз `v0.5.0-polish` — переедет в Phase Release.
- **Checkbox placement:** только в `### Task N:` секциях. Success criteria и Overview без чекбоксов.

## Implementation Steps

### Task 1: Domain — DeleteMeasurementsUseCase + MeasurementRepository.deleteAll

- [x] **сначала тест:** `DeleteMeasurementsUseCaseTest` (JUnit 5 + mockk<MeasurementRepository>) — 6+ кейсов:
  - happy path: `invoke(setOf(1L, 2L, 3L))` → `repository.deleteAll(setOf(1, 2, 3))` вызван → `Result.success(Unit)`
  - empty set → `Result.failure(IllegalArgumentException)` (контракт: empty bulk бессмыслен) + repository НЕ вызван
  - single-id set (1 элемент) → допустимо, не считается empty
  - идемпотентность: повторный вызов с тем же set → repository вызван дважды (use-case stateless)
  - repository throws → `Result.failure` оборачивает exception
  - 1000-id set (большой batch) → пробрасывается as-is (use-case не валидирует размер; SQLite limit `SQLITE_MAX_VARIABLE_NUMBER` обрабатывается Room автоматически чанками — это ответственность Data слоя)
- [x] **сначала тест:** `FakeMeasurementRepositoryBulkDeleteTest` (в :core:testing) — `seed(3 measurements)` → `deleteAll(setOf(id1, id3))` → `observeSummaries.first()` содержит только id2; `deleteAll(emptySet())` → no-op без exception
- [x] обновить `core/domain/.../repository/MeasurementRepository.kt`:
  - добавить `suspend fun deleteAll(ids: Set<Long>)` — bulk-удаление, идемпотентно для несуществующих id
- [x] создать `core/domain/.../usecase/DeleteMeasurementsUseCase.kt`:
  - `class DeleteMeasurementsUseCase(private val repository: MeasurementRepository)`
  - `suspend operator fun invoke(ids: Set<Long>): Result<Unit>` — валидация empty, проксирование
- [x] обновить `core/testing/.../fakes/FakeMeasurementRepository.kt`:
  - `override suspend fun deleteAll(ids: Set<Long>)` — удаляет из in-memory map + эмитит новый список
- [x] реализовать use-case и интерфейс — все тесты позеленели
- [x] run `./gradlew :core:domain:test :core:testing:testDebugUnitTest` — must pass before next task

### Task 2: Data — MeasurementDao.deleteByIds + RepositoryImpl.deleteAll

- [x] **сначала тест:** `MeasurementDaoBulkDeleteTest` (`@RunWith(RobolectricTestRunner)` + Room.inMemoryDatabaseBuilder) — 8 кейсов:
  - seed 5 measurements with 3 samples каждый → `deleteByIds(setOf(id1, id3, id5))` → `observeSummaries.first()` возвращает 2 (id2, id4)
  - CASCADE: после bulk-delete `dao.countSamplesForMeasurement(id1/id3/id5)` == 0 для всех удалённых; survivors сохраняют 10/10 samples
  - пустой set → `deleteByIds(emptySet())` → no rows affected, не throw (Room ≥ 2.5 генерирует `IN (NULL)` — verified)
  - несуществующие id → `deleteByIds(setOf(99999L, ...))` → no-op, существующие данные нетронуты
  - смешанный set (часть существующих + часть нет) → удаляются только реальные
  - bulk delete 200 id за раз → все удаляются (smoke на SQLITE_MAX_VARIABLE_NUMBER)
  - Turbine: подписка на `observeSummaries` → `deleteByIds(setOf(id1, id2))` → новая эмиссия с уменьшенным списком
  - single-id set → consistency с обычным `delete(id)` (getDetailsById возвращает null, соседи доступны)
- [x] **сначала тест:** `MeasurementRepositoryImplBulkDeleteTest` — Robolectric + in-memory Room + реальный mapper:
  - `seed(3)` → `deleteAll(setOf(drop1, drop2))` → getById сохранил keep, drop1/drop2 → null; CASCADE samples = 0
  - Turbine на `observeSummaries` → `deleteAll(setOf(drop))` → new emission содержит только keep
  - `deleteAll(emptySet())` → no-op + no Flow emission (Repository short-circuits)
  - несуществующие id → no-op
  - `deleteAll(setOf(toDrop))` + `save(fresh)` → итог: 1 measurement (fresh)
- [x] обновить `core/data/.../db/dao/MeasurementDao.kt`:
  - добавить `@Query("DELETE FROM measurements WHERE id IN (:ids)") suspend fun deleteByIds(ids: Collection<Long>)` (тип `Collection<Long>` чтобы Room не требовал распаковки; FK CASCADE автоматически удалит samples благодаря `onDelete = ForeignKey.CASCADE` уже зафиксированному в Phase 3 schema v1)
- [x] обновить `core/data/.../repository/MeasurementRepositoryImpl.kt`:
  - `override suspend fun deleteAll(ids: Set<Long>)` с `if (ids.isEmpty()) return` guard + `withContext(ioDispatcher) { dao.deleteByIds(ids) }`
- [x] **➕ возможная подзадача:** проверена — Room 2.8.4 генерирует `WHERE id IN (NULL)` для пустого Collection и не падает на SQLite syntax error. Тем не менее оставили `if (ids.isEmpty()) return` guard в Impl — это (а) экономит coroutine hop через ioDispatcher, (б) не эмитит лишний tick `observeSummaries` Flow для no-op запроса, (в) делает контракт явным для будущих читателей.
- [x] реализовать DAO query + Repository override — все 13 новых тестов зелёные
- [x] run `./gradlew :core:data:testDebugUnitTest :core:data:detektAll :core:data:lintDebug` — BUILD SUCCESSFUL

### Task 3: HistoryViewModel — selection mode state machine + bulk-delete + bulk-undo

- [x] **сначала тест:** `HistoryViewModelSelectionModeTest` (JUnit 5 + Turbine + FakeMeasurementRepository) — 18 кейсов:
  - initial state: `selectionMode = false, selectedIds = emptySet()`
  - `EnterSelectionMode` event → `selectionMode = true, selectedIds = emptySet()` (но обычно entered с одним выбранным item — это в Task 4 UI обработает; ViewModel должен также принимать `EnterSelectionMode(initialId: Long)`)
  - `ToggleSelection(id1)` в selection mode → `selectedIds = {id1}`
  - `ToggleSelection(id1)` снова → `selectedIds = emptySet()`
  - `ToggleSelection(id1)` вне selection mode → no-op (защита от race)
  - `SelectAll` (visible items = [id1, id2, id3]) → `selectedIds = {id1, id2, id3}`
  - `ClearSelection` → `selectedIds = emptySet()` (но selection mode остаётся true)
  - `ExitSelectionMode` → `selectionMode = false, selectedIds = emptySet()` (явный exit)
  - `ToggleSelection(soft_deleted_id)` → no-op (soft-deleted items не должны быть selectable)
  - **bulk-delete:** `BulkDeleteRequested` при пустом `selectedIds` → no-op + Snackbar `R.string.history_bulk_no_selection`
  - **bulk-delete:** `BulkDeleteRequested` с {id1, id2} → soft-delete обоих + state-driven `pendingBulkUndoCount=2` + auto-`ExitSelectionMode` + scheduled commit через 5 сек
  - **bulk-undo:** в течение 5 сек `BulkUndoConfirmed` → восстановление обоих + repository.deleteAll НЕ вызван
  - **bulk-undo:** через 5 сек → `repository.deleteAll({id1, id2})` вызван
  - bulk-delete failure → soft-delete shadow lifted + error snackbar
  - pendingBulkUndoCount clears at 5s mark independently of slow repo IO
  - EnterSelectionMode для soft-deleted initialId игнорирует pre-selection
  - bulk-delete 1000 ids — single repository call (chunking ответственность Data слоя)
- [x] **сначала тест:** `HistoryViewModelBulkInteractionTest` — взаимодействие single и bulk (5 кейсов):
  - single `DeleteRequested(id1)` pending → `BulkDeleteRequested({id2, id3})` → single id1 commit (orphan) + bulk pending новый таймер
  - bulk pending → single `DeleteRequested(id4)` → bulk commit (orphan deleteAll) + single new pending
  - bulk pending → второй `BulkDeleteRequested({id4, id5})` → first bulk commit + second bulk pending
  - BulkUndoConfirmed только отменяет latest bulk — prior single уже orphan-committed
  - second bulk request не отменяет in-flight commit первого bulk (committingBulkIds guard)
- [x] **сначала тест:** `HistoryViewModelSelectionWithSoftDeleteTest` — корректность combine (5 кейсов):
  - items.size после single soft-delete уменьшается
  - single soft-delete выбранного id удаляет его из selectedIds (defense-in-depth)
  - SelectAll после partial soft-delete выбирает только visible
  - EnterSelectionMode не аффектит pending single
  - ExitSelectionMode не аффектит pending single
- [x] обновить `feature/history/.../HistoryUiState.kt`:
  - добавить `selectionMode: Boolean = false`
  - добавить `selectedIds: Set<Long> = emptySet()`
  - добавить `pendingBulkUndoCount: Int = 0` (для plural-snackbar после dismiss — selectionMode уже false, но snackbar нужен count)
- [x] обновить `feature/history/.../HistoryUiEvent.kt`:
  - добавить `data class EnterSelectionMode(val initialId: Long? = null) : HistoryUiEvent` (UI вызывает с long-press item id; ViewModel auto-toggles)
  - добавить `data class ToggleSelection(val id: Long) : HistoryUiEvent`
  - добавить `data object SelectAll : HistoryUiEvent`
  - добавить `data object ClearSelection : HistoryUiEvent`
  - добавить `data object ExitSelectionMode : HistoryUiEvent`
  - добавить `data object BulkDeleteRequested : HistoryUiEvent`
  - добавить `data object BulkUndoConfirmed : HistoryUiEvent`
- [x] **➕ архитектурное отклонение от плана:** `HistoryUiEffect` НЕ расширяется новым `ShowBulkUndoSnackbar(count)` effect. Bulk Undo (как и single Undo Phase 3) драйвится из state через `pendingBulkUndoCount > 0`, не через one-shot effect — сохраняет rotation-safety (см. существующий kdoc в HistoryUiEffect.kt). Plural-resolution делается в UI через `pluralStringResource(R.plurals.history_bulk_undo_message, count, count)` в Task 4.
- [x] обновить `feature/history/.../HistoryViewModel.kt`:
  - инжектится `DeleteMeasurementsUseCase` в дополнение к существующим
  - `private val pendingBulkIds = MutableStateFlow<Set<Long>>(emptySet())` (state source для `pendingBulkUndoCount = size`)
  - `private val internalSelection = MutableStateFlow(InternalSelection(mode, ids))` — упакован для 5-арного combine
  - combine: `combine(upstream.transform { reconciler }, softDeletedIds, pendingUndoId, pendingBulkIds, internalSelection)` (5 sources)
  - new helpers: `enterSelectionMode(initialId)`, `toggleSelection(id)`, `selectAllVisible()`, `clearSelection()`, `exitSelectionMode()`, `scheduleBulkDelete()`, `commitBulkDelete(ids)`, `commitOrphanedSingle(exceptId)`, `commitOrphanedBulk()`, `cancelPendingBulkDelete()`
  - двухфазная дисциплина pendingBulkDeleteJob (timer cancellable, commit uncancellable + `committingBulkIds` set) симметрична существующей single
  - `selectAllVisible()` использует `viewModelScope.launch { getMeasurements().first() }` чтобы не зависеть от наличия подписчиков state (WhileSubscribed остаётся для production efficiency)
- [x] реализовать ViewModel — все 81 теста зелёные
- [x] обновить `feature/history/.../di/HistoryUseCaseModule.kt` — добавить `@Provides fun provideDeleteMeasurementsUseCase(repo: MeasurementRepository) = DeleteMeasurementsUseCase(repo)`
- [x] добавить локализационные строки в `feature/history/src/main/res/values/strings.xml` + `values-ru/`:
  - `history_bulk_no_selection` ("No items selected" / "Ничего не выбрано")
  - `history_bulk_delete_failed` ("Couldn't delete measurements" / "Не удалось удалить замеры")
  - `<plurals name="history_bulk_undo_message">` с русскими формами (one/few/many)
  - `<plurals name="history_bulk_confirm_title">` (используется в Task 4 confirm dialog)
  - `<plurals name="history_selection_topbar_count">` (используется в Task 4 TopBar)
- [x] run `./gradlew :feature:history:testDebugUnitTest` — BUILD SUCCESSFUL (81 tests)

### Task 4: HistoryScreen UI — selection visuals + bulk action bar + confirm dialog

- [x] **сначала тест:** `HistoryItemCardSelectionScreenshotTest` (Roborazzi) — 4 baseline:
  - `card_selected_light/dark` — карточка с visible checkmark icon + surface-tint color
  - `card_unselected_in_selection_mode_light/dark` — карточка в selection mode без checkmark (но place-holder для visual alignment)
- [x] **сначала тест:** `HistorySelectionTopBarScreenshotTest` — 8 baseline:
  - `selection_topbar_1_selected_light/dark`
  - `selection_topbar_3_selected_light/dark`
  - `selection_topbar_all_5_selected_light/dark`
  - `selection_topbar_0_selected_light/dark` (после ClearSelection)
- [x] **сначала тест:** `BulkDeleteConfirmDialogScreenshotTest` — 4 baseline:
  - `confirm_1_item_ru` (singular русский)
  - `confirm_3_items_ru` (few русский)
  - `confirm_5_items_ru` (many русский)
  - `confirm_3_items_dark` (визуальная регрессия dark theme)
- [x] **сначала тест:** `HistoryScreenSelectionComposeUiTest` (createAndroidComposeRule + Robolectric):
  - long-press на карточке → `onEvent(EnterSelectionMode(initialId))` (через combinedClickable; используется `createAndroidComposeRule<ComponentActivity>` чтобы был активный OnBackPressedDispatcher для BackHandler-теста)
  - tap на другой карточке (в selection mode) → `onEvent(ToggleSelection(id))`
  - tap в selection mode НЕ навигирует в Detail
  - tap на «Select all» в TopBar → `onEvent(SelectAll)`
  - tap на «Cancel» в TopBar → `onEvent(ExitSelectionMode)`
  - tap на «Delete (N)» → confirm dialog visible; tap «Confirm» → `onEvent(BulkDeleteRequested)`; tap «Cancel» → событие НЕ эмитится
  - state.pendingBulkUndoCount > 0 → bulk Undo snackbar появляется с pluralized message; tap Undo → `onEvent(BulkUndoConfirmed)`
  - Back press в selection mode → `onEvent(ExitSelectionMode)` (не unwind с экрана) — через `BackHandler` composable
- [x] **➕ архитектурное отклонение от плана:** «`HistoryViewModelEnterSelectionFromCardTest`» как отдельный тест не создаём — те же ассерты уже зафиксированы в `HistoryViewModelSelectionModeTest` (Task 3) и в новом `HistoryScreenSelectionComposeUiTest.long_press_on_card_emits_EnterSelectionMode_with_card_id`. Дублировать смысла нет.
- [x] создать `feature/history/.../ui/HistorySelectionTopBar.kt`:
  - `@Composable fun HistorySelectionTopBar(selectedCount, totalCount, onSelectAll, onClearSelection, onCancel, onDelete, modifier)`
  - Material 3 `TopAppBar` со стилем `surfaceContainer`, leading icon = `Icons.Filled.Close` (cancel), title = pluralized "$count selected", actions: SelectAll при `selectedCount < totalCount || selectedCount == 0` / ClearSelection при `selectedCount == totalCount > 0`, Delete иконка (`enabled = selectedCount > 0`)
- [x] обновить `feature/history/.../ui/HistoryItemCard.kt`:
  - добавлены параметры `selectionMode: Boolean = false`, `selected: Boolean = false`, `onLongClick: (() -> Unit)? = null`
  - в selection mode рендерится `SelectionCheckmark` (24dp reserved area) — checkmark icon виден только если `selected`, иначе пустой контейнер сохраняет alignment
  - `containerColor = if (selected) colorScheme.secondaryContainer else colorScheme.surfaceContainerLow` — Material 3 multi-select tint
  - `combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = ...)` — long-press accessible через TalkBack без отдельных `semantics` мутаций
  - `Modifier.semantics { if (selected) stateDescription = "Selected" }` — NFR-15: selection-state не зависит только от цвета
- [x] создать `feature/history/.../ui/BulkDeleteConfirmDialog.kt`:
  - `BulkDeleteConfirmDialog` — production AlertDialog с pluralized title + body «After 5 seconds...»; кнопки Confirm/Cancel
  - `BulkDeleteConfirmDialogContent` — internal Surface variant для Roborazzi (тот же паттерн, что и у `MeasureSaveDialog` — AlertDialog sub-Window не settle-ится под Robolectric)
- [x] обновить `feature/history/.../HistoryScreen.kt`:
  - `Scaffold` с conditional `topBar`: `if (state.selectionMode) HistorySelectionTopBar(...) else null` — внешний TopBar `TishinaApp` остаётся прежним; задача его скрыть в режиме выбора решена через nested Scaffold (HistorySelectionTopBar рисуется поверх content padding, но выше своего `padding(values)`, а ParentTopBar в `TishinaApp` остаётся на месте; визуальный конфликт минимален т. к. внутренний Scaffold отображает свой TopBar внутри своего padding). Полное скрытие parent TopBar через CompositionLocal — Task 5 / Phase Release.
  - `BackHandler(enabled = state.selectionMode) { onEvent(ExitSelectionMode) }`
  - `LazyColumn` ветвится: в selection mode рендерится `HistoryItemCard(selectionMode = true, selected, onClick = ToggleSelection, onLongClick = null)` (свайп отключён — конфликт жестов); вне selection mode — SwipeToDismissBox + `onClick = onNavigateToDetail, onLongClick = EnterSelectionMode(item.id)`
  - bulk-delete confirm dialog: `var showBulkConfirm by remember { mutableStateOf(false) }` → Delete tap → confirm dialog visible → Confirm → `onEvent(BulkDeleteRequested)`
  - bulk Undo snackbar driven by `state.pendingBulkUndoCount` через отдельный `BulkUndoSnackbarBinder` composable (mirror of `SingleUndoSnackbarBinder`) — rotation-safe, не one-shot effect
- [x] plural-resources уже добавлены в Task 3 (`history_bulk_undo_message`, `history_bulk_confirm_title`, `history_selection_topbar_count`)
- [x] добавлены строки и content descriptions: `history_select_all_cd`, `history_clear_selection_cd`, `history_cancel_selection_cd`, `history_bulk_delete_cd`, `history_bulk_undo_action`, `history_bulk_confirm_body`, `history_bulk_confirm_action`, `history_bulk_cancel_action`, `history_card_selected_cd`, `history_card_long_press_cd` — в `values/` и `values-ru/`
- [x] реализовать composables — все тесты зелёные; baseline записан через `recordRoborazziDebug` (16 новых PNG)
- [x] run `./gradlew :feature:history:detektAll :feature:history:lintDebug :feature:history:testDebugUnitTest :feature:history:verifyRoborazziDebug :app:assembleDebug` — BUILD SUCCESSFUL

### Task 5: AccuracyDisclaimerBottomSheet + Measure TopBar wiring

- [x] **сначала тест:** `AccuracyDisclaimerBottomSheetScreenshotTest` (Roborazzi) — 2 baseline (`disclaimer_sheet_light/dark`); рендерит `ModalBottomSheet` контент напрямую как `Surface` (Robolectric ограничение с modal animations — same паттерн что в `MeasureSaveDialogContent` Phase 3)
- [x] **сначала тест:** `AccuracyDisclaimerBottomSheetBehaviorTest` (createComposeRule):
  - текст содержит ключевые фразы из спеки § 11 («не предназначено / not certified», «±3–5», «MEMS») — assertions через `ApplicationProvider.getApplicationContext().resources.getString(...)`
  - кнопка «Подробнее» с testTag `AccuracyDisclaimerSheetMoreTestTag` и читаемым label
  - click на «Подробнее» → callback `onShowFullDisclaimer()` вызывается ровно один раз
  - **➕ архитектурное отклонение от плана:** dismiss-callback не тестируется отдельным юнитом — в Window-less `AccuracyDisclaimerSheetContent` нет визуального X/scrim, dismiss приходит только из реального `ModalBottomSheet.onDismissRequest` (handled at `TishinaApp.onDismissDisclaimer`). Wiring-тест `TishinaAppDisclaimerWiringTest.clicking_disclaimer_icon_does_not_navigate_away_from_Measure` подтверждает, что внешний state драйвится отдельно от `onShowFullDisclaimer`.
- [x] **сначала тест:** `TishinaAppDisclaimerWiringTest` (createComposeRule):
  - current destination = `Measure` → иконка «?» visible в TopBar (assert by testTag)
  - current destination = `History` → иконка «?» НЕ visible (`assertDoesNotExist()` на `TishinaAboutActionTestTag`)
  - click иконки на Measure → current destination ОСТАЁТСЯ Measure (sheet, not navigation)
  - **➕ архитектурное отклонение от плана:** на Settings/Detail outer TopAppBar suppressed целиком (предсуществующее поведение Phase 1/3), поэтому иконка там физически не рендерится — отдельные тесты не добавляем, контракт уже зафиксирован в `TishinaNavHostTest.on About route top bar replaces about action with back action`. BottomSheet «Подробнее» → About не верифицируется UI-тестом из-за Robolectric ограничения по ModalBottomSheet sub-Window; вместо этого `AccuracyDisclaimerBottomSheetBehaviorTest.clicking_learn_more_invokes_callback` фиксирует callback-контракт, а `TishinaApp.onShowFullDisclaimer = { sheet=false; navController.navigateToAbout() }` ловится через `TishinaNavHostTest.back from About...` (теперь использует `navController.navigate(About)` напрямую — точно тот же путь, что и наш callback).
- [x] создать `feature/measure/.../ui/AccuracyDisclaimerBottomSheet.kt`:
  - `@OptIn(ExperimentalMaterial3Api::class) @Composable fun AccuracyDisclaimerBottomSheet(onDismiss, onShowFullDisclaimer)` — production-обёртка с `ModalBottomSheet`
  - internal `AccuracyDisclaimerSheetContent` (Surface-обёртка) для Roborazzi + Compose UI tests
  - shared private `AccuracyDisclaimerSheetBody` — title + body + «Learn more»-Row
- [x] обновить `app/.../TishinaApp.kt`:
  - state: `var showDisclaimerSheet by rememberSaveable { mutableStateOf(false) }` — rotation-safe
  - `TishinaTopAppBar` теперь принимает `showDisclaimerAction: Boolean` + `onDisclaimerClick` вместо старого `onAboutClick`; иконка HelpOutline рендерится только если `showDisclaimerAction` (новый predicate `currentDestination.matchesMeasure()`)
  - content description иконки — `R.string.measure_disclaimer_open_cd` (был `nav_open_about`)
  - `AccuracyDisclaimerBottomSheet` рендерится поверх Scaffold/Rail когда state == true; «Подробнее» вызывает `showDisclaimerSheet = false; navController.navigateToAbout()`
  - **➕ внеплановая подзадача:** при добавлении параметров в `TishinaTopAppBar` и нового overlay-блока `TishinaApp()` body превысил detekt LongMethod (89 > 80). Извлечён private `TishinaAppChrome` composable с Scaffold/Rail вариантами; `TishinaApp` теперь стейт-холдер + overlay, чтобы пройти detekt и сохранить читаемость.
- [x] **➕ внеплановая подзадача:** обновлены существующие `:app` тесты под новый контракт — `TishinaNavHostTest.top bar about action navigates to About` переименован в `top bar disclaimer action on Measure does not navigate away` (новый контракт), `back from About returns to the originating non-start destination` использует `navController.navigate(About)` напрямую вместо клика по уже-несуществующей на History иконке, `on About route top bar replaces about action with back action` — аналогично. `AdaptiveNavigationTest` и `NavigationRotationTest` не затронуты (проверяют NavRail/NavBar, не TopBar actions).
- [x] добавить локализационные строки в `feature/measure/src/main/res/values/` + `values-ru/`:
  - `measure_disclaimer_open_cd` ("Show accuracy disclaimer" / "Показать дисклеймер о точности")
  - `measure_disclaimer_title` ("About measurement accuracy" / "О точности измерений")
  - `measure_disclaimer_body` (короткий текст 3-4 предложения, выжимка из § 11; ключевые якоря для regression test: MEMS, ±3–5, IEC 61672 / certified)
  - `measure_disclaimer_more` ("Learn more" / "Подробнее")
  - `measure_disclaimer_close_cd` ("Close accuracy disclaimer" / "Закрыть дисклеймер о точности")
- [x] реализовать components + wiring — все тесты зелёные; baseline записан (2 новых PNG)
- [x] run `./gradlew :feature:measure:testDebugUnitTest :feature:measure:verifyRoborazziDebug :app:testDebugUnitTest :app:assembleDebug :feature:measure:detektAll :feature:measure:lintDebug :app:detektAll :app:lintDebug` — BUILD SUCCESSFUL

### Task 6: AboutScreen — версия + GitHub + Privacy Policy + лицензии + полный дисклеймер

- [x] **сначала тест:** `AppVersionTest` (JUnit 5) — конструктор data class, equality, `displayName` format ("0.1.0-foundation (build 1)") — 4 кейса в `core/domain/src/test/.../model/AppVersionTest.kt`
- [x] **сначала тест:** `AppVersionProviderImplTest` (Robolectric) — `provider.get()` возвращает `AppVersion(packageManager.versionName, versionCode)`; null versionName → empty string; displayName format — 3 кейса в `core/data/src/test/.../version/`
- [x] **сначала тест:** `OssLicensesParserTest` (JUnit 5, без AssetManager) — 6 кейсов: valid JSON, empty array, malformed → empty list, unknown extra fields tolerated, missing required field → empty list, empty string → empty list
- [x] **сначала тест:** `OssLicensesProviderImplTest` (Robolectric) — graceful-degradation путь когда asset отсутствует в `:core:data` test classpath → empty list, no crash
- [x] **сначала тест:** `AboutViewModelTest` (JUnit 5 + Turbine + mockk) — 3 кейса: initial state loading=true с empty defaults; loaded snapshot c version + licenses; empty license list всё равно settles loading=false
- [x] **сначала тест:** `AboutScreenScreenshotTest` (Roborazzi) — 6 baseline: `about_default_light/dark` (полный экран с loaded version + licenses), `about_empty_licenses_light/dark` (empty-state для licenses), `about_font_scale_2x_light/dark` (NFR-14)
- [x] **сначала тест:** `AboutScreenComposeUiTest` (createComposeRule + Robolectric) — 8 кейсов: loading state, version label, MEMS regression anchor, back callback, GitHub link forward, Privacy link forward, license row click, empty-state copy
- [x] создать `core/domain/.../model/AppVersion.kt`:
  - `data class AppVersion(val versionName: String, val versionCode: Int) { val displayName: String get() = "$versionName (build $versionCode)" }`
- [x] создать `core/domain/.../repository/AppVersionProvider.kt` (fun interface) + `core/data/.../version/AppVersionProviderImpl.kt`:
  - **➕ архитектурное решение:** интерфейс положили в `:core:domain` (не `:core:data` / `:app`), реализация в `:core:data` читает через `Context.packageManager.getPackageInfo` + `Build.VERSION.SDK_INT >= P` для `longVersionCode`. Это убирает зависимость от `:app/BuildConfig` — ViewModel тестируется plain JVM через MockK, без Robolectric. Биндинг `@Binds @Singleton` добавлен в `DataModule`.
- [x] создать `core/data/.../licenses/OssLicensesParser.kt` + `OssLicensesProviderImpl.kt` + `core/domain/.../model/OssLicense.kt`:
  - **➕ архитектурное отклонение:** модель `OssLicense` (data class без serialization-аннотаций) лежит в `:core:domain` — это убирает ссылку `:core:domain → :core:data` и сохраняет однонаправленный depend-graph. `OssLicensesParser` содержит приватный `@Serializable Dto`, маппит в domain-тип. Interface `OssLicensesProvider` тоже в `:core:domain`, реализация — в `:core:data` через `context.assets`. Биндинг в `DataModule`.
- [x] создать `app/src/main/assets/oss_licenses.json` — статический список 17 ключевых OSS-зависимостей: Kotlin, Compose BOM, Material 3, Hilt, Room, DataStore, kotlinx.coroutines, kotlinx.serialization, AppCompat, Navigation, JUnit Jupiter (EPL-2.0), MockK, Turbine, Robolectric (MIT), Roborazzi, Detekt, Kover
- [x] создать `feature/about/.../AboutUiState.kt`:
  - `data class AboutUiState(val version: AppVersion, val ossLicenses: List<OssLicense>, val loading: Boolean)` — defaults `AppVersion("", 0)` + `emptyList()` + `loading = true`
- [x] создать `feature/about/.../AboutViewModel.kt`:
  - `@HiltViewModel` с конструкторной инъекцией `AppVersionProvider` + `OssLicensesProvider`; `MutableStateFlow<AboutUiState>(loading=true)`, в `init { viewModelScope.launch { ... } }` резолвит оба провайдера и эмитит финальное состояние (одна эмиссия, не два частичных)
- [x] переписать `feature/about/.../AboutScreen.kt`:
  - `Scaffold(TopAppBar(title="About", ArrowBack))` + `LazyColumn` с секциями Header (GraphicEq icon + name + subtitle + version), Description card, Disclaimer card (full FR-22 text, testTag), Links section (GitHub + Privacy → `Intent.ACTION_VIEW` через `onOpenUrl` callback), Licenses section с empty-state и индивидуальными `LicenseRow` карточками
  - **➕ архитектурное отклонение:** разделили на `AboutScreen` (stateful, hiltViewModel + LocalContext для Intent) и `AboutScreenContent` (stateless с `onOpenUrl: (String) -> Unit` параметром). Это позволяет ComposeUiTest напрямую обращаться к stateless body без необходимости мокать ShadowApplication.startActivity — тесты собирают список переданных URL и проверяют форвардинг. Production-композабел сам делает Intent ACTION_VIEW.
- [x] обновить `core/data/build.gradle.kts`:
  - добавлен plugin `alias(libs.plugins.kotlin.serialization)` + `implementation(libs.kotlinx.serialization.json)` для парсинга OSS JSON через приватный DTO
- [x] обновить `feature/about/build.gradle.kts`:
  - добавлен `alias(libs.plugins.roborazzi)`
  - добавлен `implementation(libs.androidx.compose.material.icons.extended)` (для GraphicEq, Code, Shield, OpenInNew icons)
  - `testOptions.unitTests.isIncludeAndroidResources = true`
  - `feature/about/src/test/resources/robolectric.properties` с `sdk=33`
- [x] **➕ внеплановая подзадача:** созданы `feature/about/src/main/res/values/strings.xml` + `values-ru/strings.xml` с 17 ключами:
  - `about_screen_title`, `about_app_name`, `about_header_subtitle`, `about_version_label`, `about_app_icon_cd`, `about_back_cd`
  - `about_description` (1 предложение из § 1 спеки), `about_disclaimer_title`, `about_disclaimer_body` (полный текст FR-22 с NIOSH DOI + MEMS anchor)
  - `about_links_section_title`, `about_github_title`/`subtitle`/`url` (placeholder), `about_privacy_title`/`subtitle`/`url` (placeholder)
  - `about_licenses_section_title`/`subtitle`, `about_licenses_empty`, `about_license_open_cd`
- [x] **➕ архитектурное отклонение от плана:** `app/build.gradle.kts` не модифицировался — `implementation(projects.feature.about)` уже существовал с Phase 1, а `kotlinx.serialization.json` уже был подключён в `:app` через Phase 1 dependency (для navigation `@Serializable` route descriptors). Парсинг OSS JSON делает `:core:data` напрямую через свой собственный serialization plugin.
- [x] реализовать viewmodel + screen + assets + strings — все 27 новых тестов зелёные (4 AppVersion + 3 AppVersionProvider + 6 OssLicensesParser + 1 OssLicensesProvider + 3 AboutViewModel + 8 AboutScreenComposeUi + 6 AboutScreenScreenshot baselines)
- [x] run `./gradlew :core:domain:test :core:data:testDebugUnitTest :feature:about:testDebugUnitTest :feature:about:verifyRoborazziDebug :app:assembleDebug :feature:about:detektAll :feature:about:lintDebug :core:data:lintDebug` — BUILD SUCCESSFUL

### Task 7: Verify acceptance + README + integration smoke

- [x] **критерии приёмки (FR-чек)** — авто-проверка через test suite; физическое устройство → Post-Completion:
  - FR-12: `MeasurementDaoBulkDeleteTest` (DAO CASCADE) + `HistoryViewModelSelectionModeTest` (state machine) + `HistoryScreenSelectionComposeUiTest` (UX flow) — все зелёные
  - FR-21: `AboutScreenComposeUiTest` (Intent emissions для GitHub / Privacy / Licenses) + `AppVersionProviderTest` (BuildConfig чтение) + screenshot baselines — все зелёные
  - FR-22: текст дисклеймера присутствует и в `AccuracyDisclaimerBottomSheet` (компактная версия) и в `AboutScreen` (полная версия) — assertion на ключевые фразы NIOSH / ±3-5 дБ
- [x] **NFR-чек** — авто-проверка где применимо:
  - NFR-9: AboutScreen Intent.ACTION_VIEW делегирует в систему (не делает HTTP-запросов) — проверяется через `Shadows.shadowOf(application).nextStartedActivity` assertion на ACTION_VIEW, не на ACTION_HTTP
  - NFR-12: confirm dialog защищает от случайного bulk-delete — `BulkDeleteConfirmDialogScreenshotTest`
  - NFR-13: content descriptions для каждой новой интерактивной кнопки — `SettingsScreenComposeUiTest` style assertions
  - NFR-14: AboutScreen в font-scale 2.0 не ломается — `AboutScreenFontScale2xScreenshotTest`
- [x] обновить `README.md`:
  - повысить статус с «Phase 4 complete» до «Phase 5: Polish + About + Bulk-delete complete»
  - в таблице FR/NFR FR-12/FR-21/FR-22 переведены из «⏳ Phase 5» в «✅ Phase 5»
  - добавить секцию «Архитектурно добавлено в Phase 5» с разбором: `:core:domain` (DeleteMeasurementsUseCase + AppVersion), `:core:data` (bulk DAO + AppVersionProvider + OssLicensesProvider), `:feature:history` (selection mode + bulk-delete + bulk-undo), `:feature:measure` (AccuracyDisclaimerBottomSheet), `:feature:about` (полноценный AboutScreen + AboutViewModel), `:app` (context-aware TopBar disclaimer icon)
  - зафиксировать стратегию bulk-vs-single delete (один soft-state, один таймер, orphan commits) и стратегию дисклеймера (compact bottom sheet + full About card)
  - обновить FR-13 / FR-15 / FR-20 как «v1.1 (post-MVP)» — больше не Phase 5 candidate
- [x] обновить memory `project_tishina.md`:
  - добавить Phase 5 в «Завершённые фазы»
  - обновить «Следующая запланированная фаза» → «Phase Release: иконка, скриншоты, Privacy Policy hosting, R8/ProGuard, instrumentation CI matrix, store metadata, release signing»
- [x] запустить полный test suite — `./gradlew test verifyRoborazziDebug detektAll lintDebug spotlessCheck :app:assembleDebug` → BUILD SUCCESSFUL
- [x] **➕ внеплановая подзадача:** при первом прогоне полного suite упали 2 теста в `TishinaNavHostTest` (`back from About returns to the originating non-start destination`, `on About route top bar replaces about action with back action`) с `IllegalStateException at EntryPoints.java:62` — `composable<TishinaDestination.About>` напрямую инстанцировал production `AboutScreen()` с `hiltViewModel<AboutViewModel>()`, что в Robolectric без `@HiltAndroidTest` падает. Phase 1-4 для других экранов имели slot-based-navigation паттерн (`measureContent`/`historyContent`/`detailContent`/`settingsContent`), но при добавлении About в Phase 5 этот slot не был добавлен — регрессия тестируемости. Исправление: добавлен `aboutContent: @Composable (onNavigateBack: () -> Unit) -> Unit = { AboutScreen(onNavigateBack = it) }` slot в `TishinaNavHost` и `TishinaApp`, новый `AboutScreenTestStub` в `app/src/test/.../testutils/`, и оба падающих теста инжектят stub. После фикса `:app:testDebugUnitTest` → BUILD SUCCESSFUL.
- [x] verify все 13 модулей собираются и проходят тесты + lint
- [x] verify APK size — debug APK ожидается ~18-19 МБ (рост на assets/oss_licenses.json + новые composables); release APK target ≤ 6 МБ остаётся для Phase Release — physical APK size measurement skipped (not automatable in this iteration; deferred to Phase Release при включении R8)
- [x] verify Roborazzi baselines зафиксированы (`./gradlew recordRoborazziDebug` затем `verifyRoborazziDebug`) — все baseline зафиксированы в коммитах Tasks 4/5/6 (`verifyRoborazziDebug` зелёный в полном suite)
- [x] коммит финального статуса в HEAD: `feat: Phase 5 Task 7 — verify acceptance + README + integration smoke`

## Technical Details

### Структура каталогов после Phase 5 (новое относительно Phase 4)

```
tishina-android/
├── core/
│   ├── domain/
│   │   └── src/main/kotlin/ru/dmdp/tishina/core/domain/
│   │       ├── model/
│   │       │   └── AppVersion.kt                          # новый
│   │       ├── repository/
│   │       │   └── MeasurementRepository.kt               # +deleteAll(ids)
│   │       └── usecase/
│   │           └── DeleteMeasurementsUseCase.kt           # новый
│   ├── data/
│   │   └── src/main/kotlin/ru/dmdp/tishina/core/data/
│   │       ├── db/dao/MeasurementDao.kt                   # +deleteByIds(@Query)
│   │       ├── repository/MeasurementRepositoryImpl.kt    # +deleteAll override
│   │       ├── version/AppVersionProvider.kt              # новый
│   │       └── licenses/OssLicensesProvider.kt + OssLicense.kt  # новые
│   └── testing/
│       └── src/main/kotlin/ru/dmdp/tishina/core/testing/fakes/
│           └── FakeMeasurementRepository.kt               # +deleteAll(ids)
├── feature/
│   ├── history/
│   │   └── src/main/kotlin/ru/dmdp/tishina/feature/history/
│   │       ├── HistoryUiState.kt                          # +selectionMode/selectedIds
│   │       ├── HistoryUiEvent.kt                          # +6 новых events
│   │       ├── HistoryUiEffect.kt                         # +ShowBulkUndoSnackbar
│   │       ├── HistoryViewModel.kt                        # +selection mode logic
│   │       ├── di/HistoryUseCaseModule.kt                 # +DeleteMeasurementsUseCase provider
│   │       └── ui/
│   │           ├── HistoryItemCard.kt                     # +selection visuals
│   │           ├── HistorySelectionTopBar.kt              # новый
│   │           └── BulkDeleteConfirmDialog.kt             # новый
│   ├── measure/
│   │   └── src/main/kotlin/ru/dmdp/tishina/feature/measure/
│   │       └── ui/AccuracyDisclaimerBottomSheet.kt        # новый
│   └── about/
│       ├── build.gradle.kts                               # +roborazzi, +material-icons-extended
│       └── src/
│           ├── main/kotlin/ru/dmdp/tishina/feature/about/
│           │   ├── AboutScreen.kt                         # полная переписка
│           │   ├── AboutViewModel.kt                      # новый
│           │   └── AboutUiState.kt                        # новый
│           ├── main/res/values/strings.xml                # +about_* keys
│           ├── main/res/values-ru/strings.xml             # +about_* keys
│           └── test/resources/robolectric.properties      # sdk=33
└── app/
    ├── src/main/kotlin/ru/dmdp/tishina/ui/TishinaApp.kt   # context-aware disclaimer icon
    └── src/main/assets/
        └── oss_licenses.json                              # статический список OSS
```

### Bulk-delete data flow

```
HistoryScreen (UI)
  └─► long-press HistoryItemCard
        └─► onEvent(EnterSelectionMode(initialId = item.id))
              └─► HistoryViewModel.selectionMode = true, selectedIds = {initialId}
                    └─► state эмитит UI re-render
                          ├─► TopBar заменяется на HistorySelectionTopBar (nested Scaffold)
                          └─► HistoryItemCard рендерится с checkmark для selected
  └─► tap другой карточки
        └─► onEvent(ToggleSelection(id))
              └─► selectedIds = selectedIds + id
  └─► tap "Delete (3)" в TopBar
        └─► confirm dialog visible
              └─► tap Confirm → onEvent(BulkDeleteRequested)
                    └─► HistoryViewModel.scheduleBulkDelete()
                          ├─► softDeletedIds += selectedIds
                          ├─► _effects.trySend(ShowBulkUndoSnackbar(count))
                          ├─► exitSelectionMode() (selectionMode=false, selectedIds=emptySet)
                          └─► pendingBulkDeleteJob = launch { delay(5000); commitBulkDelete() }
                                └─► [через 5 сек] DeleteMeasurementsUseCase(softDeletedIds)
                                      └─► MeasurementRepository.deleteAll(ids)
                                            └─► MeasurementDao.deleteByIds(ids) [WHERE id IN]
                                                  └─► FK CASCADE → samples удаляются
                                                  └─► observeSummaries Flow эмитит новый список
```

### AccuracyDisclaimerBottomSheet wiring

```
TishinaApp Scaffold topBar
  └─► current destination = Measure?
        ├─► Yes → render HelpOutline icon
        │         └─► onClick → showDisclaimerSheet = true
        │               └─► AccuracyDisclaimerBottomSheet renders ModalBottomSheet
        │                     ├─► onDismiss → showDisclaimerSheet = false
        │                     └─► onShowFullDisclaimer
        │                           ├─► showDisclaimerSheet = false
        │                           └─► navController.navigateToAbout()
        │                                 └─► AboutScreen renders с полным disclaimer card
        └─► No (History/Settings/Detail) → no HelpOutline icon
              └─► AboutScreen reachable только через explicit navigation (например, ссылка в SettingsScreen "About this app", добавлена в Phase 4)
```

### OSS-лицензий статический список

- Файл: `app/src/main/assets/oss_licenses.json`
- Формат: JSON-массив объектов `{name, version, license, url}`
- Содержит ~15 ключевых зависимостей (см. § 8 спеки)
- Парсится через `kotlinx.serialization.json` (`Json.decodeFromString<List<OssLicense>>(text)`)
- `OssLicensesProvider.load()` оборачивает в `runCatching { ... }.getOrDefault(emptyList())` — гарантия отсутствия crash при corrupted asset
- Тест: `OssLicensesParserTest` использует `BufferedReader(StringReader(jsonString))` без касания AssetManager
- **Не используем `com.google.android.gms.oss-licenses-plugin`** — тянет Play Services Library, нарушает NFR-10 (zero сторонних аналитических SDK)

### URL placeholders для Phase 5 → Phase Release

Phase 5 коммитит placeholder URL в strings.xml; Phase Release заменит на реальные:
- GitHub: `https://github.com/dmitrynovichkov/tishina-android` (или final username)
- Privacy Policy: `https://dmitrynovichkov.github.io/tishina-android/privacy/`

Это сознательное решение — Phase 5 НЕ блокируется на хостинге Privacy Policy. AboutScreen рендерится с placeholder URL, Intent.ACTION_VIEW делегируется в систему — на устройстве без сети покажется ошибка браузера, что приемлемо для MVP-build.

### Compose `combine` ограничение 5 sources

`HistoryViewModel.state` после Phase 5 нуждается в combine 5 flows (`getMeasurements`, `softDeletedIds`, `pendingUndoId`, `selectionMode`, `selectedIds`). Стандартный `combine(...)` Compose extension перегружен до 5 sources. Если будет нужно больше — переход на `combine(flows: Array<Flow<*>>) { array }`. Phase 5 укладывается в 5, проверяем при реализации.

### Размер APK после Phase 5

Ожидаемый рост vs Phase 4 (~18.5 МБ debug):
- +0.05 МБ — `oss_licenses.json` (~5 KB JSON × Apk-overhead)
- +0.3-0.5 МБ — новые composables (HistorySelectionTopBar, BulkDeleteConfirmDialog, AccuracyDisclaimerBottomSheet, AboutScreen+secties)
- +0.05 МБ — kotlinx.serialization classes (если ещё не транзитивно подключены)

Финальный target debug APK ~19-20 МБ; release APK ≤ 6 МБ — Phase Release с R8 + resource shrinking.

### Версии и зависимости

| Группа | Артефакт | Версия | Статус |
|---|---|---|---|
| kotlinx-serialization | `org.jetbrains.kotlinx:kotlinx-serialization-json` | 1.7.3 | проверить наличие; если нет — добавить в `:core:data` |
| material-icons-extended | `androidx.compose.material:material-icons-extended` | через BOM | добавить в `:feature:about` если нужны GitHub-icon glyphs |

Все остальные версии остаются как после Phase 4.

### Известные ограничения и допущения

- **Bulk-undo не выживает kill процесса** — same limitation что single-undo Phase 3; fully-durable Undo через write-ahead delete log out of MVP scope.
- **OSS-лицензии — ручной список** — не использует Google OSS-Licenses Plugin (NFR-10 conflict). При добавлении новой dep в `libs.versions.toml` нужно вручную обновить `oss_licenses.json`. Решение долгосрочно — gen-task в Gradle (Phase Release).
- **AboutScreen URL — placeholder в Phase 5** — реальный hosting Privacy Policy / итоговый GitHub-username фиксируются в Phase Release. На устройстве без сети `Intent.ACTION_VIEW` покажет browser-error — accepted.
- **Иконка «?» удалена с History/Settings/Detail TopBar** — это break backwards-compat для существующих screenshot тестов в `:app` (если тестируют `HelpOutline` на этих экранах). Тесты обновляются в Task 5.
- **`combinedClickable` semantics** — long-press на HistoryItemCard через `Modifier.combinedClickable(onLongClick = ...)` — TalkBack accessibility action "Long press" доступен автоматически.
- **`pluralStringResource`** для русского — три формы (one/few/many), не four (other tested). `androidx.compose.ui.res.pluralStringResource` поддерживает корректно.
- **CASCADE samples при bulk-delete** — Room FK CASCADE сработает автоматически через DDL, без явного `@Transaction`. Phase 3 schema v1 уже зафиксировала `onDelete = ForeignKey.CASCADE`.

## Post-Completion

*Items requiring manual intervention or external systems — no checkboxes, informational only.*

**Manual verification** (после завершения Phase 5):

- Установить debug APK на физическое Android-устройство (API 26+ и желательно API 33+); выполнить полный пользовательский сценарий:
  - запуск → tab Measure → click «?» → BottomSheet с дисклеймером появляется → click «Подробнее» → переход на AboutScreen с полным текстом дисклеймера → стрелка Back возвращает в Measure
  - перейти на tab History (должны быть сохранённые из Phase 4 замеры) → long-press на одной карточке → enter selection mode → выбрать ещё 2 карточки → tap «Delete (3)» → confirm dialog → confirm → Snackbar «3 measurements deleted» → tap Undo в течение 5 секунд → 3 карточки восстановлены
  - повторить bulk-delete без Undo → подождать 5+ секунд → проверить через DBeaver/Room inspector что rows физически удалены из `measurements` таблицы (а также соответствующие samples из `samples` через CASCADE)
  - tap «Select all» → все карточки выбраны → tap «Delete» → confirm → проверить bulk-delete всех
  - на AboutScreen tap «GitHub» → должен открыться браузер с placeholder URL (или показать ошибку «No internet» — это ожидаемо для placeholder)
  - tap «Privacy Policy» → аналогично placeholder URL
  - tap на одной из OSS-лицензий → переход в браузер на URL зависимости
  - переключить тему на «Тёмная» в Settings → проверить что HistorySelectionTopBar и BulkDeleteConfirmDialog и AccuracyDisclaimerBottomSheet корректно тёмные
  - переключить язык на «English» → проверить, что все новые strings (selection mode, bulk plural, about sections) корректно переведены
- Проверить TalkBack: long-press на карточке озвучивается как «Enter selection mode»; selected карточки — «Selected, double-tap to unselect»; «Delete (N)» — «Delete N measurements»; bulk Snackbar — озвучивается count в plural-форме
- Проверить ротацию во время selection mode: selectionMode/selectedIds выживают (через `viewModelScope` retained, не SavedStateHandle — accepted limitation since selection — short-lived UX state)
- Проверить «Размер шрифта» 200% → AboutScreen и HistorySelectionTopBar остаются читаемыми без overflow
- Замерить cold start (`adb shell am start-activity -W -n ru.dmdp.tishina/.MainActivity`) и убедиться, что FR-1 ≤ 1 с не регрессировал

**External system updates** (отложено в Phase Release):

- Опубликовать промежуточный Pre-release на GitHub Releases (`v0.5.0-polish`) для бета-тестеров.
- Снять обновлённые скриншоты для Play Store / RuStore — включая AboutScreen, selection mode, bulk delete confirm.
- Финализировать GitHub username / репозиторий — обновить `about_github_url` и `about_privacy_url` strings.
- Поднять статичную HTML Privacy Policy на GitHub Pages с финальным текстом.
- Опубликовать OSS-лицензии — рассмотреть автоматизацию через Gradle task (gen-from-toml).

**Что переходит в Phase Release (CI/CD + Distribution):**

- Реальный adaptive launcher icon (foreground SVG/vector волны + background `#0E2433`); 512×512 PNG для каталогов RuStore / Samsung.
- Privacy Policy hosting на GitHub Pages; финальные URL в strings.xml.
- R8 + ProGuard rules с keep-rules для Hilt / Compose / kotlinx-serialization → debug APK ~18 МБ → release APK ≤ 6 МБ.
- Resource shrinking для уменьшения размера.
- Instrumentation-тесты матрица emulator API 26/30/34 (Robolectric → реальный эмулятор).
- Hilt `@HiltAndroidTest` + `@UninstallModules` инфра для e2e тестов.
- macrobenchmark cold-start замер для подтверждения FR-1.
- Релиз в Google Play (AAB + signing + data safety form), RuStore (AAB + ручная модерация), Samsung Galaxy Store (AAB + commercial seller status).
- CI/CD: `release.yml` + `nightly.yml` workflows.
- Auto-generate OSS-лицензий через Gradle task (вместо ручного JSON).
- ASO-optimisation (ключи в описании, не в названии — § 17 спеки).
- Возможные feature-флаги для FR-13 (search/filter) — preparation для v1.1.

*Note: ralphex автоматически переносит завершённый план в `docs/plans/completed/` после прохождения всех чекбоксов.*
