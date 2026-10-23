#!/usr/bin/env python3
r"""Compatibility-contract tooling.

Two modes:

  --from-xml   Build the requirement baseline from the source AndroidManifest.xml
  --verify-apk Check that a *built* APK still satisfies that baseline

Why both: the JVM test proves the source manifest matches the documented contract,
and this check proves the merged manifest of the shipped APK did not lose anything
(a library manifest can inject components, but it can also drop/override ours).

Requirement semantics are one-directional: every required component, filter and
data attribute must exist in the APK. Extra components are reported, never failed,
so adding a screen is a non-breaking change.

AAPT2 escaping note: manifest attribute values are compiled as Android strings, so
`\\.` in the source becomes `\.` in the APK (one backslash is consumed). When reading
the *source* manifest this tool applies the same unescaping, which is why a
pathPattern written as `.*\\.ape` in XML is recorded here as `.*\.ape`.

Usage:
  python3 tools/manifest_contract.py --from-xml app/src/main/AndroidManifest.xml \\
      --package com.xjcheng.musictageditor --out docs/MANIFEST_CONTRACT.json
  python3 tools/manifest_contract.py --verify-apk app/build/outputs/apk/debug/app-debug.apk \\
      --expect docs/MANIFEST_CONTRACT.json
"""

from __future__ import annotations

import argparse
import json
import sys
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import axml  # noqa: E402

ANDROID = "{http://schemas.android.com/apk/res/android}"
SCHEMA_VERSION = 1

FILTER_KEYS = ("actions", "categories", "schemes", "mimeTypes", "hosts", "pathPatterns", "metaData")

_SIMPLE_ESCAPES = {
    "n": "\n",
    "t": "\t",
    "r": "\r",
    "0": "\0",
    "\\": "\\",
    "'": "'",
    '"': '"',
}


def android_unescape(value: str) -> str:
    """Mimic AAPT2's handling of escapes in manifest attribute values."""
    out: list[str] = []
    i = 0
    while i < len(value):
        ch = value[i]
        if ch != "\\" or i + 1 >= len(value):
            out.append(ch)
            i += 1
            continue
        nxt = value[i + 1]
        if nxt == "u" and i + 5 < len(value):
            try:
                out.append(chr(int(value[i + 2:i + 6], 16)))
                i += 6
                continue
            except ValueError:
                pass
        out.append(_SIMPLE_ESCAPES.get(nxt, nxt))  # unknown escape: backslash dropped
        i += 2
    return "".join(out)


def _attr(element: ET.Element, name: str, unescape: bool = False) -> str | None:
    """Read an attribute with or without the android: namespace prefix."""
    value = None
    for key in (ANDROID + name, name):
        if key in element.attrib:
            value = element.attrib[key]
            break
    if value is None:
        return None
    return android_unescape(value) if unescape else value


def _children(element: ET.Element, tag: str) -> list[ET.Element]:
    return [child for child in list(element) if child.tag == tag or child.tag == ANDROID + tag]


def _sorted(values) -> list[str]:
    return sorted({v for v in values if v})


def _filter_contract(intent_filter: ET.Element, unescape: bool) -> dict:
    return {
        "actions": _sorted(_attr(a, "name", unescape) for a in _children(intent_filter, "action")),
        "categories": _sorted(_attr(c, "name", unescape) for c in _children(intent_filter, "category")),
        "schemes": _sorted(_attr(d, "scheme", unescape) for d in _children(intent_filter, "data")),
        "mimeTypes": _sorted(_attr(d, "mimeType", unescape) for d in _children(intent_filter, "data")),
        "hosts": _sorted(_attr(d, "host", unescape) for d in _children(intent_filter, "data")),
        "pathPatterns": _sorted(_attr(d, "pathPattern", unescape) for d in _children(intent_filter, "data")),
        "metaData": _sorted(_attr(m, "name", unescape) for m in _children(intent_filter, "meta-data")),
    }


def _component_contract(element: ET.Element, package: str, unescape: bool) -> dict:
    raw_name = _attr(element, "name", unescape) or ""
    name = package + raw_name if raw_name.startswith(".") else raw_name
    contract: dict = {
        "name": name,
        "exported": (_attr(element, "exported", unescape) or "false").lower() == "true",
    }
    if element.tag.endswith("provider"):
        contract["authorities"] = _attr(element, "authorities", unescape)
        contract["grantUriPermissions"] = (
            _attr(element, "grantUriPermissions", unescape) or "false"
        ).lower() == "true"
    meta = _sorted(_attr(m, "name", unescape) for m in _children(element, "meta-data"))
    if meta:
        contract["metaData"] = meta
    filters = [_filter_contract(f, unescape) for f in _children(element, "intent-filter")]
    if filters:
        contract["filters"] = filters
    return contract


