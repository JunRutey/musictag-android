#!/usr/bin/env python3
"""Generate deterministic, license-free audio fixtures for the tag-engine tests.

Every fixture is synthesised from the format specifications (no real music, no
copyrighted artwork): silence-free containers whose only purpose is to exercise tag
parsing. Values are stable so tests can assert exact strings.

Currently generated:
  sample.mp3   ID3v2.3 (text frames, APIC cover, USLT lyrics) + 3 MPEG-1 L3 frames
  sample.flac  STREAMINFO + VORBIS_COMMENT + PICTURE + PADDING

Usage:
  python3 tools/gen_fixtures.py            # write fixtures
  python3 tools/gen_fixtures.py --check    # verify committed fixtures are in sync
"""

from __future__ import annotations

import struct
import sys
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "core" / "tag" / "src" / "test" / "resources" / "fixtures"

TITLE = "Fixture Title"
ARTIST = "Fixture Artist"
ALBUM = "Fixture Album"
ALBUMARTIST = "Fixture Album Artist"
GENRE = "Jazz"
COMPOSER = "Fixture Composer"
YEAR = "2021"
COMMENT = "Fixture Comment"
LYRICS = "[00:01.00]fixture line one\n[00:02.00]fixture line two"
TRACK = 3
TRACK_TOTAL = 12
DISC = 1
DISC_TOTAL = 2


# --------------------------------------------------------------------------- helpers


def tiny_png() -> bytes:
    """A valid 1x1 RGBA PNG (transparent), 70 bytes."""

    def chunk(tag: bytes, data: bytes) -> bytes:
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    ihdr = struct.pack(">IIBBBBB", 1, 1, 8, 6, 0, 0, 0)
    idat = zlib.compress(b"\x00\x00\x00\x00\x00", 9)
    return (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", ihdr)
        + chunk(b"IDAT", idat)
        + chunk(b"IEND", b"")
    )


def syncsafe(value: int) -> bytes:
    return bytes(((value >> 21) & 0x7F, (value >> 14) & 0x7F, (value >> 7) & 0x7F, value & 0x7F))


# ------------------------------------------------------------------------------ mp3


def id3v23_frame(frame_id: bytes, payload: bytes) -> bytes:
    return frame_id + struct.pack(">I", len(payload)) + struct.pack(">H", 0) + payload


def text_frame(frame_id: bytes, text: str) -> bytes:
    return id3v23_frame(frame_id, b"\x00" + text.encode("latin-1"))  # ISO-8859-1


def comment_frame(text: str) -> bytes:
    return id3v23_frame(b"COMM", b"\x00" + b"eng" + b"\x00" + text.encode("latin-1"))


def lyrics_frame(text: str) -> bytes:
    return id3v23_frame(b"USLT", b"\x00" + b"eng" + b"\x00" + text.encode("latin-1"))


def picture_frame(png: bytes) -> bytes:
    return id3v23_frame(b"APIC", b"\x00" + b"image/png" + b"\x00" + b"\x03" + b"\x00" + png)


def mp3_fixture() -> bytes:
    frames = b"".join(
        [
            text_frame(b"TIT2", TITLE),
            text_frame(b"TPE1", ARTIST),
            text_frame(b"TALB", ALBUM),
            text_frame(b"TPE2", ALBUMARTIST),
            text_frame(b"TCON", GENRE),
            text_frame(b"TCOM", COMPOSER),
            text_frame(b"TRCK", f"{TRACK}/{TRACK_TOTAL}"),
            text_frame(b"TPOS", f"{DISC}/{DISC_TOTAL}"),
            text_frame(b"TYER", YEAR),
            comment_frame(COMMENT),
            lyrics_frame(LYRICS),
            picture_frame(tiny_png()),
        ]
    )
    tag = b"ID3" + b"\x03\x00" + b"\x00" + syncsafe(len(frames)) + frames

    # MPEG-1 Layer III, 128 kbit/s, 44.1 kHz, no padding -> 417-byte frames of silence.
    header = b"\xff\xfb\x90\x00"
    frame = header + b"\x00" * (417 - len(header))
    return tag + frame * 3


# ----------------------------------------------------------------------------- flac


def flac_block(block_type: int, data: bytes, last: bool = False) -> bytes:
    return bytes([(0x80 if last else 0) | block_type]) + len(data).to_bytes(3, "big") + data


def flac_streaminfo(sample_rate: int = 44100, channels: int = 2, bits: int = 16, total: int = 132300) -> bytes:
    packed = (sample_rate << 44) | ((channels - 1) << 41) | ((bits - 1) << 36) | total
    return (
        struct.pack(">HH", 4096, 4096)  # min/max block size
        + (0).to_bytes(3, "big")        # min frame size
        + (0).to_bytes(3, "big")        # max frame size
        + packed.to_bytes(8, "big")
        + b"\x00" * 16                  # md5 (unset)
    )


def vorbis_comment(comments: list[tuple[str, str]], vendor: bytes = b"musictag-fixture") -> bytes:
    out = struct.pack("<I", len(vendor)) + vendor + struct.pack("<I", len(comments))
    for key, value in comments:
        entry = f"{key}={value}".encode("utf-8")
        out += struct.pack("<I", len(entry)) + entry
    return out


def flac_picture(png: bytes, mime: str = "image/png") -> bytes:
    mime_bytes = mime.encode("ascii")
    out = struct.pack(">I", 3)  # front cover
    out += struct.pack(">I", len(mime_bytes)) + mime_bytes
    out += struct.pack(">I", 0)  # empty description
    out += struct.pack(">IIII", 1, 1, 32, 0)  # width, height, depth, colours
    out += struct.pack(">I", len(png)) + png
    return out


