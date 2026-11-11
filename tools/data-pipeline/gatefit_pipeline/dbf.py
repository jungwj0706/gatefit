import struct
import zipfile

HEADER_TERMINATOR = 0x0D
DELETED_FLAG = b"*"


def parse_dbf(data, encoding="cp949"):
    record_count, header_length, record_length = struct.unpack("<IHH", data[4:12])

    fields = []
    offset = 32
    while data[offset] != HEADER_TERMINATOR:
        name = data[offset:offset + 11].split(b"\0")[0].decode("ascii", "replace")
        fields.append((name, data[offset + 16]))
        offset += 32

    rows = []
    position = header_length
    for _ in range(record_count):
        record = data[position:position + record_length]
        position += record_length
        if record[0:1] == DELETED_FLAG:
            continue
        row = {}
        cursor = 1
        for name, length in fields:
            row[name] = record[cursor:cursor + length].decode(encoding, "replace").strip()
            cursor += length
        rows.append(row)
    return rows


def read_table_from_zip(zip_path, required_field):
    # 압축 파일에 한글 컬럼본과 영문 컬럼본이 함께 들어 있어, 영문 컬럼명 존재 여부로 대상 선택
    with zipfile.ZipFile(zip_path) as archive:
        for info in archive.infolist():
            if not info.filename.lower().endswith(".dbf"):
                continue
            rows = parse_dbf(archive.read(info))
            if rows and required_field in rows[0]:
                return rows
    raise ValueError(f"{zip_path}: '{required_field}' 컬럼을 가진 DBF 없음")
