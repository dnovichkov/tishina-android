# Tishina — MVP Phase 2: Audio Engine + Measure Screen

## Overview

Phase 2 наполняет фундамент, заложенный в Phase 1 (см. `docs/plans/completed/2026-05-19-tishina-foundation.md`), реальной измерительной логикой. После завершения этой фазы у пользователя должно появиться **рабочее** приложение: открыл → запросил RECORD_AUDIO → нажал FAB Start → видит цифру dB(A) обновляющуюся в реальном времени, min/avg/max, цветной gauge, бегущий график за 60 секунд. Сохранение в историю **намеренно** отложено в Phase 3 (Room) — кнопка "Save" в Phase 2 остаётся видимой, но disabled с пояснением "История будет доступна в следующей версии".

**Цель фазы:**
- `:core:domain` — pure Kotlin модели и репозиторий-интерфейсы, use-cases для управления измерением.
- `:core:audio` — полная DSP-цепочка от `AudioRecord`-сырья до `Flow<SoundSample>` с dB(A): DC-block → A-weighting IIR (IEC 61672-1) → RMS sliding window → SPL формула. Источник звука инкапсулирован за интерфейсом `PcmAudioSource`, что позволяет покрыть `AudioRepositoryImpl` unit-тестами без эмулятора.
- `:feature:measure` — `MeasureViewModel` (MVI lite — `UiState` / `UiEvent` / `UiEffect`) и `MeasureScreen` с большим live-readout, дугообразным gauge, графиком dB(t), карточками Min/Avg/Max, рационале-диалогом разрешения и Bottom Action Bar.

**Какие FR / NFR из спеки покрываются:**
- **FR-3 / FR-4 / FR-5 / FR-7** — Start / отображение всех метрик / Pause / Reset.
- **FR-2** — запрос RECORD_AUDIO при первом нажатии Start (rationale-диалог + переход в системные настройки при permanent denial).
- **FR-6** — UI-каркас FAB Save присутствует, но `enabled = false` со Snackbar "History coming in next phase" (полная реализация — Phase 3).
- **FR-15 / FR-16** — A-weighting + Fast (125 мс) реализуются как умолчания; Z-weighting реализуется как внутренний bypass-фильтр (на UI не выводится — Settings придут в Phase 4); Slow (1 с) **реализуется** одновременно с Fast, но переключение остаётся на Phase 4.
- **NFR-2** — обновление UI 10 Гц, ≤ 5% CPU.
- **NFR-5 / NFR-6** — корректное освобождение `AudioRecord` при потере фокуса / SavedStateHandle.
- **NFR-8 / NFR-9 / NFR-10** — запрашивается только RECORD_AUDIO; аудио НЕ пишется в файл; никаких сторонних SDK.
- **NFR-13 / NFR-14 / NFR-15 / NFR-16** — contentDescription, font scaling, контрастные цвета, цвет не единственный носитель.

**Что НЕ входит в Phase 2 (намеренно):**
- Room persistence измерений — Phase 3.
- DataStore + SettingsRepositoryImpl + SettingsScreen (UI выбора A/C/Z, Fast/Slow, calibration slider) — Phase 4.
- Экран Детали измерения с zoom-pan по графику — Phase 3.
- Экспорт CSV / PNG / Share Intent — Phase Release (P1).
- C-weighting фильтр (PMC4331191 коэффициенты) — Phase 4 при добавлении Settings.
- Реальная иконка приложения, Privacy Policy на GitHub Pages, скриншоты для сторов — Phase Release.
- Auto-калибровка по эталонному устройству — v1.2 (out of MVP).
- Foreground Service для измерения с экраном выключения — не требуется FR; вне scope MVP.
- Instrumentation-тесты на матрице emulator API 26/30/34 — добавятся в Phase Release; в Phase 2 — Robolectric + smoke на эмуляторе локально.

## Context (from discovery)

**Состояние репозитория после Phase 1 (коммиты `5d41775` и предыдущие):**
- 11 модулей объявлены и компилируются (`:app`, `:core:designsystem`, `:core:ui`, `:core:domain`, `:core:data`, `:core:audio`, `:core:testing`, `:feature:measure`, `:feature:history`, `:feature:settings`, `:feature:about`). Модули `:core:domain`, `:core:audio`, `:core:data` содержат только `.gitkeep` — это места для кода Phase 2 / Phase 3.
- `:core:audio/build.gradle.kts` уже подключает `implementation(projects.core.domain)` и `libs.kotlinx.coroutines.android` — структурно готов; в Phase 2 потребуется добавить тестовые зависимости.
- `:feature:measure/build.gradle.kts` использует `tishina.android.feature` convention plugin — автоматически получает Compose, Hilt, designsystem, ui, domain, testing. Зависимость на `:core:audio` нужно добавить вручную.
- `MeasureScreen.kt` — placeholder через `PlaceholderScreen(R.string.measure_title, R.string.measure_placeholder)`. Будет переписан под реальный UI.
- `:core:testing` экспортирует `PreviewSheet`, `RoborazziTestRule`, `MainDispatcherRule`, JUnit 5 + Robolectric + Roborazzi — готов к Phase 2 тестам.
- `levelToSplColor` в `:core:designsystem` уже маппит dB → Color по таблице § 6 спецификации — готов к интеграции в gauge / график.

**Зафиксированные версии (`libs.versions.toml`):**
- Kotlin 2.0.21 + K2; AGP 8.7.3; Compose BOM 2026.05.00; Hilt 2.55; coroutines 1.9.0; serialization 1.7.3.
- JUnit 5.11.3 + MockK 1.13.13 + Turbine 1.2.0 + Robolectric 4.13 + Roborazzi 1.30.1.
- Robolectric pin на `sdk=33` через `robolectric.properties` (Robolectric 4.13 ещё не содержит system-image для compileSdk 35).

**Источники истины:**
- Спецификация: `docs/specs/tishina-spec.md` (888 строк), особенно §§ 4 (FR-1…FR-22), 5 (NFR-1…NFR-22), 6 (UX), 7 (архитектура), 10 (AudioRecord), 11 (калибровка + A-weighting), 13 (стратегия тестирования).
- IEC 61672-1 → коэффициенты A-weighting через Brian Hawkins, PMC4331191 (Design of digital filters for frequency weightings). Реализуем bi-quad cascade с поясами 20.598997 / 107.65265 / 737.86223 / 12194.217 Гц.
- AOSP CDD § 7.8.3 — sample rate 48 kHz обязателен без band-pass; fallback на 44.1 kHz.

## Development Approach

- **Testing approach:** **TDD (tests first)** — пользователь явно подтвердил, плюс это глобальное правило проекта ([[feedback_tdd_default]]). Для каждой задачи с поведением (DSP-фильтр, RMS-вычисление, SPL-формула, ViewModel-стейт-машина, UI-композбл с условиями) **тест пишется первым**, реализация — после того как тест зафиксировал контракт. Для чистых setup-задач (build.gradle.kts dependencies, Hilt-модули) тест = успешная компиляция плюс наличие класса в DI-графе.
- Complete each task fully before moving to the next.
- Make small, focused changes.
- **CRITICAL: every task MUST include new/updated tests** for code changes in that task:
  - JUnit 5 unit-тесты с параметризацией (`@ParameterizedTest @CsvSource`) для математических функций (RMS, A-weighting response, SPL formula, levelToSplColor edge cases).
  - Turbine-тесты `StateFlow` / `Flow<SoundSample>` для use-cases и `MeasureViewModel`.
  - Roborazzi screenshot-тесты для `MeasureScreen` в light/dark + idle/running состояниях.
  - Compose UI-тесты через `createComposeRule()` для интерактивных сценариев (FAB Start → permission dialog → активный замер).
  - Robolectric smoke-тест для `AudioRecordPcmSource` (через ShadowAudioRecord), проверяющий fallback UNPROCESSED → VOICE_RECOGNITION → MIC.
  - Тесты покрывают success **и** error/edge: clipping (PCM=`Short.MAX_VALUE`), тишина (`rms = 0` → `−∞` dB → safe clamp), нелегальные параметры (sampleRate=0).
