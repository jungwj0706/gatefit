import unittest

from gatefit_pipeline import normalize


class ParseHeightTest(unittest.TestCase):
    def test_meter_text_is_converted_to_millimeters(self):
        self.assertEqual(normalize.parse_height_mm("2.1M"), 2100)
        self.assertEqual(normalize.parse_height_mm("2M"), 2000)
        self.assertEqual(normalize.parse_height_mm("1.55M"), 1550)
        self.assertEqual(normalize.parse_height_mm("2.2m"), 2200)

    def test_comma_decimal_separator_is_accepted(self):
        self.assertEqual(normalize.parse_height_mm("1,8M"), 1800)

    def test_empty_or_unparseable_value_is_unknown(self):
        self.assertIsNone(normalize.parse_height_mm(""))
        self.assertIsNone(normalize.parse_height_mm(None))
        self.assertIsNone(normalize.parse_height_mm("높이제한 없음"))
        self.assertIsNone(normalize.parse_height_mm("2.1"))

    def test_implausible_value_is_rejected_instead_of_guessed(self):
        self.assertIsNone(normalize.parse_height_mm("50.5M"))
        self.assertIsNone(normalize.parse_height_mm("15.5M"))
        self.assertIsNone(normalize.parse_height_mm("0.5M"))

    def test_plausible_range_boundaries_are_kept(self):
        self.assertEqual(normalize.parse_height_mm("1M"), 1000)
        self.assertEqual(normalize.parse_height_mm("6.0M"), 6000)


class ParseHoursTest(unittest.TestCase):
    def test_empty_value_is_unknown_not_always_open(self):
        self.assertIsNone(normalize.parse_hours(""))
        self.assertIsNone(normalize.parse_hours(None))

    def test_full_day_is_all_day(self):
        self.assertEqual(normalize.parse_hours("0000-2400"), {"type": "ALL_DAY"})

    def test_prohibited_day_codes_are_closed(self):
        for code in ("WD_N", "WST_N", "WSU_N", "HD_N"):
            self.assertEqual(normalize.parse_hours(code), {"type": "CLOSED"})

    def test_time_range_is_formatted(self):
        self.assertEqual(
            normalize.parse_hours("0900-1800"),
            {"type": "RANGES", "ranges": [{"start": "09:00", "end": "18:00"}]},
        )

    def test_end_at_midnight_is_written_as_zero_hour(self):
        expected = {"type": "RANGES", "ranges": [{"start": "10:00", "end": "00:00"}]}
        self.assertEqual(normalize.parse_hours("1000-2400"), expected)
        self.assertEqual(normalize.parse_hours("1000-0000"), expected)

    def test_overnight_range_is_preserved(self):
        self.assertEqual(
            normalize.parse_hours("1800-0830"),
            {"type": "RANGES", "ranges": [{"start": "18:00", "end": "08:30"}]},
        )

    def test_invalid_time_is_unknown(self):
        self.assertIsNone(normalize.parse_hours("0090-1900"))
        self.assertIsNone(normalize.parse_hours("0900-2530"))
        self.assertIsNone(normalize.parse_hours("0900-2430"))
        self.assertIsNone(normalize.parse_hours("0900"))
        self.assertIsNone(normalize.parse_hours("0900-0900"))


class ParseCoordinateTest(unittest.TestCase):
    def test_seoul_coordinate_is_rounded(self):
        self.assertEqual(
            normalize.parse_coordinate("37.510528600000001", "127.006668000000005"),
            (37.5105286, 127.006668),
        )

    def test_coordinate_outside_seoul_is_rejected(self):
        self.assertIsNone(normalize.parse_coordinate("35.1796", "129.0756"))
        self.assertIsNone(normalize.parse_coordinate("127.0066", "37.5105"))
        self.assertIsNone(normalize.parse_coordinate("0", "0"))

    def test_unparseable_coordinate_is_rejected(self):
        self.assertIsNone(normalize.parse_coordinate("", ""))
        self.assertIsNone(normalize.parse_coordinate(None, "127.0"))


