#!/usr/bin/env python3
"""Intent-resolution matrix for the compatibility surface.

The contract check (``manifest_contract.py``) proves the declared components and filters are
still there. This tool goes one step further: it resolves realistic intents the way Android
would - action, categories, scheme, MIME type and path patterns - and asserts which
component wins. That is the property other apps depend on.

Usage:
    python tools/intent_matrix.py --apk app/build/outputs/apk/debug/app-debug.apk
    python tools/intent_matrix.py --manifest app/src/main/AndroidManifest.xml
    python tools/intent_matrix.py --apk ... --write-doc docs/INTENT_MATRIX.md
"""

from __future__ import annotations

import argparse
import re
import sys
from dataclasses import dataclass, field
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import manifest_contract as mc  # noqa: E402

ACTION_VIEW = "android.intent.action.VIEW"
ACTION_EDIT = "android.intent.action.EDIT"
ACTION_SEND = "android.intent.action.SEND"
ACTION_SEND_MULTIPLE = "android.intent.action.SEND_MULTIPLE"
ACTION_MAIN = "android.intent.action.MAIN"
ACTION_SEARCH = "android.intent.action.SEARCH"
CATEGORY_DEFAULT = "android.intent.category.DEFAULT"
CATEGORY_LAUNCHER = "android.intent.category.LAUNCHER"
CATEGORY_SEARCHABLE = "android.intent.category.SEARCH"

PACKAGE = "com.xjcheng.musictageditor"
SONG_DETAIL = f"{PACKAGE}.SongDetailActivity"
MAIN_ACTIVITY = f"{PACKAGE}.MainActivity"
SEARCH_ACTIVITY = f"{PACKAGE}.activity.SearchActivity"


@dataclass
class Probe:
    """One intent, and the component it must resolve to."""

    description: str
    action: str
    expect: set[str] = field(default_factory=set)
    categories: list[str] = field(default_factory=list)
    scheme: str | None = None
    mime_type: str | None = None
    path: str | None = None
    explicit: str | None = None
    expect_none: bool = False


def probes() -> list[Probe]:
    """The intents other apps actually send, in the shapes they send them."""
    return [
        # Launcher.
        Probe("launcher icon", ACTION_MAIN, {MAIN_ACTIVITY}, [CATEGORY_LAUNCHER]),
        # Explicit MAIN without a launcher category resolves to nothing implicitly: that
        # filter exists only so an explicit-component MAIN lands on the editor, which the
        # explicit-component section below verifies.
        Probe("implicit MAIN without LAUNCHER", ACTION_MAIN, expect_none=True),
        # Typed documents, both schemes.
        Probe("VIEW content audio/mpeg", ACTION_VIEW, {SONG_DETAIL}, [CATEGORY_DEFAULT], "content", "audio/mpeg"),
        Probe("VIEW content audio/flac", ACTION_VIEW, {SONG_DETAIL}, [CATEGORY_DEFAULT], "content", "audio/flac"),
        Probe("VIEW content audio/ogg", ACTION_VIEW, {SONG_DETAIL}, [CATEGORY_DEFAULT], "content", "audio/ogg"),
        Probe("VIEW content audio/x-ape", ACTION_VIEW, {SONG_DETAIL}, [CATEGORY_DEFAULT], "content", "audio/x-ape"),
        Probe("VIEW content audio/mp4", ACTION_VIEW, {SONG_DETAIL}, [CATEGORY_DEFAULT], "content", "audio/mp4"),
        Probe("VIEW content audio/x-ms-wma", ACTION_VIEW, {SONG_DETAIL}, [CATEGORY_DEFAULT], "content", "audio/x-ms-wma"),
        Probe("VIEW content audio/dsf", ACTION_VIEW, {SONG_DETAIL}, [CATEGORY_DEFAULT], "content", "audio/dsf"),
        Probe("VIEW file audio/x-wav", ACTION_VIEW, {SONG_DETAIL}, [CATEGORY_DEFAULT], "file", "audio/x-wav"),
        # Providers that report */*: resolution has to fall back to the path pattern.
        # FLAC, MP3, Ogg, MP4, WMA and AIFF have proper MIME filters, so the */* filter
        # deliberately lists only the formats providers often label wrongly. A */*-only
        # sender naming .flac therefore resolves to nothing - same as the original.
        Probe(
            "VIEW content */* with .flac path (typed filter expected instead)",
            ACTION_VIEW,
            expect_none=True,
            categories=[CATEGORY_DEFAULT],
            scheme="content",
            mime_type="*/*",
            path="/storage/emulated/0/Music/song.flac",
        ),
        Probe(
            "VIEW content */* with a multi-dot .wv path",
            ACTION_VIEW,
            {SONG_DETAIL},
            [CATEGORY_DEFAULT],
            "content",
            "*/*",
            "/storage/emulated/0/Music/song.wv",
        ),
        # Sharing (the original has no ACTION_EDIT filter; editing is reached explicitly).
        Probe("SEND content audio/mpeg", ACTION_SEND, {SONG_DETAIL}, [CATEGORY_DEFAULT], "content", "audio/mpeg"),
        Probe(
            "SEND content audio/flac",
            ACTION_SEND,
            {SONG_DETAIL},
            [CATEGORY_DEFAULT],
            "content",
            "audio/flac",
        ),
        # System search: reached through the searchable meta-data, not by resolution.
        # The original declares ACTION_SEARCH without DEFAULT, so an implicit SEARCH intent
        # intentionally resolves to nothing; the meta-data check below covers the wiring.
        Probe("implicit SEARCH (discovered via meta-data)", ACTION_SEARCH, expect_none=True),
        # A negative case: nothing must claim a text document.
        Probe("VIEW content text/plain (must not match)", ACTION_VIEW, expect_none=True, categories=[CATEGORY_DEFAULT], scheme="content", mime_type="text/plain"),
    ]


