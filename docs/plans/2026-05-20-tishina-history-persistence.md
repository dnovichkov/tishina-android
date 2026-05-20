# Tishina — MVP Phase 3: Persistence + History + Detail

## Overview

Phase 3 наполняет Phase 1 (foundation) и Phase 2 (audio engine + measure) реальным persistence-слоем. После завершения этой фазы у пользователя должен появиться полноценный замкнутый цикл UX: открыл → измерил → нажал Save → ввёл заметку → нашёл замер в History → открыл Detail → отредактировал заметку → удалил с возможностью Undo.

**Цель фазы:**
- `:core:data` — Room 2.8.x база `TishinaDatabase` с двумя таблицами (`MeasurementEntity` агрегаты + `SampleEntity` downsampled-точки 5 Гц), DAO с transaction-insert, mappers Entity↔Domain, `MeasurementRepositoryImpl` за интерфейсом из `:core:domain`, Hilt-биндинги.
- `:core:domain` — `Measurement`/`MeasurementSummary`/`MeasurementDetails` модели, `MeasurementRepository` интерфейс, use-cases `SaveMeasurementUseCase`/`GetMeasurementsUseCase`/`GetMeasurementByIdUseCase`/`DeleteMeasurementUseCase`/`UpdateMeasurementNoteUseCase`.
- `:feature:measure` — RAM-буфер 5 Гц в `MeasureViewModel`, диалог Save (title + note до 200 символов), активация Save FAB после первого семпла, реальный вызов `SaveMeasurementUseCase` вместо stub-Snackbar.
- `:feature:history` — полноценный `HistoryViewModel` + `HistoryScreen` (LazyColumn карточек, swipe-to-delete с Undo 5 секунд, empty state с CTA), `DetailViewModel` + `DetailScreen` (полный график, inline-edit заметки, удаление с подтверждением).
- `:app` — навигация History → Detail (type-safe routes с `measurementId`), интеграция `:core:data` в Hilt-граф.

**Стратегия хранения sample-потока (зафиксировано на планировании):** RAM → одной транзакцией на Save. `MeasureViewModel` накапливает downsampled-точки 5 Гц в обычный `MutableList<SoundSample>`. Нажатие Save → `SaveMeasurementUseCase` выполняет одну атомарную Room-транзакцию (вставка `MeasurementEntity` + N `SampleEntity` ссылающихся на её id). Reset/Pause/cancel — ничего не пишется. Это согласуется с FR-7 (Reset без сохранения = чистый стейт).

**Какие FR / NFR из спеки покрываются:**
- **FR-6** — диалог Save (имя ≤ 80 символов, заметка ≤ 200 символов, кнопки Save/Отмена). Save теперь функциональный, не stub.
- **FR-8** — LazyColumn замеров отсортированных по убыванию даты.
- **FR-9** — карточка с датой/именем/avg dB/длительностью/мини-спарклайном.
- **FR-10** — переход в Detail с полным графиком и inline-edit заметки.
- **FR-11** — свайп влево → удаление с Snackbar Undo (5 секунд).
- **FR-23** — Room на minSdk 26 (KMP-ветка Room 2.8.4 уже подключена в `libs.versions.toml` после Phase 1).
- **NFR-1** — `MeasureScreen` остаётся interactive ≤ 1 с (lazy Room init через Hilt).
- **NFR-5** — `MeasureViewModel` сохраняет буфер 5 Гц в `SavedStateHandle`? Нет — слишком большой для Parcel; вместо этого Save сохраняет финальный набор в Room, а ротация — резет буфера (это accepted compromise, аналогично решению Phase 2 с `recent`).
- **NFR-9** — аудио по-прежнему не пишется в файл; в Room сохраняются только агрегаты и 5 Гц-семплы dB(A) (не PCM).
- **NFR-11** — БД лежит во внутреннем app-storage (Room по умолчанию пишет в `databases/`).
- **NFR-12** — валидация title ≤ 80, note ≤ 200 на UI- и DAO-уровне (через `@CheckResult` + UI counter).
- **NFR-15 / NFR-16** — Material 3, контраст ≥ 4.5:1, цвет не единственный носитель информации (swipe-delete сопровождается иконкой trash, не только цветом).

**Что НЕ входит в Phase 3 (намеренно отложено):**
- **FR-12** (bulk-выбор + bulk-удаление) — out of scope MVP, добавится в v1.1.
- **FR-13** (поиск/фильтр по дате и тексту) — out of scope MVP (P1 в спеке), v1.1.
- **FR-14…FR-19** (Settings UI, калибровка, A/C/Z weighting, темы, язык) — Phase 4.
- **FR-20** (экспорт CSV через Storage Access Framework) — Phase Release (P1).
- **FR-21 / FR-22** (AboutScreen с дисклеймером и лицензиями) — Phase Release.
- **Zoom/pan жестами по полному графику в Detail** — P1 по спеке, v1.1.
- **Share Intent** (поделиться замером с PNG-снимком графика) — Phase Release.
- **Auto-калибровка** — v1.2.
- **C-weighting фильтр** — Phase 4 при добавлении Settings.
- **Instrumentation-тесты матрица API 26/30/34** — Phase Release; в Phase 3 используем Robolectric + Room in-memory.

## Context (from discovery)

**Состояние репозитория после Phase 2 (коммиты `b360285`, `97f96da`, `d0fe5d3`):**
- 11 модулей собираются; `:app:assembleDebug` зелёный.
- `:core:domain` содержит `SoundSample`, `MeasurementSnapshot`, `MeasurementConfig`, `FrequencyWeighting`, `TimeWeighting`, `SessionSeed`, интерфейс `AudioRepository`, stub-интерфейс `SettingsRepository` + `DefaultSettingsRepository`, use-cases `StartMeasurementUseCase`/`StopMeasurementUseCase`/`ResetMeasurementUseCase`. **Нет** интерфейса `MeasurementRepository` и нет use-cases сохранения/чтения замеров — это создаём в Phase 3.
- `:core:audio` содержит полный DSP-каскад + `AudioRecordPcmSource` + `AudioRepositoryImpl` (теперь с `.flowOn(Dispatchers.Default)` после `d0fe5d3`). Менять не нужно.
- `:core:data` — пустой (только `.gitkeep`); здесь и будет жить Room-код.
- `:core:testing` экспортирует `FakeAudioRepository` и `FakePcmAudioSource`. Добавим `FakeMeasurementRepository`.
- `:feature:measure/MeasureViewModel` — обрабатывает `SaveRequested` через `ShowSnackbar(R.string.measure_save_unavailable_phase2)` stub. Заменим на реальный flow `ShowSaveDialog` → `SaveMeasurementUseCase`.
- `:feature:measure/MeasureScreen` — `MeasureBottomBar.kt` имеет Save IconButton с `enabled = false`. В Phase 3 включим его когда `state.recent.isNotEmpty()`.
- `:feature:history` — `HistoryScreen` сейчас вызывает `PlaceholderScreen`. Полностью переписываем.
- `:app/navigation/TishinaDestinations.kt` — type-safe routes; добавим `Detail(measurementId: Long)`.

**Зафиксированные версии (после Phase 1 / 2):**
- Kotlin 2.0.21 + K2; AGP 8.7.3; Compose BOM 2026.05.00; Hilt 2.55.
- Room 2.8.4 (присутствует в `libs.versions.toml` с Phase 1, но ещё не использовалась).
- KSP — уже подключён через `tishina.android.hilt` convention plugin; Room тоже использует KSP-генерацию.
- JUnit 5.11.x + MockK 1.13.x + Turbine 1.2.x + Robolectric 4.13 + Roborazzi 1.30.x.

**Источники истины:**
- Спецификация `docs/specs/tishina-spec.md`:
  - § 4 — FR-6, FR-8…FR-13 (требования к Save dialog, History, Detail).
  - § 6 — UX (HistoryScreen карточки, swipe-delete с Undo, empty state с CTA; DetailScreen TopAppBar + полный график + inline-edit заметки).
  - § 7 — архитектура (Clean Architecture, MVI lite).
  - § 9 — модель данных Room (`MeasurementEntity` + `SampleEntity` с FK ON DELETE CASCADE, downsampling 5 Гц, лимит 8 часов).
  - § 13 — стратегия тестирования (Data слой ≥ 80%, `MigrationTestHelper` для миграций).
- Завершённые планы `docs/plans/completed/2026-05-19-tishina-foundation.md` и `2026-05-19-tishina-audio-engine.md` — образец TDD-формата и структуры tasks.

## Development Approach

