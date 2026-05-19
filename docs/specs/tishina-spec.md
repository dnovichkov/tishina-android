# Спецификация на разработку Android-приложения «Тишина»

**Полное название:** Тишина — измеритель шума / Tisha — Sound Level Meter
**Package name:** `ru.dmdp.tishina`
**GitHub репозиторий:** `tishina-android`

---

## 1. Краткое описание продукта (Vision)

**Видение.** Лёгкое, бесплатное и принципиально **без рекламы** Android-приложение «Тишина» для быстрого измерения уровня шума (SPL, Sound Pressure Level) в децибелах, с локальной историей и понятным минималистичным UI на Jetpack Compose / Material 3. Главный дифференциатор на фоне топовых конкурентов (rootApps Sound Meter, KTW Sound Meter, Decibel X, Splend Apps Decibel) — **отсутствие рекламы и подписок** при сохранении нужного объёма функций.

**Целевая аудитория.**
- Бытовые пользователи: «сосед сверлит — насколько громко?», «какой шум в спальне ребёнка?», «комфортно ли в офисе?».
- Любители аудио и DIY-сообщество: настройка домашних кинотеатров, проверка шумоподавления в наушниках, измерение шума домашних приборов.
- Студенты, инженеры начального уровня, специалисты по охране труда — как ориентировочный инструмент (не замена сертифицированному шумомеру).
- Жители РФ и СНГ — первичный рынок (русский UI, дистрибуция через RuStore); вторично — англоязычный мировой рынок (Google Play, Samsung Galaxy Store).

