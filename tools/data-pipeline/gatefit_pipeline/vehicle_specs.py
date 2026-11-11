from .transform import stable_id

# supabase/migrations의 vehicle_model_specs 제약과 동일한 범위
_RANGES = {
    "height_mm": (1000, 4500),
    "width_mm": (1000, 2600),
    "length_mm": (2000, 13000),
}


def build_vehicle_specs(curated):
    manufacturer = _required_text(curated, "manufacturer")
    verified_at = _required_text(curated, "source_verified_at")
    rows = []
    seen = set()
    for catalog in curated["catalogs"]:
        model_name = _required_text(catalog, "model_name")
        source_name = _required_text(catalog, "source_name")
        source_url = _required_text(catalog, "source_url")
        # 출처를 확인할 수 없는 제원은 공식 제원으로 배포 금지
        if not source_url.startswith("https://"):
            raise ValueError(f"{model_name}: 출처 URL은 https 필수")
        for spec in catalog["specs"]:
            trim_name = spec.get("trim_name")
            model_year = spec.get("model_year")
            key = (manufacturer, model_name, model_year or 0, trim_name or "")
            if key in seen:
                raise ValueError(f"{model_name} {trim_name or ''}: 중복 제원")
            seen.add(key)
            row = {
                "id": stable_id("vehicle_spec", "|".join(str(part) for part in key)),
                "manufacturer": manufacturer,
                "model_name": model_name,
                "model_year": model_year,
                "trim_name": trim_name,
                "source_name": source_name,
                "source_url": source_url,
                "source_verified_at": verified_at,
            }
            for field, (low, high) in _RANGES.items():
                value = spec.get(field)
                if not isinstance(value, int) or isinstance(value, bool) or not low <= value <= high:
                    raise ValueError(f"{model_name} {trim_name or ''}: {field} 값 범위 오류 ({value})")
                row[field] = value
            rows.append(row)
    return rows


def _required_text(record, field):
    value = record.get(field)
    if not isinstance(value, str) or not value.strip():
        raise ValueError(f"{field} 누락")
    return value.strip()