- **CRITICAL: all tests must pass before starting next task** — no exceptions.
- **CRITICAL: update this plan file when scope changes during implementation.**
- Run tests after each change (`./gradlew :core:audio:testDebugUnitTest` и т. д., локально быстрее чем полный build).
- Maintain backward compatibility — не трогаем `:app`, `:core:designsystem`, `:core:ui` интерфейсы кроме `MeasureScreen` сигнатуры (которая всё ещё через `MeasureScreen()` без параметров — навигационные коллбеки уже подведены в Phase 1).

## Testing Strategy

### Unit tests (JUnit 5 + MockK + Turbine)

| Слой | Что тестируется | Целевое покрытие |
|---|---|---|
| `:core:domain` — модели/use-cases | конструкторы immutable классов, аккумулятор Min/Avg/Max в use-case через FakeAudioRepository | ≥ 90% |
| `:core:audio` — DSP | `RingBuffer` (boundary, wrap-around), `DcBlockFilter` (DC-стабильность), `RmsCalculator` (известная синусоида → точность < 0.01 dB), `AWeightingFilter` (white-noise → spectral response ±0.3 dB на 31.5/125/1000/8000/16000 Гц), `SplCalculator` (sweep по амплитуде + clamp на `rms=0`) | ≥ 95% |
| `:core:audio` — pipeline | `AudioRepositoryImpl` композирует фильтры; `FakePcmAudioSource` эмиттит синтетику; Turbine ловит `Flow<SoundSample>` | ≥ 85% |
| `:feature:measure` ViewModel | State-машина: idle → permissionRequested → running → paused → reset; min/avg/max аккумулятор; Time-weighted moving average для UI 10 Гц | ≥ 85% |
| `:feature:measure` Composables | UI-композблы — smoke рендеринг + поведение FAB + диалоги | Compose UI-test, Roborazzi |

### Roborazzi screenshot тесты

- `MeasureScreenIdleScreenshotTest` — состояние до старта, light + dark.
- `MeasureScreenRunningScreenshotTest` — состояние во время измерения (фейковое значение 65 dB, история заполнена), light + dark.
- `MeasureScreenPermissionDeniedScreenshotTest` — состояние когда пользователь отказал, light + dark.
- `SplGaugeScreenshotTest` — отдельный композбл gauge на четырёх уровнях (30, 60, 85, 110 dB) × 2 темы.
- `SplLineChartScreenshotTest` — отдельный композбл графика на одном сценарии (синусоидальная волна).

### Robolectric instrumentation-стиль тесты (под `src/test/`)

- `AudioRecordPcmSourceShadowTest` — `@RunWith(RobolectricTestRunner)` + ShadowAudioRecord: проверка fallback `UNPROCESSED` → `VOICE_RECOGNITION` → `MIC`; корректное освобождение в `stop()`.
- `MeasureScreenComposeUiTest` — `createComposeRule()`: нажатие FAB → проверка рационале-диалога; tap "Allow" (через `composeTestRule.onNodeWithTag("permission_dialog_allow").performClick()`) симулирует выдачу разрешения через `ShadowApplication.grantPermissions`.

### Coverage thresholds

- Применяем пороги, заявленные в спеке § 13:
  - `:core:domain` ≥ 90%.
  - `:core:audio` ≥ 95% (математически детерминированный код).
  - `:feature:measure` ViewModel ≥ 85%.
- Kover XML-репорт публикуется как CI-артефакт; пороги **не** enforcement-фейлят PR в Phase 2 — это будет включено в Phase Release; цифры наблюдаются для контроля.

### E2E tests

- Полноценные UI-flow тесты появятся в Phase 3 (когда добавится History и можно делать end-to-end "измерить → сохранить → найти в истории"). В Phase 2 ограничиваемся Compose UI-test для одного flow (запросить разрешение → запустить → пауза → reset).

## Progress Tracking

- Mark completed items with `[x]` immediately when done.
- Add newly discovered tasks with `➕` prefix.
- Document issues/blockers with `⚠️` prefix.
- Update plan if implementation deviates from original scope.
- Keep plan in sync with actual work done.

## What Goes Where

- **Implementation Steps** (`[ ]` checkboxes): код Kotlin/Compose, build.gradle.kts изменения, тесты JUnit/Robolectric/Roborazzi, prog Gradle-команд (assemble/test/lint).
- **Post-Completion** (no checkboxes): ручная проверка на физическом устройстве (звуковая верификация против эталонного шумомера, поведение `AudioFocus` на входящем звонке), наполнение Play Store-карточки скриншотами, скриншоты для RuStore — переедет в Phase Release.
- **Checkbox placement:** только в `### Task N:` секциях. Success criteria и Overview без чекбоксов.

## Implementation Steps

### Task 1: Domain models — `:core:domain`

- [x] создать `core/domain/src/main/kotlin/ru/dmdp/tishina/core/domain/model/SoundSample.kt` — `data class SoundSample(val db: Float, val timestampMs: Long)` (immutable; `timestampMs` относительно начала измерения, не epoch — это упрощает тесты)
- [x] создать `MeasurementSnapshot.kt` — `data class MeasurementSnapshot(val currentDb: Float, val minDb: Float, val maxDb: Float, val avgDb: Float, val durationMs: Long, val recent: PersistentList<SoundSample>)` (`recent` — последние 60 секунд для графика; используем стандартный `kotlin.collections.List` вместо kotlinx-collections-immutable чтобы не тянуть зависимость в pure Kotlin модуль)
- [x] создать `FrequencyWeighting.kt` — `enum class FrequencyWeighting { A, Z }` (C добавим в Phase 4)
- [x] создать `TimeWeighting.kt` — `enum class TimeWeighting(val tauMs: Int) { FAST(125), SLOW(1000) }`
- [x] создать `MeasurementConfig.kt` — `data class MeasurementConfig(val frequencyWeighting: FrequencyWeighting = FrequencyWeighting.A, val timeWeighting: TimeWeighting = TimeWeighting.FAST, val calibrationOffsetDb: Float = 0.0f)` (defaults используются Phase 2; в Phase 4 берутся из `SettingsRepository`)
- [x] **сначала тест:** `SoundSampleTest`, `MeasurementSnapshotTest` — конструкторы, equality, copy, граничные значения `Float.NaN`, `Float.NEGATIVE_INFINITY`
- [x] **сначала тест:** `MeasurementConfigTest` — defaults, copy с одним изменённым полем
- [x] реализовать модели чтобы тесты позеленели
- [x] run `./gradlew :core:domain:test` — must pass before next task (note: `:core:domain` is a pure Kotlin JVM module, so the task is `test`, not `testDebugUnitTest`)

### Task 2: Domain repository interfaces + use-cases

