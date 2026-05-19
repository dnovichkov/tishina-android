# Тишина (Tisha) — Android Sound Level Meter

Открытое Android-приложение для измерения уровня окружающего шума. Работает офлайн, без рекламы и трекеров; данные хранятся локально на устройстве.

- **Платформа:** Android 8.0 (API 26) и выше.
- **Стек:** 100% Kotlin, Jetpack Compose, Material 3, Hilt, Room, DataStore.
- **Лицензия:** Apache 2.0 (см. [LICENSE](LICENSE)).
- **Спецификация:** [docs/specs/tishina-spec.md](docs/specs/tishina-spec.md).

## Статус

**Phase 1: Foundation — in progress.**

Текущая фаза закладывает инфраструктуру проекта (Gradle multi-module, дизайн-система, навигация-каркас, CI, статанализ). Бизнес-логика измерения звука появится в Phase 2.

Подробный план фазы: [docs/plans/2026-05-19-tishina-foundation.md](docs/plans/2026-05-19-tishina-foundation.md).

## Сборка

Требуется JDK 17 и Android SDK (compileSdk 35).

```bash
./gradlew :app:assembleDebug
```

Собранный APK будет в `app/build/outputs/apk/debug/`.

## Тестирование

```bash
./gradlew testDebugUnitTest verifyRoborazziDebug
```

## Контрибьюция

Проект на ранней стадии. Issues и pull requests приветствуются после завершения Phase 1.
