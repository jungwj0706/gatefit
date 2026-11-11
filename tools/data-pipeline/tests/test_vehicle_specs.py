import copy
import json
import unittest
from pathlib import Path

from gatefit_pipeline import emit
from gatefit_pipeline.vehicle_specs import build_vehicle_specs

CURATED_PATH = Path(__file__).resolve().parents[3] / "data" / "curated" / "vehicle_model_specs.json"

SAMPLE = {
    "manufacturer": "테스트자동차",
    "source_verified_at": "2026-11-11",
    "catalogs": [
        {
            "model_name": "모델A",
            "source_name": "테스트 카탈로그",
            "source_url": "https://example.com/a.pdf",
            "specs": [
                {"trim_name": None, "length_mm": 4700, "width_mm": 1800, "height_mm": 1450},
                {"trim_name": "루프랙", "length_mm": 4700, "width_mm": 1800, "height_mm": 1500},
            ],
        }
    ],
}


class BuildVehicleSpecsTest(unittest.TestCase):
    def test_rows_carry_source_and_stable_ids(self):
        first = build_vehicle_specs(SAMPLE)
        second = build_vehicle_specs(copy.deepcopy(SAMPLE))

        self.assertEqual([row["id"] for row in first], [row["id"] for row in second])
        self.assertEqual(len({row["id"] for row in first}), 2)
        for row in first:
            self.assertEqual(row["source_url"], "https://example.com/a.pdf")
            self.assertEqual(row["source_verified_at"], "2026-11-11")

    def test_rejects_non_https_source(self):
        broken = copy.deepcopy(SAMPLE)
        broken["catalogs"][0]["source_url"] = "http://example.com/a.pdf"
        with self.assertRaises(ValueError):
            build_vehicle_specs(broken)

    def test_rejects_missing_source_name(self):
        broken = copy.deepcopy(SAMPLE)
        broken["catalogs"][0]["source_name"] = " "
        with self.assertRaises(ValueError):
            build_vehicle_specs(broken)

    def test_rejects_out_of_range_dimension(self):
        # cm 단위 오입력 방지
        broken = copy.deepcopy(SAMPLE)
        broken["catalogs"][0]["specs"][0]["height_mm"] = 145
        with self.assertRaises(ValueError):
            build_vehicle_specs(broken)

    def test_rejects_duplicate_trim(self):
        broken = copy.deepcopy(SAMPLE)
        broken["catalogs"][0]["specs"][1]["trim_name"] = None
        with self.assertRaises(ValueError):
            build_vehicle_specs(broken)

    def test_curated_catalog_is_valid(self):
        rows = build_vehicle_specs(json.loads(CURATED_PATH.read_text(encoding="utf-8")))

        self.assertGreater(len(rows), 0)
        self.assertEqual(len({row["id"] for row in rows}), len(rows))
        self.assertTrue(all(row["source_url"].startswith("https://www.hyundai.com/") for row in rows))

    def test_seed_sql_upserts_every_row(self):
        rows = build_vehicle_specs(SAMPLE)
        sql = emit.render_seed_sql(
            "vehicle_model_specs", rows, ["테스트"], emit.upsert_clause("vehicle_model_specs")
        )

        self.assertIn("insert into public.vehicle_model_specs", sql)
        self.assertIn("on conflict (id) do update set", sql)
        self.assertEqual(sql.count("'테스트자동차'"), 2)
        self.assertIn("null", sql)


if __name__ == "__main__":
    unittest.main()