- [x] создать `core/domain/src/main/kotlin/ru/dmdp/tishina/core/domain/repository/AudioRepository.kt` — `interface AudioRepository { fun samples(config: MeasurementConfig): Flow<SoundSample>; suspend fun isAvailable(): Boolean }` (`isAvailable` — для проверки наличия микрофона при cold start)
- [x] создать `repository/SettingsRepository.kt` — `interface SettingsRepository { val config: Flow<MeasurementConfig>; suspend fun updateCalibrationOffset(db: Float); suspend fun updateFrequencyWeighting(w: FrequencyWeighting); suspend fun updateTimeWeighting(t: TimeWeighting) }` (полная реализация — Phase 4; в Phase 2 создаём только интерфейс плюс `DefaultSettingsRepository` который возвращает `flowOf(MeasurementConfig())` и no-op для setters)
- [x] создать `usecase/StartMeasurementUseCase.kt` — оператор `operator fun invoke(config: MeasurementConfig): Flow<MeasurementSnapshot>` композирует поток `SoundSample` из `AudioRepository.samples`, считает накапливаемые min/avg/max и `recent`-окно через `runningFold`
- [x] создать `usecase/StopMeasurementUseCase.kt` — задаёт сигнал останова через `MutableStateFlow<Boolean>` (передаётся как `cancel()` в `Flow`-обвес) ИЛИ через простой `cancel` корутины из caller-стороны (выбираем второй вариант — проще, состояние держит ViewModel)
- [x] создать `usecase/ResetMeasurementUseCase.kt` — возвращает пустой `MeasurementSnapshot.empty` для сброса аккумулятора
- [x] создать `fakes/FakeAudioRepository.kt` в `:core:testing/src/main/kotlin/ru/dmdp/tishina/core/testing/fakes/` — `class FakeAudioRepository : AudioRepository` с возможностью `emit(sample: SoundSample)` и `setAvailable(value: Boolean)`; используется в use-case и ViewModel-тестах
- [x] **сначала тест:** `StartMeasurementUseCaseTest` (JUnit 5 + Turbine): подаём последовательность `[40, 60, 80, 60, 40]` dB, проверяем что snapshot накапливает `min=40, max=80, avg=56, current=40` (последнее значение); проверяем что `recent` обрезается окном 60 секунд (используем `mockk<AudioRepository>` + `flowOf` вместо `FakeAudioRepository`, потому что `:core:domain` — pure Kotlin и не может зависеть от Android-модуля `:core:testing`; `FakeAudioRepository` остаётся для Android-фича-модулей)
- [x] **сначала тест:** `StartMeasurementUseCaseEmptyTest` — пустой Flow → snapshot не эмиттится / NaN safety (объединён в `StartMeasurementUseCaseTest.empty input flow does not emit a snapshot`)
- [x] реализовать use-cases чтобы тесты позеленели
- [x] run `./gradlew :core:domain:test :core:testing:testDebugUnitTest` — must pass before next task (примечание: для `:core:domain` (pure Kotlin) корректная задача `test`, не `testDebugUnitTest`)

### Task 3: DSP foundation — RingBuffer + DcBlockFilter

- [ ] добавить в `core/audio/build.gradle.kts` `testImplementation(projects.core.testing)` (получаем JUnit 5 / MockK / Turbine / Robolectric транзитивно)
- [ ] создать `core/audio/src/main/kotlin/ru/dmdp/tishina/core/audio/dsp/RingBuffer.kt` — `class RingBuffer(capacity: Int)` с примитивами `FloatArray` (не `Array<Float>` — избегаем boxing на горячем пути), `fun add(value: Float)`, `fun snapshot(): FloatArray` (возвращает копию в хронологическом порядке), `fun mean(): Float`, `fun sumOfSquares(): Float` (через цикл Кэхэна для устойчивости накопления)
- [ ] создать `dsp/DcBlockFilter.kt` — `class DcBlockFilter(private val pole: Float = 0.995f)` (1-й порядок HPF: `y[n] = x[n] − x[n−1] + pole·y[n−1]`), метод `fun process(samples: FloatArray, into: FloatArray = samples)` (in-place по умолчанию для экономии аллокаций), метод `fun reset()`
- [ ] **сначала тест:** `RingBufferTest` (`@ParameterizedTest @CsvSource`): емкость 4, добавляем [1, 2, 3] → snapshot == [1, 2, 3]; добавляем [1, 2, 3, 4, 5] → snapshot == [2, 3, 4, 5] (wrap-around); `mean()` после [10, 20, 30, 40] = 25.0f; `mean()` для пустого буфера = 0.0f; `sumOfSquares()` для [3, 4] = 25.0f
- [ ] **сначала тест:** `DcBlockFilterTest` — константный DC-сигнал `[1.0f, 1.0f, 1.0f, ...]` через фильтр → выход экспоненциально стремится к 0 (после 1000 семплов |y| < 0.01); синусоида 1 кГц на 48 кГц через фильтр практически без искажений (RMS сохраняется в пределах 1%)
- [ ] **сначала тест:** `DcBlockFilterResetTest` — `reset()` восстанавливает начальное состояние, два прогона дают одинаковый результат
- [ ] реализовать классы чтобы тесты позеленели
- [ ] run `./gradlew :core:audio:testDebugUnitTest` — must pass before next task

### Task 4: RMS calculator + Time-weighted (Fast/Slow)

- [ ] создать `core/audio/.../dsp/RmsCalculator.kt` — `class RmsCalculator(private val windowSize: Int)` с внутренним `RingBuffer`, метод `fun update(sample: Float): Float` (добавляет в буфер и возвращает текущий sqrt(sumOfSquares/N)), метод `fun reset()`
- [ ] создать `dsp/TimeWeightedRms.kt` — экспоненциально-взвешенное скользящее среднее `class TimeWeightedRms(sampleRateHz: Int, tauMs: Int)`: `alpha = exp(-1.0 / (sampleRateHz * tauMs / 1000.0))`, `fun update(sample: Float): Float` обновляет внутреннюю переменную `state` как `state = alpha·state + (1−alpha)·sample² ; sqrt(state)`; метод `fun reset()`
- [ ] **сначала тест:** `RmsCalculatorTest` (`@ParameterizedTest`):
  - синусоида амплитудой `A=0.5`, 1 кГц, 48 кГц sample rate, window=6000 (125 ms Fast) → RMS = `A/√2 = 0.3535` ± 0.001 (после стабилизации окна)
  - синусоида `A=1.0` → RMS = `0.7071` ± 0.001
  - константный 0 → RMS = 0
  - пустое окно → RMS = 0 (защита от sqrt отрицательного)
- [ ] **сначала тест:** `TimeWeightedRmsTest`:
  - параметризовано для Fast (125 мс) и Slow (1000 мс): step-функция от 0 к синусоиде амплитуды 1, проверка что после `5·tau` значение достигает 99% asymptote
  - constant signal → RMS стабилизируется на правильном значении
- [ ] реализовать классы чтобы тесты позеленели
- [ ] run `./gradlew :core:audio:testDebugUnitTest` — must pass before next task

### Task 5: A-weighting + Z-weighting IIR filters (IEC 61672-1)

