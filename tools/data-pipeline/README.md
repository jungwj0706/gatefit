# 데이터 정제 파이프라인

서울 열린데이터광장의 출입구 공공데이터를 GATEFIT 기준 데이터로 정제합니다.
Python 3.10 이상 표준 라이브러리만 사용합니다.

```text
원본 수집 (fetch_seoul_data.py)
    ↓  data/raw/seoul/*.zip + manifest.json (출처·수집 시각·SHA-256)
정제 (build_baseline.py)
    ↓  필드 매핑 → 단위·형식 정규화 → 좌표 검증 → 중복 제거 → 출처·기준일 기록
생성물
    ├─ supabase/seed/*.sql                              서버 데이터베이스 시드
    ├─ app/src/main/assets/baseline/seoul_baseline.json 앱 내장 오프라인 스냅샷
    └─ data/processed/seoul_baseline_report.json        정제 통계와 제외된 값

차량 제원 (data/curated/vehicle_model_specs.json, 공식 카탈로그에서 확인한 값)
    ↓  출처·치수 범위·중복 검사 → 고정 ID 부여
생성물
    ├─ supabase/seed/04_vehicle_model_specs.sql            서버 데이터베이스 시드
    └─ app/src/main/assets/catalog/vehicle_model_specs.json 앱 내장 제원 목록
```

## 실행

```bash
cd tools/data-pipeline

# 원본 재수집 (선택, 저장소에 원본이 이미 보존되어 있음)
python3 fetch_seoul_data.py

# 정제 및 생성물 갱신
python3 build_baseline.py

# 생성물이 최신인지 확인 (CI 용도)
python3 build_baseline.py --check

# 단위 테스트
python3 -m unittest discover -s tests
```

python.org 배포판 macOS Python에서 인증서 오류가 나면 `SSL_CERT_FILE=/etc/ssl/cert.pem`을 지정하거나 `Install Certificates.command`를 먼저 실행합니다.

## 정제 규칙

코드값은 데이터셋과 함께 제공되는 「출입구 관련 세부정의서」를 따릅니다.

| 원본 | 변환 | 비고 |
|---|---|---|
| `cmgSe` `EIO_DA` / `EIO_DB` / `EIO_DC` | `EXIT` / `ENTRY` / `BIDIRECTIONAL` | 그 외 값은 `UNKNOWN` |
| `main_entra` `MED_CA`~`MED_CX` | 주·보조·단지·입주자 전용·지상·지하·기타 | `MED_CD`는 허용 차량 유형 `RESIDENT` |
| `entrance_l` `"2.1M"` | `max_height_mm = 2100` | 1.0~6.0 m 밖의 값은 오기로 보고 `NULL` |
| `entrance_h` `EHL_Y` / `EHL_N` | `INSTALLED` / `NOT_INSTALLED` | 미설치는 '높이 제한 없음'이 아님 |
| 허용 시간 `"0900-1800"` | `{"type":"RANGES", ...}` | `"0000-2400"`은 `ALL_DAY` |
| 허용 시간 `WD_N` 등 | `{"type":"CLOSED"}` | 해당 요일 사용금지 |
| 허용 시간 빈 값 | `NULL` | 미확인이며 상시 개방으로 해석하지 않음 |
| 좌표 | 소수 7자리 | 서울 범위 밖이면 `NULL` |
| `sisulSpfc` `FU_BA`~`FU_BJ` | 시설 카테고리 | 대규모점포·문화시설·병의원·공공기관·공원 |

### 비고(`etc`) 처리

비고는 자유 서술이므로 **원문을 그대로 보존**(`restrictions.note`)하고, 의미가 분명한 표현만 구조화합니다.

- `폐쇄`, `폐문`, `사용중지` 등 → 출입구 상태 `CLOSED`
- `코로나`, `공사중` 등 조사 당시의 일시적 사유 → 상태 `UNKNOWN` (현재 상태를 알 수 없음)
- `월요일 휴무`처럼 시간표로 표현되지 않는 조건 → `restrictions.hours_exception_note`
  (분석 엔진은 허용 시간 안이어도 '확인 필요'로 판단)
- `구급차 전용`, `입주자 전용`, `직원 출입구` 등 → 허용 차량 유형

규칙에 해당하지 않는 비고는 해석하지 않고 화면에 원문으로만 보여 줍니다.

### 만들지 않는 값

- 원본에 없는 제한 너비·길이·중량
- 검증 시각 (`verified_at`), 검증 완료 등급
- 출입구 좌표가 없을 때의 대체 좌표 (시설 대표 좌표로 대체하지 않음)

## 차량 제원

`data/curated/vehicle_model_specs.json`은 사람이 공식 자료를 보고 직접 관리하는 목록입니다.
제원을 추가하거나 고칠 때는 이 파일만 수정하고 `build_baseline.py`를 다시 실행합니다.

- 출처 이름과 `https` 출처 URL, 확인 날짜가 없는 제원은 빌드가 실패합니다.
- 전고·전폭·전장이 데이터베이스 제약 범위를 벗어나면 빌드가 실패합니다. (cm 단위 오입력 방지)
- 같은 차종·연식·트림이 두 번 들어가면 빌드가 실패합니다.

## 재현성

레코드 ID는 원본 ID에서 UUIDv5로 만들기 때문에 다시 실행해도 같은 결과가 나옵니다.
시설·출입구 시드는 `on conflict (id) do nothing`으로 적용되어, 운영 중 검증·수정된 레코드를 덮어쓰지 않습니다.
차량 제원 시드는 목록 파일이 기준이므로 같은 ID의 행을 목록 내용으로 갱신합니다.
