import re

# 차량 출입구 높이로 보기 어려운 값을 걸러내기 위한 검증 범위, 범위 밖 값은 오기로 보고 미확인 처리
MIN_PLAUSIBLE_HEIGHT_MM = 1000
MAX_PLAUSIBLE_HEIGHT_MM = 6000

# 서울특별시 행정구역을 감싸는 좌표 범위
SEOUL_LAT_RANGE = (37.41, 37.72)
SEOUL_LNG_RANGE = (126.73, 127.27)

DIRECTION_BY_CODE = {
    "EIO_DA": "EXIT",
    "EIO_DB": "ENTRY",
    "EIO_DC": "BIDIRECTIONAL",
}

ENTRANCE_CLASS_BY_CODE = {
    "MED_CA": "MAIN",
    "MED_CB": "SUB",
    "MED_CC": "COMPLEX",
    "MED_CD": "RESIDENT_ONLY",
    "MED_CE": "GROUND",
    "MED_CF": "UNDERGROUND",
    "MED_CX": "OTHER",
}

ENTRANCE_CLASS_LABEL = {
    "MAIN": "주출입구",
    "SUB": "보조 출입구",
    "COMPLEX": "단지 출입구",
    "RESIDENT_ONLY": "입주자 전용 출입구",
    "GROUND": "지상 출입구",
    "UNDERGROUND": "지하 출입구",
    "OTHER": "기타 출입구",
}

HEIGHT_STRUCTURE_BY_CODE = {
    "EHL_Y": "INSTALLED",
    "EHL_N": "NOT_INSTALLED",
}

CATEGORY_BY_CODE = {
    "FU_BA": "SHOPPING",
    "FU_BB": "CULTURE",
    "FU_BC": "CULTURE",
    "FU_BD": "HOSPITAL",
    "FU_BE": "HOSPITAL",
    "FU_BF": "PUBLIC_OFFICE",
    "FU_BG": "PUBLIC_OFFICE",
    "FU_BH": "PUBLIC_OFFICE",
    "FU_BI": "PARK",
    "FU_BJ": "PARK",
}

CLOSED_DAY_CODES = {"WD_N", "WST_N", "WSU_N", "HD_N"}

_HEIGHT_PATTERN = re.compile(r"^(\d+(?:[.,]\d+)?)\s*[mM]$")
_HOURS_PATTERN = re.compile(r"^(\d{2})(\d{2})-(\d{2})(\d{2})$")

# 비고 원문 해석 규칙, 의미가 명확한 표현만 구조화하고 나머지는 원문 그대로 노출
_CLOSED_NOTE = re.compile(
    r"^(폐쇄|폐문|사용중지|출입금지)$|현재\s*사용불가|이용 불가|주차장 이용금지|주차장 폐쇄|주차장 중단"
)
_TEMPORARY_NOTE = re.compile(r"코로나|임시선별소|공사\s*중")
_HOURS_EXCEPTION_NOTE = re.compile(
    r"휴무|휴관|휴일|사용금지|허용시간|점심시간|야간|운영시간|영업시간|마감시간|이용시간|관람시간"
    r"|미개방|미운영|제외|까지 운영|[월화수목금토일]요일|\d{4}\s*-\s*\d{3,4}|\d{1,2}:\d{2}"
)
_ACCESS_NOTE_RULES = (
    ("EMERGENCY", re.compile(r"구급차 전용")),
    ("SERVICE", re.compile(r"직원|물류|화물전용|화물용차량|하역장|관계자")),
    ("RESIDENT", re.compile(
        r"입주|외부차량|외부인|월주차 전용|교직원|지정차량|일반방문차량 사용불가|일반인 사용불가|회원전용"
    )),
)

FACILITY_CLOSED_NOTE = re.compile(r"폐점|폐업|영업종료|영업 안함|미운영|폐쇄|철거|이전함|출입 불가")
_FACILITY_TEMPORARY_NOTE = re.compile(r"코로나|공사|리모델")
FACILITY_DUPLICATE_NOTE = re.compile(r"중복")


def clean_text(value):
    # 원본 인코딩으로 표현되지 않아 깨진 문자는 제거
    text = re.sub(r"\s+", " ", (value or "").replace("\ufffd", "")).strip()
    return text or None


def parse_height_mm(raw):
    match = _HEIGHT_PATTERN.match((raw or "").strip())
    if not match:
        return None
    millimeters = round(float(match.group(1).replace(",", ".")) * 1000)
    if not MIN_PLAUSIBLE_HEIGHT_MM <= millimeters <= MAX_PLAUSIBLE_HEIGHT_MM:
        return None
    return millimeters


def parse_hours(raw):
    value = (raw or "").strip()
    # 빈 값은 미확인이며 상시 개방으로 해석 금지
    if not value:
        return None
    if value in CLOSED_DAY_CODES:
        return {"type": "CLOSED"}
    match = _HOURS_PATTERN.match(value)
    if not match:
        return None
    start_hour, start_minute, end_hour, end_minute = (int(group) for group in match.groups())
    if start_minute > 59 or end_minute > 59 or start_hour > 24 or end_hour > 24:
        return None
    if (start_hour == 24 and start_minute) or (end_hour == 24 and end_minute):
        return None
    start = start_hour % 24 * 60 + start_minute
    end = end_hour % 24 * 60 + end_minute
    if start == end:
        # 0000-2400처럼 하루 전체를 덮는 경우만 상시 허용, 그 외 동일 시각은 해석 불가
        if start == 0 and (start_hour, end_hour) == (0, 24):
            return {"type": "ALL_DAY"}
        return None
    return {
        "type": "RANGES",
        "ranges": [{"start": _format_minutes(start), "end": _format_minutes(end)}],
    }


def _format_minutes(total):
    return f"{total // 60:02d}:{total % 60:02d}"


def parse_coordinate(raw_latitude, raw_longitude):
    try:
        latitude = float(raw_latitude)
        longitude = float(raw_longitude)
    except (TypeError, ValueError):
        return None
    in_latitude = SEOUL_LAT_RANGE[0] <= latitude <= SEOUL_LAT_RANGE[1]
    in_longitude = SEOUL_LNG_RANGE[0] <= longitude <= SEOUL_LNG_RANGE[1]
    if not (in_latitude and in_longitude):
        return None
    return round(latitude, 7), round(longitude, 7)


def parse_reference_date(raw):
    value = (raw or "").strip()
    if not re.fullmatch(r"\d{8}", value):
        return None
    return f"{value[:4]}-{value[4:6]}-{value[6:]}"


def classify_entrance_note(note):
    result = {"status": "ACTIVE", "hours_exception": False, "permitted_vehicle_types": None}
    if not note:
        return result
    # 조사 당시의 일시적 사유는 현재 상태를 알 수 없으므로 폐쇄로 단정하지 않음
    if _TEMPORARY_NOTE.search(note):
        result["status"] = "UNKNOWN"
    elif _CLOSED_NOTE.search(note):
        result["status"] = "CLOSED"
    result["hours_exception"] = bool(_HOURS_EXCEPTION_NOTE.search(note))
    types = [name for name, pattern in _ACCESS_NOTE_RULES if pattern.search(note)]
    if types:
        result["permitted_vehicle_types"] = types
    return result


def classify_facility_note(note):
    # 기준일 당시의 공사·임시 휴관은 현재 상태를 알 수 없으므로 미확인 유지
    if not note or _FACILITY_TEMPORARY_NOTE.search(note):
        return "UNKNOWN"
    if FACILITY_CLOSED_NOTE.search(note):
        return "CLOSED"
    return "UNKNOWN"