- **Testing approach:** **TDD (tests first)** — глобальное правило проекта ([[feedback_tdd_default]]) плюс явное подтверждение на планировании. Для каждой задачи с поведением (DAO-запросы, mapper round-trip, ViewModel state-машина, диалог валидации) **тест пишется первым**, реализация — после того как тест зафиксировал контракт. Чистые setup-задачи (build.gradle.kts dependencies, Hilt-модули) валидируются успешной сборкой + наличием класса в DI-графе.
- Complete each task fully before moving to the next.
- Make small, focused changes.
- **CRITICAL: every task MUST include new/updated tests** for code changes in that task:
  - JUnit 5 unit-тесты для domain-моделей, mappers, use-cases (с `FakeMeasurementRepository` или `mockk<MeasurementRepository>`).
  - Robolectric + Room `inMemoryDatabaseBuilder` для DAO-тестов (insert/query/delete в одной транзакции, FK CASCADE, валидация constraint'ов).
  - Turbine-тесты `StateFlow` для `HistoryViewModel`, `DetailViewModel`, обновлений `MeasureViewModel`.
  - Roborazzi screenshot-тесты для `HistoryScreen` (empty state, list, swipe state), `DetailScreen`, `MeasureSaveDialog`.
  - Compose UI-тесты через `createComposeRule()` для интерактивных сценариев (swipe → Snackbar Undo → восстановление; inline-edit заметки с валидацией 200 символов).
  - `MigrationTestHelper`-инфраструктура установлена для v1, готова к v2+ в Phase 4.
  - Тесты покрывают success **и** error/edge: пустая БД, попытка сохранить с note=null vs note="" vs note=200символов vs note=201символ, удалить несуществующий id, открыть несуществующий Detail.
- **CRITICAL: all tests must pass before starting next task** — no exceptions.
- **CRITICAL: update this plan file when scope changes during implementation.**
- Run tests after each change (`./gradlew :core:data:testDebugUnitTest`, `:feature:history:testDebugUnitTest` и т. д. локально быстрее, чем полный build).
- Maintain backward compatibility: не ломаем интерфейс `MeasureScreen()` / `HistoryScreen()` / `DetailScreen()` без default параметров, чтобы навигационные тесты в `:app` (использующие stub-композблы) продолжали работать; принцип следует Phase 2 Task 11 где `measureContent: @Composable () -> Unit = { MeasureScreen() }` был добавлен в `TishinaApp`.

## Testing Strategy

### Unit tests (JUnit 5 + MockK + Turbine)

| Слой | Что тестируется | Целевое покрытие |
|---|---|---|
| `:core:domain` — модели Measurement/Summary/Details | конструкторы immutable классов, equality, copy, граничные значения (note=null vs empty vs maxLen) | ≥ 90% |
| `:core:domain` — use-cases | `SaveMeasurementUseCase` (валидация title/note длин, проброс в repository); `GetMeasurementsUseCase` (Flow проброс); `GetMeasurementByIdUseCase`; `DeleteMeasurementUseCase`; `UpdateMeasurementNoteUseCase` — через `mockk<MeasurementRepository>` плюс `FakeMeasurementRepository` для UI-тестов | ≥ 90% |
| `:core:data` — DAO | insert + samples в одной транзакции, query list (orderBy createdAt DESC, LIMIT N), query by id с join samples, delete с CASCADE, update note (только note колонка), max-length constraint enforcement | ≥ 90% |
| `:core:data` — mappers | round-trip Entity ↔ Domain (включая null-handling note, conversion to/from `Instant`/epoch millis) | ≥ 95% |
| `:core:data` — `MeasurementRepositoryImpl` | композиция DAO + mappers, ошибки SQLite пробрасываются как `Result.failure`, `Flow<List>` корректно реактирует на изменения БД | ≥ 80% |
| `:feature:measure` ViewModel расширение | RAM-буфер 5 Гц (downsampling), `ShowSaveDialog` effect, `SaveDialogConfirmed(title, note)` → repository call → `ShowSnackbar(measure_saved)`, валидация длин | ≥ 85% |
| `:feature:history` `HistoryViewModel` | начальная загрузка списка, обработка пустого состояния, удаление + undo (5 секунд буфер в VM), Flow подписка на repository | ≥ 85% |
| `:feature:history` `DetailViewModel` | загрузка по id, edit-mode заметки, обновление, удаление с навигацией назад | ≥ 85% |
| Composables UI | smoke-рендеринг + screenshot baselines + интерактивное поведение через `createComposeRule` | Roborazzi + Compose UI Test |

### Robolectric instrumentation-стиль тесты (под `src/test/`)

- `TishinaDatabaseMigrationTest` — `@RunWith(RobolectricTestRunner)` + `MigrationTestHelper`. На v1 ничего не мигрирует, но фиксирует helper-инфру: открыть БД на v1, закрыть, переоткрыть без миграций. Helper-инфра будет переиспользована в Phase 4 для v1→v2.
- `MeasurementDaoTest` (`@RunWith(RobolectricTestRunner)`) — Room.inMemoryDatabaseBuilder + полный жизненный цикл DAO. 10+ тестов: insert/transaction-rollback на падении в середине, query sort, delete CASCADE удаляет samples, update note меняет только одно поле, max-length constraint.
- `MeasurementRepositoryImplTest` — `Robolectric` + in-memory Room + реальный DAO; проверяет mapper-граф end-to-end.
- `HistoryScreenComposeUiTest` — `createComposeRule()`: swipe-to-dismiss → Snackbar появляется → клик "Отменить" → запись восстановлена.
- `DetailScreenComposeUiTest` — `createComposeRule()`: клик по заметке → TextField активен → ввод 201 символа → counter показывает 201/200 и кнопка Save заблокирована.

### Roborazzi screenshot тесты

- `HistoryEmptyStateScreenshotTest` — light + dark (2 baseline).
- `HistoryListScreenshotTest` — list из 5 карточек (разные dB, длительности, наличие/отсутствие заметок); light + dark.
- `HistorySwipeStateScreenshotTest` — частично свайпнутая карточка с открытой иконкой trash; light + dark.
- `MeasureSaveDialogScreenshotTest` — диалог пустой; диалог с введёнными title + note; диалог с note превышающим 200 (counter красный); light + dark.
- `DetailScreenScreenshotTest` — readonly-mode (заметка просто текст); edit-mode (TextField активен); пустая заметка; light + dark.
- `HistoryItemCardScreenshotTest` — отдельный композбл карточки в 3 вариантах (короткая заметка, длинная с ellipsis, без заметки); light + dark.

### Coverage thresholds

- Применяем пороги, заявленные в спеке § 13:
  - `:core:domain` ≥ 90% INSTRUCTION (включая новые модели + use-cases).
  - `:core:data` ≥ 80%.
  - ViewModels `:feature:measure` / `:feature:history` ≥ 85%.
- Kover XML-репорт публикуется как CI-артефакт; пороги **не** enforcement-фейлят PR в Phase 3 — это будет включено в Phase Release; цифры наблюдаются для контроля.

### E2E tests

- Полноценные UI flow тесты ("измерить → save → найти → открыть Detail → отредактировать → удалить → undo") — в `:app/src/test/` как Robolectric Compose UI test, используя `FakeMeasurementRepository` через Hilt test bindings (или вручную через TestNavHost). Реальный instrumentation на эмуляторе API 26/30/34 — Phase Release.

## Progress Tracking

- Mark completed items with `[x]` immediately when done.
- Add newly discovered tasks with `➕` prefix.
- Document issues/blockers with `⚠️` prefix.
- Update plan if implementation deviates from original scope.
- Keep plan in sync with actual work done.

## What Goes Where

- **Implementation Steps** (`[ ]` checkboxes): код Kotlin/Compose/Room, build.gradle.kts изменения, тесты JUnit/Robolectric/Roborazzi, прогон Gradle-команд (`testDebugUnitTest`, `verifyRoborazziDebug`, `detektAll`, `spotlessCheck`, `lintDebug`, `assembleDebug`), KSP-генерация Room.
- **Post-Completion** (no checkboxes): ручная проверка на физическом устройстве (полный жизненный цикл измерения и сохранения, поведение при ротации, корректность спарклайна в карточках), наполнение Play Store-карточки скриншотами с реальной историей, бэта-релиз `v0.3.0-history` — переедет в Phase Release.
- **Checkbox placement:** только в `### Task N:` секциях. Success criteria и Overview без чекбоксов.

## Implementation Steps

### Task 1: Domain layer — Measurement models + MeasurementRepository interface + use-cases

- [x] **сначала тест:** `MeasurementSummaryTest`, `MeasurementDetailsTest`, `NewMeasurementTest` — конструкторы immutable классов, equality, copy, граничные значения (title=null/empty/80symbols/81symbols; note=null/empty/200symbols/201symbols)
- [x] **сначала тест:** `SaveMeasurementUseCaseTest` (JUnit 5 + mockk<MeasurementRepository>): счастливый путь возвращает `Result.success(newId)`; title>80 → `Result.failure(IllegalArgumentException)`; note>200 → `Result.failure`; пустой `samples` список → `Result.failure` (бессмысленно сохранять без графика); samples с возрастающим timestampMs передаются в repository без изменений
- [x] **сначала тест:** `GetMeasurementsUseCaseTest` — Flow из repository пробрасывается напрямую; пустой список из repository превращается в пустой список на выходе (sanity)
- [x] **сначала тест:** `GetMeasurementByIdUseCaseTest` — id найден → `MeasurementDetails`; id не найден → `null`
- [x] **сначала тест:** `DeleteMeasurementUseCaseTest` — вызов с id → repository.delete(id) вызван; идемпотентность для несуществующего id (repository отвечает Unit)
- [x] **сначала тест:** `UpdateMeasurementNoteUseCaseTest` — новая заметка ≤ 200 → repository.updateNote вызван с новым значением; null заметка очищает поле; >200 → `Result.failure`
- [x] создать `core/domain/src/main/kotlin/ru/dmdp/tishina/core/domain/model/Measurement.kt`:
  - `data class MeasurementSummary(val id: Long, val createdAtEpochMs: Long, val durationMs: Long, val avgDb: Float, val minDb: Float, val maxDb: Float, val title: String?, val note: String?, val sparklinePreview: List<Float>)` — `sparklinePreview` это downsampled-до-20-точек массив avg dB для мини-графика в карточке
  - `data class MeasurementDetails(val summary: MeasurementSummary, val samples: List<SoundSample>, val weighting: FrequencyWeighting, val timeWeighting: TimeWeighting, val calibrationOffsetDb: Float, val sampleRateHz: Int)` — для DetailScreen
  - `data class NewMeasurement(val createdAtEpochMs: Long, val durationMs: Long, val avgDb: Float, val minDb: Float, val maxDb: Float, val title: String?, val note: String?, val weighting: FrequencyWeighting, val timeWeighting: TimeWeighting, val calibrationOffsetDb: Float, val sampleRateHz: Int, val samples: List<SoundSample>)` — DTO для Save
  - В companion object: `const val MAX_TITLE_LENGTH = 80`, `const val MAX_NOTE_LENGTH = 200`
- [x] создать `core/domain/.../repository/MeasurementRepository.kt`:
  - `interface MeasurementRepository`
  - `fun observeSummaries(): Flow<List<MeasurementSummary>>` — реактивный список для HistoryScreen
  - `suspend fun getById(id: Long): MeasurementDetails?`
  - `suspend fun save(measurement: NewMeasurement): Long` — возвращает сгенерированный id
  - `suspend fun delete(id: Long)` — удаление вместе с samples через FK CASCADE
  - `suspend fun updateNote(id: Long, note: String?)`
- [x] создать use-cases в `core/domain/.../usecase/`:
  - `SaveMeasurementUseCase.kt` — валидирует длины, проверяет `samples.isNotEmpty()`, вызывает `repository.save`
  - `GetMeasurementsUseCase.kt` — `operator fun invoke(): Flow<List<MeasurementSummary>> = repository.observeSummaries()`
  - `GetMeasurementByIdUseCase.kt`
  - `DeleteMeasurementUseCase.kt`
  - `UpdateMeasurementNoteUseCase.kt`
- [x] создать `core/testing/src/main/kotlin/ru/dmdp/tishina/core/testing/fakes/FakeMeasurementRepository.kt`:
  - `class FakeMeasurementRepository : MeasurementRepository`
  - in-memory `MutableMap<Long, MeasurementDetails>`, `MutableStateFlow<List<MeasurementSummary>>`, monotonic id generator
  - метод `seed(measurements: List<NewMeasurement>)` для предзаполнения тестов
- [x] реализовать модели, интерфейс, use-cases, fake — чтобы тесты позеленели
- [x] run `./gradlew :core:domain:test :core:testing:testDebugUnitTest` — must pass before next task (note: `:core:domain` — pure Kotlin JVM, task `test` без Android-варианта)

### Task 2: Room foundation — `:core:data` module + entities + DAO + database

- [x] обновить `core/data/build.gradle.kts`:
  - подключены `tishina.android.library` + `tishina.android.hilt` (KSP применяется им транзитивно) + `tishina.jvm.testing`; **отказались от `roborazzi`-alias** — в Task 2 не нужны screenshot-тесты, только Robolectric DAO-тесты, поэтому добавили `testImplementation(libs.robolectric)` + `testRuntimeOnly(libs.junit.vintage.engine)` напрямую (тот же паттерн что в `:core:audio`)
  - добавлены `implementation(libs.room.runtime/ktx)` + `ksp(libs.room.compiler)` + `testImplementation(libs.room.testing)`
  - `android.defaultConfig.javaCompileOptions.annotationProcessorOptions.arguments["room.schemaLocation"]` + `ksp { arg("room.schemaLocation", ...) }` оба добавлены (Room 2.8 KMP-ветка читает через KSP-аргумент, javaCompileOptions оставлены для совместимости)
  - `sourceSets.test.assets.srcDirs("$projectDir/schemas")` — чтобы `MigrationTestHelper`-style тесты в Robolectric могли читать exported schema
- [x] **сначала тест:** `MeasurementDaoTest` (`@RunWith(RobolectricTestRunner)`) — 13 тестов в `core/data/src/test/.../db/MeasurementDaoTest.kt`. Покрывают все запрошенные сценарии: атомарный insert+samples, descending order, Turbine flow re-emit after delete, null-safety getById, round-trip, CASCADE, updateNote only-note column, CHECK на title/note > limits, null-title/note, sparkline ≤ 20 + < 20
- [x] **сначала тест:** `TishinaDatabaseTest` — 2 теста: open + версия 1; проверка наличия `schemas/.../1.json`
- [x] **сначала тест:** `TishinaDatabaseMigrationTest` — переосмыслен с `MigrationTestHelper` на `Room.databaseBuilder` напрямую, потому что в Room 2.8.4 + Robolectric `MigrationTestHelper` падает с `IllegalArgumentException: This driver is configured to open a database named 'X' but '<absolute path>/X' was requested`. ⚠️ Известный bug несовместимости Room 2.8 KMP-driver + Robolectric. Полноценный `MigrationTestHelper` будет работать в androidTest на эмуляторе в Phase Release. В Phase 3 проверяем: (a) schema JSON v1 присутствует, (b) Room открывает on-disk БД и переоткрывает её идемпотентно
- [x] создан `core/data/src/main/kotlin/ru/dmdp/tishina/core/data/db/entity/MeasurementEntity.kt` (PK autoGenerate + все поля из спеки)
- [x] создан `core/data/.../db/entity/SampleEntity.kt` с FK CASCADE + Index
- [x] создан `core/data/.../db/dao/MeasurementDao.kt`:
  - `@Transaction insertWithSamples` (composes protected `@Insert insertMeasurement` + `@Insert insertSamples`, подставляет реальный id в samples)
  - `observeSummaries(): Flow<List<MeasurementSummaryRow>>` — slim projection без samples; отдельный POJO `MeasurementSummaryRow.kt`
  - `loadSparklinePreview(id): List<Float>` — LIMIT 20 ORDER BY tOffsetMs
  - `getDetailsById(id): MeasurementWithSamples?` — `@Relation` POJO `MeasurementWithSamples.kt`
  - `delete`, `updateNote`, плюс `countSamplesForMeasurement` (test helper для CASCADE-assertions)
- [x] создан `core/data/.../db/TishinaDatabase.kt`:
  - `@Database(entities = [...], version = 1, exportSchema = true)`
  - **➕ внеплановая подзадача:** Room 2.8 не поддерживает CHECK в `@Entity` — длины title/note контролируются через `LENGTH_GUARD_CALLBACK` (RoomDatabase.Callback), который ставит BEFORE INSERT/UPDATE триггеры с `RAISE(ABORT, 'CHECK constraint failed: ...')`. Триггеры идемпотентны (`IF NOT EXISTS`); ставятся в `onCreate` + `onOpen` чтобы существующие установки тоже подхватили
- [x] **сначала тест:** проверка `schemas/.../1.json` — `TishinaDatabaseTest.room schema for version 1 is exported and committed to the repository`
- [x] реализовано — все 17 тестов зелёные
- [x] `./gradlew :core:data:testDebugUnitTest` — 17/17 passed; `:core:data:detektAll spotlessCheck lintDebug` — 0 warnings; `:app:assembleDebug` — SUCCESSFUL

### Task 3: Data layer — MeasurementRepositoryImpl + mappers + Hilt module

- [x] **сначала тест:** `MeasurementMapperTest` — round-trip Entity↔Domain:
  - `MeasurementEntity.toSummary(sparkline)` корректно копирует все поля
  - `MeasurementEntity.toDetails(samples)` строит `MeasurementDetails`
  - `NewMeasurement.toEntity()` отбрасывает `samples` (они идут отдельной таблицей) и корректно сериализует enum'ы в строки ("A"/"Z", "FAST"/"SLOW")
  - `SoundSample.toEntity(measurementId)` устанавливает FK
  - `SampleEntity.toDomain()` восстанавливает SoundSample
  - граничный случай: `title = null` остаётся null
- [x] **сначала тест:** `MeasurementRepositoryImplTest` (Robolectric + in-memory Room + реальный mapper):
  - `save → getById` round-trip: данные идентичны (modulo автогенерация id и conversion enum'ов)
  - `observeSummaries` Turbine: подписка → save → новая эмиссия с новым summary
  - `delete` удаляет measurement и его samples через CASCADE (проверка через прямой DAO-запрос на samples count)
  - `updateNote(null)` устанавливает note=null; getById возвращает null note
  - `getById(99999)` → null
  - параллельные save через `Dispatchers.IO` не интерферируют (n=5 параллельных save → все сохранены с разными id)
- [x] **сначала тест:** `MeasurementRepositoryImplSparklineTest` — `observeSummaries` дёргает `loadSparklinePreview` для каждого summary; sparkline содержит ≤ 20 точек; если в БД меньше 20 samples — возвращает все (без padding)
- [x] создан `core/data/.../mapper/MeasurementMapper.kt`:
  - extension `MeasurementEntity.toSummary(sparkline: List<Float>): MeasurementSummary`
  - extension `MeasurementEntity.toDetails(samples: List<SoundSample>, sparkline: List<Float>): MeasurementDetails` (сигнатура расширена sparkline-параметром, чтобы repository мог переиспользовать ту же sparkline-выборку для Detail-экрана без двойного запроса)
  - extension `NewMeasurement.toEntity(): MeasurementEntity` (internal, для repository)
  - extension `SoundSample.toEntity(measurementId: Long): SampleEntity`
  - extension `SampleEntity.toDomain(): SoundSample`
  - private helpers `String.toFrequencyWeighting()` / `String.toTimeWeighting()` с defensive fallback на A/FAST для неизвестных значений (защита от ручного редактирования БД и downgrade-сценариев Phase 4+)
- [x] создан `core/data/.../repository/MeasurementRepositoryImpl.kt`:
  - `@Singleton class MeasurementRepositoryImpl @Inject constructor(private val dao: MeasurementDao, @IoDispatcher private val ioDispatcher: CoroutineDispatcher) : MeasurementRepository`
  - `observeSummaries()` — `dao.observeSummaries().map { rows -> coroutineScope { rows.map { async { row.toSummary(dao.loadSparklinePreview(row.id)) } }.awaitAll() } }.flowOn(ioDispatcher)` (выбран параллельный async per-row)
  - `getById(id)` — `dao.getDetailsById(id)?.measurement.toDetails(samples=..., sparkline=dao.loadSparklinePreview(id))`
  - `save(measurement)` — `dao.insertWithSamples(measurement.toEntity(), measurement.samples.map { it.toEntity(0L) })` под `withContext(ioDispatcher)`
  - `delete(id)` / `updateNote(id, note)` — проксирование под `withContext(ioDispatcher)`
- [x] создан `core/data/.../di/CoroutineDispatchers.kt` — qualifier `@IoDispatcher` для `:core:data` (отдельный от `:core:audio` IoDispatcher, чтобы не тянуть транзитивную зависимость на audio)
- [x] создан `core/data/.../di/DataModule.kt`:
  - `interface DataModule` (паттерн как в `AudioModule`)
  - `@Binds @Singleton fun bindMeasurementRepository(impl: MeasurementRepositoryImpl): MeasurementRepository`
  - companion `@Provides @Singleton fun provideTishinaDatabase(@ApplicationContext context: Context): TishinaDatabase` через `Room.databaseBuilder(...).addCallback(LENGTH_GUARD_CALLBACK).build()` — без `.allowMainThreadQueries()`, без `.fallbackToDestructiveMigration()`
  - `@Provides fun provideMeasurementDao(db: TishinaDatabase): MeasurementDao = db.measurementDao()`
  - `@Provides @IoDispatcher fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO` (с `@Suppress("InjectDispatcher")` как в AudioModule)
- [x] `:app/build.gradle.kts` уже подключал `implementation(projects.core.data)` после Task 2 — изменения не требуются
- [x] реализован mapper, repository, DI — все тесты позеленели
- [x] `./gradlew :core:data:testDebugUnitTest :core:data:detektAll :core:data:lintDebug :app:assembleDebug` — SUCCESSFUL; `:core:data:spotlessCheck` (с `--no-configuration-cache` из-за известного бага spotless + config cache) — SUCCESSFUL; Hilt-граф валиден (assembleDebug собирает APK без ошибок MissingBinding)

### Task 4: Measure → Save flow (RAM 5Hz buffer + SaveDialog + ViewModel wiring)

- [x] **сначала тест:** `MeasureViewModelSampleBufferTest` (5 тестов): 10 Hz → каждый второй; 20 Hz → каждый четвёртый; 5 Hz → один-к-одному; Reset очищает буфер (Save после Reset ничего не пишет); Pause замораживает + Resume продолжает накопление (sample во время Pause НЕ попадает в буфер)
- [x] **сначала тест:** `MeasureViewModelSaveFlowTest` (7 тестов): пустой буфер → `ShowSnackbar(measure_save_no_data)`; non-empty → `ShowSaveDialog`; valid confirm → useCase + `ShowSnackbar(measure_saved)` + Reset; title>80 → `measure_save_title_too_long`; note>200 → `measure_save_note_too_long`; repository throws → `measure_save_failed`; `SaveDialogDismissed` — no-op
- [x] **сначала тест:** `MeasureSaveDialogScreenshotTest` — 6 baseline (empty/filled/note_overflow × light/dark) записаны через `:feature:measure:recordRoborazziDebug`; `verifyRoborazziDebug` зелёный
- [x] **сначала тест:** `MeasureSaveDialogValidationTest` (Compose UI Test) — 6 тестов: note>200 disabled; title>80 disabled; обе на max-length enabled; confirm callback с правильными значениями; cancel callback; пустые поля → null значения
- [x] обновить `MeasureViewModel.kt`:
  - инжектится `SaveMeasurementUseCase`
  - приватный `sampleBuffer: mutableListOf<SoundSample>()` + `lastBufferedMs: Long = -BUFFER_PERIOD_MS` (5 Гц)
  - в `startCollecting().onEach` — downsample by elapsed ≥ 200ms (BUFFER_PERIOD_MS)
  - `Reset` — `sampleBuffer.clear()` + `lastBufferedMs = -BUFFER_PERIOD_MS`
  - Pause/Resume буфер сохраняется (NF-guard `phase != Running` в onEach предотвращает накопление во время Pause)
  - `handleSaveRequested()` — empty → `ShowSnackbar(measure_save_no_data)`; иначе `ShowSaveDialog`
  - `handleSaveDialogConfirmed(event)` — нормализация empty → null; валидация длин (defense-in-depth); собирает `NewMeasurement` из state + buffer; success → snackbar `measure_saved` + Reset; failure → `measure_save_failed`
  - `handleSaveDialogDismissed` — `Unit` (no-op)
- [x] добавлены `MeasureUiEvent.SaveDialogConfirmed(title, note)` + `MeasureUiEvent.SaveDialogDismissed`
- [x] добавлен `MeasureUiEffect.ShowSaveDialog`
- [x] создан `feature/measure/.../ui/MeasureSaveDialog.kt`:
  - public `MeasureSaveDialog` (Material 3 `AlertDialog`) — используется production-кодом
  - **➕ внеплановая подзадача:** internal `MeasureSaveDialogContent` (Material 3 `Surface`-вариант без sub-Window) — тесты используют его, чтобы обойти `AppNotIdleException` от M3 `AlertDialog` + `OutlinedTextField` focus animations под Robolectric. Production вариант с настоящим scrim'ом будет покрыт instrumentation-тестами в Phase Release
  - оба варианта: 2 `OutlinedTextField` (title 1-line/80 + note 2..4-line/200), counter "current / limit" с error-цветом при overflow, валидация `canSave = !overflow`
  - `rememberSaveable` для `title`/`note` (ротация)
- [x] обновлён `MeasureScreen.kt`:
  - `var showSaveDialog by remember { mutableStateOf(false) }`
  - `ShowSaveDialog` effect → `showSaveDialog = true`
  - `MeasureScreenContent` принимает `showSaveDialog` + `onSaveDialogConfirm` + `onSaveDialogDismiss` (с default-параметрами для тестов)
  - диалог рендерится при `showSaveDialog == true`
- [x] обновлён `MeasureBottomBar.kt`: добавлен параметр `saveEnabled: Boolean = false`; Save IconButton используется на основе callee-флага; `MeasureScreen` передаёт `state.recent.isNotEmpty() || state.phase == Paused`
- [x] добавлены локализационные строки (RU/EN) в `values/` и `values-ru/`:
  - `measure_save_dialog_title`, `measure_save_title_label`, `measure_save_title_placeholder`, `measure_save_note_label`, `measure_save_note_placeholder`, `measure_save_confirm`, `measure_save_cancel`
  - `measure_saved`, `measure_save_failed`, `measure_save_no_data`, `measure_save_title_too_long`, `measure_save_note_too_long`
  - обновлён `measure_save_disabled_cd` (раньше "Save (will be available in the next version)" — теперь "Save (no data yet)" / "Сохранить (пока нет данных)")
- [x] удалена старая stub-строка `measure_save_unavailable_phase2`
- [x] добавлен `@Provides` для `SaveMeasurementUseCase` в `MeasureUseCaseModule` — биндит `MeasurementRepository` из `:core:data/DataModule`
- [x] реализованы composables + ViewModel — все тесты зелёные
- [x] вместо переименования удалён `MeasureViewModelSaveStubTest` (его ассертация на stub-строку устарела); заменён более полным `MeasureViewModelSaveFlowTest` (7 кейсов, включая `ShowSaveDialog` effect и end-to-end save)
- [x] существующие тесты `MeasureViewModelStartTest`/`PauseResetTest`/`EngineErrorTest`/`SavedStateHandleTest`/`PermissionFlowTest` обновлены — добавлен новый параметр `saveMeasurement = SaveMeasurementUseCase(FakeMeasurementRepository())`
- [x] `./gradlew :feature:measure:testDebugUnitTest :feature:measure:verifyRoborazziDebug` — 101/101 passed; `:app:assembleDebug` — SUCCESSFUL

### Task 5: HistoryViewModel + HistoryScreen — list + swipe-delete с Undo

- [x] **сначала тест:** `HistoryViewModelTest` (Turbine + FakeMeasurementRepository) — 7 кейсов: empty repository → loading=false; seed 3 в descending createdAt; DeleteRequested → soft-delete + ShowUndoSnackbar; UndoConfirmed в окне 5с → восстановление + repository.delete НЕ вызван; через 5с → commitDelete; consecutive DeleteRequested → orphan commit + новый pending; DeleteRequested на несуществующий id → no-op без падений; UndoConfirmed без pending → no-op
- [x] **сначала тест:** выбран подход A (soft-delete через `softDeletedIds: MutableStateFlow<Set<Long>>` + combine-filter), стратегия зафиксирована тестами `UndoConfirmed within 5s restores...` и `timer commits delete to repository after 5 seconds`
- [x] **сначала тест:** `HistoryEmptyStateScreenshotTest` — 2 baseline (light + dark) с `AppEmptyState` + CTA `FilledTonalButton`
- [x] **сначала тест:** `HistoryListScreenshotTest` — 2 baseline (list_light, list_dark): 5 карточек с разнообразными dB, длительностями, наличием/отсутствием заметок и одной длинной (ellipsis)
- [x] **сначала тест:** `HistoryItemCardScreenshotTest` — 6 baseline (card_short_note/card_no_note/card_long_note × light/dark) на фиксированной `STABLE_CREATED_AT = 2024-10-02 UTC` для стабильности `DateUtils.getRelativeTimeSpanString`
- [x] **сначала тест:** `HistoryScreenComposeUiTest` (createComposeRule + Robolectric): empty state CTA → onNavigateToMeasure; клик по карточке → onNavigateToDetail(id); LazyColumn рендер с key-стабильностью; loading state не показывает ни list ни empty CTA. **➕ внеплановое:** физический `swipeLeft()` под Material 3 `SwipeToDismissBox` не settling под Robolectric (Animatable + AnchoredDraggable не достигают idle); поведение swipe-to-dismiss проверяется через unit-тест VM (`DeleteRequested hides item from list`) + интегральная проверка проводки на устройстве — Phase Release
- [x] создан `feature/history/.../HistoryUiState.kt` — `items + loading + pendingUndoId`
- [x] создан `feature/history/.../HistoryUiEvent.kt` — `DeleteRequested(id) + UndoConfirmed`
- [x] создан `feature/history/.../HistoryUiEffect.kt` — `ShowUndoSnackbar(messageRes, actionRes, durationMs)` + companion `UNDO_WINDOW_MS = 5_000L`
- [x] создан `feature/history/.../HistoryViewModel.kt`:
  - `@HiltViewModel` с инжектом `GetMeasurementsUseCase` + `DeleteMeasurementUseCase`
  - combine 3 flows (`getMeasurements()` × `softDeletedIds` × `pendingUndoId`) → `stateIn(WhileSubscribed(5000), initialState=loading=true)`
  - `scheduleDelete(id)` — commit orphaned soft-deletes, soft-delete новый id, эмит `ShowUndoSnackbar`, шедулит `pendingDeleteJob` с `delay(5000) → commitDelete`
  - `commitOrphanedSoftDeletes(except)` — защита от latest-write-wins: если уже есть pending — добиваем его и стартуем новый таймер только для нового id
  - `cancelPendingDelete()` — `pendingDeleteJob.cancel()` + `softDeletedIds.update { it - id }` + сброс `pendingUndoId`
- [x] создан `feature/history/.../ui/HistoryItemCard.kt` — `Card(surfaceContainerLow)` с двумя колонками (text/stats); разнесён на `CardTextColumn` + `CardStatsColumn` после фикса `LongMethod` detekt; формат даты через `DateUtils.getRelativeTimeSpanString` + FORMAT_ABBREV_ALL; ellipsis на title/note maxLines=1
- [x] **➕ внеплановое:** создан `feature/history/.../ui/SparklineChart.kt` — собственная микро-версия чарта для карточки (≤20 точек, без timestamp оси, цвет от avg dB через `levelToSplColor`). Полноценный `SplLineChart` из `:feature:measure` переедет в `:core:ui` только в Task 6 — пока что мини-чарт автономен
- [x] создан `feature/history/.../ui/HistoryEmptyState.kt` — обёртка над `AppEmptyState` с `Icons.Filled.GraphicEq` + локализованные строки + CTA на Measure tab
- [x] переписан `feature/history/.../HistoryScreen.kt`:
  - публичный `HistoryScreen(onNavigateToDetail, onNavigateToMeasure, modifier, viewModel = hiltViewModel())` с default-параметрами для обратной совместимости с тестами `:app`
  - внутренний `HistoryScreenContent(...)` для unit-тестабельности (тот же паттерн что в `MeasureScreen`)
  - `LazyColumn` с `items(key = { it.id })` обёрнутыми в `SwipeToDismissBox(enableDismissFromEndToStart = true, enableDismissFromStartToEnd = false)`; на dismiss-confirm → `onEvent(DeleteRequested(item.id))`
  - `LaunchedEffect { effects.collect { ShowUndoSnackbar → snackbarHostState.showSnackbar(...); if (ActionPerformed) onEvent(UndoConfirmed) } }`
  - empty-state path: при `!loading && items.isEmpty()` → `HistoryEmptyState`
  - loading path: Box(empty) — без транзитного спиннера ради NFR-1
- [x] добавлены локализационные строки в `feature/history/src/main/res/values/strings.xml` и `values-ru/strings.xml` — empty/CTA/undo/card content descriptions (11 строк × 2 локали)
- [x] обновлён `feature/history/build.gradle.kts` — добавлен `roborazzi`-плагин, `material-icons-extended`, `lifecycle-runtime-compose`, `testOptions.unitTests.isIncludeAndroidResources = true`; создан `src/test/resources/robolectric.properties` (sdk=33)
- [x] создан `feature/history/.../di/HistoryUseCaseModule.kt` — `@Provides` для `GetMeasurementsUseCase` + `DeleteMeasurementUseCase` в `SingletonComponent`
- [x] обновлён `app/.../navigation/TishinaNavHost.kt` — `HistoryScreen(onNavigateToDetail = { /* Task 7 */ }, onNavigateToMeasure = { navController.navigate(Measure) })`
- [x] реализованы composables, ViewModel — все тесты зелёные
- [x] `./gradlew :feature:history:testDebugUnitTest verifyRoborazziDebug` — 22/22 passed; `:feature:history:detektAll lintDebug` — clean; `:feature:history:spotlessCheck` (через `--no-configuration-cache`) — clean; `:app:assembleDebug` — SUCCESSFUL (Hilt-граф валиден, BindsModule не дублирует MeasureUseCaseModule)

### Task 6: DetailViewModel + DetailScreen — полный график + inline-edit заметки

- [ ] **сначала тест:** `DetailViewModelTest`:
  - инжект id через `SavedStateHandle` (route argument) → loadById вызван
  - id найден → `DetailUiState(details = ..., loading = false)`
  - id не найден → `DetailUiState(error = R.string.detail_not_found, loading = false)` + один effect `NavigateBack` через 2 секунды (или сразу — выберем "сразу", тест проверяет одну эмиссию `NavigateBack`)
  - `onEvent(StartEditingNote)` → `editingNote = true`
  - `onEvent(NoteChanged("new note"))` → `noteDraft = "new note"`
  - `onEvent(SaveNote)` с note ≤ 200 → `updateMeasurementNote` вызван, `editingNote = false`, состояние обновлено локально (для optimistic UI)
  - `onEvent(SaveNote)` с note > 200 → effect `ShowSnackbar(R.string.detail_note_too_long)`, не вызывает useCase
  - `onEvent(CancelEditingNote)` → `editingNote = false`, `noteDraft = details.note`
  - `onEvent(DeleteRequested)` + confirm-диалог → `deleteMeasurement(id)` вызван → effect `NavigateBack`
- [ ] **сначала тест:** `DetailScreenScreenshotTest` — 6 baseline:
  - readonly mode (заметка-текст), light + dark
  - edit-mode (TextField активен с counter), light + dark
  - пустая заметка (placeholder "Добавить заметку"), light + dark
- [ ] **сначала тест:** `DetailScreenComposeUiTest`:
  - клик по карточке заметки в readonly → переход в edit-mode (TextField появляется)
  - ввод 201 символа → counter "201/200" красный, кнопка `Save` disabled
  - клик "Сохранить" с валидным значением → `viewModel.onEvent(SaveNote)` вызван
  - клик иконки удаления → AlertDialog подтверждения; "Удалить" → `viewModel.onEvent(DeleteRequested)`; "Отмена" → диалог закрывается без действия
- [ ] создать `feature/history/.../detail/DetailUiState.kt`:
  - `data class DetailUiState(val details: MeasurementDetails? = null, val loading: Boolean = true, val editingNote: Boolean = false, val noteDraft: String = "", val error: Int? = null, val deleteConfirmVisible: Boolean = false)`
- [ ] создать `DetailUiEvent.kt`:
  - `sealed interface DetailUiEvent`
  - `data object StartEditingNote : DetailUiEvent`
  - `data class NoteChanged(val value: String) : DetailUiEvent`
  - `data object SaveNote : DetailUiEvent`
  - `data object CancelEditingNote : DetailUiEvent`
  - `data object DeleteRequested : DetailUiEvent`
  - `data object DeleteConfirmed : DetailUiEvent`
  - `data object DeleteCancelled : DetailUiEvent`
- [ ] создать `DetailUiEffect.kt`:
  - `sealed interface DetailUiEffect`
  - `data class ShowSnackbar(@StringRes val messageRes: Int) : DetailUiEffect`
  - `data object NavigateBack : DetailUiEffect`
- [ ] создать `feature/history/.../detail/DetailViewModel.kt`:
  - `@HiltViewModel class DetailViewModel @Inject constructor(savedStateHandle: SavedStateHandle, private val getMeasurementById: GetMeasurementByIdUseCase, private val updateNote: UpdateMeasurementNoteUseCase, private val deleteMeasurement: DeleteMeasurementUseCase)`
  - id извлекается из `savedStateHandle.toRoute<DetailRoute>().measurementId` (type-safe navigation)
  - в init: `viewModelScope.launch { val details = getMeasurementById(id); if (details == null) { _effects.send(ShowSnackbar(R.string.detail_not_found)); _effects.send(NavigateBack) } else { _state.update { it.copy(details = details, noteDraft = details.summary.note.orEmpty(), loading = false) } } }`
- [ ] создать `feature/history/.../detail/DetailScreen.kt`:
  - `TopAppBar` с navigation-icon back, title = `details.summary.title ?: stringResource(R.string.history_card_no_title)`, action — IconButton Delete
  - body: полный `SplLineChart` (переиспользуем из `:feature:measure/ui/`) — нужно либо переместить в `:core:ui` либо дублировать; **решение:** переместить `SplLineChart`, `SplStatsRow`, `SplReadout` (если нужно), `levelToSplColor` уже в `:core:designsystem` → нет конфликта; перемещаем `SplLineChart` + `SplStatsRow` в `:core:ui` для переиспользования (это **➕ внеплановая подзадача**)
  - `SplStatsRow(min = details.minDb, avg = details.avgDb, max = details.maxDb)`
  - Card "Метаданные": дата создания (formatted), длительность (mm:ss), weighting ("A" / "Z"), time-weighting ("Fast" / "Slow"), calibration offset
  - Card "Заметка" с inline-edit: при `editingNote=false` — `Text(noteDraft.takeIf { it.isNotBlank() } ?: stringResource(R.string.detail_add_note))` с кликом → `StartEditingNote`; при `editingNote=true` — `OutlinedTextField` + кнопки "Сохранить"/"Отмена" + counter `${draft.length}/200`
  - AlertDialog подтверждения удаления при `deleteConfirmVisible=true`
- [ ] добавить локализационные строки:
  - `detail_not_found` ("Замер не найден" / "Measurement not found")
  - `detail_note_label` ("Заметка" / "Note")
  - `detail_add_note` ("Добавить заметку" / "Add note")
  - `detail_note_too_long` ("Заметка длиннее 200 символов" / "Note longer than 200 characters")
  - `detail_delete_confirm_title` ("Удалить замер?" / "Delete measurement?")
  - `detail_delete_confirm_body` ("Действие нельзя отменить" / "This cannot be undone")
  - `detail_delete_confirm_action` ("Удалить" / "Delete")
  - `detail_delete_cancel_action` ("Отмена" / "Cancel")
  - `detail_meta_weighting` ("Взвешивание" / "Weighting")
  - `detail_meta_duration` ("Длительность" / "Duration")
  - `detail_meta_offset` ("Калибровка" / "Calibration offset")
- [ ] **➕ внеплановое:** переместить `feature/measure/.../ui/SplLineChart.kt`, `SplStatsRow.kt` в `:core:ui/components/` (или создать новый пакет `:core:ui/.../chart/`) для переиспользования между `:feature:measure` и `:feature:history.detail`. Это требует:
  - обновить импорты в `MeasureScreen.kt`
  - проверить что screenshot baselines не сломались
  - убедиться что `:feature:measure` всё ещё подключает `:core:ui` (он подключён транзитивно через `tishina.android.feature`)
- [ ] добавить в `HistoryUseCaseModule` `@Provides` для `GetMeasurementByIdUseCase`, `UpdateMeasurementNoteUseCase` (DeleteMeasurementUseCase уже добавлен в Task 5)
- [ ] реализовать composables, ViewModel — чтобы тесты позеленели
- [ ] run `./gradlew :feature:history:testDebugUnitTest :feature:measure:testDebugUnitTest verifyRoborazziDebug` — must pass before next task

### Task 7: Navigation wiring + cold start guard

- [ ] **сначала тест:** `TishinaNavHostHistoryToDetailTest` — `createComposeRule`: стартовый Measure → переход на History tab → клик по карточке → переход на Detail → клик back → возврат на History; используется тот же паттерн stub-композблов из Phase 2 (`historyContent`, `detailContent` параметры с default = реальные экраны)
- [ ] **сначала тест:** `TishinaColdStartTest` (Robolectric) — стартует Activity, измеряет время `setContent` → `LayoutNode.measured` для MeasureScreen content; ассерт что время < 600мс на Robolectric (proxy для FR-1 ≤ 1 с на реальном устройстве; точный ассерт оставляем для Phase Release с macrobenchmark на эмуляторе). Тест маркируется как ⚠️ approximation
- [ ] обновить `app/src/main/kotlin/ru/dmdp/tishina/navigation/TishinaDestinations.kt`:
  - добавить `@Serializable data class Detail(val measurementId: Long) : TishinaDestination`
  - (`Detail` — НЕ top-level destination для bottom nav-bar; ему не нужны label/icon, так что в `enum class TopLevelDestination` его НЕ добавляем; навигация в Detail происходит из History через `navController.navigate(Detail(measurementId = id))`)
- [ ] обновить `app/.../navigation/TishinaNavHost.kt`:
  - добавить `composable<Detail> { backStackEntry -> DetailScreen(onNavigateBack = { navController.popBackStack() }) }`
  - в `composable<History>` пробросить `onNavigateToDetail = { id -> navController.navigate(Detail(measurementId = id)) }` и `onNavigateToMeasure = { navController.navigate(Measure) { popUpTo(Measure) { inclusive = false } } }`
- [ ] обновить `:app/build.gradle.kts`:
  - убедиться что `implementation(projects.feature.history)` уже подключён (после Phase 1 — да)
  - НЕ нужно подключать `:core:data` напрямую в `:app` если он подключён транзитивно через `:feature:history` или `:feature:measure` — проверить и подключить если транзитивности нет
- [ ] проверить что `MeasurementDatabase` инициализируется лениво (НЕ в `TishinaApplication.onCreate`):
  - открыть `app/.../TishinaApplication.kt` — убедиться что только `@HiltAndroidApp` без явного inject `MeasurementRepository` или `TishinaDatabase`
  - если в `Application.onCreate` есть прямое обращение к БД (которого быть не должно) — удалить
- [ ] реализовать навигационные изменения — чтобы тесты позеленели
- [ ] run `./gradlew :app:testDebugUnitTest :app:assembleDebug` — must pass before next task

### Task 8: Verify acceptance criteria + final smoke + README

- [ ] verify all requirements from Overview are implemented:
  - FR-6 (Save dialog с валидацией длин) — покрыт Task 4
  - FR-8 (History list orderBy createdAt DESC) — покрыт Task 5 (`MeasurementDaoTest.observeSummaries_emitsInDescendingCreatedAtOrder` + `HistoryListScreenshotTest`)
  - FR-9 (карточка с date/title/avg/duration/sparkline) — покрыт Task 5 (`HistoryItemCardScreenshotTest`)
  - FR-10 (Detail с полным графиком + inline-edit заметки) — покрыт Task 6
  - FR-11 (swipe-delete с Snackbar Undo 5s) — покрыт Task 5 (`HistoryScreenComposeUiTest` + `HistoryViewModelTest`)
- [ ] verify edge cases handled:
  - `SaveMeasurementUseCase` с пустым `samples` → `Result.failure` (покрыт `SaveMeasurementUseCaseTest`)
  - `DeleteMeasurementUseCase` для несуществующего id → no-op (покрыт `DeleteMeasurementUseCaseTest`)
  - `GetMeasurementByIdUseCase` для несуществующего id → null + UI показывает ошибку + navigate back (покрыт `DetailViewModelTest`)
  - title > 80 / note > 200 — UI валидация + use-case валидация (покрыт `MeasureSaveDialogValidationTest` + `SaveMeasurementUseCaseTest`)
  - SwipeToDismiss + kill процесса (soft-delete теряется) — приемлемое поведение, документировано в `HistoryViewModelUndoStrategyTest`
  - удаление через CASCADE забирает все samples — покрыт `MeasurementDaoTest.delete_cascadesToSamples`
- [ ] run full test suite: `./gradlew testDebugUnitTest verifyRoborazziDebug` — 100% зелёных
- [ ] run e2e smoke (Robolectric Compose UI test в `:app`):
  - старт → tab Measure → запросить разрешение (`ShadowApplication.grantPermissions(RECORD_AUDIO)`) → FAB Start → подождать пока FakeAudioRepository эмиттит 5 семплов → FAB Save → диалог появляется → ввести title="Test" + note="" → "Сохранить" → Snackbar "Сохранено" → tab History → видна карточка "Test" → клик → DetailScreen → клик по заметке → ввести "Edited" → "Сохранить" → закрыть Detail back → клик по trash → AlertDialog подтверждения → "Удалить" → возврат на History → пустое состояние
  - этот тест требует Hilt test bindings (`@HiltAndroidTest` + `@UninstallModules(DataModule::class)` + кастомный `TestDataModule` с in-memory Room); если Hilt-testing инфра слишком тяжёлая для Phase 3 — упростить до прямого `compose-rule` теста через `TestNavHost(navController, fakeRepository)`. **Решение:** идём через `TestNavHost` без Hilt-testing — Hilt-test в Phase Release
- [ ] run linter: `./gradlew detektAll spotlessCheck lintDebug` — все warnings/errors устранены (применить `spotlessApply` если нужно автоформат)
- [ ] verify test coverage report генерируется: `./gradlew koverHtmlReportDebug koverXmlReportDebug` (плюс `:core:domain:koverHtmlReport`); зафиксировать цифры покрытия (просто наблюдение):
  - `:core:domain` — ожидаем ≥ 90% (новые модели + use-cases полностью покрыты)
  - `:core:data` — ожидаем ≥ 80% (DAO + mapper + repository)
  - `:feature:measure` — ожидаем сохранение покрытия после расширения (≥ 80%)
  - `:feature:history` — ожидаем ≥ 80%
- [ ] verify APK size: `Get-ChildItem app/build/outputs/apk/debug/*.apk | Select-Object Length` — Room + 5 use-cases + HistoryScreen + DetailScreen увеличат APK на ~3-5 МБ (Room runtime + KSP-generated DAO); реальный target ≤ 6 МБ — Phase Release с R8
- [ ] verify что `core/data/schemas/ru.dmdp.tishina.core.data.db.TishinaDatabase/1.json` присутствует в `git ls-files` и закоммичен
- [ ] verify Roborazzi screenshots: `./gradlew recordRoborazziDebug` (если есть изменения в существующих screenshot-тестах из Phase 2 после переноса `SplLineChart` в `:core:ui` — обновить baseline)
- [ ] verify FR-1 не регрессировал: `./gradlew :app:assembleDebug` → установить на эмулятор API 30+ → измерить cold start через `adb shell am start-activity -W` (manual, в Post-Completion — этот шаг non-blocking, документируется)
- [ ] обновить `README.md`:
  - изменить статус "Phase 2: Audio Engine + Measure complete" → "Phase 3: Persistence + History complete"
  - добавить раздел "Phase 3: что добавлено":
    - `:core:data` Room DB
    - History/Detail экраны с полным CRUD
    - Save dialog с валидацией длин
    - swipe-to-delete с Undo
  - обновить таблицу FR-coverage: FR-6, FR-8…FR-11 → ✅
- [ ] run финальный smoke-прогон (два invocation для обхода Kover/clean гонки из Phase 1 Task 9):
  - `./gradlew clean assembleDebug -x test`
  - `./gradlew testDebugUnitTest verifyRoborazziDebug detektAll spotlessCheck lintDebug`
- [ ] обновить `MEMORY.md` (auto-memory): добавить запись `[Архитектура persistence](project_tishina_persistence.md)` со стратегией RAM-buffer 5Гц + soft-delete + миграции готовы к v2

## Technical Details

### Структура каталогов после Phase 3 (новое относительно Phase 2)

```
tishina-android/
├── core/
│   ├── domain/
│   │   └── src/main/kotlin/ru/dmdp/tishina/core/domain/
│   │       ├── model/
│   │       │   ├── Measurement.kt           # NEW: MeasurementSummary, MeasurementDetails, NewMeasurement
│   │       │   └── (existing models)
│   │       ├── repository/
│   │       │   ├── MeasurementRepository.kt # NEW
│   │       │   └── (existing)
│   │       └── usecase/
│   │           ├── SaveMeasurementUseCase.kt              # NEW
│   │           ├── GetMeasurementsUseCase.kt              # NEW
│   │           ├── GetMeasurementByIdUseCase.kt           # NEW
│   │           ├── DeleteMeasurementUseCase.kt            # NEW
│   │           ├── UpdateMeasurementNoteUseCase.kt        # NEW
│   │           └── (existing)
│   ├── data/                                  # NEW MODULE (was empty)
│   │   ├── build.gradle.kts                   # NEW: Room + KSP + Hilt
│   │   ├── schemas/                           # NEW: Room schema export
│   │   │   └── ru.dmdp.tishina.core.data.db.TishinaDatabase/
│   │   │       └── 1.json
│   │   └── src/main/kotlin/ru/dmdp/tishina/core/data/
│   │       ├── db/
│   │       │   ├── TishinaDatabase.kt
│   │       │   ├── entity/
│   │       │   │   ├── MeasurementEntity.kt
│   │       │   │   └── SampleEntity.kt
│   │       │   └── dao/
│   │       │       └── MeasurementDao.kt
│   │       ├── mapper/
│   │       │   └── MeasurementMapper.kt
│   │       ├── repository/
│   │       │   └── MeasurementRepositoryImpl.kt
│   │       └── di/
│   │           └── DataModule.kt
│   ├── ui/                                    # MODIFIED: chart components moved here
│   │   └── src/main/kotlin/ru/dmdp/tishina/core/ui/components/
│   │       ├── chart/
│   │       │   ├── SplLineChart.kt           # MOVED from :feature:measure
│   │       │   └── SplStatsRow.kt            # MOVED from :feature:measure
│   │       └── (existing components)
│   └── testing/
│       └── src/main/kotlin/ru/dmdp/tishina/core/testing/fakes/
│           ├── FakeMeasurementRepository.kt   # NEW
│           └── (existing fakes)
├── feature/
│   ├── measure/
│   │   └── src/main/kotlin/ru/dmdp/tishina/feature/measure/
│   │       ├── MeasureViewModel.kt           # MODIFIED: RAM buffer, save flow
│   │       ├── MeasureScreen.kt              # MODIFIED: SaveDialog integration
│   │       ├── ui/
│   │       │   ├── MeasureSaveDialog.kt      # NEW
│   │       │   └── (existing components, minus SplLineChart/SplStatsRow which moved)
│   │       └── (existing UiState/UiEvent/UiEffect — added SaveDialog variants)
│   └── history/
│       └── src/main/kotlin/ru/dmdp/tishina/feature/history/
│           ├── HistoryScreen.kt              # REWRITTEN
│           ├── HistoryViewModel.kt           # NEW
│           ├── HistoryUiState.kt             # NEW
│           ├── HistoryUiEvent.kt             # NEW
│           ├── HistoryUiEffect.kt            # NEW
│           ├── ui/
│           │   ├── HistoryItemCard.kt        # NEW
│           │   └── HistoryEmptyState.kt      # NEW
│           ├── detail/
│           │   ├── DetailScreen.kt           # NEW
│           │   ├── DetailViewModel.kt        # NEW
│           │   ├── DetailUiState.kt          # NEW
│           │   ├── DetailUiEvent.kt          # NEW
│           │   └── DetailUiEffect.kt         # NEW
│           └── di/
│               └── HistoryUseCaseModule.kt   # NEW
└── app/
    └── src/main/kotlin/ru/dmdp/tishina/navigation/
        ├── TishinaDestinations.kt            # MODIFIED: + Detail route
        └── TishinaNavHost.kt                 # MODIFIED: + Detail composable
```

### Room schema (version = 1)

Полная схема описана в спеке § 9. Ключевые моменты:
- **`measurements`** — агрегаты + метаданные замера. Constraint'ы: `LENGTH(title) <= 80`, `LENGTH(note) <= 200` (через `@ColumnInfo` + проверки в DAO; CHECK constraint в SQL для гарантии).
- **`samples`** — downsampled-точки 5 Гц. `FOREIGN KEY(measurementId) REFERENCES measurements(id) ON DELETE CASCADE`. Индекс на `measurementId` для быстрого join при `getDetailsById`.
- `exportSchema = true` → `core/data/schemas/ru.dmdp.tishina.core.data.db.TishinaDatabase/1.json` коммитится в repo для миграционных тестов в Phase 4+.

### DAO transaction pattern для insert-with-samples

```kotlin
@Transaction
suspend fun insertWithSamples(measurement: MeasurementEntity, samples: List<SampleEntity>): Long {
    val id = insertMeasurement(measurement)
    val samplesWithFk = samples.map { it.copy(measurementId = id) }
    insertSamples(samplesWithFk)
    return id
}

@Insert(onConflict = OnConflictStrategy.ABORT)
protected abstract suspend fun insertMeasurement(entity: MeasurementEntity): Long

@Insert(onConflict = OnConflictStrategy.ABORT)
protected abstract suspend fun insertSamples(entities: List<SampleEntity>)
```

`@Transaction` гарантирует атомарность: либо measurement + все samples сохранены, либо ничего (rollback на ошибке).

### RAM-буфер 5 Гц в MeasureViewModel

```kotlin
private val sampleBuffer = mutableListOf<SoundSample>()
private var lastBufferedMs: Long = -200L  // -200 чтобы первый snapshot прошёл

// в collect block:
.onEach { snapshot ->
    val elapsed = snapshot.durationMs - lastBufferedMs
    if (elapsed >= 200) {  // 5 Hz = period 200ms
        sampleBuffer += SoundSample(db = snapshot.currentDb, timestampMs = snapshot.durationMs)
        lastBufferedMs = snapshot.durationMs
    }
    // ...update UI state
}
```

При Reset: `sampleBuffer.clear(); lastBufferedMs = -200L`.
При Pause: буфер не трогаем; при Resume: продолжаем добавлять (но `lastBufferedMs` сохраняется через `SessionSeed.durationOffsetMs`).

Лимит безопасности: 8 часов × 5 Гц = 144 000 семплов × 12 байт ≈ 1.7 МБ — приемлемо для RAM. Если набор превысит 144 000 — `ShowSnackbar(R.string.measure_too_long)` и отказ Save (Phase 3 не реализует — out of scope MVP, проверяется только тестом существования FR-7 reset). Однако в `SaveMeasurementUseCase` добавим soft-limit: если `samples.size > 144_000` — `Result.failure(MeasurementTooLongException)`.

### Soft-delete стратегия для Undo

```kotlin
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val getMeasurements: GetMeasurementsUseCase,
    private val deleteMeasurement: DeleteMeasurementUseCase,
) : ViewModel() {
    private val softDeletedIds = MutableStateFlow(emptySet<Long>())
    private var pendingDeleteJob: Job? = null

    val state: StateFlow<HistoryUiState> = combine(getMeasurements(), softDeletedIds) { all, deleted ->
        HistoryUiState(items = all.filter { it.id !in deleted }, loading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HistoryUiState())

    fun onEvent(event: HistoryUiEvent) = when (event) {
        is DeleteRequested -> scheduleDelete(event.id)
        UndoConfirmed -> cancelPendingDelete()
    }

    private fun scheduleDelete(id: Long) {
        softDeletedIds.update { it + id }
        _effects.trySend(ShowUndoSnackbar(R.string.history_undo_snackbar_message))
        pendingDeleteJob?.cancel()
        pendingDeleteJob = viewModelScope.launch {
            delay(5000)
            softDeletedIds.update { it - id }  // remove from soft-deleted before actual delete to avoid race
            deleteMeasurement(id)
        }
    }

    private fun cancelPendingDelete() {
        pendingDeleteJob?.cancel()
        softDeletedIds.update { emptySet() }
    }
}
```

**Note:** soft-delete теряется при kill процесса (запись возвращается после рестарта). Это документированное приемлемое поведение для MVP — fully-durable Undo требует write-ahead delete log, что overkill для Phase 3.

### Версии и зависимости

| Группа | Артефакт | Версия | Статус |
|---|---|---|---|
| Room | `androidx.room:room-runtime` | 2.8.4 | подключаем в `:core:data` |
| Room | `androidx.room:room-ktx` | 2.8.4 | подключаем в `:core:data` |
| Room | `androidx.room:room-compiler` | 2.8.4 | KSP в `:core:data` |
| Room | `androidx.room:room-testing` | 2.8.4 | `testImplementation` в `:core:data` |

Все версии уже декларированы в `libs.versions.toml` после Phase 1 (см. § 8 спецификации). Если какой-то артефакт отсутствует — добавить.

### Convention plugin & Hilt wiring

- `:core:data` подключает `tishina.android.library` + `tishina.android.hilt` + `tishina.jvm.testing` + `org.jetbrains.kotlin.plugin.ksp` (через alias).
- `DataModule` биндится в `SingletonComponent`:
  - `@Binds @Singleton fun bindMeasurementRepository(impl: MeasurementRepositoryImpl): MeasurementRepository`
  - `@Provides @Singleton fun provideTishinaDatabase(@ApplicationContext context: Context): TishinaDatabase`
  - `@Provides @Singleton fun provideMeasurementDao(db: TishinaDatabase): MeasurementDao`
- `:feature:history` `HistoryUseCaseModule`:
  - `@Provides fun provideGetMeasurementsUseCase(repository: MeasurementRepository): GetMeasurementsUseCase`
  - аналогично для остальных 4 use-cases (`GetMeasurementByIdUseCase`, `DeleteMeasurementUseCase`, `UpdateMeasurementNoteUseCase`, `SaveMeasurementUseCase` — последний нужен также `:feature:measure` MeasureUseCaseModule, но Hilt разрешает повторное `@Provides` на разные ScopedComponent'ы; решение: положить `SaveMeasurementUseCase` provide в `:core:data/DataModule.kt` чтобы избежать дублирования)

### Известные ограничения и допущения

- **Soft-delete теряется при kill процесса** — задокументировано выше.
- **Bulk-delete и search/filter отложены** — FR-12, FR-13.
- **Zoom/pan по графику в Detail отложены** — P1, v1.1.
- **Share Intent отложен** — Phase Release.
- **Migration test for v1** — placeholder; реальные миграционные сценарии появятся когда добавим колонку в Phase 4 (calibration offset кешированный, либо settings_snapshot).
- **Hilt instrumentation testing** не вводим в Phase 3 (`@HiltAndroidTest` + `@UninstallModules`) — это требует `kspAndroidTest(hilt-compiler)` и значительной инфры; вместо этого `:app` e2e-smoke использует TestNavHost + FakeMeasurementRepository напрямую. Hilt-testing — Phase Release.
- **Sparkline в карточке HistoryItemCard** — 20 точек downsample от 5Гц замера; для часового замера это 1 точка на 3 минуты, грубо но достаточно. Полное представление графика — в DetailScreen.
- **FR-1 cold start ≤ 1 c** — Room ленивая инициализация через Hilt provider должна сохранить это; реальный замер — на эмуляторе через macrobenchmark в Phase Release.

## Post-Completion

*Items requiring manual intervention or external systems — no checkboxes, informational only.*

**Manual verification** (после завершения Phase 3):

- Установить debug APK на физическое Android-устройство (API 26+, желательно API 33+); выполнить полный пользовательский сценарий: запуск → tab Measure → разрешить микрофон → 30 секунд замера → нажать Save → ввести "Спальня" + заметку "22:30 вечер" → проверить что запись появилась в History → открыть запись → проверить полный график + статистику + заметку → отредактировать заметку → проверить что изменение сохранилось → удалить через trash IconButton → подтвердить → проверить что записи больше нет.
- Проверить swipe-to-delete с Undo: после свайпа в Snackbar нажать "Отменить" в течение 5 секунд — запись возвращается; пропустить 5 секунд — запись окончательно удалена.
- Проверить rotation во время Save dialog: ввести текст → повернуть экран → текст должен сохраниться через `rememberSaveable` (это нужно явно реализовать в `MeasureSaveDialog`).
- Проверить ротацию на DetailScreen в edit-mode: текст заметки в TextField должен переживать ротацию через `rememberSaveable`.
- Проверить TalkBack: HistoryItemCard полностью озвучивается; swipe-delete объявляет результат; диалоги имеют корректные contentDescription.
- Проверить производительность с 100+ записями: history list scroll должен быть плавным (60 fps в Layout Inspector).
- Замерить cold start (`adb shell am start-activity -W -n ru.dmdp.tishina/.MainActivity`) и убедиться, что FR-1 ≤ 1 с не регрессировал (lazy Room init).

**External system updates** (отложено в Phase Release):

- Опубликовать промежуточный Pre-release на GitHub Releases (`v0.3.0-persistence`) для бета-тестеров.
- Снять скриншоты для Play Store / RuStore с реальной историей (5-7 разнообразных карточек).
- Документировать в README раздел "How to backup measurements" — пока что нет export, рекомендация делать `adb pull` файла БД для опытных пользователей.

**Что переходит в Phase 4 (Settings + DataStore):**

- `:core:data` `SettingsRepositoryImpl` через DataStore Preferences (заменит `DefaultSettingsRepository` stub из Phase 2).
- `:feature:settings` `SettingsViewModel` + `SettingsScreen` — реальный UI для калибровки (slider −20…+20 dB), выбора A/C/Z (нужно добавить C-weighting фильтр в `:core:audio`), Fast/Slow, темы, языка.
- `:core:audio` `CWeightingFilter.kt` — IEC 61672-1 C-weighting коэффициенты.
- `MeasureViewModel` подписывается на `SettingsRepository.config` и передаёт в `StartMeasurementUseCase`.
- Возможная Room migration v1 → v2: добавить колонки `calibrationOffsetSnapshot` (для исторической калибровки на момент замера) — будет триггер для использования `MigrationTestHelper`.

**Что переходит в Phase Release:**

- Реальный adaptive launcher icon (foreground SVG/vector с волной).
- Privacy Policy на GitHub Pages.
- R8 + ProGuard rules + size optimization до ≤ 6 МБ.
- Instrumentation-тесты на матрице emulator API 26/30/34 (полный e2e сценарий).
- Hilt `@HiltAndroidTest` + `@UninstallModules` инфра для интеграционных тестов.
- AboutScreen с дисклеймером и лицензиями OSS-зависимостей.
- Релиз в Google Play / RuStore / Samsung Galaxy Store.
- macrobenchmark cold-start замер для подтверждения FR-1.
