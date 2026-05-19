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

**Phase 2: Audio Engine + Measure complete.**

К инфраструктуре Phase 1 добавлен рабочий измерительный pipeline и реальный экран `MeasureScreen`.

Покрытые FR/NFR из [спецификации](docs/specs/tishina-spec.md):

- **FR-2** — запрос `RECORD_AUDIO` при первом Start с rationale-диалогом и переходом в системные настройки при permanent denial.
- **FR-3 / FR-4 / FR-5 / FR-7** — Start / отображение dB(A) live, min/avg/max, графика 60 секунд, дугообразного gauge / Pause / Reset.
- **FR-6** — UI-каркас FAB Save присутствует, но `enabled = false` со Snackbar «History coming in next phase» (полная реализация — Phase 3).
- **FR-15 / FR-16** — A-weighting (IEC 61672-1 ±0.3 dB на референсных 31.5/125/1000/8000/16000 Гц) + Fast (125 мс) как умолчания; Z-weighting и Slow (1 с) реализованы, переключение остаётся на Phase 4.
- **NFR-2** — обновление UI ≈10 Гц, без блокировок UI-потока.
- **NFR-5 / NFR-6** — корректное освобождение `AudioRecord` при отмене корутины; `SavedStateHandle` восстанавливает min/avg/max после rotation.
- **NFR-8 / NFR-9 / NFR-10** — единственное запрашиваемое разрешение `RECORD_AUDIO`; аудио НЕ пишется в файл; никаких сторонних SDK.
- **NFR-13 / NFR-14 / NFR-15 / NFR-16** — contentDescription, font scaling, контрастные цвета, цвет не единственный носитель информации.

Архитектурно добавлено:

- `:core:domain` — модели (`SoundSample`, `MeasurementSnapshot`, `MeasurementConfig`), `AudioRepository` / `SettingsRepository`, use-cases (`StartMeasurementUseCase`, `ResetMeasurementUseCase`, `StopMeasurementUseCase`).
- `:core:audio` — собственный DSP-стек: `RingBuffer`, `DcBlockFilter`, `BiquadFilter`, `AWeightingFilter` (matched-Z + anti-aliasing zero, Class 1 IEC 61672-1), `ZWeightingFilter`, `RmsCalculator`, `TimeWeightedRms`, `SplCalculator`, `AudioProcessor` + `AudioProcessorFactory`; `PcmAudioSource` интерфейс и `AudioRecordPcmSource` (UNPROCESSED → VOICE_RECOGNITION → MIC; 48 kHz → 44.1 kHz fallback); `AudioRepositoryImpl` + Hilt-модуль `AudioModule`.
- `:core:testing` — `FakeAudioRepository` и `FakePcmAudioSource` (генератор тонов с AOSP-anchor RMS=2500/32768 ↔ 90 dB SPL).
- `:feature:measure` — `MeasureViewModel` (MVI lite — UiState/UiEvent/UiEffect, runtime permission flow, SavedStateHandle); композблы `SplReadout`, `SplArcGauge`, `SplLineChart`, `SplStatsRow`, `MeasureBottomBar`, `PermissionRationaleDialog`; `MeasureScreen` с реальным интерактивным UI.

Тестовое покрытие (наблюдательно, без enforced threshold в Phase 2):

- `:core:domain` — 100% INSTRUCTION (цель ≥ 90%).
- `:core:audio` — 89.7% INSTRUCTION (цель ≥ 95% — gap преимущественно в Android-зависимом `AudioRecordPcmSource`; полное покрытие — Phase Release с emulator-матрицей).
- `:feature:measure` — 77.3% INSTRUCTION (цель ≥ 85% — gap в `MeasureScreen` Hilt-обвязке, которая требует instrumentation-теста).
- Roborazzi: 34 baseline-снимка (light/dark × idle/running/paused/permission-denied/gauge-stages/chart/stats/bottombar).

Следующий этап — **Phase 3: History + Persistence** (Room, MeasurementRepositoryImpl, HistoryScreen + DetailScreen, реальный `SaveMeasurementUseCase`).

Подробные планы:

- Phase 1: [docs/plans/completed/2026-05-19-tishina-foundation.md](docs/plans/completed/2026-05-19-tishina-foundation.md).
- Phase 2: [docs/plans/2026-05-19-tishina-audio-engine.md](docs/plans/2026-05-19-tishina-audio-engine.md).
- Полная спецификация продукта: [docs/specs/tishina-spec.md](docs/specs/tishina-spec.md).

## Сборка

Требуется JDK 17 и Android SDK (compileSdk 35, build-tools 35.x). Путь к SDK задаётся переменной `ANDROID_HOME` или строкой `sdk.dir=...` в `local.properties`.

```bash
./gradlew :app:assembleDebug
```

Собранный APK будет в `app/build/outputs/apk/debug/`.

## Тестирование

```bash
./gradlew :build-logic:convention:test testDebugUnitTest verifyRoborazziDebug
```

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
- Robolectric 4.13 не поддерживает API 35; для unit-тестов SDK зафиксирован на 33 через `src/test/resources/robolectric.properties` в `:app`, `:core:designsystem`, `:core:ui`, `:feature:measure`.
- `MeasureScreen` использует `hiltViewModel()`, поэтому навигационные тесты в `:app` подменяют его на пустой stub через параметр `measureContent` у `TishinaApp`/`TishinaNavHost`, не нагружая Hilt-граф.

## Контрибьюция

Проект на ранней стадии. Issues и pull requests приветствуются.
