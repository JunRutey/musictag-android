"""Binary Android XML (AXML) decoder -> readable XML text.

Used by tools/manifest_contract.py to inspect the *merged* manifest inside a built
APK without aapt2/aapt, so the CI contract check only needs python3.

Pure stdlib; no Android tooling required.
"""

from __future__ import annotations

import struct
from html import escape

ANDROID_NS = "http://schemas.android.com/apk/res/android"


def _string_pool(data: bytes, offset: int) -> list[str]:
    (_type, _header_size, _size) = struct.unpack_from("<HHI", data, offset)
    count, _style_count, flags, strings_start, _ = struct.unpack_from("<IIIII", data, offset + 8)
    utf8 = bool(flags & 0x100)
    offsets = struct.unpack_from("<%dI" % count, data, offset + 28)
    base = offset + strings_start
    out: list[str] = []
    for rel in offsets:
        p = base + rel
        if utf8:
            p += 1  # utf-16 length (ignored)
            first = data[p]
            p += 1
            if first & 0x80:
                p += 1
            length = data[p]
            p += 1
            if length & 0x80:
                length = ((length & 0x7F) << 8) | data[p]
                p += 1
            out.append(data[p:p + length].decode("utf-8", "replace"))
        else:
            length = struct.unpack_from("<H", data, p)[0]
            p += 2
            if length & 0x8000:
                length = ((length & 0x7FFF) << 16) | struct.unpack_from("<H", data, p)[0]
                p += 2
            out.append(data[p:p + length * 2].decode("utf-16-le", "replace"))
    return out


def decode_bytes(data: bytes) -> str:
    if struct.unpack_from("<H", data, 0)[0] != 0x0003:
        raise ValueError("not a binary Android XML document")
    pool: list[str] = []
    lines: list[str] = []
    depth = 0
    offset = 8
    total = len(data)

    def value_of(raw: int, data_type: int, value: int) -> str:
        if raw != 0xFFFFFFFF:
            return pool[raw]
        if data_type == 0x12:
            return "true" if value != 0 else "false"
        if data_type == 0x01:
            return "@ref/0x%08x" % value
        if data_type == 0x10 and value > 0x7FFFFFFF:
            return str(value - (1 << 32))
        return str(value)

    while offset + 8 <= total:
        chunk_type, _header_size, chunk_size = struct.unpack_from("<HHI", data, offset)
        if chunk_size == 0:
            break
        if chunk_type == 0x0001:
            pool = _string_pool(data, offset)
        elif chunk_type == 0x0102:
            _ns, name_idx, attr_start, attr_size, attr_count = struct.unpack_from("<IIHHH", data, offset + 16)
            tag = pool[name_idx]
            attrs: list[str] = []
            p = offset + 16 + attr_start
            for _ in range(attr_count):
                _a_ns, a_name, a_raw, _a_size, _a_res0, a_type, a_data = struct.unpack_from("<IIIHBBI", data, p)
                attrs.append('%s="%s"' % (pool[a_name], escape(value_of(a_raw, a_type, a_data), quote=True)))
                p += attr_size
            pad = "  " * depth
            if attrs:
                lines.append(f"{pad}<{tag} " + " ".join(attrs) + ">")
            else:
                lines.append(f"{pad}<{tag}>")
            depth += 1
        elif chunk_type == 0x0103:
            depth -= 1
            name_idx = struct.unpack_from("<I", data, offset + 20)[0]
            lines.append("  " * depth + f"</{pool[name_idx]}>")
        offset += chunk_size
    return "\n".join(lines)


def decode_file(path: str) -> str:
    with open(path, "rb") as fh:
        return decode_bytes(fh.read())
