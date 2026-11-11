-- tools/data-pipeline/build_baseline.py 생성 파일, 직접 수정 금지

insert into public.data_sources (id, source_name, source_type, source_url, license, imported_at, original_updated_at, notes) values
  ('69563d5c-2f75-59b5-b8fe-08152c6269ec', '서울 열린데이터광장 「서울시 차량 출입구 정보」 (OA-21700)', 'PUBLIC', 'https://data.seoul.go.kr/dataList/OA-21700/S/1/datasetView.do', '공공누리 제1유형 (출처표시, 상업적 이용 및 변경 가능)', '2026-11-11T04:35:44Z', '2022-12-10', '일회성 사업으로 구축된 데이터셋, 사업 종료 이후 현행화되지 않음'),
  ('5cc850be-aeb6-5e41-8a80-9230425d18ea', '서울 열린데이터광장 「서울시 시설물 정보」 (OA-21698)', 'PUBLIC', 'https://data.seoul.go.kr/dataList/OA-21698/S/1/datasetView.do', '공공누리 제1유형 (출처표시, 상업적 이용 및 변경 가능)', '2026-11-11T04:35:44Z', '2022-12-07', '일회성 사업으로 구축된 데이터셋, 사업 종료 이후 현행화되지 않음')
on conflict (id) do update set source_name = excluded.source_name, source_type = excluded.source_type, source_url = excluded.source_url, license = excluded.license, imported_at = excluded.imported_at, original_updated_at = excluded.original_updated_at, notes = excluded.notes;