- [ ] создать `core/audio/.../dsp/FrequencyFilter.kt` — `interface FrequencyFilter { fun process(samples: FloatArray, into: FloatArray = samples); fun reset() }`
- [ ] создать `dsp/BiquadFilter.kt` — `class BiquadFilter(val b0: Float, val b1: Float, val b2: Float, val a1: Float, val a2: Float)` (нормализованные коэффициенты, `a0 = 1`), вычисление Direct Form II Transposed: `y[n] = b0·x[n] + d1; d1 = b1·x[n] − a1·y[n] + d2; d2 = b2·x[n] − a2·y[n]`; метод `fun reset()` обнуляет `d1`, `d2`
- [ ] создать `dsp/AWeightingFilter.kt` — `class AWeightingFilter(sampleRateHz: Int) : FrequencyFilter` — каскад **четырёх** биквад-секций (два HPF на 20.598997 Гц, один HPF на 107.65265 Гц, один HPF на 737.86223 Гц + LPF на 12194.217 Гц с поправочным усилением `+2.0` dB на 1 кГц по IEC 61672-1). Коэффициенты вычисляются через bilinear transform от continuous-time передаточной функции; реализуем функцию `aWeightingCoefficients(sampleRateHz: Int): List<BiquadFilter>` для 48 kHz и 44.1 kHz отдельными случаями (предвычисленные константы), с защитой через `require(sampleRateHz == 48_000 || sampleRateHz == 44_100)`
- [ ] создать `dsp/ZWeightingFilter.kt` — `class ZWeightingFilter : FrequencyFilter` — pass-through (`process` копирует `samples → into` или ничего не делает при in-place); используется как baseline для тестирования RMS без влияния A-weighting
- [ ] **сначала тест:** `BiquadFilterTest` — unity filter (`b0=1, b1=b2=a1=a2=0`) пропускает сигнал без искажений; первого порядка HPF (через биквад) гасит DC, пропускает 1 кГц
- [ ] **сначала тест:** `AWeightingFilterSpectralTest` — генерируем синусоиды на референсных частотах **31.5 / 125 / 1000 / 8000 / 16000 Гц** на 48 кГц sample rate с известной амплитудой; пропускаем через `AWeightingFilter`, вычисляем RMS на выходе, конвертируем в dB; ожидаемые значения A-weighting response (из таблицы IEC 61672-1): −39.4 / −16.1 / 0.0 / −1.1 / −6.6 dB; допуск **±0.3 dB** на каждую точку
- [ ] **сначала тест:** `AWeightingFilter44100Test` — повтор для 44.1 кГц с теми же допусками
- [ ] **сначала тест:** `AWeightingFilterResetTest` — после `reset()` два прогона одинаковых входов дают одинаковые выходы
- [ ] **сначала тест:** `AWeightingFilterIllegalSampleRateTest` — конструктор бросает `IllegalArgumentException` для неподдерживаемых частот (e.g. 22 050)
- [ ] **сначала тест:** `ZWeightingFilterTest` — pass-through: вход равен выходу bit-for-bit
- [ ] реализовать фильтры; коэффициенты выводим через bilinear transform (формулы из Brian Hawkins PMC4331191), результат фиксируем как `private val` константы в companion object — тесты проверяют спектральный отклик, не сами коэффициенты
- [ ] run `./gradlew :core:audio:testDebugUnitTest` — must pass before next task

> Примечание: A-weighting реализуется собственным DSP-кодом без сторонних библиотек (см. § 8 спеки — "Реализуется собственным DSP-модулем"). Это сознательно, чтобы оставаться в режиме "никаких сторонних аудио-SDK".

### Task 6: SPL calculator + AudioProcessor pipeline

- [ ] создать `core/audio/.../dsp/SplCalculator.kt` — `class SplCalculator(referenceRms: Float = DEFAULT_REFERENCE_RMS)` — формула `dB = 20 · log10(max(rms, MIN_RMS) / referenceRms) + calibrationOffset`, где `MIN_RMS = 1e-9f` для защиты от `log10(0) = −Infinity` (clamp в нижний предел ≈ −180 dB); метод `fun toDb(rms: Float, calibrationOffsetDb: Float): Float`; константа `DEFAULT_REFERENCE_RMS = 2500f / 32768f` (по AOSP CDD: "Close-talk config: 90 dB SPL reads RMS of 2500 (16 bit samples)" — § 11 спеки)
- [ ] создать `dsp/AudioProcessor.kt` — pipeline-композитор: `class AudioProcessor(private val dcBlock: DcBlockFilter, private val weighting: FrequencyFilter, private val timeWeightedRms: TimeWeightedRms, private val spl: SplCalculator)`; метод `fun process(pcm: ShortArray, offsetDb: Float): Float` (нормализует short в Float, применяет фильтры in-place в shared scratch-буфере, возвращает текущий dB); метод `fun reset()` вызывает `reset` для каждого компонента
- [ ] создать `dsp/AudioProcessorFactory.kt` — `class AudioProcessorFactory @Inject constructor()` с методом `fun create(sampleRateHz: Int, config: MeasurementConfig): AudioProcessor` — инстанциирует Dc/A-or-Z/TimeWeighted/Spl по `config.frequencyWeighting` и `config.timeWeighting`
- [ ] **сначала тест:** `SplCalculatorTest` (`@ParameterizedTest @CsvSource`):
  - `referenceRms=0.0763f` (~2500/32768), `rms=0.0763f`, `offset=0` → 90.0 dB
  - `rms=0.00763f` (10× меньше) → 70.0 dB
  - `rms=0.763f` → 110.0 dB
  - `rms=0.0f` → clamp в `MIN_RMS`, dB ≈ −180 (не -∞)
  - `offset=+5` поверх 90 dB-входа → 95.0 dB
- [ ] **сначала тест:** `AudioProcessorIntegrationTest` — генерируем синусоиду 1 кГц амплитудой соответствующей 90 dB SPL (через `referenceRms × 32768`), пропускаем через полный pipeline (DC + A-weighting + TimeWeighted + SPL), ожидаем выход 90 dB ± 0.5 dB; повторяем для Z-weighting (та же амплитуда → 90 dB, без 0 dB поправки A-кривой на 1 кГц)
- [ ] **сначала тест:** `AudioProcessorResetTest` — после `reset()` два идентичных прогона дают идентичный результат
- [ ] реализовать классы чтобы тесты позеленели
- [ ] run `./gradlew :core:audio:testDebugUnitTest` — must pass before next task

### Task 7: PcmAudioSource interface + AudioRecordPcmSource

