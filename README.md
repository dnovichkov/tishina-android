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

**Phase 1: Foundation complete.**

Готова инфраструктура проекта:

- Multi-module Gradle (`:app` + 6 `core:*` + 4 `feature:*`) на composite build `build-logic` с convention-плагинами.
- Material 3 дизайн-система с поддержкой dynamic colors (API 31+), SPL-палитра уровней шума, типографика.
- Адаптивная навигация: `NavigationBar` (compact) / `NavigationRail` (medium+expanded) между 4 placeholder-экранами Measure / History / Settings / About.
- Локализация ru/en (primary ru), edge-to-edge, splash screen.
- Статанализ: Detekt + Ktlint (через Spotless) + Android Lint; покрытие через Kover.
- Unit-тесты (JUnit 5 + Robolectric) и Roborazzi screenshot-тесты (8 baseline-снимков).
- GitHub Actions CI: `static-checks` → `unit-tests` + `build` (APK + AAB).

Следующий этап — **Phase 2: Audio Engine + Measure** (AudioRecord, A-weighting, RMS, SPL).

Подробный план Phase 1: [docs/plans/2026-05-19-tishina-foundation.md](docs/plans/2026-05-19-tishina-foundation.md).
Полная спецификация продукта: [docs/specs/tishina-spec.md](docs/specs/tishina-spec.md).

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

## Известные особенности Phase 1

- Размер debug-APK ~18 МБ. NFR-4 (≤ 6 МБ) применим к release-сборке после включения R8/resource shrinking — отложено до Phase Release.
- При прогоне `clean` + Kover в одном invocation возможна гонка `kover-agent.args FileNotFoundException`. Workaround: разделить на два прогона — `./gradlew clean build`, затем `./gradlew testDebugUnitTest verifyRoborazziDebug koverXmlReportDebug`.
- После `clean` Spotless может выдать stale config-cache. Workaround: удалить `.gradle/configuration-cache/` и повторить.
- Robolectric 4.13 не поддерживает API 35; для unit-тестов SDK зафиксирован на 33 через `src/test/resources/robolectric.properties` в `:app`, `:core:designsystem`, `:core:ui`.

## Контрибьюция

Проект на ранней стадии. Issues и pull requests приветствуются после завершения Phase 1.
