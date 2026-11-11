-- GATEFIT 핵심 스키마
-- 제공되지 않은 제한 조건은 NULL 또는 UNKNOWN으로 저장, NULL을 '제한 없음'으로 해석 금지

create function public.set_updated_at()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

-- 요일 유형별 출입 허용 시간 형식 검증
-- NULL: 미확인 / {"type":"ALL_DAY"} / {"type":"CLOSED"} / {"type":"RANGES","ranges":[{"start":"HH:MM","end":"HH:MM"}]}
-- end가 start 이하면 자정을 넘기는 구간, end "00:00"은 자정까지를 의미
create function public.is_valid_operating_hours(hours jsonb)
returns boolean
language sql
immutable
set search_path = ''
as $$
  select hours is null
    or (
      jsonb_typeof(hours) = 'object'
      and (
        hours ->> 'type' in ('ALL_DAY', 'CLOSED')
        or (
          hours ->> 'type' = 'RANGES'
          and jsonb_typeof(hours -> 'ranges') = 'array'
          and jsonb_array_length(hours -> 'ranges') > 0
          and not exists (
            select 1
            from jsonb_array_elements(hours -> 'ranges') as time_range
            where coalesce(time_range ->> 'start', '') !~ '^([01][0-9]|2[0-3]):[0-5][0-9]$'
               or coalesce(time_range ->> 'end', '') !~ '^([01][0-9]|2[0-3]):[0-5][0-9]$'
               or time_range ->> 'start' = time_range ->> 'end'
          )
        )
      )
    );
$$;

create table public.data_sources (
  id uuid primary key default gen_random_uuid(),
  source_name text not null check (length(btrim(source_name)) > 0),
  source_type text not null check (source_type in ('PUBLIC', 'OFFICIAL', 'FIELD', 'COMMUNITY')),
  source_url text,
  license text,
  imported_at timestamptz,
  original_updated_at date,
  notes text,
  created_at timestamptz not null default now()
);

create table public.facilities (
  id uuid primary key default gen_random_uuid(),
  name text not null check (length(btrim(name)) > 0),
  category text not null default 'OTHER'
    check (category in ('HOSPITAL', 'SHOPPING', 'HOTEL', 'PUBLIC_OFFICE', 'CULTURE', 'PARKING', 'PARK', 'OTHER')),
  road_address text,
  lot_address text,
  -- 시설 대표 좌표, 출입구 좌표로 대체 사용 금지
  latitude double precision check (latitude between -90 and 90),
  longitude double precision check (longitude between -180 and 180),
  aliases text[] not null default '{}',
  status text not null default 'UNKNOWN'
    check (status in ('OPERATING', 'CLOSED', 'UNDER_CONSTRUCTION', 'UNKNOWN')),
  status_note text,
  data_source_id uuid references public.data_sources (id),
  source_record_id text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint facilities_coordinate_pair check ((latitude is null) = (longitude is null))
);

create unique index facilities_source_record_key
  on public.facilities (data_source_id, source_record_id)
  where source_record_id is not null;

create trigger facilities_set_updated_at
  before update on public.facilities
  for each row execute function public.set_updated_at();