- [ ] создать `core/audio/.../source/PcmAudioSource.kt` — `interface PcmAudioSource { val sampleRateHz: Int; fun samples(): Flow<ShortArray>; suspend fun isUnprocessedSupported(): Boolean }` (Flow закрывается при отмене корутины; внутри Flow вызывает `AudioRecord.start()` в `flow { ... }.onCompletion { record.stop(); record.release() }`)
- [ ] создать `source/AudioRecordPcmSource.kt` — `class AudioRecordPcmSource @Inject constructor(@ApplicationContext private val context: Context) : PcmAudioSource` — конфигурация по § 10 спеки: `sampleRateHz = 48_000` (с fallback на 44.1 кГц через try-catch на `AudioRecord.STATE_INITIALIZED`); `AudioSource.UNPROCESSED` (API 24+) с fallback на `VOICE_RECOGNITION` далее на `MIC`; bufferSize `max(getMinBufferSize, sampleRate/10 * 2)` (≥ 100 ms); read через `AudioRecord.read(ShortArray, 0, len)`; в `flow { ... }` цикл `while (currentCoroutineContext().isActive)`
- [ ] создать `source/AudioRecordCheck.kt` — utility `fun isUnprocessedSupported(context: Context): Boolean` через `AudioManager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"`
- [ ] добавить в `AndroidManifest.xml` `:core:audio` `<uses-permission android:name="android.permission.RECORD_AUDIO" tools:node="merge"/>` (permission уже объявлено в `:app`-манифесте, но добавляем в audio-модуль для лучшей discoverability)
- [ ] **сначала тест:** `AudioRecordPcmSourceShadowTest` (`@RunWith(RobolectricTestRunner)`):
  - проверка: при `Build.VERSION_CODES.N+` и `getProperty(...) == "true"` создаётся `AudioRecord` с `AudioSource.UNPROCESSED`
  - fallback: при `getProperty(...) == "false"` создаётся с `VOICE_RECOGNITION`
  - fallback второго уровня: симулируется `IllegalArgumentException` в конструкторе `AudioRecord` для `VOICE_RECOGNITION` → создаётся с `MIC`
  - sample rate fallback: при `getMinBufferSize(48_000, ..) == AudioRecord.ERROR_BAD_VALUE` (имитируем через ShadowAudioRecord) → переключение на 44 100 Гц
  - смоук: запустить `samples().take(1).toList(...)` под `runTest` и убедиться что Flow эмиттит хотя бы один `ShortArray` (Robolectric ShadowAudioRecord возвращает синтетический буфер)
  - lifecycle: после `cancel()` корутины вызывается `record.stop()` + `record.release()` (verify через ShadowAudioRecord state-инспекцию)
- [ ] **сначала тест:** `AudioRecordCheckTest` — Robolectric ShadowAudioManager `setProperty(PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED, "true")` → функция возвращает `true`
- [ ] реализовать классы чтобы тесты позеленели
- [ ] добавить Hilt-модуль `core/audio/.../di/AudioModule.kt` с `@Binds` для `PcmAudioSource → AudioRecordPcmSource`, `@Provides` для `AudioProcessorFactory`
- [ ] run `./gradlew :core:audio:testDebugUnitTest` — must pass before next task

### Task 8: AudioRepositoryImpl + FakePcmAudioSource + Hilt wiring

- [ ] создать `core/audio/.../AudioRepositoryImpl.kt` — `class AudioRepositoryImpl @Inject constructor(private val source: PcmAudioSource, private val processorFactory: AudioProcessorFactory) : AudioRepository`
  - `fun samples(config: MeasurementConfig): Flow<SoundSample>` — `source.samples().scan(...)` с `AudioProcessor`; внутри `flow { ... }` отслеживает `timestampMs` от `System.nanoTime()` (для устойчивости к wall-clock-drift)
  - `suspend fun isAvailable(): Boolean` — `context.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)`
- [ ] создать `core/testing/.../fakes/FakePcmAudioSource.kt` — `class FakePcmAudioSource : PcmAudioSource` с возможностью `emitBuffer(buffer: ShortArray)`, `emitTone(frequencyHz: Float, amplitudeDb: Float, durationMs: Int)`, `setSampleRate(rate: Int)` — для тестирования полного `AudioRepositoryImpl` без AudioRecord
- [ ] добавить в `AudioModule` биндинг `@Binds AudioRepositoryImpl → AudioRepository` (Hilt scope = SingletonComponent)
- [ ] **сначала тест:** `AudioRepositoryImplTest` (JUnit 5 + Turbine):
  - `FakePcmAudioSource.emitTone(1000f, 90f, 1000)` → `samples(MeasurementConfig(A, FAST))` эмиттит примерно 80 значений (10 Hz × 1 сек ≈ 10, но Flow эмиттит per-buffer); каждое значение ≈ 90 dB ± 1.5 dB
  - повтор для `Z`-weighting → те же 90 dB ± 1.5 dB (на 1 кГц A и Z совпадают)
  - повтор для частоты 100 Hz, амплитуды 90 dB → `A` показывает ≈ 70 dB (с учётом A-кривой −19 dB на 100 Hz), `Z` показывает ≈ 90 dB
  - `calibrationOffsetDb = +5` → выходное значение сдвинуто на +5 dB
- [ ] **сначала тест:** `AudioRepositoryImplCancellationTest` — `take(1)` корректно отменяет underlying `PcmAudioSource.samples`; verify `FakePcmAudioSource.cancelCount == 1`
- [ ] реализовать классы чтобы тесты позеленели
- [ ] run `./gradlew :core:audio:testDebugUnitTest :core:testing:testDebugUnitTest` — must pass before next task

### Task 9: MeasureViewModel — MVI lite state machine

- [ ] добавить в `feature/measure/build.gradle.kts` `implementation(projects.core.audio)` (чтобы получить доступ к `AudioRepository` через Hilt-граф; technically `:core:audio` уже подключен транзитивно через `:app`, но явная зависимость нужна для использования `MeasurementConfig` в ViewModel)
- [ ] создать `feature/measure/.../MeasureUiState.kt` — `data class MeasureUiState(val current: Float = 0f, val min: Float = Float.POSITIVE_INFINITY, val max: Float = Float.NEGATIVE_INFINITY, val avg: Float = 0f, val durationMs: Long = 0, val recent: List<SoundSample> = emptyList(), val state: MeasurementPhase = MeasurementPhase.Idle, val permissionState: PermissionState = PermissionState.Unknown)`
- [ ] создать `MeasurementPhase.kt` — `enum class MeasurementPhase { Idle, Running, Paused }`
- [ ] создать `PermissionState.kt` — `enum class PermissionState { Unknown, Granted, Denied, PermanentlyDenied }`
- [ ] создать `MeasureUiEvent.kt` — `sealed interface MeasureUiEvent` с `data object StartRequested`, `data object PauseRequested`, `data object ResetRequested`, `data object SaveRequested`, `data class PermissionResult(val granted: Boolean, val shouldShowRationale: Boolean)`
- [ ] создать `MeasureUiEffect.kt` — `sealed interface MeasureUiEffect` с `data object RequestPermission`, `data class ShowSnackbar(val messageRes: Int)`, `data object OpenAppSettings`
- [ ] создать `MeasureViewModel.kt` — `@HiltViewModel class MeasureViewModel @Inject constructor(savedStateHandle: SavedStateHandle, private val startMeasurement: StartMeasurementUseCase, private val resetMeasurement: ResetMeasurementUseCase)`:
  - `val state: StateFlow<MeasureUiState>` через `MutableStateFlow` или `savedStateHandle.getStateFlow(...)` для NFR-5
  - `val effects: Flow<MeasureUiEffect>` через `Channel(Channel.BUFFERED).receiveAsFlow()`
  - `fun onEvent(event: MeasureUiEvent)`:
    - `StartRequested` + `permissionState == Granted` → запускает `startMeasurement(config)` в `viewModelScope.launch { ... }` коллектит в state
    - `StartRequested` + `permissionState == Unknown` → эмиттит `RequestPermission` effect
    - `PauseRequested` → отменяет collect-job, `phase = Paused`, состояние замораживается (min/avg/max сохраняются)
    - `ResetRequested` → отмена + `state.value = MeasureUiState()` (reset accumulator)
    - `SaveRequested` → эмиттит `ShowSnackbar(R.string.measure_save_unavailable_phase2)` — Phase 3 заменит на реальную save-логику
    - `PermissionResult(true)` → переход в `Granted`, авто-старт измерения
    - `PermissionResult(false, shouldShowRationale = false)` → `PermanentlyDenied`, эмиттит `OpenAppSettings`