def contract_from_manifest(root: ET.Element, package: str | None = None, unescape: bool = False) -> dict:
    """Normalise a manifest tree into the contract.

    `package` may be passed explicitly because AGP 8 derives the application id from
    `namespace` and no longer writes `package` in the source manifest; the merged
    manifest inside the APK always carries it.

    `unescape` must be True only for the *source* XML: values read back from a built
    APK are already in post-AAPT2 form.
    """
    application = _children(root, "application")[0]
    resolved_package = package or _attr(root, "package") or ""
    return {
        "schema": SCHEMA_VERSION,
        "package": resolved_package,
        "activities": [_component_contract(e, resolved_package, unescape) for e in _children(application, "activity")],
        "services": [_component_contract(e, resolved_package, unescape) for e in _children(application, "service")],
        "providers": [_component_contract(e, resolved_package, unescape) for e in _children(application, "provider")],
    }


def contract_from_apk(apk_path: str) -> dict:
    with zipfile.ZipFile(apk_path) as zf:
        binary = zf.read("AndroidManifest.xml")
    root = ET.fromstring(axml.decode_bytes(binary))
    return contract_from_manifest(root)


# ---------------------------------------------------------------- verification


def _filter_covers(required: dict, candidate: dict) -> bool:
    for key in FILTER_KEYS:
        if not set(required.get(key, [])) <= set(candidate.get(key, [])):
            return False
    return True


def _component_covers(required: dict, candidate: dict) -> list[str]:
    problems: list[str] = []
    if required.get("exported") != candidate.get("exported"):
        problems.append(f"exported mismatch: required={required.get('exported')} actual={candidate.get('exported')}")
    if "authorities" in required and required["authorities"] != candidate.get("authorities"):
        problems.append(f"authorities mismatch: required={required['authorities']} actual={candidate.get('authorities')}")
    if "grantUriPermissions" in required and required["grantUriPermissions"] != candidate.get("grantUriPermissions"):
        problems.append("grantUriPermissions mismatch")
    for required_meta in required.get("metaData", []):
        if required_meta not in candidate.get("metaData", []):
            problems.append(f"missing meta-data {required_meta}")

    candidates = candidate.get("filters", [])
    for required_filter in required.get("filters", []):
        if any(_filter_covers(required_filter, cf) for cf in candidates):
            continue
        union = {key: set().union(*[set(cf.get(key, [])) for cf in candidates]) if candidates else set()
                 for key in FILTER_KEYS}
        missing = [
            f"{key}={sorted(set(required_filter.get(key, [])) - union[key])}"
            for key in FILTER_KEYS
            if not set(required_filter.get(key, [])) <= union[key]
        ]
        problems.append("no intent-filter covers: " + "; ".join(missing or ["<empty filter>"]))
        for index, cf in enumerate(candidates):
            patterns = cf.get("pathPatterns", [])
            problems.append(
                f"    candidate #{index}: actions={cf.get('actions')} schemes={cf.get('schemes')} "
                f"mimes={cf.get('mimeTypes')} hosts={cf.get('hosts')} "
                f"patterns({len(patterns)})={patterns[:3]}{'...' if len(patterns) > 3 else ''}"
            )
    return problems


def verify(expected: dict, actual: dict) -> tuple[list[str], list[str]]:
    failures: list[str] = []
    notes: list[str] = []

    if expected["package"] != actual["package"]:
        failures.append(f"package: expected {expected['package']}, got {actual['package']}")

    for group in ("activities", "services", "providers"):
        actual_by_name = {c["name"]: c for c in actual.get(group, [])}
        for required in expected.get(group, []):
            candidate = actual_by_name.get(required["name"])
            if candidate is None:
                failures.append(f"{group}: missing component {required['name']}")
                continue
            for problem in _component_covers(required, candidate):
                failures.append(f"{group}: {required['name']}: {problem}")

        required_names = {c["name"] for c in expected.get(group, [])}
        for extra in sorted(set(actual_by_name) - required_names):
            notes.append(f"{group}: extra component present (allowed): {extra}")

    return failures, notes


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--from-xml", metavar="MANIFEST", help="source AndroidManifest.xml")
    group.add_argument("--verify-apk", metavar="APK", help="built APK to verify")
    parser.add_argument("--out", metavar="JSON", help="write contract JSON (with --from-xml)")
    parser.add_argument("--expect", metavar="JSON", help="contract JSON to satisfy (with --verify-apk)")
    parser.add_argument("--package", metavar="ID", default=None,
                        help="application id (required when the source manifest has no package attribute)")
    args = parser.parse_args()

    if args.from_xml:
        root = ET.parse(args.from_xml).getroot()
        contract = contract_from_manifest(root, args.package, unescape=True)
        payload = json.dumps(contract, indent=2, ensure_ascii=False) + "\n"
        if args.out:
            Path(args.out).write_text(payload, encoding="utf-8")
            print(f"wrote {args.out}: {len(contract['activities'])} activities, "
                  f"{len(contract['services'])} services, {len(contract['providers'])} providers")
        else:
            print(payload)
        return 0

    assert args.expect, "--expect is required with --verify-apk"
    expected = json.loads(Path(args.expect).read_text(encoding="utf-8"))
    actual = contract_from_apk(args.verify_apk)
    failures, notes = verify(expected, actual)

    for note in notes:
        print(f"note: {note}")
    if failures:
        print("\nMANIFEST CONTRACT VIOLATIONS:")
        for failure in failures:
            print(f"  - {failure}")
        return 1
    print("manifest contract OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
