import json
import unittest

from gatefit_pipeline import emit, transform

MANIFEST = {
    "provider": "서울특별시",
    "license": "공공누리 제1유형",
    "datasets": {
        "vehicle_entrances": {
            "dataset_id": "OA-21700",
            "title": "서울시 차량 출입구 정보",
            "page_url": "https://example.invalid/OA-21700",
            "file": "entrances.zip",
            "sha256": "",
            "downloaded_at": "2026-11-11T00:00:00Z",
        },
        "facilities": {
            "dataset_id": "OA-21698",
            "title": "서울시 시설물 정보",
            "page_url": "https://example.invalid/OA-21698",
            "file": "facilities.zip",
            "sha256": "",
            "downloaded_at": "2026-11-11T00:00:00Z",
        },
    },
}


def facility_row(**overrides):
    row = {
        "sisulId": "B0001",
        "sisulNm": "테스트 쇼핑몰",
        "latitude": "37.5012665",
        "longitude": "127.026482",
        "sisulSpfc": "FU_BA",
        "rdnmadr": "서울특별시 강남구 테스트로 1",
        "lnmadr": "서울특별시 강남구 테스트동 1",
        "etc": "",
        "refDate": "20221207",
    }
    row.update(overrides)
    return row


def entrance_row(**overrides):
    row = {
        "sisulId": "B0001_C001",
        "sisulNm": "테스트 쇼핑몰",
        "entrcId": "C001",
        "latitude": "37.5013428",
        "longitude": "127.026443",
        "main_entra": "MED_CA",
        "cmgSe": "EIO_DB",
        "entrance_h": "EHL_Y",
        "entrance_l": "2.1M",
        "wkOpertime": "0900-2200",
        "satOpertim": "0000-2400",
        "sumOpertim": "WSU_N",
        "hlOpertime": "",
        "etc": "",
        "refDate": "20221210",
    }
    row.update(overrides)
    return row


def build(facility_rows, entrance_rows):
    return transform.build_dataset(facility_rows, entrance_rows, MANIFEST)


