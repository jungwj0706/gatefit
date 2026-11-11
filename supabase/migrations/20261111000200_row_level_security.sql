-- 접근 제어
-- 앱에는 anon 키만 포함되므로, 공개 데이터 읽기와 제보 등록 외 모든 동작 차단
-- 데이터 등록·수정·검수는 service_role 키를 가진 관리 도구에서만 수행

alter table public.data_sources enable row level security;
alter table public.facilities enable row level security;
alter table public.entrances enable row level security;
alter table public.vehicle_model_specs enable row level security;
alter table public.entrance_reports enable row level security;
alter table public.entrance_verifications enable row level security;

revoke all on public.data_sources from anon, authenticated;
revoke all on public.facilities from anon, authenticated;
revoke all on public.entrances from anon, authenticated;
revoke all on public.vehicle_model_specs from anon, authenticated;
revoke all on public.entrance_reports from anon, authenticated;
revoke all on public.entrance_verifications from anon, authenticated;

grant select on public.data_sources to anon, authenticated;
grant select on public.facilities to anon, authenticated;
grant select on public.entrances to anon, authenticated;
grant select on public.vehicle_model_specs to anon, authenticated;

-- 증빙 참조와 내부 메모는 비공개
grant select (id, entrance_id, verification_method, verified_fields, verifier_type, verified_at)
  on public.entrance_verifications to anon, authenticated;

-- 제보자는 검수 상태·시각 지정 불가, 등록한 제보 재조회 불가
grant insert (id, entrance_id, report_type, description, proposed_value)
  on public.entrance_reports to anon, authenticated;

create policy "data sources are publicly readable"
  on public.data_sources for select
  to anon, authenticated
  using (true);

create policy "facilities are publicly readable"
  on public.facilities for select
  to anon, authenticated
  using (true);

create policy "entrances are publicly readable"
  on public.entrances for select
  to anon, authenticated
  using (true);

create policy "vehicle model specs are publicly readable"
  on public.vehicle_model_specs for select
  to anon, authenticated
  using (true);

create policy "entrance verifications are publicly readable"
  on public.entrance_verifications for select
  to anon, authenticated
  using (true);

create policy "anyone can submit a pending report"
  on public.entrance_reports for insert
  to anon, authenticated
  with check (status = 'PENDING' and reviewed_at is null and review_note is null);

revoke execute on function public.set_updated_at() from public, anon, authenticated;
