# Supabase 백엔드

GATEFIT의 서버 데이터베이스(PostgreSQL) 정의입니다.

```text
supabase/
├── config.toml        Supabase CLI 설정 (시드 경로)
├── migrations/        스키마, 제약, RLS 정책
├── seed/              공공데이터 기준 시드 (tools/data-pipeline 생성물, 직접 수정 금지)
└── tests/             스키마·RLS 검증 스크립트
```

## 테이블

| 테이블 | 용도 | anon 권한 |
|---|---|---|
| `facilities` | 시설 | 조회 |
| `entrances` | 개별 차량 출입구 | 조회 |
| `data_sources` | 데이터 출처·이용 조건·기준일 | 조회 |
| `vehicle_model_specs` | 출처가 확인된 차량 제원 | 조회 |
| `entrance_verifications` | 검증 이력 | 공개 컬럼만 조회 |
| `entrance_reports` | 사용자 제보 | 등록만 가능 (조회·수정·삭제 불가) |

- 모든 테이블에 RLS가 켜져 있습니다.
- 앱에는 `anon` 키만 들어가며, `service_role` 키는 앱에 넣지 않습니다.
- 제보는 항상 `PENDING` 상태로 등록되고, 공식 데이터를 자동으로 바꾸지 않습니다.
- 검증 완료 등급(`VERIFIED_OFFICIAL`, `VERIFIED_FIELD`)은 검증 시각과 출처가 없으면 제약으로 거부됩니다.

기획서의 테이블 정의에서 달라진 점은 다음과 같습니다.

- `facilities.address` → `road_address`, `lot_address` (원본이 도로명·지번을 따로 제공)
- `entrances`에 `entrance_class`, `permitted_vehicle_types`, `height_limit_structure`, `source_record_id` 추가
- PostGIS는 위치 기반 기능을 도입할 때 별도 마이그레이션으로 추가할 예정이며 현재는 사용하지 않습니다.

## 프로젝트에 적용

Supabase 프로젝트를 만든 뒤 다음을 실행합니다.

```bash
supabase link --project-ref <프로젝트 ref>
supabase db push --include-seed
```

앱은 저장소 루트의 `local.properties`(커밋 금지)에서 접속 정보를 읽습니다.

```properties
SUPABASE_URL=https://<프로젝트 ref>.supabase.co
SUPABASE_ANON_KEY=<anon 키>
```

값이 없으면 앱은 내장 스냅샷만으로 동작하고, 제보는 기기에 보관한 채 전송하지 않습니다.

## 검증

비어 있는 임시 데이터베이스에서 마이그레이션, 시드, 제약, RLS를 한 번에 확인합니다.

```bash
createdb gatefit_verify
PGDATABASE=gatefit_verify supabase/tests/verify_schema.sh
dropdb gatefit_verify
```