- [ ] **сначала тест:** `MeasureViewModelStartTest` (JUnit 5 + Turbine + MockK):
  - идеал: разрешение уже выдано, `onEvent(StartRequested)` → `phase` переходит в `Running`, через `FakeAudioRepository.emit(60f)` `state.value.current == 60f`, `min == 60f`, `max == 60f`, `avg == 60f`
  - повтор для последовательности `[40, 60, 80, 60, 40]` → final `min=40, max=80, avg=56`
- [ ] **сначала тест:** `MeasureViewModelPermissionFlowTest`:
  - `permissionState = Unknown`, `onEvent(StartRequested)` → НЕ стартует measurement, **emit** `RequestPermission` effect (verified via Turbine `effects.test { awaitItem() shouldBe RequestPermission }`)
  - `onEvent(PermissionResult(true, false))` → `permissionState = Granted`, авто-старт измерения
  - `onEvent(PermissionResult(false, true))` → `permissionState = Denied`, эмиттит `ShowSnackbar`
  - `onEvent(PermissionResult(false, false))` → `permissionState = PermanentlyDenied`, эмиттит `OpenAppSettings`
- [ ] **сначала тест:** `MeasureViewModelPauseResetTest`:
  - после нескольких эмитов `onEvent(PauseRequested)` → `phase = Paused`, дальнейшие emits от FakeAudio **не** обновляют state
  - `onEvent(ResetRequested)` → all metrics обнуляются, `phase = Idle`
- [ ] **сначала тест:** `MeasureViewModelSaveStubTest` — `onEvent(SaveRequested)` → ровно один `ShowSnackbar(R.string.measure_save_unavailable_phase2)` effect; state не меняется
- [ ] **сначала тест:** `MeasureViewModelSavedStateHandleTest` (Robolectric, для типизированного `SavedStateHandle`) — после симуляции process death (`SavedStateHandle` с заранее записанным `MeasureUiState`) новый ViewModel восстанавливает `min/avg/max/durationMs`
- [ ] реализовать ViewModel; UI-эффекты через `Channel.BUFFERED` чтобы не пропадали при rotation
- [ ] добавить локализационные строки `measure_save_unavailable_phase2`, `measure_permission_rationale_title`, `measure_permission_rationale_body`, `measure_permission_settings_action` в `:feature:measure/src/main/res/values/strings.xml` и `values-ru/strings.xml`
- [ ] run `./gradlew :feature:measure:testDebugUnitTest` — must pass before next task

### Task 10: MeasureScreen UI — readout + gauge + chart + bottom bar

- [ ] создать `feature/measure/.../ui/SplReadout.kt` — `@Composable fun SplReadout(db: Float, modifier: Modifier)`: `Text(text = "%.1f".format(db), style = MaterialTheme.typography.displayLarge.copy(fontSize = 96.sp))` + единица "дБ A" мелким шрифтом справа (NFR-13: `contentDescription = stringResource(R.string.measure_readout_cd, db)`)
- [ ] создать `ui/SplArcGauge.kt` — `@Composable fun SplArcGauge(db: Float, modifier: Modifier)`: Compose `Canvas` рисует дугу 270° с сегментированным градиентом `veryQuiet → quiet → moderate → loud → veryLoud → extreme` (из `LocalSplLevelPalette`); индикатор-стрелка указывает на текущее значение (interp между 30–110 dB по диапазону gauge); ниже — текстовый референс через `dbToReferenceLabel(db: Float): String` (mapping из § 6 спеки: ≤20 "Шёпот", ≤30 "Тихая спальня", ..., ≥120 "Гром")
- [ ] создать `ui/SplLineChart.kt` — `@Composable fun SplLineChart(samples: List<SoundSample>, modifier: Modifier)`: Compose `Canvas` рисует `Path` по последним 60 секундам; цвет — динамический по `levelToSplColor(currentDb, palette)`; ось Y — 30..110 dB, ось X — последние 60 секунд (без подписей в Phase 2); чисто-line graph без fill
- [ ] создать `ui/SplStatsRow.kt` — `@Composable fun SplStatsRow(min: Float, avg: Float, max: Float, modifier: Modifier)`: три Material 3 `Card(filled)` рядом, каждая с лейблом ("Мин"/"Сред"/"Макс") и значением `headlineSmall`
- [ ] создать `ui/MeasureBottomBar.kt` — `@Composable fun MeasureBottomBar(phase: MeasurementPhase, onStartPause: () -> Unit, onReset: () -> Unit, onSave: () -> Unit)`: левая кнопка Reset (`IconButton`, enabled только в `Paused`), центральный FAB Start/Pause (иконка зависит от phase), правая кнопка Save (`IconButton`, `enabled = false` всегда в Phase 2)
- [ ] создать `ui/PermissionRationaleDialog.kt` — `@Composable fun PermissionRationaleDialog(onConfirm: () -> Unit, onDismiss: () -> Unit)`: Material 3 `AlertDialog` с текстом из FR-2 спеки ("Чтобы измерить уровень шума, приложению нужен доступ к микрофону. Аудио НЕ записывается и НЕ покидает устройство.")
- [ ] переписать `MeasureScreen.kt` — `@Composable fun MeasureScreen(viewModel: MeasureViewModel = hiltViewModel(), onNavigateToAbout: () -> Unit)`:
  - `Scaffold` с `LargeTopAppBar` (название "Тишина" + actions "?" → onNavigateToAbout, "i" disclaimer-iconка)
  - `Column { SplReadout(...); SplArcGauge(...); SplStatsRow(...); SplLineChart(...); Spacer; durationText }`
  - `MeasureBottomBar` снизу
  - `PermissionRationaleDialog` при `effect == RequestPermission && permissionState != PermanentlyDenied`
  - подключение Compose permission state через `rememberPermissionState(Manifest.permission.RECORD_AUDIO)` (используем `accompanist-permissions`? **НЕТ** — accompanist deprecated, используем `rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> viewModel.onEvent(PermissionResult(granted, !granted /* shouldShowRationale упрощённо */)) }`)
- [ ] **сначала тест:** `SplReadoutScreenshotTest` (Roborazzi) — четыре снимка: 0.0 / 45.5 / 78.3 / 105.7 dB на light/dark
- [ ] **сначала тест:** `SplArcGaugeScreenshotTest` — четыре снимка: 30 / 60 / 85 / 110 dB на light/dark
- [ ] **сначала тест:** `SplLineChartScreenshotTest` — один снимок: синусоидальная история (60 сэмплов колеблются 40–80 dB), light + dark
- [ ] **сначала тест:** `SplStatsRowScreenshotTest` — снимок: min=40, avg=65, max=90, light + dark
- [ ] **сначала тест:** `MeasureBottomBarScreenshotTest` — три снимка: phase = Idle / Running / Paused, light + dark
- [ ] **сначала тест:** `PermissionRationaleDialogScreenshotTest` — один снимок (модалка над фейковым фоном), light + dark
- [ ] **сначала тест:** `MeasureScreenIdleScreenshotTest` (Roborazzi + Hilt fake): полный экран в `MeasurementPhase.Idle, permissionState = Unknown` — light + dark
- [ ] **сначала тест:** `MeasureScreenRunningScreenshotTest` — полный экран в `Running` с заполненной историей и тек. 65 dB — light + dark
- [ ] **сначала тест:** `MeasureScreenComposeBehaviorTest` (`createComposeRule()` + Robolectric):
  - стартовое состояние: FAB виден, кнопка `Save` disabled, текст "0.0 дБ" виден
  - симуляция события: после `viewModel.onEvent(PermissionResult(granted = true))` и эмита через FakeAudioRepository значение `60f` — UI обновляется (`onNodeWithText("60.0").assertExists()`)
  - tap FAB в `Idle` без permission → диалог `PermissionRationaleDialog` появляется (`onNodeWithTag("permission_dialog").assertIsDisplayed()`)