**Ценностное предложение (UVP).**
1. **Без рекламы навсегда.** В RuStore и Google Play подавляющее большинство популярных шумомеров (KTW, rootApps, Splend, Decibel X, Angry Robot) монетизируются либо рекламой, либо подписками; пользовательские отзывы массово жалуются на это («GIANT FULL PAGE ADS that CAN'T BE CLOSED», «много рекламы», «реклама со звуком в шумомере — издевательство»).
2. **Один клик — одно измерение.** Открыл — измерил — сохранил, без онбординга, баннеров, обязательных аккаунтов.
3. **Локальная история с заметками.** Привязка к дате/месту/обстоятельствам, полностью офлайн, без облака и без сбора данных.
4. **Современный UI.** Material 3, динамические цвета (Android 12+), тёмная/светлая тема, поддержка крупных шрифтов и доступности.
5. **Честная коммуникация о точности.** В отличие от ряда конкурентов, не претендуем на «professional accuracy» — открыто пишем о методологии, источниках погрешности и ссылаемся на исследования NIOSH.

**Позиционирование бренда.** Имя «Тишина» эмоционально-нейтральное, лёгко локализуется в `Tisha` для англоязычной аудитории, не пересекается с известными брендами в категории Tools и хорошо ложится в маркетинговое сообщение: «измерь, чтобы добиться тишины».

---

## 2. Анализ конкурентов

### 2.1 Сравнительная таблица — Google Play

| App / Разработчик | Package | Рейтинг | Установки | Реклама / IAP | Сильные стороны | Слабые стороны |
|---|---|---|---|---|---|---|
| **Sound Meter (rootApps)** | com.gamebasic.decibel | 4,47 ★ (≈180 тыс. оценок, AppBrain) | 10M+ | Баннер + полноэкранные ads | Простой gauge, min/avg/max, калибровка; лидер по установкам | Жалобы: «GIANT FULL PAGE ADS that CAN'T BE CLOSED»; нет истории; ограничение ~90 дБ |
| **Sound meter: SPL & dB (KTW Apps)** | com.ktwapps.soundmeter | 4,78 ★ (≈43 тыс. оценок, AppBrain) | 5M+ | Баннер + IAP «remove ads» | График real-time, save history, min/max/avg, time weighting, экспорт данных | Реклама занимает значительную часть UI; cookie-баннеры; пользователи указывают на +22 дБ калибровку |
| **Sound Decibel Meter (Splend Apps)** | com.splendapps.decibel | ≈4,4 ★ | 5M+ | Реклама | Чистый UI, простое управление, график | Предупреждение об ограничениях AGC и ~90 дБ; самокритично |
| **Decibel X (SkyPaw)** | com.skypaw.decibel | 3,7 ★ Google Play / 4,7 ★ App Store (≈153 тыс. оценок) | 10M+ | Freemium с subscription ≈$60/год Pro | A/B/C/Z + ITU-R 468; FFT/RTA; dosimeter NIOSH/OSHA; InstaDecibel-overlay; экспорт CSV/PNG | Подписка вызывает раздражение; в free-версии только Z-weighted и баннер |
| **Sound Meter Pro (Smart Tools)** | com.gamemalt.soundmeterpro и др. | 4,2–4,5 ★ | 10M+ | Платный/реклама | Калибровка под конкретные модели Android | UI устарел; реклама |
| **Mobacorn Decibel Meter** | mobacorn.com.decibelmeter | ≈4,3 ★ | 100K+ | Реклама | Лёгкий, гейдж + значения | Минимум функций |
| **Binghuo Sound Meter** | com.binghuo.soundmeter | ≈4,5 ★ | 100K+ | Реклама | Простота | Минимум функций |
| **NIOSH Sound Level Meter (iOS only)** | — | 4,8 ★ (App Store) | — | **Бесплатно, без рекламы, без IAP** | Профессиональная методология, A/C/Z, Fast/Slow, LAeq/TWA/Dose, ±2 дБ(А) от Type-2 SLM на iOS | Android-версии нет; CDC прямо пишет: «Verifying the accuracy of an Android-based app is not currently possible» |

### 2.2 Сравнительная таблица — RuStore

| App | Package | Рейтинг | Установки | Реклама | Заметки |
|---|---|---|---|---|---|
| Шумомер измеритель громкости (Angry Robot) | ru.angryrobot.soundmeter | 4,1 ★ (410 оценок) | 10K+ | Да, агрессивная | Тёмная/светлая тема, min/max/avg, история. В отзывах: «Это не приложение, а реклама…» |
| Шумомер (Sound Meter) — melon soft | app.melon.sound_meter | 4,8 ★ | 20K+ | Реклама (бесплатно) | Корейский разработчик, mature codebase, ≈96 тыс. оценок в Google Play |
| Шумомер. Анализатор звукового спектра (Большаков Денис) | com.bolshakovdenis.soundanalyzer | 4,7 ★ (6 оценок) | 3K+ | Нет | БПФ 4096/8192, A/C/ITU-R 468, 1/1 и 1/3 октавы, ввод калибровочной АЧХ — лучший «инженерный» вариант |
| Шумомер HQ PRO (Just4Fun Tools) | com.just4funtools.soundmeternoisehd | 0 (нет оценок) | <1K | Нет | Честно предупреждает о пределе ~90–100 дБ (RuStore-карточка) |
| Шумомер (sav.max) | sav.max.shumomer | 3,4 ★ (43 оценки) | 10K+ | Реклама | Простой, шумные жалобы в отзывах |
| Шумомер / Уровень громкости и шума (OakDev) | com.devoak.soundmeter | 4,8 ★ (5 оценок) | 7K+ | Реклама | Реальные показания иногда +17 дБ от паспортных |
| Шумомер (Sound Meter) — gamebasic | com.gamebasic.decibel | 5,0 ★ (5 оценок) | 9K+ | Баннер | Тот же rootApps APK |
| Шумометр и детектор шума (Coocent) | coocent.app.tools.soundmeter.noisedetector | 2,0 ★ (4 оценки) | 1K+ | Да, в т.ч. поп-апы со звуком | Жалобы на навязчивую рекламу |
| Громкость / HP Volume (ByteHamster) | de.hp.volume | 4,8 ★ (32 оценки) | 7K+ | **Бесплатно и без рекламы** | NB: это не SPL-метр, а контролер громкости — но эталон бренда «минимализм + zero ads» |

### 2.3 Аналоги на iOS / macOS / Web

- **Decibel X (SkyPaw / NSL Studios)** — iOS/Android/Mac. ITU-R 468 + A/B/C/Z, RTA, FFT, spectrogram, дозиметр NIOSH/OSHA, InstaDecibel-overlay, calibrate ±15/±50 дБ. Эталон коммерческого продукта, subscription-based.
- **NIOSH Sound Level Meter (iOS, App Store id 1096545820)** — разработан EA LAB d.o.o. совместно с NIOSH. **Полностью бесплатный, без рекламы, без IAP.** Поддерживает LAeq, TWA, Max, Peak, Dose, Projected Dose, A/C/Z согласно NIOSH/OSHA. Тестирован на соответствие IEC 61672-3 Class 2 при использовании внешнего калиброванного микрофона MicW i436. Исходный код **не опубликован** на GitHub — приложение проприетарное. Источники: cdc.gov/niosh/noise/about/app.html, Kardous & Shaw, JASA 135(4):EL186 (2014), DOI 10.1121/1.4865269.
- **SoundMeter (Faber Acoustical)** — iOS. Одно из четырёх iOS-приложений, прошедших тест Kardous-Shaw 2014 с погрешностью ≤ 2 дБ(А); лучшее по A-weighted (Δ = −0,52 дБА).
- **SPLnFFT (Fabien Lefebvre)** — iOS. Лучший результат по невзвешенному уровню (Δ = 0,07 дБ).
- **Studio Six Digital AudioTools / SPL Meter / SoundTools** — iOS, профессиональный пакет: SPL Meter, RTA 1/3 oct, FFT, ETC, импульсный отклик, transfer function, фильтры ANSI Type 1. Требует доп. покупок модулей и внешнего микрофона (iTestMic2, iAudioInterface2, uPrecisionMic).
- **Faber Acoustical SignalScope** — macOS, тот же разработчик, что и SoundMeter; настольный «осциллограф + анализатор» уровня.
- **SPLnFFT Noise Meter (Mac/iOS)** — компактный SPL + FFT-анализатор.
- **dB Meter & Spectrum Analyzer** (различные iOS) — категория, повторяющая фичи Decibel X.
- **Web-приложения** на Web Audio API — типовой UX-паттерн: один большой digital-readout + горизонтальная цветная шкала + текстовый референс «whisper / conversation / vacuum / chainsaw». Стоит перенять.

### 2.4 Ключевые наблюдения

1. **Реклама — главная боль рынка.** Видна и в Google Play, и в RuStore. Бесплатное приложение без рекламы — не просто фича, а основной конкурентный «крючок».
2. **«Один экран — всё видно».** Победители (rootApps, KTW) показывают на главном экране одновременно: текущий dB крупно, gauge или горизонтальную шкалу, min/avg/max, мини-график времени.
3. **Цветовое кодирование универсально:** зелёный → жёлтый → оранжевый → красный по уровню риска для слуха.
4. **Список референсов** («whisper 30 dB / conversation 60 dB / chainsaw 110 dB») используется почти всеми и снижает порог понимания.
5. **Калибровка обязательна.** Все серьёзные приложения позволяют сдвинуть показания на ±N дБ. Без этого первая реакция пользователя — «не точно».
6. **Честные дисклеймеры повышают доверие.** KTW, Splend, melon soft, NIOSH открыто пишут об ограничениях ~90 дБ из-за микрофона смартфона.
7. **История измерений** — редкая фича в RuStore: в большинстве конкурентов её нет или она примитивна. Наша территория для улучшения.
8. **Заметки к измерениям** не встречаются почти нигде — дифференциатор.

---

## 3. Лучшие практики, выработанные из анализа

1. **Главный экран должен загружаться и быть готов к измерению без подтверждений.** Разрешение запрашиваем при первом нажатии «Старт», не на старте приложения.
2. **Крупный «живой» readout** (≥ 80–96 sp) — центральная метрика.
3. **Цветной gauge или дугообразный indicator** + **горизонтальная шкала-референс**.
4. **Min / Avg / Max — всегда видимы**, считаются от старта замера, не «всех времён».
5. **Лента-график dB по времени** (последние 30–60 секунд) — даёт интуитивное понимание динамики.
6. **Одна основная кнопка-FAB для Start / Stop / Save**, чтобы записать замер в историю.
7. **История = список карточек** с датой, средним dB, длительностью, мини-графиком и заметкой.
8. **Опциональные заметки** (≤ 200 символов) — поле появляется в диалоге сохранения.
9. **Калибровочный offset** (−20…+20 дБ) в настройках, отдельный экран с пояснением.
10. **Тёмная тема как первичная**, тёплый/нейтральный акцент.
11. **Дисклеймер о точности** — компактный, на главном экране в иконке-«i», и развёрнутый в «О приложении».
12. **Полное отсутствие сбора телеметрии** = чистая Data Safety декларация в Google Play.

---

## 4. Функциональные требования (FR)

### Главный экран измерения
- **FR-1.** Приложение запускается на главном экране измерения за ≤ 1 с после холодного старта (Compose-warm-up + Splash API).
- **FR-2.** При первом нажатии «Старт» приложение запрашивает разрешение `android.permission.RECORD_AUDIO`. До этого — экран-плейсхолдер с объяснением, зачем нужно разрешение.
- **FR-3.** После выдачи разрешения нажатие на FAB «Старт» запускает непрерывное измерение SPL.
- **FR-4.** В активном режиме отображаются:
  - текущий уровень dB (large readout, обновление 10 Гц),
  - min / avg / max (Leq) с момента старта,
  - круглый или дугообразный gauge с цветовой шкалой,
  - график dB(t) за последние 60 с,
  - длительность замера (mm:ss),
  - текстовый референс ближайшего уровня («Тихий разговор», «Уличный трафик» и т. д.).
- **FR-5.** Нажатие «Пауза» останавливает обработку, но удерживает накопленные min/avg/max.
- **FR-6.** Нажатие «Сохранить» открывает диалог: имя замера, опциональная заметка (≤ 200 символов), кнопки «Сохранить» / «Отмена».
- **FR-7.** Нажатие «Сброс» обнуляет min/avg/max и очищает график без сохранения.

### История
- **FR-8.** Экран «История» отображает список сохранённых замеров, отсортированных по убыванию даты.
- **FR-9.** Каждая карточка показывает: дату/время, имя/заметку (первые 60 символов), среднее dB, длительность, мини-спарклайн.
- **FR-10.** Нажатие на карточку открывает экран «Детали» с полным графиком, всеми метриками и полем редактирования заметки.
- **FR-11.** Свайп влево (или контекстное меню) → «Удалить» с подтверждением Snackbar/undo (5 с).
- **FR-12.** Поддерживается множественный выбор → bulk-удаление.
- **FR-13.** Поиск/фильтр по дате и тексту заметки (**P1**, не блокер для MVP).

### Настройки
- **FR-14.** Калибровочный offset: слайдер от −20,0 до +20,0 dB с шагом 0,1, пояснение методики.
- **FR-15.** Выбор частотного взвешивания: A-weighting (по умолчанию), C-weighting, Z (без взвешивания). **Только A в MVP допустимо; C/Z — P1.**
- **FR-16.** Выбор временного взвешивания: Fast (125 мс) — по умолчанию, Slow (1 с). Impulse — **P2**.
- **FR-17.** Темы: Системная / Светлая / Тёмная. Опция «Динамические цвета (Android 12+)».
- **FR-18.** Язык: Системный / Русский / Английский.
- **FR-19.** Кнопка «Сбросить калибровку» → возврат к 0,0 dB.
- **FR-20.** Экспорт истории в CSV через Storage Access Framework (**P1**).

### О приложении
- **FR-21.** Версия приложения, ссылка на GitHub-репозиторий, политика конфиденциальности, лицензии OSS-зависимостей.
- **FR-22.** Полный текст дисклеймера о точности (см. раздел 11).

### Системные требования
- **FR-23.** Min SDK 26 (Android 8.0), Target SDK ≥ 35 (политика Google Play 2025–2026).
- **FR-24.** 100 % Kotlin + Jetpack Compose + Material 3.
- **FR-25.** Работа полностью офлайн. Никаких сетевых запросов в MVP.

---

## 5. Нефункциональные требования (NFR)

### Производительность
- **NFR-1.** Время холодного старта до интерактивного экрана ≤ 1 с на референсном устройстве среднего класса (Pixel 6a / Snapdragon 7-gen).
- **NFR-2.** Обновление UI dB-readout 10 Гц; не более 5 % CPU на типовом устройстве в режиме измерения.
- **NFR-3.** Пиковое потребление RAM ≤ 60 МБ.
- **NFR-4.** Размер APK ≤ 6 МБ (после R8/ProGuard), AAB ≤ 8 МБ.

### Надёжность
- **NFR-5.** Сохранение состояния измерения при повороте экрана и переходе в фон (`SavedStateHandle`, `viewModelScope`).
- **NFR-6.** Корректное освобождение `AudioRecord` при потере фокуса, низком заряде батареи, входящем звонке (`AudioManager.OnAudioFocusChangeListener`).
- **NFR-7.** Crash-free sessions ≥ 99,5 % (метрика Play Vitals, без сторонних SDK).

### Безопасность и приватность
- **NFR-8.** Запрашивается только `RECORD_AUDIO`. Никаких других опасных разрешений.
- **NFR-9.** Аудио **НЕ** записывается в файл, ни на диск, ни в кэш. Обрабатываются только PCM-сэмплы в RAM.
- **NFR-10.** Никаких сторонних аналитических/рекламных SDK.
- **NFR-11.** Все БД-операции — внутри app-private storage.
- **NFR-12.** Поле заметок ограничено 200 символами, валидируется на UI- и DAO-уровне.

### Доступность (a11y)
- **NFR-13.** Все интерактивные элементы имеют `contentDescription`.
- **NFR-14.** Поддержка масштабирования системного шрифта вплоть до 200 %; layout-tests на Compose.
- **NFR-15.** Контраст текста ≥ 4,5 : 1 (WCAG AA) во всех темах.
- **NFR-16.** Цвет — не единственный носитель информации (используется также иконка/подпись для уровней опасности).

### Локализация
- **NFR-17.** Базовая локаль — `ru-RU`. Полная локализация `en-US` для Google Play.
- **NFR-18.** Все строки — через `strings.xml`; никаких хардкод-литералов в коде (проверяется Detekt-правилом `NoHardcodedStrings`).
- **NFR-19.** Числа форматируются через `NumberFormat`, для dB — `String.format(locale, "%.1f", value)`.

### Совместимость
- **NFR-20.** Поддержка устройств с одним микрофоном (нижний / разговорный — выбирается `AudioSource.UNPROCESSED` с fallback на `MIC`).
- **NFR-21.** Корректная работа на устройствах с notch/cutout/edge-to-edge (Compose `WindowInsets`).
- **NFR-22.** Поддержка landscape и складных экранов (адаптивный layout через `WindowSizeClass`).

---

## 6. UX/UI-требования

### Главный экран (MeasureScreen)
**Layout (top → bottom):**
1. `LargeTopAppBar`: название «Тишина» слева, иконки «?» (дисклеймер) и шестерёнка (Настройки) справа.
2. Большая цифра текущего dB — `displayLarge` (≥ 96 sp), рядом — единица «дБ A».
3. Гейдж: дугообразный 270° индикатор с градиентом зелёный → жёлтый → оранжевый → красный (диапазон 30–110 дБ).
4. Подпись-референс: «Шёпот», «Тихая комната», «Разговор», «Городская улица», «Концерт», «Отбойный молоток».
5. Ряд из трёх Material 3 `Card(filled)`: **Мин**, **Сред (Leq)**, **Макс** — числа `headlineSmall`.
6. Lineographic-график dB(t) за 60 с (Compose Canvas + `Path`, без сторонних libs).
7. Длительность замера (mm:ss).
8. Bottom Action Bar: центральный primary-FAB **Start/Pause**, слева — **Reset** (icon), справа — **Save** (icon `Bookmark`).

### Экран истории (HistoryScreen)
- `LazyColumn` карточек.
- На карточке: дата (relative time, `DateUtils.getRelativeTimeSpanString`), заметка (1 строка ellipsis), avg dB крупно, min–max диапазон мелко, мини-спарклайн.
- Свайп влево → action `Delete` с Snackbar Undo.
- Пустое состояние: иллюстрация (Material Symbol), текст «Здесь будут ваши замеры», ссылка «Сделать первый замер».

### Экран деталей (DetailScreen)
- `TopAppBar` с навигацией назад.
- Большой график полной длительности (zoom-pan жестами — **P1**).
- Полная статистика: min, avg (Leq), max, длительность, дата, время суток.
- Карточка «Заметка» с inline-edit (`TextField`, save on focus loss).
- Кнопки «Поделиться» (Intent с текстом + PNG-снимок графика — **P1**), «Удалить».

### Экран настроек (SettingsScreen)
- Material 3 Preference-стиль через собственную Compose-реализацию.
- Группы: «Измерение» (взвешивание, время-взвешивание, калибровка), «Внешний вид» (тема, динамические цвета, язык), «Данные» (экспорт CSV, удалить всю историю), «О приложении».

### Экран «О приложении» (AboutScreen)
- Версия, иконка, ссылка на GitHub, краткое описание, полный текст дисклеймера (раздел 11), лицензии.

### Темы
- Базовая палитра: фирменный «декабельный» оттенок (предложение — teal `#0FB5BA` для primary), нейтральный фон.
- Поддержка `dynamicLightColorScheme`/`dynamicDarkColorScheme` на API 31+.
- Тёмная тема — primary вечером (следование системе).

### Цветовое кодирование уровней
- ≤ 40 дБ — зелёный `#2E7D32`
- 41–60 — светло-зелёный `#7CB342`
- 61–75 — жёлтый `#FBC02D`
- 76–85 — оранжевый `#F57C00`
- 86–100 — красно-оранжевый `#E64A19`
- > 100 — красный `#C62828`

### Шкала референсов

| dB | Описание |
|---|---|
| 10 | Дыхание |
| 20 | Шёпот, шелест листьев |
| 30 | Тихая спальня |
| 40 | Библиотека, тихий офис |
| 50 | Холодильник, тихая улица |
| 60 | Обычный разговор |
| 70 | Пылесос, оживлённая улица |
| 80 | Будильник, плотный трафик |
| 90 | Мотоцикл, фен |
| 100 | Метро, газонокосилка |
| 110 | Концерт, бензопила |
| 120 | Гром, сирена скорой помощи |
| 130+ | Реактивный самолёт, выстрел |

### Минимализм vs информативность
**Решение: layered information design.** Главный экран — максимум 1 экран без скролла, всё ключевое. Детали и продвинутые опции — на отдельных экранах. FFT/RTA/спектрограмму на главном **не** показываем — уводит в нишу «инженерного» Большакова и обременяет UI.

### Брендинг и иконка
- Иконка: круг с волнообразной звуковой линией внутри, плавно затихающей в плоскую линию → метафора «звук → тишина».
- Цветовой акцент иконки: teal `#0FB5BA` на нейтральном фоне (или градиент к мягкому тёмно-синему для контрастности).
- Adaptive icon: foreground (волна) + background (заливка); статичная версия 512×512 PNG для каталогов сторов.

---

## 7. Архитектура приложения

### Слои (Clean Architecture, упрощённая)

```
+--------------------------------------------------------+
|                  Presentation (UI)                     |
|  Jetpack Compose Screens, ViewModels, Navigation       |
|  - MeasureScreen / MeasureViewModel                    |
|  - HistoryScreen / HistoryViewModel                    |
|  - DetailScreen  / DetailViewModel                     |
|  - SettingsScreen / SettingsViewModel                  |
+--------------------------------------------------------+
                       |  StateFlow / Action
                       v
+--------------------------------------------------------+
|                       Domain                           |
|  UseCases / Interactors (pure Kotlin, no Android deps) |
|  - StartMeasurementUseCase                             |
|  - SaveMeasurementUseCase                              |
|  - GetMeasurementsUseCase                              |
|  - DeleteMeasurementUseCase                            |
|  - GetSettingsUseCase / UpdateCalibrationUseCase       |
|                                                        |
|  Domain models: Measurement, SoundSample, Settings     |
|  Repositories (interfaces): MeasurementRepository,     |
|    SettingsRepository, AudioRepository                 |
+--------------------------------------------------------+
                       |
                       v
+--------------------------------------------------------+
|                      Data                              |
|  - AudioRepositoryImpl (AudioRecord + DSP)             |
|  - MeasurementRepositoryImpl (Room)                    |
|  - SettingsRepositoryImpl (DataStore Preferences)      |
|                                                        |
|  - Room DB (entities + DAO + TypeConverters)           |
|  - DSP module: AWeightingFilter, CWeightingFilter,     |
|    RmsCalculator, SplCalculator, RingBuffer            |
+--------------------------------------------------------+
```

### Модули Gradle
- `:app` — приложение (Activity, Application, Navigation).
- `:core:ui` — общие Compose-компоненты (Gauge, LineChart, ReferenceScale).
- `:core:designsystem` — темы, цвета, типографика, M3-токены.
- `:core:domain` — pure Kotlin: модели, интерфейсы, use-cases.
- `:core:data` — реализации репозиториев, Room, DataStore.
- `:core:audio` — DSP, AudioRecord-обёртки (Coroutines + Flow).
- `:core:testing` — фикстуры, тестовые правила, fake-реализации.
- `:feature:measure`, `:feature:history`, `:feature:settings`, `:feature:about`.

### Поток данных при измерении

```
AudioRecord (PCM 16-bit, 48 kHz, mono, UNPROCESSED)
   │ short[] buffer (Flow<ShortArray>)
   ▼
AudioRepositoryImpl
   ├─► DC-block (1st-order high-pass)
   ├─► A-weighting IIR filter (биквадные секции, IEC 61672-1)
   ├─► Sliding RMS (окно 125 мс Fast / 1 с Slow)
   ├─► dB SPL = 20 * log10(rms / ref) + calibrationOffset
   ▼
Flow<SoundSample(dbA: Float, timestamp: Long)>
   ▼
MeasureViewModel: collect → MeasurementState(current, min, avg, max, history[60s])
   ▼
StateFlow → Compose UI
```

### Диаграмма зависимостей

```
app  ──► feature:measure ──► core:domain
                          ──► core:audio  ──► core:domain
                          ──► core:ui     ──► core:designsystem
app  ──► feature:history ──► core:data    ──► core:domain
                          ──► core:ui
app  ──► feature:settings ──► core:data
                           ──► core:ui
core:data ──► core:domain (импортирует интерфейсы)
```

`core:domain` не зависит ни от чего, кроме `kotlinx.coroutines` и `javax.inject`.

### Архитектурный паттерн UI-слоя — MVI lite
Каждый ViewModel:
- держит `StateFlow<UiState>` (immutable data class),
- принимает `UiEvent` (sealed interface) через `fun onEvent(...)`,
- эмитит `UiEffect` (one-shot) через `Channel<UiEffect>.receiveAsFlow()`.

Это упрощает тестирование (assertion на state-snapshot) и работу с Compose (state-hoisting).

---

## 8. Технологический стек (с обоснованием)

| Слой | Технология | Обоснование |
|---|---|---|
| Язык | **Kotlin 2.0+ (K2-compiler)** | Стандарт Android 2025–2026; нужен для KMP-ready Room |
| UI | **Jetpack Compose + Compose BOM 2026.05.00** (Compose 1.11.1, Material 3 1.5.x) | Per Android Developers и jetc.dev: «The Compose 2026.05.00 BOM editions are out. The stable BOM should point to 1.11.1, containing bug fixes.» |
| Design system | **Material 3 (`androidx.compose.material3`)** | Динамические цвета, обновлённые компоненты (FilledTonalButton, FAB), новый color-role model |
| DI | **Hilt** | Compile-time safety; глубокая интеграция с ViewModel/Compose (`hiltViewModel()`); рекомендован Google. Альтернатива Koin рассматривалась — гибче, но Hilt предпочтительнее для строгой архитектуры и обнаружения ошибок на этапе сборки |
| БД | **Room 2.8.4** (стабильная KMP-совместимая ветка) | Per developer.android.com/kotlin/multiplatform/room — текущая стабильная KMP-версия Room 2.x. Альтернативно — Room 3.0-alpha01 (пакет `androidx.room3`) с полной поддержкой JS/WASM (анонс март 2026, Android Developers Blog) — оставляем в roadmap |
| Settings | **DataStore (Preferences) 1.1.x** | Современная замена `SharedPreferences`; ACID; async; Flow API |
| Async | **kotlinx.coroutines + Flow** | StateFlow / SharedFlow / Channel — идеально для аудио-потока |
| Логирование | **Timber + Android Log** | Лёгкий, без сторонних зависимостей |
| Тестирование | **JUnit 5 (Jupiter)**, **MockK**, **Turbine**, **Robolectric**, **Compose UI Test** | JUnit 5 — современный, parametric tests; MockK — Kotlin-friendly; Turbine — стандарт для Flow |
| Статанализ | **Detekt**, **Ktlint** (через Spotless), **Android Lint** | Качество кода + единый стиль |
| Покрытие | **Kover 0.9+** | Лучше JaCoCo для Kotlin; отчёты HTML/XML; интеграция в GitHub Actions |
| CI/CD | **GitHub Actions** | Бесплатно для публичных репо; нативная интеграция с GitHub Releases |
| Подписание | **r0adkll/sign-android-release@v1** | Стандарт для GitHub Actions |
| Dep-management | **Gradle Version Catalog** (`libs.versions.toml`) | Единый источник версий |

**Сознательный отказ от:**
- Realm — избыточно;
- Retrofit/OkHttp — нет сетевых запросов в MVP;
- Firebase / Crashlytics / Analytics — приватность > удобство; краши собираем через Play Vitals и GitHub Issues;
- Рекламных SDK — принципиально.

---

## 9. Модель данных и схема БД (Room)

```kotlin
@Entity(tableName = "measurements")
data class MeasurementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long,                  // epoch millis
    val durationMs: Long,
    val avgDb: Float,                     // Leq
    val minDb: Float,
    val maxDb: Float,
    val weighting: String,                // "A" | "C" | "Z"
    val timeWeighting: String,            // "FAST" | "SLOW"
    val calibrationOffset: Float,
    val title: String?,                   // ≤ 80 символов
    val note: String?,                    // ≤ 200 символов
    val sampleRateHz: Int,
    val appVersionCode: Int
)

@Entity(
    tableName = "samples",
    foreignKeys = [ForeignKey(
        entity = MeasurementEntity::class,
        parentColumns = ["id"],
        childColumns = ["measurementId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("measurementId")]
)
data class SampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val measurementId: Long,
    val tOffsetMs: Int,                   // относительно начала замера
    val db: Float
)
```

**Стратегия хранения сэмплов:** записываем downsampled-точки 5 Гц (200 мс). Час замера ≈ 18 000 × 12 байт ≈ 216 КБ. Для долгих замеров — лимит 8 часов + предупреждение.

**Версионирование схемы:** `@Database(version = 1, exportSchema = true)`, `schemas` в `app/schemas` коммитятся в репо. Каждая миграция покрыта `MigrationTestHelper`-тестом.

---

## 10. Разрешения и работа с микрофоном

### AndroidManifest.xml

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />

<uses-feature
    android:name="android.hardware.microphone"
    android:required="false" />
```

### Запрос разрешения
- Compose-обёртка `rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission())`.
- Перед запросом: rationale-диалог («Чтобы измерить уровень шума, приложению нужен доступ к микрофону. Аудио НЕ записывается и НЕ покидает устройство.»).
- При permanent denial — диалог с ссылкой «Открыть настройки».

### AudioRecord vs MediaRecorder

Используется **`AudioRecord`**, потому что:
1. `MediaRecorder.getMaxAmplitude()` возвращает только `short`-пиковое значение (0–32767), что даёт фактический потолок dB SPL около **90,3 дБ** (плюс это пиковая, а не RMS-метрика).
2. `MediaRecorder` обязан писать поток в файл (даже если в `/dev/null`), что неэффективно и грязно с точки зрения приватности.
3. `AudioRecord` даёт **сырые PCM-сэмплы**, которые нужны для (а) RMS, (б) A-weighting фильтрации, (в) корректной обработки полного 16-битного диапазона.

### Конфигурация AudioRecord

```kotlin
val sampleRate = 48_000          // см. ниже обоснование
val channelConfig = AudioFormat.CHANNEL_IN_MONO
val audioFormat = AudioFormat.ENCODING_PCM_16BIT
val minBuf = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
val bufferSize = max(minBuf, sampleRate / 10 * 2)  // ≥ 100 мс
val record = AudioRecord(
    MediaRecorder.AudioSource.UNPROCESSED,   // fallback на VOICE_RECOGNITION → MIC
    sampleRate, channelConfig, audioFormat, bufferSize
)
```

**Источник звука: `MediaRecorder.AudioSource.UNPROCESSED`** (API 24+). Это критически важно — стандартный `MIC` пропускается через AGC/шумоподавление/эхокомпенсацию системы, что разрушает SPL-метрику. Если устройство не поддерживает UNPROCESSED (`AudioManager.getProperty(PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED)` == false) — fallback на `VOICE_RECOGNITION` (без AGC у большинства производителей), далее на `MIC`.

### Sample rate: 48 kHz vs 44,1 kHz
**Решение: 48 000 Гц.** Согласно AOSP CDD (section 7.8.3), все Android-устройства обязаны поддерживать либо 44,1 либо 48 кГц **без band-pass / antialiasing-фильтров**. 48 кГц — нативный sample rate большинства мобильных AudioHAL; меньше resampling-артефактов. Если на устройстве 48 кГц не поддерживается — fallback 44,1 кГц.

---

## 11. Калибровка и точность

### Методология вычисления dB SPL

1. Чтение `ShortArray` PCM 16-bit signed.
2. Нормализация: `x[i] = samples[i] / 32768.0f`.
3. DC-block: первый порядок HPF `y[n] = x[n] − x[n−1] + 0,995·y[n−1]`.
4. **A-weighting фильтр** — IIR bi-quad-cascade, коэффициенты по IEC 61672-1 для частот f1 = 20,598997 Гц, f2 = 107,65265 Гц, f3 = 737,86223 Гц, f4 = 12194,217 Гц (источник: Brian Hawkins, «Design of digital filters for frequency weightings (A and C) required for risk assessments of workers exposed to noise», PMC4331191). Реализуется собственным DSP-модулем без сторонних библиотек.
5. **RMS** в скользящем окне:
   - Fast: 125 мс (`N = 0,125 × 48000 = 6000` сэмплов).
   - Slow: 1 с (`N = 48000` сэмплов).
6. `dB = 20 * log10(rms / refRms) + calibrationOffset`.
7. Усреднение через экспоненциально-взвешенное скользящее среднее с τ = 125 мс (Fast) для UI; точное Leq для статистики.

### Калибровка
- **Базовая (factory):** статический `refRms` × поправка устройства. AOSP CDD: «Close-talk config: 90 dB SPL reads RMS of 2500 (16 bit samples). Level tracks linearly from −18 dB to +12 dB relative to 90 dB SPL.» Используем как опорную точку для усреднённого Android-устройства.
- **Пользовательская:** offset −20…+20 дБ. Пояснение в UI: «Возьмите профессиональный шумомер или поставьте смартфон рядом со SPL-метром коллеги, измерьте одно и то же, и подстройте.»
- **Авто-калибровка (P2):** при первом запуске предложить «тихий замер» в безшумной комнате с эталоном 30 дБ и подстроить offset.

### Известные ограничения
1. **Микрофоны смартфонов — MEMS**, оптимизированы под голос (300–3400 Гц). Верхняя частотная характеристика часто завалена выше 8 кГц.
2. **AGC и шумоподавление** на Android невозможно полностью отключить через публичный API на части устройств (в отличие от iOS).
3. **Верхний предел измерения** — у большинства Android-устройств ~90–100 дБ из-за компрессии на уровне HAL и физического клипинга MEMS.
4. **Разброс между моделями** — до ±3–5 дБ. Поэтому единая фабричная калибровка не идеальна.
5. **Положение микрофона** — внизу корпуса, может быть прикрыт пальцем.

### Стандарт IEC 61672
- IEC 61672-1:2013 определяет 2 класса: **Class 1** (±1,1 дБ@1 кГц) и **Class 2** (±1,4 дБ@1 кГц).
- Наше приложение **НЕ заявляет соответствие** ни Class 1, ни Class 2 — Android-устройства не могут пройти эту аттестацию без внешнего калиброванного микрофона.
- Внутренне A-weighting реализуем по математической спецификации стандарта (формы передаточных функций), как делают NIOSH SLM на iOS и Decibel X.

### Текст дисклеймера (готовый, для экрана «О приложении»)

> **Точность измерений.** «Тишина» использует встроенный микрофон вашего устройства, который оптимизирован для записи человеческого голоса. Из-за физических ограничений MEMS-микрофонов смартфонов и невозможности полностью отключить системную обработку звука на ряде Android-устройств, погрешность показаний может составлять **±3–5 дБ(А)**, а в верхнем диапазоне (> 90 дБ) — больше.
>
> Приложение **не сертифицировано** по стандарту IEC 61672 (Class 1/Class 2) и **не предназначено** для официальных измерений шума на рабочих местах, юридически значимой экспертизы или контроля соблюдения санитарных норм. Для таких задач используйте профессиональный шумомер.
>
> Исследование NIOSH (Kardous & Shaw, *Journal of the Acoustical Society of America*, 135(4):EL186, 2014, DOI 10.1121/1.4865269) показало, что из протестированных приложений только **4 iOS-приложения** (SoundMeter от Faber Acoustical, SPLnFFT, NoiSee, Noise Hunter) достигли точности **±2 дБ(А)** от Type-1 SLM. Авторы прямо констатируют: *«Overall, none of the Android-based apps met our initial test criteria, mainly because the Android marketplace is fragmented among many manufacturers with different requirements for parts and lack of uniform audio integration of software and hardware across the different devices.»* Подробнее: https://www.cdc.gov/niosh/noise/about/app.html.
>
> «Тишина» рекомендуется использовать как **ориентировочный** инструмент: для понимания относительных уровней шума, сравнения комнат, контроля динамики, обучения детей и общего любопытства.

---

## 12. Локализация

### Базовая стратегия
- Локали: `values/` (en, фоллбэк), `values-ru/` (русский, первичный).
- Все строки — в `strings.xml`. Плюрали — через `plurals` (`one`, `few`, `many`, `other` для русского).
- Числа и даты — `NumberFormat.getInstance(Locale)` и `DateTimeFormatter.ofLocalizedDateTime(...)`.

### Контрольные строки (выборка)

| key | ru | en |
|---|---|---|
| `app_name` | Тишина | Tisha |
| `app_full_name` | Тишина — измеритель шума | Tisha — Sound Level Meter |
| `measure_start` | Начать измерение | Start |
| `measure_pause` | Пауза | Pause |
| `measure_save` | Сохранить замер | Save measurement |
| `measure_reset` | Сбросить | Reset |
| `stat_min` | Мин | Min |
| `stat_avg` | Среднее | Avg (Leq) |
| `stat_max` | Макс | Max |
| `unit_db` | дБ | dB |
| `unit_dba` | дБ(А) | dB(A) |
| `permission_rationale` | Нужен доступ к микрофону, чтобы измерить уровень звука. Аудио не записывается. | We need microphone access to measure sound level. Audio is not recorded. |
| `disclaimer_short` | Не сертифицированный измеритель. Ориентировочно. | Not a certified meter. For reference only. |

### Тестирование локализаций
- `PseudoLocaleTestRule` в Compose UI Tests — проверяем, что строки не обрезаются.
- Screenshot-тесты (Paparazzi или Roborazzi) для обеих локалей.

---

## 13. Стратегия тестирования

### Целевые показатели покрытия
- **Domain слой:** ≥ 90 % линейного покрытия (Kover).
- **Data слой:** ≥ 80 %.
- **Audio/DSP слой:** ≥ 95 % (математически проверяемый код).
- **ViewModels:** ≥ 85 %.
- **Compose UI:** smoke-тесты на критические сценарии + screenshot-тесты ключевых экранов.

### Unit-тесты (JUnit 5 + MockK + Turbine)
- **`:core:domain`:** use-cases — все ветки, граничные случаи.
- **`:core:audio`:**
  - `RmsCalculatorTest` — синусоидальный сигнал известной амплитуды → проверка RMS с погрешностью < 0,01 дБ.
  - `AWeightingFilterTest` — белый шум → spectral analysis → сравнение с эталонной кривой IEC 61672-1 (точки 31,5 / 125 / 1000 / 8000 / 16000 Гц, допуск ±0,3 дБ).
  - `SplCalculatorTest` — sweep по амплитуде.
  - `RingBufferTest`.
- **ViewModels:** Turbine-тесты state-flow, симуляция событий, проверка эффектов.

### Robolectric-тесты
- `AudioRepositoryImplTest` — мок `AudioRecord` через `ShadowAudioRecord`, проверка lifecycle (start/stop/release).
- `RoomMigrationTest` — `MigrationTestHelper`.

### Compose UI Tests (`androidx.compose.ui.test.junit4`)
- `MeasureScreenTest` — отображение начального состояния, реакция на FAB, валидация диалога сохранения.
- `HistoryScreenTest` — пустое состояние, отображение карточек, свайп-удаление с Undo.
- `SettingsScreenTest` — изменение калибровки, сохранение в DataStore.
- Под Robolectric (Compose 1.6+) — быстрее, чем на эмуляторе.

### Screenshot-тесты (Paparazzi)
- Главный экран в светлой и тёмной теме, ru и en, для трёх Density-классов.
- Pull-request status check, авто-обновление baseline-снимков через label.

### Instrumentation-тесты (минимум)
- Один smoke-test полного пути: запуск → разрешить → измерить 3 с → сохранить → найти в истории → удалить. Выполняется на эмуляторе API 26 / 30 / 34 в GitHub Actions.

### Fake/Test-doubles
- `FakeAudioRepository` в `:core:testing` — эмиттит синтетический Flow для UI-тестов.
- `FakeMeasurementRepository` с in-memory storage.

---

## 14. CI/CD на GitHub Actions

### Структура workflow

`.github/workflows/`:
- **`ci.yml`** — на каждый push/PR в `main`/`develop`.
- **`release.yml`** — на git-tag `v*.*.*`.
- **`nightly.yml`** (P1) — ежедневный полный прогон.

### `ci.yml` (упрощённо)

```yaml
name: CI
on:
  push: { branches: [main, develop] }
  pull_request: { branches: [main, develop] }

