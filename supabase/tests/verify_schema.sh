#!/usr/bin/env bash
# 비어 있는 임시 PostgreSQL 데이터베이스에 마이그레이션과 시드를 적용하고 제약·RLS 동작 검증
# 접속 정보는 PGHOST, PGPORT, PGUSER, PGDATABASE 등 표준 환경 변수 사용, 운영 DB 대상 실행 금지
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
RUN=(psql -v ON_ERROR_STOP=1 -q)
QUERY=(psql -At)
failures=0

expect_ok() {
  local name="$1" sql="$2" output
  if output=$("${QUERY[@]}" -c "$sql" 2>&1); then
    echo "PASS  $name"
  else
    echo "FAIL  $name :: $(grep -m1 ERROR <<<"$output")"
    failures=$((failures + 1))
  fi
}

expect_value() {
  local name="$1" sql="$2" expected="$3" output
  output=$("${QUERY[@]}" -c "$sql" 2>&1 | tail -1)
  if [[ "$output" == "$expected" ]]; then
    echo "PASS  $name"
  else
    echo "FAIL  $name :: 기대값 $expected, 실제값 $output"
    failures=$((failures + 1))
  fi
}

expect_error() {
  local name="$1" sql="$2" pattern="$3" output
  if output=$("${QUERY[@]}" -c "$sql" 2>&1); then
    echo "FAIL  $name :: 허용됨"
    failures=$((failures + 1))
  elif grep -q "$pattern" <<<"$output"; then
    echo "PASS  $name"
  else
    echo "FAIL  $name :: 예상과 다른 오류 $(grep -m1 ERROR <<<"$output")"
    failures=$((failures + 1))
  fi
}

# Supabase가 기본 제공하는 역할과 기본 권한을 재현, 마이그레이션의 권한 회수 동작까지 검증하기 위함
"${RUN[@]}" <<'SQL'
do $$
begin
  if not exists (select from pg_roles where rolname = 'anon') then create role anon nologin; end if;
  if not exists (select from pg_roles where rolname = 'authenticated') then create role authenticated nologin; end if;
  if not exists (select from pg_roles where rolname = 'service_role') then
    create role service_role nologin bypassrls;
  end if;
end $$;
grant usage on schema public to anon, authenticated, service_role;
alter default privileges in schema public grant all on tables to anon, authenticated, service_role;
alter default privileges in schema public grant all on functions to anon, authenticated, service_role;
SQL