class ClassifyEntranceNoteTest(unittest.TestCase):
    def test_no_note_keeps_recorded_state(self):
        flags = normalize.classify_entrance_note(None)
        self.assertEqual(flags["status"], "ACTIVE")
        self.assertFalse(flags["hours_exception"])
        self.assertIsNone(flags["permitted_vehicle_types"])

    def test_explicit_closure_is_closed(self):
        for note in ("폐쇄", "폐문", "사용중지", "MED_CX(현재사용불가)", "현재 고장으로 인해 이용 불가"):
            self.assertEqual(normalize.classify_entrance_note(note)["status"], "CLOSED", note)

    def test_temporary_closure_is_unknown_instead_of_closed(self):
        for note in ("코로나 폐쇄", "내부 공사중", "보건소 주차 불가(임시선별소 설치)"):
            self.assertEqual(normalize.classify_entrance_note(note)["status"], "UNKNOWN", note)

    def test_partial_restriction_is_not_a_closure(self):
        for note in ("관계자 외 출입금지", "일반인 사용불가", "도서관 운영시간 외 사용불가", "2,4주째 월요일 폐문"):
            self.assertEqual(normalize.classify_entrance_note(note)["status"], "ACTIVE", note)

    def test_schedule_notes_are_flagged_as_hours_exception(self):
        for note in ("월요일 휴무", "2,4주 일요일 휴무", "금요일 허용시간 1030-2200", "2,4주째 월요일 폐문"):
            self.assertTrue(normalize.classify_entrance_note(note)["hours_exception"], note)

    def test_unrelated_notes_are_not_hours_exceptions(self):
        for note in ("기계식주차장", "유료주차", "주차타워"):
            self.assertFalse(normalize.classify_entrance_note(note)["hours_exception"], note)

    def test_restricted_access_notes_map_to_vehicle_types(self):
        cases = {
            "구급차 전용": ["EMERGENCY"],
            "물류차량만 이용": ["SERVICE"],
            "외부차량 진입금지": ["RESIDENT"],
            "입주자 전용": ["RESIDENT"],
        }
        for note, expected in cases.items():
            self.assertEqual(normalize.classify_entrance_note(note)["permitted_vehicle_types"], expected, note)

    def test_ambiguous_access_notes_are_left_unknown(self):
        for note in ("병원이용자만 사용가능", "장애인 전용 주차구역", "아파트랑 같이 사용"):
            self.assertIsNone(normalize.classify_entrance_note(note)["permitted_vehicle_types"], note)


class ClassifyFacilityNoteTest(unittest.TestCase):
    def test_closed_business_is_closed(self):
        self.assertEqual(normalize.classify_facility_note("폐점"), "CLOSED")
        self.assertEqual(normalize.classify_facility_note("건물 영업종료됨"), "CLOSED")

    def test_construction_and_empty_notes_stay_unknown(self):
        self.assertEqual(normalize.classify_facility_note("공사중"), "UNKNOWN")
        self.assertEqual(normalize.classify_facility_note("코로나 종식이전 폐쇄"), "UNKNOWN")
        self.assertEqual(normalize.classify_facility_note(None), "UNKNOWN")
        self.assertEqual(normalize.classify_facility_note("대형 상가"), "UNKNOWN")


class CodeMappingTest(unittest.TestCase):
    def test_direction_codes_follow_the_dataset_definition(self):
        self.assertEqual(normalize.DIRECTION_BY_CODE["EIO_DA"], "EXIT")
        self.assertEqual(normalize.DIRECTION_BY_CODE["EIO_DB"], "ENTRY")
        self.assertEqual(normalize.DIRECTION_BY_CODE["EIO_DC"], "BIDIRECTIONAL")

    def test_reference_date_is_iso_formatted(self):
        self.assertEqual(normalize.parse_reference_date("20221210"), "2022-12-10")
        self.assertIsNone(normalize.parse_reference_date(""))


if __name__ == "__main__":
    unittest.main()