- [ ] реализовать композблы; **сначала** запускаем `recordRoborazziDebug` чтобы baseline-снимки записались, проверяем визуально, коммитим
- [ ] run `./gradlew :feature:measure:testDebugUnitTest verifyRoborazziDebug` — must pass before next task

### Task 11: Integration smoke + acceptance criteria + README

- [ ] verify all requirements from Overview are implemented:
  - `:app:assembleDebug` собирается без ошибок; debug APK устанавливается на эмулятор Android API 30; нажатие на FAB вызывает permission dialog; после grant — values обновляются 10 раз в секунду
  - placeholder MeasureScreen полностью заменён реальным UI
  - все Hilt-зависимости резолвятся (`AudioRepository`, `AudioRecordPcmSource`, `AudioProcessorFactory`, `StartMeasurementUseCase`, `MeasureViewModel`)
- [ ] verify edge cases handled:
  - `SplCalculator` clamp при `rms = 0` (тишина в записи)
  - `MeasureViewModel.PermissionResult(false, false)` корректно эмиттит `OpenAppSettings`
  - rotation: `MeasureViewModel.savedStateHandle` восстанавливает min/avg/max
  - кнопка Save показывает Snackbar и не падает
- [ ] run full test suite: `./gradlew testDebugUnitTest verifyRoborazziDebug` — 100% зелёных
- [ ] run linter: `./gradlew detektAll spotlessCheck lintDebug` — все warnings/errors устранены или явно подавлены
- [ ] verify test coverage report генерируется: `./gradlew koverHtmlReportDebug koverXmlReportDebug`; визуально проверяем что `:core:audio` ≥ 95% и `:core:domain` ≥ 90% (просто наблюдение, без enforced threshold)
- [ ] verify APK size — debug APK всё ещё ≤ 25 МБ (R8 не включён, реальный target ≤ 6 МБ — Phase Release); фиксируем фактическое значение в этот ⚠️ комментарий ниже
- [ ] зафиксировать baseline Roborazzi screenshots (`./gradlew recordRoborazziDebug`) и закоммитить новые PNG в `feature/measure/src/test/snapshots/`
- [ ] обновить `README.md`: статус "Phase 2: Audio Engine + Measure" → "Complete"; обновить раздел "What's working" с списком FR, покрытых в Phase 2
- [ ] run финальный smoke-прогон: `./gradlew clean assembleDebug testDebugUnitTest verifyRoborazziDebug detektAll spotlessCheck lintDebug` (помним про Kover/clean race из Phase 1 ⚠️ — при необходимости разбиваем на два invocation: `./gradlew clean assembleDebug -x test` потом `./gradlew testDebugUnitTest verifyRoborazziDebug detektAll spotlessCheck lintDebug`)

## Technical Details

### Структура каталогов после Phase 2 (новое относительно Phase 1)

```
tishina-android/
├── core/
│   ├── domain/
│   │   └── src/main/kotlin/ru/dmdp/tishina/core/domain/
│   │       ├── model/
│   │       │   ├── SoundSample.kt
│   │       │   ├── MeasurementSnapshot.kt
│   │       │   ├── FrequencyWeighting.kt
│   │       │   ├── TimeWeighting.kt
│   │       │   └── MeasurementConfig.kt
│   │       ├── repository/
│   │       │   ├── AudioRepository.kt
│   │       │   └── SettingsRepository.kt
│   │       └── usecase/
│   │           ├── StartMeasurementUseCase.kt
│   │           ├── StopMeasurementUseCase.kt
│   │           └── ResetMeasurementUseCase.kt
│   ├── audio/
│   │   ├── src/main/AndroidManifest.xml      # tools:node="merge" для RECORD_AUDIO
│   │   └── src/main/kotlin/ru/dmdp/tishina/core/audio/
│   │       ├── dsp/
│   │       │   ├── RingBuffer.kt
│   │       │   ├── DcBlockFilter.kt
│   │       │   ├── BiquadFilter.kt
│   │       │   ├── FrequencyFilter.kt
│   │       │   ├── AWeightingFilter.kt
│   │       │   ├── ZWeightingFilter.kt
│   │       │   ├── RmsCalculator.kt
│   │       │   ├── TimeWeightedRms.kt
│   │       │   ├── SplCalculator.kt
│   │       │   ├── AudioProcessor.kt
│   │       │   └── AudioProcessorFactory.kt
│   │       ├── source/
│   │       │   ├── PcmAudioSource.kt
│   │       │   ├── AudioRecordPcmSource.kt
│   │       │   └── AudioRecordCheck.kt
│   │       ├── di/
│   │       │   └── AudioModule.kt
│   │       └── AudioRepositoryImpl.kt
│   └── testing/
│       └── src/main/kotlin/ru/dmdp/tishina/core/testing/fakes/
│           ├── FakeAudioRepository.kt
│           └── FakePcmAudioSource.kt
└── feature/measure/
    └── src/main/kotlin/ru/dmdp/tishina/feature/measure/
        ├── MeasureScreen.kt                  # переписан
        ├── MeasureViewModel.kt
        ├── MeasureUiState.kt
        ├── MeasurementPhase.kt
        ├── PermissionState.kt
        ├── MeasureUiEvent.kt
        ├── MeasureUiEffect.kt
        └── ui/
            ├── SplReadout.kt
            ├── SplArcGauge.kt
            ├── SplLineChart.kt
            ├── SplStatsRow.kt
            ├── MeasureBottomBar.kt
            └── PermissionRationaleDialog.kt
```

### DSP pipeline architecture

```
AudioRecord (PCM 16-bit, 48 kHz mono UNPROCESSED → VOICE_RECOGNITION → MIC)
   │ ShortArray buffer (Flow<ShortArray>, ~100ms chunks)
   ▼
PcmAudioSource (interface)  ←──  AudioRecordPcmSource (real) | FakePcmAudioSource (test)
   ▼
AudioRepositoryImpl.samples(config)
   │
   │ for each ShortArray chunk:
   │   1. normalize: x[i] = pcm[i] / 32768.0f                      ← Float buffer (scratch)
   │   2. DcBlockFilter.process(x)                                 ← in-place
   │   3. (config.A) AWeightingFilter.process(x)                   ← in-place
   │      or
   │      (config.Z) ZWeightingFilter.process(x)                   ← no-op
   │   4. for sample in x: rms = TimeWeightedRms.update(sample)
   │      (UI обновляется per-chunk, dB = SplCalculator.toDb(rms, config.offset))
   │
   ▼
Flow<SoundSample(db: Float, timestampMs: Long)>
   ▼
StartMeasurementUseCase.invoke(config)
   │ scan into MeasurementSnapshot (accumulate min/avg/max + 60s ring of recent)
   ▼
Flow<MeasurementSnapshot>
   ▼
MeasureViewModel.state: StateFlow<MeasureUiState>
   ▼
MeasureScreen Composables
```

### Convention plugin & Hilt wiring

