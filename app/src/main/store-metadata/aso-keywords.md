# ASO keyword strategy — Tisha (Тишина)

Living document for store-listing keyword choices. Per spec § 17, generic keywords (`шумомер`, `измеритель шума`, `sound level meter`, etc.) live in **subtitle** and **full description**, never in the **app name** — the name stays `Тишина` / `Tisha` to avoid Google Play impersonation/generic-naming flags (NetWalk lesson applied here).

## Store-listing titles (full title field in store carousel)

| Store | Title | Length | Limit |
|---|---|---:|---:|
| Google Play (ru-RU) | `Тишина — измеритель шума` | 24 | 30 |
| Google Play (en-US) | `Tisha — Sound Level Meter` | 25 | 30 |
| RuStore (ru-RU) | `Тишина — шумомер без рекламы` | 28 | 50 |
| Samsung Galaxy Store (en-US) | `Tisha — Sound Level Meter` | 25 | 30 |

Short descriptions and full descriptions live next to this file in `google-play/`, `rustore/`, and `samsung/` subdirectories; `StoreMetadataLengthLimitTest` enforces character limits on every PR.

## Primary keywords — Russian (russian-language stores)

Distribute naturally across the full description. Avoid keyword stuffing (Google Play flags it; RuStore moderators flag it).

- измеритель шума
- шумомер
- уровень шума
- дБ метр
- измерение звука
- громкость
- децибел
- замер шума
- замер звука

## Secondary keywords — Russian (long tail, lower volume but high intent)

- фоновый шум
- шум соседей
- шум на работе
- шум в офисе
- шум стройки
- безопасный уровень звука
- громкость звука в квартире
- уровень шума в децибелах

## Primary keywords — English (international stores)

- sound level meter
- decibel meter
- noise meter
- dB meter
- SPL meter
- sound measurement

## Secondary keywords — English

- noise level app
- ambient noise meter
- decibel app
- loudness meter
- room noise level
- workplace noise app

## Keywords to AVOID

- `Galaxy`, `Samsung` (Samsung Store Terms of Service explicitly forbid these in title, icon, description).
- `professional`, `Class 1`, `Class 2`, `IEC 61672 certified` — we are not certified; using these terms would attract complaints and store moderation review (spec § 11).
- `medical`, `hearing test`, `tinnitus diagnosis` — keep clear of health-app classification under Google Play and Roskomnadzor adjacent rules.
- Competitor names (`Decibel X`, `Sound Meter PRO`, `NIOSH SLM`) — direct comparison is fine in marketing materials, not in store metadata.

## Localization notes

- `дБ` vs `дБ(А)` — use `дБ(А)` in body copy where weighting matters; `дБ метр` is a search-form keyword users actually type, keep the no-parenthesis variant in the keyword list.
- `шумомер` is the single most-searched query in Russian for this category; it must appear in the RuStore short description and at least twice in the Google Play full description.
- English `dB` vs `decibel` — use both. Users search for the long form ("decibel meter") more often than `dB meter` per Search Console long-tail data (approximate ratio 3:1 in this category).

## Localization NOT done in v1.0

- German, French, Spanish, Portuguese full descriptions — v1.1+.
- Simplified Chinese / Japanese / Korean — these markets need transliterated app names plus careful legal review; out of v1.0 scope.

## Update cadence

Revisit this file after the first 30 days of public release based on:

1. Google Play Console "Search terms" report (which queries actually drove installs).
2. RuStore Console download breakdown by source query.
3. User reviews citing missing features that are actually present (signals a description discoverability gap).
