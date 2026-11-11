import re
import uuid
from collections import Counter

from . import normalize

# 재실행해도 동일한 ID가 나오도록 고정한 네임스페이스
ID_NAMESPACE = uuid.UUID("6f1d3c52-0c0e-4b7e-9a57-8d1c2a4e7b10")

_SOURCE_ENTRANCE_ID = re.compile(r"^([A-Z]\d+)_(C\d+)$")

_HOURS_FIELDS = (
    ("weekday_hours", "wkOpertime"),
    ("saturday_hours", "satOpertim"),
    ("sunday_hours", "sumOpertim"),
    ("holiday_hours", "hlOpertime"),
)


def stable_id(kind, key):
    return str(uuid.uuid5(ID_NAMESPACE, f"{kind}:{key}"))


def build_data_source(dataset, license_name, original_updated_at):
    return {
        "id": stable_id("source", dataset["dataset_id"]),
        "source_name": f"서울 열린데이터광장 「{dataset['title']}」 ({dataset['dataset_id']})",
        "source_type": "PUBLIC",
        "source_url": dataset["page_url"],
        "license": license_name,
        "imported_at": dataset["downloaded_at"],
        "original_updated_at": original_updated_at,
        "notes": "일회성 사업으로 구축된 데이터셋, 사업 종료 이후 현행화되지 않음",
    }


def build_dataset(facility_rows, entrance_rows, manifest):
    datasets = manifest["datasets"]
    imported_at = datasets["vehicle_entrances"]["downloaded_at"]
    report = Counter()
    rejected = {"heights": [], "hours": [], "coordinates": [], "entrance_ids": []}

    facility_source = build_data_source(
        datasets["facilities"],
        manifest["license"],
        _single_reference_date(facility_rows),
    )
    entrance_source = build_data_source(
        datasets["vehicle_entrances"],
        manifest["license"],
        _single_reference_date(entrance_rows),
    )

    entrances = _build_entrances(entrance_rows, entrance_source["id"], imported_at, report, rejected)
    facility_keys_with_entrances = {entrance["_facility_key"] for entrance in entrances}
    facilities = _build_facilities(
        facility_rows, facility_keys_with_entrances, facility_source["id"], imported_at, report, rejected
    )

    known_facility_keys = {facility["source_record_id"] for facility in facilities}
    linked = []
    for entrance in entrances:
        facility_key = entrance.pop("_facility_key")
        # 시설 데이터셋에 없는 출입구는 시설 좌표를 대신 만들지 않고 제외
        if facility_key not in known_facility_keys:
            report["entrances_dropped_without_facility"] += 1
            continue
        linked.append(entrance)

    report["facilities_out"] = len(facilities)
    report["entrances_out"] = len(linked)
    report["facilities_with_entrances"] = len({entrance["facility_id"] for entrance in linked})
    report["entrances_with_height"] = sum(1 for entrance in linked if entrance["max_height_mm"] is not None)
    report["entrances_with_any_hours"] = sum(
        1 for entrance in linked if any(entrance[field] is not None for field, _ in _HOURS_FIELDS)
    )
    for entrance in linked:
        report[f"entrance_status_{entrance['status']}"] += 1
        report[f"entrance_type_{entrance['entrance_type']}"] += 1

    dataset = {
        "data_sources": [entrance_source, facility_source],
        "facilities": facilities,
        "entrances": linked,
    }
    return dataset, {"counts": dict(sorted(report.items())), "rejected": rejected}


def _single_reference_date(rows):
    dates = {normalize.parse_reference_date(row.get("refDate")) for row in rows}
    dates.discard(None)
    return max(dates) if dates else None


def _build_facilities(rows, keys_with_entrances, source_id, imported_at, report, rejected):
    facilities = {}
    for row in sorted(rows, key=lambda item: item["sisulId"]):
        report["facility_rows_in"] += 1
        key = row["sisulId"].strip()
        name = normalize.clean_text(row.get("sisulNm"))
        if not key or not name or key in facilities:
            report["facilities_dropped_invalid_or_duplicate_id"] += 1
            continue

        note = normalize.clean_text(row.get("etc"))
        has_entrances = key in keys_with_entrances
        # 원본에 중복이라고 기록된 시설은 연결된 출입구가 없을 때만 제외
        if note and normalize.FACILITY_DUPLICATE_NOTE.search(note) and not has_entrances:
            report["facilities_dropped_marked_duplicate"] += 1
            continue

        coordinate = normalize.parse_coordinate(row.get("latitude"), row.get("longitude"))
        if coordinate is None:
            report["facilities_without_valid_coordinate"] += 1
            rejected["coordinates"].append({"record": key, "raw": [row.get("latitude"), row.get("longitude")]})

        status = normalize.classify_facility_note(note)
        report[f"facility_status_{status}"] += 1
        facilities[key] = {
            "id": stable_id("facility", key),
            "name": name,
            "category": normalize.CATEGORY_BY_CODE.get(row.get("sisulSpfc", "").strip(), "OTHER"),
            "road_address": normalize.clean_text(row.get("rdnmadr")),
            "lot_address": normalize.clean_text(row.get("lnmadr")),
            "latitude": coordinate[0] if coordinate else None,
            "longitude": coordinate[1] if coordinate else None,
            "aliases": [],
            "status": status,
            "status_note": note,
            "data_source_id": source_id,
            "source_record_id": key,
            "updated_at": imported_at,
        }
    return list(facilities.values())