create table public.entrances (
  id uuid primary key default gen_random_uuid(),
  facility_id uuid not null references public.facilities (id) on delete cascade,
  name text not null check (length(btrim(name)) > 0),
  latitude double precision check (latitude between -90 and 90),
  longitude double precision check (longitude between -180 and 180),
  entrance_type text not null default 'UNKNOWN'
    check (entrance_type in ('ENTRY', 'EXIT', 'BIDIRECTIONAL', 'UNKNOWN')),
  entrance_class text
    check (entrance_class in ('MAIN', 'SUB', 'COMPLEX', 'RESIDENT_ONLY', 'GROUND', 'UNDERGROUND', 'OTHER')),
  -- NULL은 허용 차량 유형 미확인
  permitted_vehicle_types text[]
    check (permitted_vehicle_types <@ array['GENERAL', 'RESIDENT', 'EMERGENCY', 'SERVICE']::text[]),
  max_height_mm integer check (max_height_mm > 0),
  height_limit_structure text not null default 'UNKNOWN'
    check (height_limit_structure in ('INSTALLED', 'NOT_INSTALLED', 'UNKNOWN')),
  max_width_mm integer check (max_width_mm > 0),
  max_length_mm integer check (max_length_mm > 0),
  max_weight_kg integer check (max_weight_kg > 0),
  -- {"note": 원문 비고, "hours_exception_note": 시간표로 표현되지 않는 휴무·예외 조건}
  restrictions jsonb check (jsonb_typeof(restrictions) = 'object'),
  weekday_hours jsonb check (public.is_valid_operating_hours(weekday_hours)),
  saturday_hours jsonb check (public.is_valid_operating_hours(saturday_hours)),
  sunday_hours jsonb check (public.is_valid_operating_hours(sunday_hours)),
  holiday_hours jsonb check (public.is_valid_operating_hours(holiday_hours)),
  data_source_id uuid references public.data_sources (id),
  trust_level text not null default 'UNKNOWN'
    check (trust_level in ('VERIFIED_OFFICIAL', 'VERIFIED_FIELD', 'PUBLIC_BASELINE', 'COMMUNITY_REPORTED', 'UNKNOWN')),
  verified_at timestamptz,
  route_target_type text not null default 'ENTRANCE_POINT'
    check (route_target_type in ('ENTRANCE_POINT', 'ROAD_SNAPPED_POINT', 'FACILITY_CENTROID')),
  status text not null default 'UNKNOWN' check (status in ('ACTIVE', 'CLOSED', 'UNKNOWN')),
  source_record_id text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint entrances_coordinate_pair check ((latitude is null) = (longitude is null)),
  -- 검증 완료 등급은 검증 시각과 출처 없이 부여 불가
  constraint entrances_verified_requires_evidence check (
    trust_level not in ('VERIFIED_OFFICIAL', 'VERIFIED_FIELD')
    or (verified_at is not null and data_source_id is not null)
  )
);

create index entrances_facility_id_idx on public.entrances (facility_id);

create unique index entrances_source_record_key
  on public.entrances (data_source_id, source_record_id)
  where source_record_id is not null;

create trigger entrances_set_updated_at
  before update on public.entrances
  for each row execute function public.set_updated_at();

-- 제조사 공식 자료로 확인된 차량 제원만 등록
create table public.vehicle_model_specs (
  id uuid primary key default gen_random_uuid(),
  manufacturer text not null check (length(btrim(manufacturer)) > 0),
  model_name text not null check (length(btrim(model_name)) > 0),
  model_year integer check (model_year between 1980 and 2100),
  trim_name text,
  height_mm integer not null check (height_mm between 1000 and 4500),
  width_mm integer not null check (width_mm between 1000 and 2600),
  length_mm integer not null check (length_mm between 2000 and 13000),
  source_name text not null check (length(btrim(source_name)) > 0),
  source_url text not null check (source_url ~ '^https://'),
  source_verified_at timestamptz not null,
  created_at timestamptz not null default now()
);

create unique index vehicle_model_specs_model_key
  on public.vehicle_model_specs (manufacturer, model_name, coalesce(model_year, 0), coalesce(trim_name, ''));

create table public.entrance_reports (
  id uuid primary key default gen_random_uuid(),
  entrance_id uuid not null references public.entrances (id) on delete cascade,
  report_type text not null check (
    report_type in (
      'WRONG_LOCATION', 'ENTRANCE_CLOSED', 'HEIGHT_CHANGED', 'WRONG_DIRECTION',
      'HOURS_CHANGED', 'RESTRICTION_CHANGED', 'OTHER'
    )
  ),
  description text check (length(description) <= 500),
  proposed_value jsonb check (
    jsonb_typeof(proposed_value) = 'object' and pg_column_size(proposed_value) <= 2048
  ),
  status text not null default 'PENDING' check (status in ('PENDING', 'REVIEWING', 'APPROVED', 'REJECTED')),
  created_at timestamptz not null default now(),
  reviewed_at timestamptz,
  review_note text
);

create index entrance_reports_entrance_status_idx on public.entrance_reports (entrance_id, status);

-- 한 번 검증된 출입구도 변경 이력을 추적할 수 있도록 검증 기록 누적
create table public.entrance_verifications (
  id uuid primary key default gen_random_uuid(),
  entrance_id uuid not null references public.entrances (id) on delete cascade,
  verification_method text not null check (length(btrim(verification_method)) > 0),
  verified_fields text[] not null check (cardinality(verified_fields) > 0),
  evidence_reference text,
  verifier_type text not null check (verifier_type in ('ADMIN', 'FACILITY', 'FIELD')),
  verified_at timestamptz not null,
  notes text,
  created_at timestamptz not null default now()
);

create index entrance_verifications_entrance_idx
  on public.entrance_verifications (entrance_id, verified_at desc);