def flac_fixture() -> bytes:
    comments = [
        ("TITLE", TITLE),
        ("ARTIST", ARTIST),
        ("ALBUM", ALBUM),
        ("ALBUMARTIST", ALBUMARTIST),
        ("GENRE", GENRE),
        ("COMPOSER", COMPOSER),
        ("TRACKNUMBER", str(TRACK)),
        ("TRACKTOTAL", str(TRACK_TOTAL)),
        ("DISCNUMBER", str(DISC)),
        ("DISCTOTAL", str(DISC_TOTAL)),
        ("DATE", YEAR),
        ("COMMENT", COMMENT),
        ("LYRICS", LYRICS),
    ]
    return (
        b"fLaC"
        + flac_block(0, flac_streaminfo())
        + flac_block(4, vorbis_comment(comments))
        + flac_block(6, flac_picture(tiny_png()))
        + flac_block(1, b"\x00" * 256, last=True)
    )


# ------------------------------------------------------------------------------ ogg


def ogg_crc(data: bytes) -> int:
    """CRC-32 as used by Ogg: polynomial 0x04C11DB7, no reflection, init 0."""
    crc = 0
    for byte in data:
        crc ^= byte << 24
        for _ in range(8):
            if crc & 0x80000000:
                crc = ((crc << 1) ^ 0x04C11DB7) & 0xFFFFFFFF
            else:
                crc = (crc << 1) & 0xFFFFFFFF
    return crc


def ogg_page(payload: bytes, header_type: int, granule: int, serial: int, sequence: int) -> bytes:
    segments = []
    remaining = len(payload)
    while remaining >= 255:
        segments.append(255)
        remaining -= 255
    segments.append(remaining)
    header = (
        b"OggS"
        + bytes([0, header_type])
        + struct.pack("<q", granule)
        + struct.pack("<I", serial)
        + struct.pack("<I", sequence)
        + b"\x00\x00\x00\x00"
        + bytes([len(segments)])
        + bytes(segments)
    )
    page = header + payload
    checksum = ogg_crc(page)
    return page[:22] + struct.pack("<I", checksum) + page[26:]


def ogg_vorbis_identification(rate: int = 44100, channels: int = 2) -> bytes:
    return (
        b"\x01vorbis"
        + struct.pack("<I", 0)          # vorbis version
        + bytes([channels])
        + struct.pack("<I", rate)
        + struct.pack("<i", -1)         # bitrate maximum
        + struct.pack("<i", 128000)     # bitrate nominal
        + struct.pack("<i", -1)         # bitrate minimum
        + bytes([0xB8])                 # blocksizes 2^8 / 2^11
        + b"\x01"                       # framing
    )


def ogg_vorbis_comment(comments: list[tuple[str, str]], vendor: bytes = b"musictag-fixture") -> bytes:
    out = b"\x03vorbis" + struct.pack("<I", len(vendor)) + vendor + struct.pack("<I", len(comments))
    for key, value in comments:
        entry = f"{key}={value}".encode("utf-8")
        out += struct.pack("<I", len(entry)) + entry
    return out + b"\x01"


def ogg_fixture() -> bytes:
    comments = [
        ("TITLE", TITLE),
        ("ARTIST", ARTIST),
        ("ALBUM", ALBUM),
        ("ALBUMARTIST", ALBUMARTIST),
        ("GENRE", GENRE),
        ("COMPOSER", COMPOSER),
        ("TRACKNUMBER", str(TRACK)),
        ("TRACKTOTAL", str(TRACK_TOTAL)),
        ("DISCNUMBER", str(DISC)),
        ("DISCTOTAL", str(DISC_TOTAL)),
        ("DATE", YEAR),
        ("COMMENT", COMMENT),
        ("LYRICS", LYRICS),
    ]
    serial = 0x4D555354  # "MUST"
    setup = b"\x05vorbis" + b"\x00" * 64
    return (
        ogg_page(ogg_vorbis_identification(), 0x02, 0, serial, 0)          # BOS + identification
        + ogg_page(ogg_vorbis_comment(comments) + setup, 0x00, 0, serial, 1)  # comment + setup
        + ogg_page(b"\x00" * 64, 0x04, 44100 * 3, serial, 2)           # EOS, 3 s of audio
    )



# ------------------------------------------------------------------------------ main

FIXTURES = {
    "sample.mp3": mp3_fixture,
    "sample.flac": flac_fixture,
    "sample.ogg": ogg_fixture,
    # Standalone cover used by the write tests to prove replacement round-trips.
    "cover.png": tiny_png,
}


def main() -> int:
    check = "--check" in sys.argv[1:]
    OUT.mkdir(parents=True, exist_ok=True)
    failures = 0
    for name, builder in FIXTURES.items():
        payload = builder()
        path = OUT / name
        if check:
            if not path.exists():
                print(f"missing fixture: {path.relative_to(ROOT)}")
                failures += 1
            elif path.read_bytes() != payload:
                print(f"out of date fixture: {path.relative_to(ROOT)}")
                failures += 1
            else:
                print(f"ok {path.relative_to(ROOT)} ({len(payload)} bytes)")
        else:
            path.write_bytes(payload)
            print(f"wrote {path.relative_to(ROOT)} ({len(payload)} bytes)")
    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
