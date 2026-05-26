#!/usr/bin/env python3
"""
collect_store_screenshots.py — copy Roborazzi baselines into store-metadata folders
with the numbered filenames each store listing expects.

Roborazzi records two snapshot variants per scene at 1080 × 1920 px:
  - `*_ru.png` (Russian locale, Cyrillic UI strings, Russian fixture data)
  - `*_en.png` (English locale, English UI strings, English fixture data)

This script does NOT regenerate snapshots — run

    ./gradlew :feature:<module>:testDebugUnitTest \
        --tests "*StoreScreenshotTest*" -Proborazzi.test.record=true

first, then run this script to publish the PNGs into the catalogue folder.

Scenes (in carousel order):
    01 measure-running   — Measure feature, mid-measurement, chart visible.
    02 measure-idle      — Measure feature, idle / ready-to-start state.
    03 history-list      — History feature, list of saved measurements with notes.
    04 history-detail    — History detail screen with chart + statistics + note.
    05 settings          — Settings screen with calibration slider at +3 dB.
    06 about             — About screen with brand, version, accuracy disclaimer.
"""
from __future__ import annotations

import argparse
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

# Scene -> source-path-template tuple. {lang} placeholder is replaced with `ru` or `en`.
SCENES: list[tuple[str, str]] = [
    (
        "01-measure-running.png",
        "feature/measure/src/test/snapshots/StoreScreenshotTest_measure_store_running_light_{lang}.png",
    ),
    (
        "02-measure-idle.png",
        "feature/measure/src/test/snapshots/StoreScreenshotTest_measure_store_idle_light_{lang}.png",
    ),
    (
        "03-history-list.png",
        "feature/history/src/test/snapshots/StoreScreenshotTest_history_store_list_light_{lang}.png",
    ),
    (
        "04-history-detail.png",
        "feature/history/src/test/snapshots/StoreScreenshotTest_history_store_detail_light_{lang}.png",
    ),
    (
        "05-settings.png",
        "feature/settings/src/test/snapshots/StoreScreenshotTest_settings_store_default_light_{lang}.png",
    ),
    (
        "06-about.png",
        "feature/about/src/test/snapshots/StoreScreenshotTest_about_store_default_light_{lang}.png",
    ),
]


def collect(target_dir: Path, lang: str) -> int:
    target_dir.mkdir(parents=True, exist_ok=True)
    missing: list[str] = []
    for dest_name, source_template in SCENES:
        source = ROOT / source_template.format(lang=lang)
        if not source.exists():
            missing.append(str(source.relative_to(ROOT)))
            continue
        dest = target_dir / dest_name
        shutil.copy2(source, dest)
        print(f"  copied {source.relative_to(ROOT)}  ->  {dest.relative_to(ROOT)}")
    if missing:
        print(
            "\nMissing source PNGs (run record-roborazzi first):",
            *("  - " + m for m in missing),
            sep="\n",
            file=sys.stderr,
        )
        return 1
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--target",
        choices=("rustore", "google-play-ru", "google-play-en", "samsung"),
        default="rustore",
        help="Store metadata folder + locale to publish into (default: rustore).",
    )
    args = parser.parse_args()

    if args.target == "rustore":
        target = ROOT / "app/src/main/store-metadata/rustore/screenshots"
        lang = "ru"
    elif args.target == "google-play-ru":
        target = ROOT / "app/src/main/store-metadata/google-play/screenshots/ru-RU"
        lang = "ru"
    elif args.target == "google-play-en":
        target = ROOT / "app/src/main/store-metadata/google-play/screenshots/en-US"
        lang = "en"
    else:
        target = ROOT / "app/src/main/store-metadata/samsung/screenshots"
        lang = "en"

    print(f"Publishing {lang}-locale snapshots into {target.relative_to(ROOT)}")
    return collect(target, lang)


if __name__ == "__main__":
    sys.exit(main())
