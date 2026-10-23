#!/usr/bin/env python3
"""Fast static self-checks that do not need Gradle or an Android SDK.

Catches the mistakes that otherwise only surface after a CI round trip:

  * malformed XML in resources / manifest
  * manifest component classes that have no matching Kotlin file
  * manifest string references that are missing from res/values/strings.xml
  * launcher icon / xml resources referenced from the manifest but absent
  * unbalanced braces in Kotlin sources (cheap syntax smoke test)

Exit code 0 = clean. Run in CI before the Gradle build so failures are explained in
seconds instead of after a full compile.
"""

from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
APP = ROOT / "app" / "src" / "main"
ANDROID = "{http://schemas.android.com/apk/res/android}"

errors: list[str] = []
notes: list[str] = []


def check_xml() -> None:
    for path in sorted(ROOT.rglob("*.xml")):
        if any(part in {".git", "build", ".gradle"} for part in path.parts):
            continue
        try:
            ET.parse(path)
        except ET.ParseError as exc:
            errors.append(f"malformed XML: {path.relative_to(ROOT)}: {exc}")
    notes.append(f"xml: checked {len(list(ROOT.rglob('*.xml')))} files")


def strings_defined() -> dict[str, str]:
    tree = ET.parse(APP / "res" / "values" / "strings.xml")
    return {el.get("name"): (el.text or "") for el in tree.getroot()}


def kotlin_class_names() -> set[str]:
    """All class/object names declared in Kotlin sources (several may share a file)."""
    names: set[str] = set()
    pattern = re.compile(r"^\s*(?:@\w+(?:\([^)]*\))?\s*)*(?:public\s+|internal\s+|private\s+|open\s+|abstract\s+|sealed\s+|data\s+)*(?:class|object|interface)\s+(\w+)")
    for path in (APP / "java").rglob("*.kt"):
        for line in path.read_text(encoding="utf-8").splitlines():
            match = pattern.match(line)
            if match:
                names.add(match.group(1))
    return names


def check_manifest() -> None:
    manifest_path = APP / "AndroidManifest.xml"
    root = ET.parse(manifest_path).getroot()
    known_strings = strings_defined()

    declared_classes = kotlin_class_names()
    declared: list[str] = []
    for tag in ("activity", "service", "provider"):
        for el in root.iter(tag):
            name = el.get(ANDROID + "name") or ""
            if not name or name.startswith("androidx."):
                continue
            declared.append(name)
            simple = name.split(".")[-1]
            if simple not in declared_classes:
                errors.append(f"manifest references {name} but no Kotlin class {simple} is declared")

    # @string references
    for el in root.iter():
        for key, value in el.attrib.items():
            if value.startswith("@string/"):
                name = value[len("@string/"):]
                if name not in known_strings:
                    errors.append(f"manifest references missing string @string/{name}")
            if value.startswith("@xml/") or value.startswith("@mipmap/") or value.startswith("@drawable/"):
                kind, _, name = value[1:].partition("/")
                found = any(APP.joinpath("res").rglob(f"{name}.*"))
                if not found:
                    errors.append(f"manifest references missing resource {value} ({kind}/{name})")

    # every <application android:name> must exist too
    app_name = root.find("application").get(ANDROID + "name") or ""
    if app_name and app_name.split(".")[-1] not in declared_classes:
        errors.append(f"application class {app_name} has no Kotlin source")

    # AAPT2 unescapes manifest attribute values, so a pathPattern needs a doubled
    # backslash in the source or the regex escape silently disappears from the APK.
    for data in root.iter("data"):
        pattern = data.get(ANDROID + "pathPattern")
        if pattern is None:
            continue
        i = 0
        while i < len(pattern):
            if pattern[i] == "\\":
                if i + 1 >= len(pattern) or pattern[i + 1] != "\\":
                    errors.append(
                        f"pathPattern uses a single backslash (AAPT2 strips it): {pattern!r} "
                        f"- write a doubled backslash in the manifest"
                    )
                    break
                i += 2
            else:
                i += 1

    notes.append(f"manifest: {len(declared)} app components checked")