def _build_entrances(rows, source_id, imported_at, report, rejected):
    entrances = []
    seen_ids = set()
    seen_signatures = set()

    for row in sorted(rows, key=lambda item: item["sisulId"]):
        report["entrance_rows_in"] += 1
        source_record_id = row["sisulId"].strip()
        match = _SOURCE_ENTRANCE_ID.match(source_record_id)
        if not match or source_record_id in seen_ids:
            report["entrances_dropped_invalid_or_duplicate_id"] += 1
            rejected["entrance_ids"].append(source_record_id)
            continue
        seen_ids.add(source_record_id)
        facility_key, entrance_key = match.groups()

        coordinate = normalize.parse_coordinate(row.get("latitude"), row.get("longitude"))
        if coordinate is None:
            report["entrances_without_valid_coordinate"] += 1
            rejected["coordinates"].append(
                {"record": source_record_id, "raw": [row.get("latitude"), row.get("longitude")]}
            )

        raw_height = row.get("entrance_l", "").strip()
        max_height_mm = normalize.parse_height_mm(raw_height)
        if raw_height and max_height_mm is None:
            report["heights_rejected"] += 1
            rejected["heights"].append({"record": source_record_id, "raw": raw_height})

        hours = {}
        for field, column in _HOURS_FIELDS:
            raw_hours = row.get(column, "").strip()
            hours[field] = normalize.parse_hours(raw_hours)
            if raw_hours and hours[field] is None:
                report["hours_rejected"] += 1
                rejected["hours"].append({"record": source_record_id, "column": column, "raw": raw_hours})

        entrance_class = normalize.ENTRANCE_CLASS_BY_CODE.get(row.get("main_entra", "").strip())
        note = normalize.clean_text(row.get("etc"))
        note_flags = normalize.classify_entrance_note(note)

        permitted = set(note_flags["permitted_vehicle_types"] or [])
        if entrance_class == "RESIDENT_ONLY":
            permitted.add("RESIDENT")

        restrictions = {}
        if note:
            restrictions["note"] = note
        if note_flags["hours_exception"]:
            restrictions["hours_exception_note"] = note

        entrance = {
            "id": stable_id("entrance", source_record_id),
            "facility_id": stable_id("facility", facility_key),
            "name": _entrance_name(entrance_class, entrance_key),
            "latitude": coordinate[0] if coordinate else None,
            "longitude": coordinate[1] if coordinate else None,
            "entrance_type": normalize.DIRECTION_BY_CODE.get(row.get("cmgSe", "").strip(), "UNKNOWN"),
            "entrance_class": entrance_class,
            # 허용 차량 유형 정보가 없으면 NULL, 제한 없음으로 기록 금지
            "permitted_vehicle_types": sorted(permitted) or None,
            "max_height_mm": max_height_mm,
            "height_limit_structure": normalize.HEIGHT_STRUCTURE_BY_CODE.get(
                row.get("entrance_h", "").strip(), "UNKNOWN"
            ),
            "max_width_mm": None,
            "max_length_mm": None,
            "max_weight_kg": None,
            "restrictions": restrictions or None,
            **hours,
            "data_source_id": source_id,
            "trust_level": "PUBLIC_BASELINE",
            "verified_at": None,
            "route_target_type": "ENTRANCE_POINT",
            "status": note_flags["status"],
            "source_record_id": source_record_id,
            "updated_at": imported_at,
            "_facility_key": facility_key,
        }

        signature = _signature(entrance)
        if signature in seen_signatures:
            report["entrances_dropped_duplicate_content"] += 1
            continue
        seen_signatures.add(signature)
        entrances.append(entrance)
    return entrances


def _entrance_name(entrance_class, entrance_key):
    label = normalize.ENTRANCE_CLASS_LABEL.get(entrance_class, "차량 출입구")
    return f"{label} {int(entrance_key[1:])}번"


def _signature(entrance):
    # 같은 시설에서 좌표와 모든 조건이 완전히 같은 레코드만 중복으로 판단
    ignored = {"id", "name", "source_record_id"}
    return repr(sorted((key, repr(value)) for key, value in entrance.items() if key not in ignored))