for migration in "$ROOT"/supabase/migrations/*.sql; do
  "${RUN[@]}" -f "$migration"
done

apply_seed() {
  for seed in "$ROOT"/supabase/seed/*.sql; do
    "${RUN[@]}" -f "$seed"
  done
}

apply_seed
first_counts=$("${QUERY[@]}" -c "select (select count(*) from facilities), (select count(*) from entrances)")
apply_seed
expect_value "시드 재적용 시 행 수 불변" \
  "select (select count(*) from facilities), (select count(*) from entrances)" "$first_counts"
expect_value "기준 데이터는 검증 완료로 표시되지 않음" \
  "select count(*) from entrances where trust_level <> 'PUBLIC_BASELINE' or verified_at is not null" "0"
expect_value "모든 출입구에 출처 기록" "select count(*) from entrances where data_source_id is null" "0"
expect_value "모든 테이블 RLS 활성" \
  "select count(*) from pg_tables where schemaname = 'public' and not rowsecurity" "0"

entrance=$("${QUERY[@]}" -c "select id from entrances order by id limit 1")
anon="set role anon;"
denied="permission denied"

expect_ok "anon 시설 조회" "$anon select count(*) from facilities"
expect_ok "anon 출입구 조회" "$anon select count(*) from entrances"
expect_ok "anon 출처 조회" "$anon select count(*) from data_sources"
expect_ok "anon 차량 제원 조회" "$anon select count(*) from vehicle_model_specs"
expect_ok "anon 검증 기록 공개 컬럼 조회" "$anon select count(verified_at) from entrance_verifications"
expect_ok "anon 제보 등록" \
  "$anon insert into entrance_reports (id, entrance_id, report_type, description, proposed_value)
   values ('11111111-1111-4111-8111-111111111111', '$entrance', 'HEIGHT_CHANGED', '표지판 높이 변경',
           '{\"max_height_mm\": 2000}')"
expect_value "제보는 항상 PENDING으로 등록" "select string_agg(distinct status, ',') from entrance_reports" "PENDING"

expect_error "anon 동일 ID 제보 재등록" \
  "$anon insert into entrance_reports (id, entrance_id, report_type)
   values ('11111111-1111-4111-8111-111111111111', '$entrance', 'OTHER')" "duplicate key"
expect_error "anon 제보 조회" "$anon select count(*) from entrance_reports" "$denied"
expect_error "anon 제보 상태 지정" \
  "$anon insert into entrance_reports (entrance_id, report_type, status) values ('$entrance', 'OTHER', 'APPROVED')" \
  "$denied"
expect_error "anon 제보 수정" "$anon update entrance_reports set status = 'APPROVED'" "$denied"
expect_error "anon 제보 삭제" "$anon delete from entrance_reports" "$denied"
expect_error "anon 잘못된 제보 유형" \
  "$anon insert into entrance_reports (entrance_id, report_type) values ('$entrance', 'SPAM')" "check constraint"
expect_error "anon 500자 초과 제보" \
  "$anon insert into entrance_reports (entrance_id, report_type, description)
   values ('$entrance', 'OTHER', repeat('가', 501))" "check constraint"
expect_error "anon 존재하지 않는 출입구 제보" \
  "$anon insert into entrance_reports (entrance_id, report_type)
   values ('00000000-0000-4000-8000-000000000000', 'OTHER')" "foreign key"
expect_error "anon 출입구 수정" "$anon update entrances set max_height_mm = 9999" "$denied"
expect_error "anon 출입구 삭제" "$anon delete from entrances" "$denied"
expect_error "anon 시설 등록" "$anon insert into facilities (name) values ('x')" "$denied"
expect_error "anon 출처 수정" "$anon update data_sources set license = 'x'" "$denied"
expect_error "anon 검증 증빙 조회" "$anon select evidence_reference from entrance_verifications" "$denied"
expect_error "anon 검증 기록 등록" \
  "$anon insert into entrance_verifications (entrance_id, verification_method, verified_fields, verifier_type, verified_at)
   values ('$entrance', '현장', array['max_height_mm'], 'FIELD', now())" "$denied"
expect_ok "service_role 제보 조회" "set role service_role; select count(*) from entrance_reports"

expect_error "검증 시각 없는 검증 등급" \
  "update entrances set trust_level = 'VERIFIED_FIELD' where id = '$entrance'" "entrances_verified_requires_evidence"
expect_error "형식이 잘못된 허용 시간" \
  "update entrances set weekday_hours = '{\"type\":\"RANGES\",\"ranges\":[{\"start\":\"9:00\",\"end\":\"18:00\"}]}'
   where id = '$entrance'" "check constraint"
expect_error "구간이 없는 허용 시간" \
  "update entrances set weekday_hours = '{\"type\":\"RANGES\",\"ranges\":[]}' where id = '$entrance'" "check constraint"
expect_error "한쪽만 있는 좌표" \
  "update entrances set longitude = null where id = '$entrance'" "entrances_coordinate_pair"
expect_error "정의되지 않은 허용 차량 유형" \
  "update entrances set permitted_vehicle_types = array['TRUCK'] where id = '$entrance'" "check constraint"
expect_error "출처 URL 없는 차량 제원" \
  "insert into vehicle_model_specs (manufacturer, model_name, height_mm, width_mm, length_mm, source_name, source_verified_at)
   values ('a', 'b', 1500, 1800, 4500, 's', now())" "not-null"

expect_ok "검증 시각과 함께 검증 등급 부여" \
  "update entrances set trust_level = 'VERIFIED_FIELD', verified_at = now() where id = '$entrance'"
apply_seed
expect_value "시드 재적용이 검증된 레코드를 덮어쓰지 않음" \
  "select trust_level from entrances where id = '$entrance'" "VERIFIED_FIELD"

if ((failures > 0)); then
  echo "검증 실패 ${failures}건"
  exit 1
fi
echo "모든 검증 통과"
