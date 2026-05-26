# Чек-лист публикации в RuStore — Тишина v1.0.0

Последовательность шагов от готовых материалов до опубликованной карточки в
RuStore Developer Console (https://console.rustore.ru).

Все источники истины:
- Тексты: `app/src/main/store-metadata/rustore/ru-RU/`
- Скриншоты: `app/src/main/store-metadata/rustore/screenshots/`
- Иконка: `app/src/main/play-store-icon.png`
- Конфигурация полей карточки: `app/src/main/store-metadata/rustore/listing-config.md`
- Декларации: `data-safety.md`, `permissions-rationale.md`
- Спецификация продукта: `docs/specs/tishina-spec.md` § 15

## 1. Подготовка AAB

Локальная сборка релизного AAB **не** даст `versionName=1.0.0` — она вернёт `1.0.0-dev` (см. `app/build.gradle.kts:34`). Production-AAB должен собирать CI по тегу `v1.0.0`.

### Вариант A — через CI (рекомендуется)

```bash
git tag v1.0.0
git push origin v1.0.0
```

Workflow `.github/workflows/release.yml` создаст draft GitHub Release с
`app-release.aab`, `app-release.apk`, `mapping.txt`. Скачать AAB из релиза.

### Вариант B — локально с правильным versionName

Подготовьте переменные окружения, чтобы build.gradle не свалился в `-dev`:

```powershell
$env:UPLOAD_KEYSTORE_PATH    = "C:\secure\upload-key.jks"
$env:UPLOAD_KEYSTORE_PASSWORD = "<пароль keystore>"
$env:UPLOAD_KEY_ALIAS         = "upload"
$env:UPLOAD_KEY_PASSWORD      = "<пароль ключа>"
$env:VERSION_CODE             = "1"
$env:VERSION_NAME             = "1.0.0"
./gradlew :app:bundleRelease
```

Артефакт: `app/build/outputs/bundle/release/app-release.aab` (~5.4 МБ).

## 2. Проверка перед загрузкой

| Проверка | Команда | Что должно быть |
|---|---|---|
| Сборка проходит | `./gradlew :app:bundleRelease` | BUILD SUCCESSFUL |
| Размер AAB | `Get-Item app\build\outputs\bundle\release\app-release.aab` | ≤ 8 МБ (NFR-4) |
| Подпись | `jarsigner -verify -verbose app-release.aab` | `jar verified` |
| Тесты лимитов | `./gradlew :app:testDebugUnitTest --tests "*StoreMetadataLengthLimitTest*"` | PASS |
| Скриншоты в репо | `dir app\src\main\store-metadata\rustore\screenshots\*.png` | 6 файлов 1080×1920 |

## 3. RuStore Developer Console — заполнение карточки

### Шаг 1. Создать приложение

1. Войти: https://console.rustore.ru.
2. «Создать приложение» → «Мобильное приложение».
3. Package: `ru.dmdp.tishina`.
4. Загрузить AAB → автозаполнятся versionCode/versionName/minSdk/targetSdk.

### Шаг 2. Информация о приложении

| Поле | Значение | Файл-источник |
|---|---|---|
| Название (RU) | «Тишина — измеритель шума» | `rustore/ru-RU/title.txt` |
| Краткое описание (RU) | См. файл | `rustore/ru-RU/short_description.txt` |
| Подробное описание (RU) | См. файл | `rustore/ru-RU/full_description.txt` |
| Категория | Полезные инструменты | `listing-config.md` |
| Подкатегория | Утилиты | `listing-config.md` |
| Возрастной рейтинг | 0+ | `listing-config.md` |
| Поисковые теги (до 5) | шумомер, измеритель шума, уровень шума, децибелметр, измерение звука | `listing-config.md` |

### Шаг 3. Графические материалы

| Поле | Файл |
|---|---|
| Иконка (512×512, без прозрачности) | `app/src/main/play-store-icon.png` |
| Скриншоты телефон (порядок 01→06) | `app/src/main/store-metadata/rustore/screenshots/01..06-*.png` |
| Скриншоты планшет | пропустить — оптимизация под phone (v1.0) |
| Видео-обзор | не загружаем для v1.0 |

### Шаг 4. Что нового в этой версии

Скопировать содержимое `rustore/ru-RU/whatsnew.txt`.

### Шаг 5. Декларация разрешений и приватность

| Поле | Значение |
|---|---|
| Требуется `RECORD_AUDIO` | Да |
| Обоснование | Скопировать «короткое описание» из `permissions-rationale.md` |
| Собирает ли данные | Нет (см. `data-safety.md`) |
| Передаёт ли данные третьим лицам | Нет |
| Использует ли рекламу | Нет |
| Встроенные покупки | Нет |
| Политика конфиденциальности (URL) | https://dnovichkov.github.io/tishina-android/privacy/ |

### Шаг 6. Контакты разработчика

| Поле | Значение |
|---|---|
| Email | dmitry.novichkov@gmail.com |
| Сайт | https://dnovichkov.github.io/tishina-android/ |
| ВК-группа | не заполняем |

### Шаг 7. Комментарий для модератора

Вставить:

> Тишина — измеритель уровня шума с открытым кодом (Apache 2.0). Запрашивает
> только `RECORD_AUDIO` для замера уровня окружающего шума. Аудиопоток
> обрабатывается в RAM и моментально отбрасывается — аудиофайлы НЕ
> сохраняются и НЕ передаются (нет сетевого кода в release-манифесте, нет
> SDK-аналитики, нет рекламы). Полный код доступен на GitHub. Подробности
> приватности: https://dnovichkov.github.io/tishina-android/privacy/.

### Шаг 8. Отправить на модерацию

«Отправить на проверку». По данным RuStore, среднее время модерации — менее
часа, ~93 % приложений проходят с первого раза.

## 4. После публикации

- Сохранить упомянутый mapping.txt из CI-артефакта (для деобфускации крашей в v1.0.x patches).
- Подписаться на уведомления о новых отзывах в RuStore Console.
- Через 30 дней — обновить `aso-keywords.md` на основе реальных поисковых запросов из Console.

## 5. Типичные причины реджекта и решения

| Причина | Решение |
|---|---|
| Иконка с прозрачностью | Уже исправлено — `play-store-icon.png` имеет непрозрачный фон `#0E2433` |
| Описание не на русском | Все поля RuStore заполняются из `rustore/ru-RU/` (RU) |
| `RECORD_AUDIO` без объяснения | Шаг 7 (комментарий модератору) явно объясняет |
| Скриншоты с заглушкой/splash | Все 6 скринов показывают реальную функциональность |
| Версия с `-dev` или `-SNAPSHOT` | Использовать вариант A (CI по тегу) или вариант B с явным `VERSION_NAME` |
| Несовпадение подписи с предыдущей версией | Первая публикация — не применимо. Для v1.0.1+ обязательно использовать тот же upload key |

## 6. Регенерация материалов

| Что | Команда |
|---|---|
| Иконка 512×512 | `python docs/tools/render_launcher_icon.py` |
| Скриншоты RU+EN (12 PNG) | `./gradlew :feature:measure:testDebugUnitTest :feature:history:testDebugUnitTest :feature:settings:testDebugUnitTest :feature:about:testDebugUnitTest --tests "*StoreScreenshotTest*" -Proborazzi.test.record=true` |
| Публикация для RuStore | `python docs/tools/collect_store_screenshots.py --target rustore` |
| Публикация для Google Play (RU) | `python docs/tools/collect_store_screenshots.py --target google-play-ru` |
| Публикация для Google Play (EN) | `python docs/tools/collect_store_screenshots.py --target google-play-en` |
| Публикация для Samsung | `python docs/tools/collect_store_screenshots.py --target samsung` |
