# GitHub Actions Workflows — Тишина

Эта директория содержит CI/CD-пайплайны для проекта **Тишина (Tisha)**.

## Workflows

### `ci.yml` — Continuous Integration

Триггеры:

- `push` в ветки `main` и `develop`.
- `pull_request` на ветки `main` и `develop`.

Workflow состоит из трёх параллельных job'ов (с зависимостями), которые запускаются на `ubuntu-latest` под JDK 17 (zulu).

#### Job: `static-checks`

Запускает статический анализ и проверки стиля. Падает быстро, если код не соответствует базовым требованиям.

Команда: `./gradlew detektAll spotlessCheck lintDebug --no-daemon --stacktrace`

Артефакты при падении:

- `detekt-reports` — HTML/XML-отчёты Detekt из `**/build/reports/detekt/`.
- `lint-reports` — HTML/XML-отчёты Android Lint из `**/build/reports/lint-results-*.{html,xml}`.

#### Job: `unit-tests` (`needs: static-checks`)

Прогоняет unit-тесты, Roborazzi screenshot-verification и собирает Kover XML-отчёт о покрытии.

Команда: `./gradlew testDebugUnitTest verifyRoborazziDebug koverXmlReportDebug --no-daemon --stacktrace`

Артефакты:

- `roborazzi-failure-images` (только при падении) — diff-изображения снапшотов из `**/build/outputs/roborazzi/` и `**/build/reports/roborazzi/`.
- `kover-xml-report` — `build/reports/kover/reportDebug.xml` (загружается всегда).
- `unit-test-reports` — HTML/XML JUnit-отчёты из `**/build/reports/tests/` и `**/build/test-results/`.

Опциональный шаг загрузки в Codecov активируется, если в Repository Secrets задан `CODECOV_TOKEN`. Шаг помечен `fail_ci_if_error: false` — отсутствие токена или временный сбой Codecov не валит CI.

#### Job: `build` (`needs: static-checks`)

Собирает debug-APK и debug-AAB.

Команда: `./gradlew :app:assembleDebug :app:bundleDebug --no-daemon --stacktrace`

Артефакты:

- `debug-apk` — `app/build/outputs/apk/debug/*.apk`.
- `debug-aab` — `app/build/outputs/bundle/debug/*.aab`.

#### Кеширование

Все job'ы используют `gradle/actions/setup-gradle@v3` для:

- кеширования `~/.gradle/caches/` и `~/.gradle/wrapper/`;
- использования общего Gradle build cache (read/write для `main`/`develop`, read-only для PR/feature-веток).

#### Параллелизм и отмена устаревших запусков

`concurrency: ci-${{ github.workflow }}-${{ github.ref }}` с `cancel-in-progress: true` — при пуше нового коммита в ту же ветку предыдущий run автоматически отменяется. Экономит минуты на активно дорабатываемых PR.

## Локальная отладка CI

Опционально, если установлен [`nektos/act`](https://github.com/nektos/act):

```bash
act -j static-checks
act -j unit-tests
act -j build
```

`act` запускает GitHub Actions локально в Docker. Не идеально (особенно с Android SDK), но полезно для базовой валидации синтаксиса workflow.

## Dependabot

См. `.github/dependabot.yml`. Обновляет:

- Gradle-зависимости — еженедельно (понедельник 08:00 МСК), группирует обновления по семействам (`androidx`, `compose`, `hilt`, `kotlinx`, `testing`).
- GitHub Actions версии — еженедельно (там же).

PR помечаются label'ами `dependencies` + (`gradle` | `github-actions`).
