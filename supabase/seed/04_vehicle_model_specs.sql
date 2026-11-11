-- tools/data-pipeline/build_baseline.py 생성 파일, 직접 수정 금지
-- 출처: 현대자동차 공식 카탈로그, 원본 목록은 data/curated/vehicle_model_specs.json

insert into public.vehicle_model_specs (id, manufacturer, model_name, model_year, trim_name, height_mm, width_mm, length_mm, source_name, source_url, source_verified_at) values
  ('75148208-a02f-5715-a45f-672a9a431d77', '현대자동차', '아반떼', null, null, 1420, 1825, 4710, '현대자동차 공식 카탈로그 (아반떼)', 'https://www.hyundai.com/contents/repn-car/catalog/avante-catalog.pdf', '2026-11-11'),
  ('b850091b-6992-5094-a22e-6a2051502f06', '현대자동차', '쏘나타 디 엣지', null, null, 1445, 1860, 4910, '현대자동차 공식 카탈로그 (쏘나타 디 엣지)', 'https://www.hyundai.com/contents/repn-car/catalog/sonata-the-edge-catalog.pdf', '2026-11-11'),
  ('13e95b4d-1582-5a44-9e21-d7af40026f19', '현대자동차', '그랜저', null, null, 1460, 1880, 5035, '현대자동차 공식 카탈로그 (그랜저)', 'https://www.hyundai.com/contents/repn-car/catalog/grandeur-catalog.pdf', '2026-11-11'),
  ('7efca089-cb10-5253-8b35-5c2870a47a56', '현대자동차', '코나', null, '17·18인치 휠 (루프랙 미적용)', 1580, 1825, 4350, '현대자동차 공식 카탈로그 (코나)', 'https://www.hyundai.com/contents/repn-car/catalog/kona-catalog.pdf', '2026-11-11'),
  ('888ac7c4-ecc2-5ca0-bdd1-0ce67e8e7a84', '현대자동차', '코나', null, '17·18인치 휠 (루프랙 적용)', 1585, 1825, 4350, '현대자동차 공식 카탈로그 (코나)', 'https://www.hyundai.com/contents/repn-car/catalog/kona-catalog.pdf', '2026-11-11'),
  ('b84bbd96-c245-54f6-b830-7609eeb9cc32', '현대자동차', '코나', null, '19인치 휠 (루프랙 미적용)', 1585, 1825, 4350, '현대자동차 공식 카탈로그 (코나)', 'https://www.hyundai.com/contents/repn-car/catalog/kona-catalog.pdf', '2026-11-11'),
  ('36cd4cdc-11ac-5149-9ca7-556074615a54', '현대자동차', '코나', null, '19인치 휠 (루프랙 적용)', 1590, 1825, 4350, '현대자동차 공식 카탈로그 (코나)', 'https://www.hyundai.com/contents/repn-car/catalog/kona-catalog.pdf', '2026-11-11'),
  ('5ed51918-4e76-5ef8-8b47-f1795b563fdf', '현대자동차', '투싼', null, 'N Line 제외', 1665, 1865, 4640, '현대자동차 공식 카탈로그 (투싼)', 'https://www.hyundai.com/contents/repn-car/catalog/tucson-catalog.pdf', '2026-11-11'),
  ('2acab92a-4ce6-5c7d-9437-cce24cb7cd81', '현대자동차', '싼타페', null, '루프랙 미적용', 1720, 1900, 4830, '현대자동차 공식 카탈로그 (싼타페)', 'https://www.hyundai.com/contents/repn-car/catalog/santafe-catalog.pdf', '2026-11-11'),
  ('fcae4686-5c66-5938-a090-2ad738052e74', '현대자동차', '싼타페', null, '루프랙 적용', 1770, 1900, 4830, '현대자동차 공식 카탈로그 (싼타페)', 'https://www.hyundai.com/contents/repn-car/catalog/santafe-catalog.pdf', '2026-11-11'),
  ('50e60c46-9837-59f8-ac95-3898661bd4ff', '현대자동차', '싼타페', null, '캘리그래피 (루프랙 미적용)', 1730, 1900, 4830, '현대자동차 공식 카탈로그 (싼타페)', 'https://www.hyundai.com/contents/repn-car/catalog/santafe-catalog.pdf', '2026-11-11'),
  ('99e5e690-f129-5820-90a6-2349fcf7d542', '현대자동차', '싼타페', null, '캘리그래피 (루프랙 적용)', 1780, 1900, 4830, '현대자동차 공식 카탈로그 (싼타페)', 'https://www.hyundai.com/contents/repn-car/catalog/santafe-catalog.pdf', '2026-11-11'),
  ('63a0c6e3-0492-56e6-9bed-73660a1ca630', '현대자동차', '팰리세이드', null, '기본 (루프랙 포함 전고, 하이루프 제외)', 1805, 1980, 5060, '현대자동차 공식 카탈로그 (팰리세이드)', 'https://www.hyundai.com/contents/repn-car/catalog/palisade-catalog.pdf', '2026-11-11'),
  ('ff299484-9e82-5965-923c-bae87ddf3b63', '현대자동차', '팰리세이드', null, '캘리그래피 (루프랙 포함 전고, 하이루프 제외)', 1805, 1980, 5065, '현대자동차 공식 카탈로그 (팰리세이드)', 'https://www.hyundai.com/contents/repn-car/catalog/palisade-catalog.pdf', '2026-11-11'),
  ('ea4ea4bb-a94b-5601-bd95-7ff6de68eb21', '현대자동차', '팰리세이드', null, 'XRT (루프랙 포함 전고, 하이루프 제외)', 1805, 1980, 5095, '현대자동차 공식 카탈로그 (팰리세이드)', 'https://www.hyundai.com/contents/repn-car/catalog/palisade-catalog.pdf', '2026-11-11'),
  ('3fa0491c-b655-5fc0-bf6d-3d51906fa48f', '현대자동차', '아이오닉 5', null, null, 1605, 1890, 4655, '현대자동차 공식 카탈로그 (아이오닉 5)', 'https://www.hyundai.com/contents/repn-car/catalog/ioniq5-catalog.pdf', '2026-11-11'),
  ('13b55643-dba3-5039-80c4-ff23ab5c1cc6', '현대자동차', '아이오닉 9', null, null, 1790, 1980, 5060, '현대자동차 공식 카탈로그 (아이오닉 9)', 'https://www.hyundai.com/contents/repn-car/catalog/ioniq9-catalog.pdf', '2026-11-11'),
  ('10ea60dd-4afe-566a-a3b5-9daf498cd524', '현대자동차', '스타리아', null, '투어러', 1990, 1995, 5255, '현대자동차 공식 카탈로그 (스타리아)', 'https://www.hyundai.com/contents/repn-car/catalog/staria-catalog.pdf', '2026-11-11'),
  ('ebe94745-feed-51e3-9cb3-4c564f074921', '현대자동차', '스타리아', null, '카고', 2000, 1995, 5255, '현대자동차 공식 카탈로그 (스타리아)', 'https://www.hyundai.com/contents/repn-car/catalog/staria-catalog.pdf', '2026-11-11'),
  ('9f66edb7-8227-5701-9df8-cb27e6cf7ae3', '현대자동차', '스타리아', null, '킨더', 2120, 1995, 5255, '현대자동차 공식 카탈로그 (스타리아)', 'https://www.hyundai.com/contents/repn-car/catalog/staria-catalog.pdf', '2026-11-11'),
  ('f8e01b26-a1d6-52cc-a37a-fbe02f375046', '현대자동차', '스타리아 라운지', null, '기본 (리무진·캠퍼 제외)', 1990, 1995, 5255, '현대자동차 공식 카탈로그 (스타리아 라운지)', 'https://www.hyundai.com/contents/repn-car/catalog/staria-lounge-catalog.pdf', '2026-11-11'),
  ('e668e835-80d0-51b6-961f-b4145fd9269f', '현대자동차', '스타리아 라운지', null, '캠퍼', 2095, 1995, 5255, '현대자동차 공식 카탈로그 (스타리아 라운지)', 'https://www.hyundai.com/contents/repn-car/catalog/staria-lounge-catalog.pdf', '2026-11-11'),
  ('83864c5c-5396-57e2-9d1e-27a86d5e7127', '현대자동차', '스타리아 라운지', null, '리무진', 2200, 1995, 5255, '현대자동차 공식 카탈로그 (스타리아 라운지)', 'https://www.hyundai.com/contents/repn-car/catalog/staria-lounge-catalog.pdf', '2026-11-11')
on conflict (id) do update set manufacturer = excluded.manufacturer, model_name = excluded.model_name, model_year = excluded.model_year, trim_name = excluded.trim_name, height_mm = excluded.height_mm, width_mm = excluded.width_mm, length_mm = excluded.length_mm, source_name = excluded.source_name, source_url = excluded.source_url, source_verified_at = excluded.source_verified_at;
