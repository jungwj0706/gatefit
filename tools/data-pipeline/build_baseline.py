#!/usr/bin/env python3
import argparse
import hashlib
import json
import sys
from pathlib import Path

from gatefit_pipeline import emit, transform
from gatefit_pipeline.dbf import read_table_from_zip
from gatefit_pipeline.vehicle_specs import build_vehicle_specs

REPO_ROOT = Path(__file__).resolve().parents[2]
RAW_DIR = REPO_ROOT / "data" / "raw" / "seoul"
REPORT_PATH = REPO_ROOT / "data" / "processed" / "seoul_baseline_report.json"
VEHICLE_SPECS_PATH = REPO_ROOT / "data" / "curated" / "vehicle_model_specs.json"
SEED_DIR = REPO_ROOT / "supabase" / "seed"
ASSET_DIR = REPO_ROOT / "app" / "src" / "main" / "assets"
SNAPSHOT_PATH = ASSET_DIR / "baseline" / "seoul_baseline.json"
SPEC_CATALOG_PATH = ASSET_DIR / "catalog" / "vehicle_model_specs.json"

GENERATED_NOTICE = "tools/data-pipeline/build_baseline.py 생성 파일, 직접 수정 금지"


def load_manifest():
    manifest = json.loads((RAW_DIR / "manifest.json").read_text(encoding="utf-8"))
    for dataset in manifest["datasets"].values():
        path = RAW_DIR / dataset["file"]
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        # 보존된 원본이 수집 당시와 다르면 출처 기록을 신뢰할 수 없으므로 중단
        if digest != dataset["sha256"]:
            raise SystemExit(f"{path.name}: sha256 불일치, 원본 재수집 필요")
    return manifest


def source_header(manifest, key):
    dataset = manifest["datasets"][key]
    return [
        GENERATED_NOTICE,
        f"출처: 서울특별시, 서울 열린데이터광장 「{dataset['title']}」 ({dataset['dataset_id']})",
        f"이용조건: {manifest['license']}",
    ]


def write_text(path, text):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


def main():
    parser = argparse.ArgumentParser(description="서울시 출입구 공공데이터를 GATEFIT 기준 데이터로 정제")
    parser.add_argument("--check", action="store_true", help="생성물이 최신인지 확인만 하고 파일은 쓰지 않음")
    args = parser.parse_args()

    manifest = load_manifest()
    datasets = manifest["datasets"]
    facility_rows = read_table_from_zip(RAW_DIR / datasets["facilities"]["file"], "sisulSpfc")
    entrance_rows = read_table_from_zip(RAW_DIR / datasets["vehicle_entrances"]["file"], "entrcId")
    dataset, report = transform.build_dataset(facility_rows, entrance_rows, manifest)
    vehicle_specs = build_vehicle_specs(json.loads(VEHICLE_SPECS_PATH.read_text(encoding="utf-8")))

    outputs = {
        SEED_DIR / "01_data_sources.sql": emit.render_seed_sql(
            "data_sources",
            dataset["data_sources"],
            [GENERATED_NOTICE],
            emit.upsert_clause("data_sources"),
        ),
        # 운영 중 검증·수정된 레코드를 기준 데이터가 덮어쓰지 않도록 기존 행은 유지
        SEED_DIR / "02_facilities.sql": emit.render_seed_sql(
            "facilities",
            dataset["facilities"],
            source_header(manifest, "facilities"),
            "on conflict (id) do nothing",
        ),
        SEED_DIR / "03_entrances.sql": emit.render_seed_sql(
            "entrances",
            dataset["entrances"],
            source_header(manifest, "vehicle_entrances"),
            "on conflict (id) do nothing",
        ),
        SEED_DIR / "04_vehicle_model_specs.sql": emit.render_seed_sql(
            "vehicle_model_specs",
            vehicle_specs,
            [GENERATED_NOTICE, "출처: 현대자동차 공식 카탈로그, 원본 목록은 data/curated/vehicle_model_specs.json"],
            emit.upsert_clause("vehicle_model_specs"),
        ),
        SNAPSHOT_PATH: emit.render_snapshot_json(dataset, datasets["vehicle_entrances"]["downloaded_at"]),
        SPEC_CATALOG_PATH: emit.render_vehicle_spec_catalog_json(vehicle_specs),
        REPORT_PATH: json.dumps(report, ensure_ascii=False, indent=2) + "\n",
    }

    if args.check:
        stale = [
            str(path.relative_to(REPO_ROOT))
            for path, text in outputs.items()
            if not path.exists() or path.read_text(encoding="utf-8") != text
        ]
        if stale:
            print("최신이 아닌 생성물: " + ", ".join(stale))
            return 1
        print("모든 생성물 최신 상태")
        return 0

    for path, text in outputs.items():
        write_text(path, text)
        print(f"생성: {path.relative_to(REPO_ROOT)} ({len(text.encode('utf-8')):,} bytes)")
    print(json.dumps(report["counts"], ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