jobs:
  static-checks:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: 'zulu', java-version: '17' }
      - uses: gradle/actions/setup-gradle@v3
      - run: ./gradlew detekt ktlintCheck lint

  unit-tests:
    needs: static-checks
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: 'zulu', java-version: '17' }
      - uses: gradle/actions/setup-gradle@v3
      - run: ./gradlew testDebugUnitTest koverXmlReportDebug
      - uses: codecov/codecov-action@v4
        with:
          files: ./app/build/reports/kover/reportDebug.xml

  instrumentation-tests:
    needs: unit-tests
    runs-on: ubuntu-latest
    strategy:
      matrix:
        api-level: [26, 30, 34]
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: 'zulu', java-version: '17' }
      - uses: reactivecircus/android-emulator-runner@v2
        with:
          api-level: ${{ matrix.api-level }}
          script: ./gradlew connectedDebugAndroidTest

  build:
    needs: [unit-tests]
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: 'zulu', java-version: '17' }
      - run: ./gradlew assembleDebug bundleDebug
      - uses: actions/upload-artifact@v4
        with:
          name: debug-apk
          path: app/build/outputs/apk/debug/*.apk
```

### `release.yml`

```yaml
name: Release
on:
  push:
    tags: ['v*.*.*']

jobs:
  build-and-publish:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: 'zulu', java-version: '17' }
      - name: Decode keystore
        run: |
          mkdir -p $RUNNER_TEMP/keystore
          echo "${{ secrets.KEYSTORE_BASE64 }}" | base64 -d \
            > $RUNNER_TEMP/keystore/release.jks
      - name: Build release AAB & APK
        env:
          KEYSTORE_PATH: ${{ runner.temp }}/keystore/release.jks
          KEYSTORE_PASSWORD: ${{ secrets.KEYSTORE_PASSWORD }}
          KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
          KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
        run: ./gradlew bundleRelease assembleRelease
      - uses: actions/upload-artifact@v4
        with:
          name: release-artifacts
          path: |
            app/build/outputs/bundle/release/*.aab
            app/build/outputs/apk/release/*.apk
            app/build/outputs/mapping/release/mapping.txt
      - uses: softprops/action-gh-release@v2
        with:
          files: |
            app/build/outputs/apk/release/*.apk
            app/build/outputs/bundle/release/*.aab
          generate_release_notes: true
```

### Secrets
- `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
- Опционально: `PLAY_SERVICE_ACCOUNT_JSON` для авто-публикации через `r0adkll/upload-google-play@v1` (P1).
- Опционально: `RUSTORE_API_KEY` для авто-публикации через RuStore Public API (P1).

### Версионирование
- SemVer `MAJOR.MINOR.PATCH`.
- `versionCode` = `MAJOR * 10000 + MINOR * 100 + PATCH` (детерминированно) или `github.run_number` (монотонно).
- `versionName` берётся из git-tag.

### Артефакты
- **Google Play:** AAB.
- **RuStore:** AAB (поддерживается нативно с февраля 2024 — официальная документация RuStore: «*RuStore supports uploading applications in APK and AAB formats*») или APK.
- **Samsung Galaxy Store:** AAB (Galaxy Store сам генерирует универсальный APK).
- Mapping.txt сохраняется отдельным артефактом и аплоадится в Play Console для деобфускации крашей.

---

## 15. Дистрибуция

### Google Play
- **Карточка:** название «Тишина — измеритель шума» (ru) / «Tisha — Sound Level Meter» (en). В качестве `app_name` в манифесте — `Тишина` / `Tisha`.
- **Data Safety декларация:**
  - Audio files collected? **No** (мы НЕ записываем аудио и НЕ сохраняем сэмплы вне устройства).
  - App activity / App info collected? **No**.
  - All data: stored only on device.
- **Permissions declaration:** `RECORD_AUDIO` — обоснование «Core functionality: measuring ambient sound levels in real time. No audio is stored or transmitted.»
- **Возрастной рейтинг:** IARC → 3+ (Everyone).
- **Privacy Policy:** обязательна. Хостим на GitHub Pages (`https://<user>.github.io/tishina-android/privacy/`).
- **Описание:** ASO-ключи в полном описании, но **не в названии** (`Тишина` уникальное, бренд + дескриптор). Это снижает риск претензии по impersonation/generic-naming (опыт с приложением NetWalk это подтверждает).
- **Target SDK:** 35 (Android 15) — минимум для новых публикаций в 2025–2026.
- **Подписание:** Play App Signing (Google управляет ключом), upload-key храним в GitHub Secrets.

### RuStore
- Категория: «Полезные инструменты».
- Название в карточке: «Тишина — измеритель шума».
- **Особенности модерации:** ручная проверка каждой версии. Per Илья Сверчков, операционный директор RuStore (пресс-релиз VK / CNews, 23 авг 2023): «*Среднее время от проверки до публикации приложения составляет менее часа*»; ~93 % приложений проходят модерацию.
- Чувствительность модерации к:
  - честному описанию запрашиваемых разрешений (явно объяснить, зачем `RECORD_AUDIO`);
  - наличию политики конфиденциальности (обязательно);
  - запрету на чрезмерную рекламу (у нас её нет — преимущество);
  - запрету «приложение является обёрткой над сайтом» (мы — нативный Compose-app).
- **Платёжная система:** не требуется (free, no IAP).
- **Формат сборки:** Per официальной документации RuStore (rustore.ru/help/en/developers): «*RuStore supports uploading applications in APK and AAB formats*». То есть **загружаем нативный AAB** — никаких костылей с bundletool/universal APK не требуется.
- **Возрастной рейтинг:** 0+.

### Samsung Galaxy Store
- Название в карточке: «Tisha — Sound Level Meter» (англ. рынок) / «Тишина — измеритель шума» (RU-локаль).
- Per официального Samsung Developer FAQ (developer.samsung.com/galaxy-store/faq.html): «*Galaxy Store requires a target API level >=33 and at least one 64-bit binary to be registered.*»
- Принимает AAB и сам генерирует универсальный APK; **нет Play Asset Delivery**, поэтому Dynamic Feature-модули не используем.
- Требует commercial seller status (D-U-N-S или ИП/юрлицо) — шаг развёртывания, который автору нужно сделать заранее.
- Запрет на любые ссылки на «Samsung» / «Galaxy» в названии/иконке/описании.
- Модерация — обычно 2–7 рабочих дней.

### Возрастной рейтинг (сводно)
- Google Play / IARC: 3+ (Everyone).
- RuStore: 0+.
- Samsung Galaxy Store: All ages.

---

## 16. Что НЕ входит в MVP (out of scope)

| Фича | Причина откладывания | Возможная итерация |
|---|---|---|
| FFT/RTA-спектр | Уведёт в нишу «инженерного» приложения | v1.1 / v2.0 |
| Дозиметр (TWA, NIOSH/OSHA Dose) | Требует строгой калибровки, иначе вводит в заблуждение | v2.0 |
| Экспорт PNG/CSV | P1; не блокер для MVP | v1.1 |
| Поделиться измерением (Intent) | P1 | v1.1 |
| Виджет на главном экране (Glance) | Низкий приоритет | v1.2 |
| Wear OS-компаньон | Маленький TAM | v2.0+ |
| Auto-калибровка по реф-устройству | Требует исследовательской работы | v1.2 |
| Облачная синхронизация истории | Нарушает privacy-first позиционирование | Никогда / опционально с шифрованием |
| Реклама / IAP / Pro-версия | Принципиально нет | Никогда |
| FFT-спектрограмма | См. выше | v2.0 |
| Поддержка Bluetooth-микрофонов | Сложность калибровки | v2.0+ |
| Pre-built калибровки под популярные модели | Требует сбора данных от пользователей | v1.2 (telemetry-free через GitHub Issues) |
| Импорт-экспорт настроек | Низкий спрос | v1.3 |

---

## 17. Идентификация проекта

### Финальное решение

| Параметр | Значение |
|---|---|
| **Краткое название (app_name)** | Тишина |
| **Краткое название (en)** | Tisha |
| **Полное название карточки в сторах (ru)** | Тишина — измеритель шума |
| **Полное название карточки в сторах (en)** | Tisha — Sound Level Meter |
| **Application ID / package name** | `ru.dmdp.tishina` |
| **GitHub-репозиторий** | `tishina-android` |
| **GitHub Pages (Privacy Policy)** | `https://<user>.github.io/tishina-android/privacy/` |
| **Лицензия исходного кода** | Apache 2.0 |
| **Базовая ветка** | `main` |
| **Ветка разработки** | `develop` |

### Обоснование выбора
- **Эмоциональная нейтральность и позитив.** «Тишина» — то, ради чего пользователь измеряет шум; имя несёт обещание, а не пугает термином.
- **Лаконичность.** Одно слово, легко набирается в поиске, помещается на иконке.
- **Транслитерация.** `Tisha` корректно произносится англоязычными пользователями и не совпадает с известными брендами в категории Tools.
- **Юридическая безопасность.** Не использует generic-термин (`Шумомер`, `Sound Meter`, `Decibel Meter`), что снижает риск претензий со стороны Google Play по impersonation-политике (релевантно, учитывая прошлый опыт с приложением NetWalk).
- **ASO-стратегия.** Generic-ключи (`измеритель шума`, `шумомер`, `уровень звука`, `дБ метр`, `sound level meter`, `decibel meter`) уходят в подзаголовок и полное описание карточки, а не в название.

### Маркетинговый слоган (кандидаты)
- «Измерь, чтобы найти тишину.»
- «Шум вокруг — в цифрах в руке.»
- «Тишина — твой карманный шумомер. Без рекламы. Навсегда.»

### Иконка
- Минимализм: круг с волнообразной звуковой линией, плавно вырождающейся в плоскую горизонталь.
- Палитра: teal `#0FB5BA` (foreground) на нейтральном тёмно-синем (`#0E2433`) или светло-сером фоне.
- Adaptive icon (Android 8+): отдельные XML-слои foreground/background.
- Каталоговая 512×512 PNG версия — для RuStore и Samsung Galaxy Store.

---

## 18. Глоссарий

| Термин | Определение |
|---|---|
| **SPL** | Sound Pressure Level, уровень звукового давления; измеряется в дБ относительно опорного давления 20 мкПа |
| **dB / дБ** | Децибел, логарифмическая безразмерная единица |
| **dB(A) / дБА** | A-взвешенный уровень — корректировка частотной характеристики под чувствительность человеческого уха |
| **dB(C) / дБС** | C-взвешенный уровень — менее агрессивная коррекция, для высоких уровней и низких частот |
| **dB(Z)** | Z-weighted = zero weighting = без коррекции, плоская характеристика |
| **Leq** | Equivalent Continuous Sound Level — энергетически усреднённый уровень за период |
| **RMS** | Root Mean Square — среднеквадратическое значение амплитуды, основа для расчёта SPL |
| **A-weighting** | IIR-фильтр со специфической АЧХ по IEC 61672-1, эмулирующий чувствительность уха |
| **Fast / Slow** | Time weighting — τ=125 мс / τ=1 с, экспоненциально-взвешенное усреднение для индикации |
| **Impulse** | Time weighting τ=35 мс attack / 1,5 с decay; для коротких звуков (выстрел, удар) |
| **IEC 61672** | Международный стандарт на шумомеры; Class 1 (±1,1 дБ) и Class 2 (±1,4 дБ) |
| **NIOSH** | National Institute for Occupational Safety and Health (США) — автор приложения NIOSH SLM на iOS |
| **AudioRecord** | Android API для получения сырых PCM-сэмплов с микрофона |
| **MediaRecorder** | Android API для записи аудио в файлы; даёт только peak-амплитуду через `getMaxAmplitude()`; не подходит для SPL |
| **AGC** | Automatic Gain Control — авто-регулировка уровня микрофона; искажает SPL-метрику |
| **MEMS-микрофон** | Микро-электромеханический микрофон в смартфонах; оптимизирован под голос |
| **MVI / MVVM** | Архитектурные паттерны UI |
| **Hilt** | DI-фреймворк от Google, основан на Dagger |
| **Room** | Android Jetpack ORM-обёртка над SQLite |
| **DataStore** | Современная замена `SharedPreferences` |
| **Compose BOM** | Bill of Materials для согласования версий Compose-библиотек |
| **Kover** | Kotlin-native инструмент покрытия кода |
| **Detekt** | Статический анализатор Kotlin-кода |
| **Ktlint** | Linter для стиля Kotlin |
| **RuStore** | Российский магазин приложений (VK) |
| **AAB** | Android App Bundle — формат публикации в Google Play, RuStore, Samsung Galaxy Store |
| **APK** | Android Package — устанавливаемый бинарь |
| **TWA** | Trusted Web Activity — Android-обёртка над PWA; **не используется** в этом проекте |
| **UNPROCESSED audio source** | Источник звука без AGC/NS/AEC, доступен с API 24 |
| **CDD** | Compatibility Definition Document — спецификация AOSP, описывающая обязательные характеристики Android-устройств |

---

## Приложение A. Бюджет рисков и контрольные точки

| Риск | Вероятность | Влияние | Митигация |
|---|---|---|---|
| Реджект Google Play за generic name | Низкая (имя брендовое) | Высокое | Использовать `Тишина / Tisha` как app_name; generic-ключи только в описании |
| Реджект RuStore по описанию `RECORD_AUDIO` | Низкая | Среднее | Явное описание в карточке: «Микрофон используется только для измерения уровня окружающего шума. Аудио НЕ сохраняется» |
| Жалобы пользователей на неточность | Высокая | Среднее | Видимый дисклеймер + кнопка калибровки в одно касание |
| Невозможность отключить AGC на части устройств | Высокая | Среднее | Чётко логировать, какой `AudioSource` доступен; в Настройках показать диагностику |
| Полупустая история на старте | Высокая | Низкое | Empty state с CTA «Сделать первый замер» |
| Низкая видимость в Play Store | Высокая | Среднее | ASO-оптимизация: ключи «измеритель шума», «уровень звука», «дБ метр», «sound level meter», «decibel meter» в полном описании, но не в названии |
| Коллизия package `ru.dmdp.tishina` с существующим приложением | Очень низкая | Высокое | Проверить уникальность в Google Play / RuStore до релиза; зарезервировать имя в Play Console |

---

## Приложение B. Контрольный чек-лист перед релизом MVP

- [ ] App name `Тишина` / `Tisha` зарезервирован в Google Play Console.
- [ ] Package `ru.dmdp.tishina` уникален во всех целевых сторах.
- [ ] GitHub-репозиторий `tishina-android` создан, README заполнен.
- [ ] Privacy Policy опубликована на GitHub Pages (`/tishina-android/privacy/`).
- [ ] Data Safety в Google Play Console заполнена корректно.
- [ ] Permissions declaration `RECORD_AUDIO` сформирована.
- [ ] Скриншоты для Play Console (8), RuStore (≤10), Samsung Store.
- [ ] Иконки adaptive (foreground + background, 512×512 PNG для каталога).
- [ ] Feature graphic 1024×500 для Google Play.
- [ ] Описания на ru и en (короткое + полное).
- [ ] Test track в Play Console (internal testing) пройден.
- [ ] Дисклеймер о точности виден на главном экране (иконка «?»).
- [ ] Калибровка работает и сохраняется в DataStore.
- [ ] CI зелёный, покрытие ≥ заявленных порогов.
- [ ] R8/ProGuard включён, mapping.txt сохраняется.
- [ ] Размер AAB ≤ 8 МБ.
- [ ] Сборка подписана release-ключом, ключ в GitHub Secrets.

---

*Конец спецификации. Документ готов к передаче в качестве основы для составления плана разработки и последующего написания кода.*