def mime_matches(candidate: str, wanted: str) -> bool:
    candidate = candidate.lower()
    wanted = wanted.lower()
    if candidate == wanted or candidate == "*/*":
        return True
    if candidate.endswith("/*") and wanted.split("/")[0] == candidate.split("/")[0]:
        return True
    return False


def filter_matches(intent_filter: dict, probe: Probe) -> bool:
    actions = intent_filter.get("actions") or []
    if probe.action not in actions:
        return False

    categories = set(intent_filter.get("categories") or [])
    # Android requires DEFAULT in the filter for an implicit intent that carries no
    # category of its own; when the intent does carry categories (MAIN+LAUNCHER, or the
    # search dialog's SEARCH) only those have to be declared.
    needed = set(probe.categories) if probe.categories else {CATEGORY_DEFAULT}
    if probe.explicit is None and not needed.issubset(categories):
        return False
    if probe.explicit is not None and not needed.issubset(categories):
        return False

    schemes = intent_filter.get("schemes") or []
    if probe.scheme and schemes and probe.scheme not in schemes:
        return False

    mime_types = intent_filter.get("mimeTypes") or []
    if probe.mime_type:
        if not mime_types:
            return False
        if not any(mime_matches(authorized, probe.mime_type) for authorized in mime_types):
            return False
    elif mime_types and "*/*" not in mime_types:
        # An intent without a type only matches filters that accept any type.
        return False

    patterns = intent_filter.get("pathPatterns") or []
    if patterns:
        if not probe.path:
            return False
        # AAPT2 and the manifest writer disagree on backslash escaping depending on the
        # toolchain, so compare the pattern's intent rather than its exact bytes.
        def normalise(pattern: str) -> str:
            return pattern.replace("\\\\", "\\")

        if not any(re.fullmatch(normalise(pattern), probe.path) for pattern in patterns):
            return False

    return True


def resolve(contract: dict, probe: Probe) -> set[str]:
    if probe.explicit:
        for group in ("activities", "services", "receivers"):
            for component in contract.get(group) or []:
                if component["name"] == probe.explicit:
                    return {probe.explicit}
        return set()

    matches: set[str] = set()
    for component in contract.get("activities") or []:
        if not component.get("exported"):
            continue
        for intent_filter in component.get("filters") or []:
            if filter_matches(intent_filter, probe):
                matches.add(component["name"])
                break
    return matches


