import json

SNAPSHOT_SCHEMA_VERSION = 1
SQL_BATCH_SIZE = 200

_TABLE_COLUMNS = {
    "data_sources": (
        "id", "source_name", "source_type", "source_url", "license",
        "imported_at", "original_updated_at", "notes",
    ),
    "facilities": (
        "id", "name", "category", "road_address", "lot_address", "latitude", "longitude",
        "aliases", "status", "status_note", "data_source_id", "source_record_id", "updated_at",
    ),
    "entrances": (
        "id", "facility_id", "name", "latitude", "longitude", "entrance_type", "entrance_class",
        "permitted_vehicle_types", "max_height_mm", "height_limit_structure",
        "max_width_mm", "max_length_mm", "max_weight_kg", "restrictions",
        "weekday_hours", "saturday_hours", "sunday_hours", "holiday_hours",
        "data_source_id", "trust_level", "verified_at", "route_target_type",
        "status", "source_record_id", "updated_at",
    ),
}

_TEXT_ARRAY_COLUMNS = {"aliases", "permitted_vehicle_types"}
_JSONB_COLUMNS = {"restrictions", "weekday_hours", "saturday_hours", "sunday_hours", "holiday_hours"}


def _quote(text):
    return "'" + text.replace("'", "''") + "'"


def sql_literal(column, value):
    if value is None:
        return "null"
    if column in _TEXT_ARRAY_COLUMNS:
        if not value:
            return "'{}'::text[]"
        return "array[" + ", ".join(_quote(item) for item in value) + "]::text[]"
    if column in _JSONB_COLUMNS:
        return _quote(json.dumps(value, ensure_ascii=False, sort_keys=True)) + "::jsonb"
    if isinstance(value, bool):
        return "true" if value else "false"
    if isinstance(value, (int, float)):
        return repr(value)
    return _quote(str(value))


def render_seed_sql(table, rows, header_lines, on_conflict):
    columns = _TABLE_COLUMNS[table]
    lines = [f"-- {line}" for line in header_lines]
    lines.append("")
    for start in range(0, len(rows), SQL_BATCH_SIZE):
        batch = rows[start:start + SQL_BATCH_SIZE]
        lines.append(f"insert into public.{table} ({', '.join(columns)}) values")
        values = [
            "  (" + ", ".join(sql_literal(column, row[column]) for column in columns) + ")"
            for row in batch
        ]
        lines.append(",\n".join(values))
        lines.append(f"{on_conflict};")
        lines.append("")
    return "\n".join(lines)


def upsert_clause(table):
    columns = [column for column in _TABLE_COLUMNS[table] if column != "id"]
    assignments = ", ".join(f"{column} = excluded.{column}" for column in columns)
    return f"on conflict (id) do update set {assignments}"


def render_snapshot_json(dataset, generated_at):
    snapshot = {
        "schema_version": SNAPSHOT_SCHEMA_VERSION,
        "generated_at": generated_at,
        "data_sources": [_without_nulls(row) for row in dataset["data_sources"]],
        "facilities": [_without_nulls(row) for row in dataset["facilities"]],
        "entrances": [_without_nulls(row) for row in dataset["entrances"]],
    }
    return json.dumps(snapshot, ensure_ascii=False, separators=(",", ":"))


def _without_nulls(row):
    return {key: value for key, value in row.items() if value is not None and value != []}