class BuildDatasetTest(unittest.TestCase):
    def test_entrance_fields_are_mapped(self):
        dataset, _ = build([facility_row()], [entrance_row()])

        entrance = dataset["entrances"][0]
        facility = dataset["facilities"][0]
        self.assertEqual(entrance["facility_id"], facility["id"])
        self.assertEqual(entrance["name"], "주출입구 1번")
        self.assertEqual(entrance["entrance_type"], "ENTRY")
        self.assertEqual(entrance["max_height_mm"], 2100)
        self.assertEqual(entrance["height_limit_structure"], "INSTALLED")
        self.assertEqual(entrance["weekday_hours"]["type"], "RANGES")
        self.assertEqual(entrance["saturday_hours"], {"type": "ALL_DAY"})
        self.assertEqual(entrance["sunday_hours"], {"type": "CLOSED"})
        self.assertIsNone(entrance["holiday_hours"])
        self.assertEqual(entrance["route_target_type"], "ENTRANCE_POINT")
        self.assertEqual(entrance["source_record_id"], "B0001_C001")

    def test_baseline_rows_are_never_marked_verified(self):
        dataset, _ = build([facility_row()], [entrance_row()])

        entrance = dataset["entrances"][0]
        self.assertEqual(entrance["trust_level"], "PUBLIC_BASELINE")
        self.assertIsNone(entrance["verified_at"])

    def test_limits_missing_from_the_source_stay_null(self):
        dataset, _ = build([facility_row()], [entrance_row(entrance_h="EHL_N", entrance_l="")])

        entrance = dataset["entrances"][0]
        self.assertIsNone(entrance["max_height_mm"])
        self.assertEqual(entrance["height_limit_structure"], "NOT_INSTALLED")
        self.assertIsNone(entrance["max_width_mm"])
        self.assertIsNone(entrance["max_length_mm"])
        self.assertIsNone(entrance["max_weight_kg"])
        self.assertIsNone(entrance["permitted_vehicle_types"])

    def test_rejected_height_is_reported_and_left_unknown(self):
        dataset, report = build([facility_row()], [entrance_row(entrance_l="50.5M")])

        self.assertIsNone(dataset["entrances"][0]["max_height_mm"])
        self.assertEqual(report["rejected"]["heights"], [{"record": "B0001_C001", "raw": "50.5M"}])

    def test_resident_only_class_restricts_vehicle_types(self):
        dataset, _ = build([facility_row()], [entrance_row(main_entra="MED_CD")])

        self.assertEqual(dataset["entrances"][0]["permitted_vehicle_types"], ["RESIDENT"])

    def test_note_is_kept_verbatim_and_flags_schedule_exception(self):
        dataset, _ = build([facility_row()], [entrance_row(etc="월요일 휴무")])

        self.assertEqual(
            dataset["entrances"][0]["restrictions"],
            {"note": "월요일 휴무", "hours_exception_note": "월요일 휴무"},
        )

    def test_unknown_direction_code_is_unknown(self):
        dataset, _ = build([facility_row()], [entrance_row(cmgSe="")])

        self.assertEqual(dataset["entrances"][0]["entrance_type"], "UNKNOWN")

    def test_entrance_without_facility_is_dropped(self):
        dataset, report = build([facility_row()], [entrance_row(sisulId="B9999_C001")])

        self.assertEqual(dataset["entrances"], [])
        self.assertEqual(report["counts"]["entrances_dropped_without_facility"], 1)

    def test_identical_entrance_content_is_deduplicated(self):
        rows = [entrance_row(), entrance_row(sisulId="B0001_C002", entrcId="C002")]

        dataset, report = build([facility_row()], rows)

        self.assertEqual(len(dataset["entrances"]), 1)
        self.assertEqual(report["counts"]["entrances_dropped_duplicate_content"], 1)

    def test_same_point_with_different_direction_is_kept(self):
        rows = [entrance_row(), entrance_row(sisulId="B0001_C002", entrcId="C002", cmgSe="EIO_DA")]

        dataset, _ = build([facility_row()], rows)

        self.assertEqual(len(dataset["entrances"]), 2)

    def test_facility_marked_duplicate_is_dropped_only_without_entrances(self):
        facilities = [
            facility_row(etc="중복시설"),
            facility_row(sisulId="B0002", sisulNm="다른 시설", etc="숭인공원과 중복"),
        ]

        dataset, _ = build(facilities, [entrance_row()])

        self.assertEqual([facility["source_record_id"] for facility in dataset["facilities"]], ["B0001"])

    def test_ids_are_stable_between_runs(self):
        first, _ = build([facility_row()], [entrance_row()])
        second, _ = build([facility_row()], [entrance_row()])

        self.assertEqual(first, second)

    def test_data_sources_record_license_and_reference_date(self):
        dataset, _ = build([facility_row()], [entrance_row()])

        entrance_source = dataset["data_sources"][0]
        self.assertEqual(entrance_source["source_type"], "PUBLIC")
        self.assertEqual(entrance_source["license"], "공공누리 제1유형")
        self.assertEqual(entrance_source["original_updated_at"], "2022-12-10")
        self.assertEqual(dataset["entrances"][0]["data_source_id"], entrance_source["id"])


class EmitTest(unittest.TestCase):
    def test_sql_literals_are_escaped(self):
        self.assertEqual(emit.sql_literal("name", "O'Reilly"), "'O''Reilly'")
        self.assertEqual(emit.sql_literal("max_height_mm", None), "null")
        self.assertEqual(emit.sql_literal("max_height_mm", 2100), "2100")
        self.assertEqual(emit.sql_literal("aliases", []), "'{}'::text[]")
        self.assertEqual(emit.sql_literal("permitted_vehicle_types", ["RESIDENT"]), "array['RESIDENT']::text[]")
        self.assertEqual(emit.sql_literal("weekday_hours", {"type": "ALL_DAY"}), "'{\"type\": \"ALL_DAY\"}'::jsonb")

    def test_snapshot_omits_null_fields(self):
        dataset, _ = build([facility_row()], [entrance_row()])

        snapshot = json.loads(emit.render_snapshot_json(dataset, "2026-11-11T00:00:00Z"))

        entrance = snapshot["entrances"][0]
        self.assertEqual(snapshot["schema_version"], emit.SNAPSHOT_SCHEMA_VERSION)
        self.assertNotIn("holiday_hours", entrance)
        self.assertNotIn("verified_at", entrance)
        self.assertEqual(entrance["max_height_mm"], 2100)


if __name__ == "__main__":
    unittest.main()
