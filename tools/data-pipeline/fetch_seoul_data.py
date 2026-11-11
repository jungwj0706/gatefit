#!/usr/bin/env python3
import argparse
import hashlib
import json
import sys
import urllib.parse
import urllib.request
import zipfile
from datetime import datetime, timezone
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
RAW_DIR = REPO_ROOT / "data" / "raw" / "seoul"
MANIFEST_PATH = RAW_DIR / "manifest.json"

DOWNLOAD_URL = "https://datafile.seoul.go.kr/bigfile/iot/inf/nio_download.do?&useCache=false"
PAGE_URL = "https://data.seoul.go.kr/dataList/{dataset_id}/S/1/datasetView.do"

PROVIDER = "서울특별시 (서울 열린데이터광장)"
LICENSE = "공공누리 제1유형 (출처표시, 상업적 이용 및 변경 가능)"

# file_seq는 데이터셋 페이지의 파일 목록에서 공간파일(zip)에 부여된 번호
DATASETS = {
    "vehicle_entrances": {
        "dataset_id": "OA-21700",
        "title": "서울시 차량 출입구 정보",
        "file": "OA-21700_vehicle_entrances.zip",
        "file_seq": "1",
        "inf_seq": "3",
    },
    "facilities": {
        "dataset_id": "OA-21698",
        "title": "서울시 시설물 정보",
        "file": "OA-21698_facilities.zip",
        "file_seq": "1",
        "inf_seq": "3",
    },
}


def download(dataset):
    page_url = PAGE_URL.format(dataset_id=dataset["dataset_id"])
    form = urllib.parse.urlencode({
        "infId": dataset["dataset_id"],
        "seqNo": "",
        "seq": dataset["file_seq"],
        "infSeq": dataset["inf_seq"],
    }).encode("ascii")
    request = urllib.request.Request(DOWNLOAD_URL, data=form, headers={"Referer": page_url})
    with urllib.request.urlopen(request, timeout=60) as response:
        return response.read()


def manifest_entry(dataset, payload, downloaded_at):
    return {
        "dataset_id": dataset["dataset_id"],
        "title": dataset["title"],
        "page_url": PAGE_URL.format(dataset_id=dataset["dataset_id"]),
        "file": dataset["file"],
        "sha256": hashlib.sha256(payload).hexdigest(),
        "downloaded_at": downloaded_at,
    }


def main():
    argparse.ArgumentParser(description="서울 열린데이터광장 출입구 원본 데이터 수집").parse_args()

    downloaded_at = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    RAW_DIR.mkdir(parents=True, exist_ok=True)

    entries = {}
    for key, dataset in DATASETS.items():
        payload = download(dataset)
        target = RAW_DIR / dataset["file"]
        target.write_bytes(payload)
        # 오류 페이지가 저장되는 경우를 막기 위해 zip 여부 확인
        if not zipfile.is_zipfile(target):
            target.unlink()
            raise SystemExit(f"{dataset['dataset_id']}: 응답이 zip 파일이 아님")
        entries[key] = manifest_entry(dataset, payload, downloaded_at)
        print(f"보존: {target.relative_to(REPO_ROOT)} ({len(payload):,} bytes)")

    manifest = {"provider": PROVIDER, "license": LICENSE, "datasets": entries}
    MANIFEST_PATH.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"기록: {MANIFEST_PATH.relative_to(REPO_ROOT)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