- `:core:audio` уже подключает `tishina.android.library` + `tishina.jvm.testing` через convention plugins. Нужно добавить `tishina.android.hilt` (для KSP-генерации `@HiltAndroidApp`-зависимостей в `:app`).
- В `:feature:measure` подключение `tishina.android.feature` уже даёт Hilt и Compose автоматически.
- `AudioModule` биндится в `SingletonComponent`:
  - `@Binds @Singleton fun bindPcmAudioSource(impl: AudioRecordPcmSource): PcmAudioSource`
  - `@Binds @Singleton fun bindAudioRepository(impl: AudioRepositoryImpl): AudioRepository`
  - `@Provides @Singleton fun provideAudioProcessorFactory(): AudioProcessorFactory = AudioProcessorFactory()`
  - `@Provides @Singleton fun provideDefaultSettingsRepository(): SettingsRepository = DefaultSettingsRepository()` (stub до Phase 4)

### A-weighting coefficients (для 48 kHz, по IEC 61672-1)

Реализуется в `AWeightingFilter.kt` как четыре каскадных биквад-секции. Точные коэффициенты не приводятся здесь (вычисляются bilinear transform от continuous-time poles); тестируются спектральным откликом на референсных частотах 31.5 / 125 / 1000 / 8000 / 16000 Гц с допуском ±0.3 dB. Источник методики — Brian Hawkins, "Design of digital filters for frequency weightings (A and C)", PMC4331191; см. § 11 спецификации.

### Sample rate fallback logic

```kotlin
private val supportedSampleRates = listOf(48_000, 44_100)

fun chooseSampleRate(): Int = supportedSampleRates.firstOrNull { rate ->
    AudioRecord.getMinBufferSize(rate, CHANNEL_IN_MONO, ENCODING_PCM_16BIT) != ERROR_BAD_VALUE
} ?: error("No supported sample rate available on this device")
```

### AudioSource fallback chain

```kotlin
private val sourcePriority = listOf(
    AudioSource.UNPROCESSED,         // API 24+, only if PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED == "true"
    AudioSource.VOICE_RECOGNITION,   // no AGC on most devices
    AudioSource.MIC                  // last-resort fallback
)

fun chooseAudioSource(context: Context, sampleRate: Int): Int {
    val unprocessedSupported = AudioRecordCheck.isUnprocessedSupported(context)
    val candidates = if (unprocessedSupported) sourcePriority else sourcePriority.drop(1)
    return candidates.first { source ->
        // smoke-проверка: AudioRecord конструктор не бросает IllegalArgumentException
        runCatching {
            val record = AudioRecord(source, sampleRate, CHANNEL_IN_MONO, ENCODING_PCM_16BIT, bufferSize)
            val ok = record.state == AudioRecord.STATE_INITIALIZED
            record.release()
            ok
        }.getOrDefault(false)
    }
}
```

### Параметры компиляции и зависимости

- `:core:audio` потребует `testImplementation(projects.core.testing)` (получаем JUnit 5 / MockK / Turbine / Robolectric / Roborazzi).
- `:feature:measure` потребует `implementation(projects.core.audio)` для `MeasurementConfig` импорта (хотя `AudioRepository` доступен из `:core:domain`).
- KSP-генерация Hilt: уже работает через `tishina.android.hilt` convention plugin в Phase 1; для `:core:audio` понадобится добавить `alias(libs.plugins.tishina.android.hilt)` в `build.gradle.kts`.

### Известные ограничения и допущения

- **TimeWeighted RMS** обновляется по-сэмплу, но UI обновляется только при эмите Flow-чанка (≈10 Hz). Это даёт NFR-2: 10 Hz UI refresh.
- **`AudioRepositoryImpl` не накапливает recent в RingBuffer** — это делает `StartMeasurementUseCase` через `scan` от `MeasurementSnapshot`. Логически чище: core:audio не знает про 60-секундное окно, core:domain — знает.
- **Z-weighting реализуется**, но **на UI не выводится** в Phase 2 (выбор A/Z — Phase 4 через Settings). Z используется внутренне для unit-тестов RMS-блока в изоляции от частотного фильтра.
- **Foreground Service** не используется. Это означает: если пользователь сворачивает приложение, измерение **прерывается** (Android может убить процесс по policy). Это сознательное решение MVP — добавление FGS требует `FOREGROUND_SERVICE_MICROPHONE` permission и нотификации, что усложняет Data Safety декларацию и требует отдельной проработки.
- **Audio focus listener** (NFR-6) реализуется упрощённо: `MeasureViewModel` отписывается от `AudioRepository.samples` при `lifecycleState <= STOPPED`; полноценный `OnAudioFocusChangeListener` с pause/resume на входящем звонке — Phase Release (можно вынести в отдельный PR).

## Post-Completion

*Items requiring manual intervention or external systems — no checkboxes, informational only.*

**Manual verification** (после завершения Phase 2):

- Установить debug APK на физическое Android-устройство (Pixel 6+, Samsung Galaxy S20+, Xiaomi Redmi Note среднего ценового сегмента); рядом включить **эталонный сертифицированный шумомер** (или знакомого инженера со SLM Class 2) и сделать **5 контрольных замеров** на разных уровнях (≈40, ≈60, ≈75, ≈85, ≈95 dB) в одинаковых условиях; убедиться что разброс показаний не превышает заявленных ±5 dB.
- Проверить корректность `AudioSource.UNPROCESSED` fallback на устройствах разных производителей (Samsung, Xiaomi, Pixel); зафиксировать список устройств, на которых fallback пошёл на `VOICE_RECOGNITION` или `MIC`, в issue.
- Проверить поведение при входящем звонке: измерение должно автоматически приостановиться (`Paused`) и возобновиться после звонка (или явно показать "paused due to call").
- Проверить ротацию экрана (portrait → landscape) во время активного измерения: метрики `min/avg/max` должны сохраниться через `SavedStateHandle`; график обнуляется (это ожидаемо, recent не сохраняется через savedState — выберем "сбрасывается на ротации" как компромисс).
- Проверить переход в фон (Home button): измерение должно автоматически приостановиться; при возврате — статус `Paused` сохранён.
- Проверить с TalkBack: все интерактивные элементы озвучиваются (FAB, статистика, диалог разрешения).

**External system updates** (отложено в Phase Release / Phase 3):

- Создать GitHub Issue-шаблон "Calibration data report" — для сбора пользовательских репортов о точности на конкретных устройствах (без telemetry, через GitHub Issues), чтобы потом сформировать pre-built калибровки (v1.2).
- Опубликовать промежуточный Pre-release на GitHub Releases (`v0.2.0-audio-engine`) для бета-тестеров.
- Добавить в README.md раздел "How to calibrate" — короткий мануал из § 11 спеки.
- **Phase 3 готовит:** Room schema, MeasurementRepositoryImpl, HistoryViewModel + HistoryScreen, DetailScreen, реальная `SaveMeasurementUseCase` (заменит stub в `MeasureViewModel.SaveRequested`).
- **Phase 4 готовит:** DataStore SettingsRepositoryImpl, SettingsScreen с переключателями A/C/Z + Fast/Slow + Calibration slider, добавление C-weighting фильтра в `:core:audio`.
- **Phase Release готовит:** реальный adaptive launcher icon, Privacy Policy на GitHub Pages, R8 + ProGuard rules + size optimization до ≤ 6 МБ, instrumentation-тесты на матрице emulator API 26/30/34, релиз в Google Play / RuStore / Samsung Galaxy Store.
