#!/usr/bin/env python3
"""Diff the compatibility surface of two APKs.

Answers the only question that matters for drop-in replacement: *what a third-party
caller can observe* — package, component names, exported flags, intent filters
(actions/categories/schemes/MIME/host/pathPattern), provider authorities and
meta-data. UI, resources, code and version strings are deliberately ignored.

Usage:
  python3 tools/compat_diff.py --old original.apk --new app-debug.apk
  python3 tools/compat_diff.py --old original.apk --new app-debug.apk --allow-extra
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import manifest_contract as mc  # noqa: E402


def normalise(component: dict) -> dict:
    return {
        "exported": component.get("exported"),
        "authorities": component.get("authorities"),
        "grantUriPermissions": component.get("grantUriPermissions"),
        "metaData": sorted(component.get("metaData", [])),
        "filters": sorted(
            (
                tuple(f.get("actions", [])),
                tuple(f.get("categories", [])),
                tuple(f.get("schemes", [])),
                tuple(f.get("mimeTypes", [])),
                tuple(f.get("hosts", [])),
                tuple(f.get("pathPatterns", [])),
                tuple(f.get("metaData", [])),
            )
            for f in component.get("filters", [])
        ),
    }


def describe_filter(entry) -> str:
    actions, categories, schemes, mimes, hosts, patterns, meta = entry
    parts = [f"actions={list(actions)}"]
    if categories:
        parts.append(f"categories={list(categories)}")
    if schemes:
        parts.append(f"schemes={list(schemes)}")
    if mimes:
        parts.append(f"mimeTypes={len(mimes)}")
    if hosts:
        parts.append(f"hosts={list(hosts)}")
    if patterns:
        # patterns are verbose; show the first two and the count
        parts.append(f"pathPatterns({len(patterns)})={list(patterns)[:2]}")
    if meta:
        parts.append(f"meta={list(meta)}")
    return ", ".join(parts)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--old", required=True, help="APK being replaced (e.g. 1.2.5.2)")
    ap.add_argument("--new", required=True, help="APK produced by this project")
    ap.add_argument("--ignore-exported", action="store_true",
                    help="ignore exported differences (pre-API-31 apps left it implicit)")
    args = ap.parse_args()

    old = mc.contract_from_apk(args.old)
    new = mc.contract_from_apk(args.new)

    blocking: list[str] = []
    informational: list[str] = []

    if old["package"] != new["package"]:
        blocking.append(f"package: old={old['package']} new={new['package']}")
    else:
        print(f"package: {old['package']} (identical)")

    for group in ("activities", "services", "providers"):
        old_map = {c["name"]: c for c in old.get(group, [])}
        new_map = {c["name"]: c for c in new.get(group, [])}

        missing = sorted(set(old_map) - set(new_map))
        for name in missing:
            blocking.append(f"{group}: missing in new build: {name}")

        extra = sorted(set(new_map) - set(old_map))
        for name in extra:
            informational.append(f"{group}: new component: {name}")

        common = sorted(set(old_map) & set(new_map))
        for name in common:
            o, n = normalise(old_map[name]), normalise(new_map[name])
            if not args.ignore_exported and o["exported"] != n["exported"]:
                blocking.append(f"{group}: {name}: exported {o['exported']} -> {n['exported']}")
            for key in ("authorities", "grantUriPermissions"):
                if o[key] != n[key]:
                    blocking.append(f"{group}: {name}: {key} {o[key]} -> {n[key]}")

            old_only = [f for f in o["filters"] if f not in n["filters"]]
            new_only = [f for f in n["filters"] if f not in o["filters"]]
            for f in old_only:
                blocking.append(f"{group}: {name}: filter lost: {describe_filter(f)}")
            for f in new_only:
                informational.append(f"{group}: {name}: filter added: {describe_filter(f)}")

        print(f"{group}: {len(common)} shared, {len(missing)} missing, {len(extra)} new")

    for line in informational:
        print(f"note: {line}")

    if blocking:
        print("\nINCOMPATIBLE:")
        for line in blocking:
            print(f"  - {line}")
        return 1
    print("\ncompatible: no caller-visible component, filter or authority was lost")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