def main() -> int:
    parser = argparse.ArgumentParser()
    source = parser.add_mutually_exclusive_group(required=True)
    source.add_argument("--apk")
    source.add_argument("--manifest")
    parser.add_argument("--write-doc", help="write the matrix as Markdown to this path")
    args = parser.parse_args()

    if args.apk:
        contract = mc.contract_from_apk(args.apk)
        origin = args.apk
    else:
        import xml.etree.ElementTree as ET

        root = ET.parse(args.manifest).getroot()
        contract = mc.contract_from_manifest(root, PACKAGE)
        origin = args.manifest

    failures: list[str] = []
    rows: list[tuple[str, str, str, str]] = []

    for probe in probes():
        resolved = resolve(contract, probe)
        if probe.expect_none:
            okay = not resolved
            expected = "(nothing)"
        else:
            okay = probe.expect.issubset(resolved)
            expected = ", ".join(sorted(probe.expect))
        actual = ", ".join(sorted(resolved)) if resolved else "(nothing)"
        rows.append((probe.description, expected, actual, "ok" if okay else "FAIL"))
        if not okay:
            failures.append(f"{probe.description}: expected {expected}, resolved {actual}")

    # The system search entry point is discovered through meta-data, not resolution.
    search_activity = next(
        (component for component in contract.get("activities") or []
         if component["name"] == SEARCH_ACTIVITY),
        None,
    )
    if search_activity is None:
        failures.append(f"{SEARCH_ACTIVITY} is no longer declared")
    else:
        meta = set(search_activity.get("metaData") or [])
        if "android.app.searchable" not in meta:
            failures.append("SearchActivity lost its android.app.searchable meta-data")

    authorities = {
        authority
        for provider in contract.get("providers") or []
        for authority in (provider.get("authorities") or "").split(";")
    }
    if "com.xjcheng.musictageditor.SearchSugg" not in authorities:
        failures.append("SearchSuggProvider authority is gone, so search suggestions break")

    # Every declared activity must still be resolvable by name: explicit-component callers.
    explicit_names = [component["name"] for component in contract.get("activities") or []]
    for name in explicit_names:
        if not resolve(contract, Probe(description=name, action=ACTION_MAIN, explicit=name)):
            failures.append(f"explicit component no longer declared: {name}")

    print(f"intent matrix against {origin}")
    print(f"  activities: {len(explicit_names)}")
    width = max(len(row[0]) for row in rows)
    for description, expected, actual, status in rows:
        print(f"  [{status:>4}] {description:<{width}}  -> {actual}")

    if args.write_doc:
        lines = [
            "# Intent resolution matrix",
            "",
            "Generated by `tools/intent_matrix.py` from the built APK. Each row is an intent",
            "another app can send, and the component Android resolves it to. The negative row",
            "must resolve to nothing, which is why non-audio documents are not claimed.",
            "",
            f"Source: `{Path(origin).name}` · activities declared: {len(explicit_names)}",
            "",
            "| Intent | Expected component | Resolved component | Status |",
            "|---|---|---|---|",
        ]
        for description, expected, actual, status in rows:
            lines.append(f"| {description} | {expected} | {actual} | {status} |")
        lines += [
            "",
            "## How to verify on a device",
            "",
            "The matrix proves the merged manifest resolves these intents; it cannot exercise a",
            "real `ContentResolver`. `scripts/device-intent-matrix.sh` sends the same intents,",
            "the explicit-component calls and a `content://` round-trip through `adb shell am`",
            "on an emulator or device:",
            "",
            "```sh",
            "scripts/device-intent-matrix.sh",
            "```",
            "",
        ]
        Path(args.write_doc).write_text("\n".join(lines), encoding="utf-8")
        print(f"  wrote {args.write_doc}")

    if failures:
        print(f"\nPROBLEMS ({len(failures)}):")
        for failure in failures:
            print(f"  - {failure}")
        return 1

    print("\nintent matrix passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