def source_files(pattern: str) -> list[Path]:
    return sorted(p for p in ROOT.glob(pattern) if not any(part in {"build", ".gradle", ".git"} for part in p.parts))


def check_kotlin() -> None:
    files = source_files("**/src/**/*.kt")
    native_files = source_files("native/**/*.cpp") + source_files("native/**/*.h")
    for path in files + native_files:
        text = path.read_text(encoding="utf-8")
        if text.count("{") != text.count("}"):
            errors.append(f"unbalanced braces: {path.relative_to(ROOT)} "
                          f"({text.count('{')} open / {text.count('}')} close)")
        if text.count("(") != text.count(")"):
            errors.append(f"unbalanced parentheses: {path.relative_to(ROOT)}")
        if "\t" in text:
            errors.append(f"tab character: {path.relative_to(ROOT)}")
        if not text.endswith("\n"):
            errors.append(f"missing trailing newline: {path.relative_to(ROOT)}")

        # A KDoc continuation line may only contain the terminator when it *is* the
        # terminator. Otherwise the comment closes early and the rest of the line is
        # parsed as code (a wildcard MIME in a comment broke the first CI run).
        for lineno, line in enumerate(text.splitlines(), 1):
            stripped = line.strip()
            if stripped.startswith("*") and "*/" in stripped[:-2]:
                errors.append(
                    f"comment terminator inside a KDoc line: {path.relative_to(ROOT)}:{lineno} "
                    f"(write 'any-any MIME' instead of the wildcard)"
                )
    notes.append(f"kotlin: {len(files)} files, native: {len(native_files)} files checked")


def check_fixtures() -> None:
    sys.path.insert(0, str(ROOT / "tools"))
    import gen_fixtures  # noqa: PLC0415

    checked = 0
    for name, builder in gen_fixtures.FIXTURES.items():
        path = gen_fixtures.OUT / name
        if not path.exists():
            errors.append(f"missing fixture {name} (run tools/gen_fixtures.py)")
            continue
        if path.read_bytes() != builder():
            errors.append(f"fixture out of date: {name} (run tools/gen_fixtures.py)")
        checked += 1
    notes.append(f"fixtures: {checked} generated files verified")


def check_native_build() -> None:
    cmake = ROOT / "native" / "taglib" / "CMakeLists.txt"
    bridge = ROOT / "native" / "taglib" / "jni_bridge.cpp"
    for required in (cmake, bridge):
        if not required.exists():
            errors.append(f"missing native source: {required.relative_to(ROOT)}")
            continue
        if required.suffix == ".cpp":
            text = required.read_text(encoding="utf-8")
            declared = re.findall(r"Java_com_xjcheng_musictageditor_core_tag_NativeTagBridge_(\w+)", text)
            kotlin = (ROOT / "core" / "tag" / "src" / "main" / "kotlin" / "com" / "xjcheng" /
                      "musictageditor" / "core" / "tag" / "NativeTagBridge.kt").read_text(encoding="utf-8")
            for symbol in declared:
                if f"external fun {symbol}(" not in kotlin:
                    errors.append(f"JNI export {symbol} has no matching Kotlin external declaration")
                else:
                    notes.append(f"jni: {symbol} matched")
    notes.append("native: CMake project present")



def check_workflows() -> None:
    wf = ROOT / ".github" / "workflows"
    if not wf.exists():
        errors.append("missing .github/workflows")
        return
    for path in sorted(wf.glob("*.yml")):
        # PyYAML may be absent in a bare runner image; do a shallow sanity check only.
        text = path.read_text(encoding="utf-8")
        if not text.startswith("name:"):
            errors.append(f"workflow without name: {path.name}")
        if "runs-on:" not in text:
            errors.append(f"workflow without runs-on: {path.name}")
        if "\t" in text:
            errors.append(f"tab character in workflow: {path.name}")
    notes.append(f"workflows: {len(list(wf.glob('*.yml')))} files")


def main() -> int:
    check_xml()
    check_manifest()
    check_kotlin()
    check_workflows()
    check_fixtures()
    check_native_build()

    for note in notes:
        print(f"ok   {note}")
    if errors:
        print("\nPROBLEMS:")
        for error in errors:
            print(f"  - {error}")
        return 1
    print("\nself-check passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
