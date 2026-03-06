#!/usr/bin/env python3
from __future__ import annotations

import re
from dataclasses import dataclass
from pathlib import Path


FQN_RE = re.compile(
    r"\b(com\.moud(?:\.[a-z0-9_]+)+\.[A-Z][A-Za-z0-9_]*(?:\.[A-Z][A-Za-z0-9_]*)*)\b"
)
PACKAGE_RE = re.compile(r"^\s*package\s+([a-zA-Z0-9_.]+)\s*;\s*$")
IMPORT_RE = re.compile(r"^\s*import\s+([a-zA-Z0-9_.]+)\s*;\s*$")


@dataclass(frozen=True)
class ImportInfo:
    fqn: str
    simple: str


def simple_name(fqn: str) -> str:
    return fqn.rsplit(".", 1)[-1]


def parse_existing_imports(lines: list[str]) -> dict[str, set[str]]:
    by_simple: dict[str, set[str]] = {}
    for line in lines:
        m = IMPORT_RE.match(line)
        if not m:
            continue
        fqn = m.group(1)
        s = simple_name(fqn)
        by_simple.setdefault(s, set()).add(fqn)
    return by_simple


def find_package(lines: list[str]) -> str | None:
    for line in lines:
        m = PACKAGE_RE.match(line)
        if m:
            return m.group(1)
    return None


def insert_imports(lines: list[str], new_imports: list[str]) -> list[str]:
    if not new_imports:
        return lines

    # Find import block
    first_import_idx = None
    last_import_idx = None
    for i, line in enumerate(lines):
        if IMPORT_RE.match(line):
            if first_import_idx is None:
                first_import_idx = i
            last_import_idx = i

    if first_import_idx is None:
        # No imports: insert after package decl (and following blank line if present)
        pkg_idx = None
        for i, line in enumerate(lines):
            if PACKAGE_RE.match(line):
                pkg_idx = i
                break
        if pkg_idx is None:
            return lines
        insert_at = pkg_idx + 1
        if insert_at < len(lines) and lines[insert_at].strip() == "":
            insert_at += 1
        block = [f"import {imp};\n" for imp in sorted(set(new_imports))]
        return lines[:insert_at] + ["\n"] + block + lines[insert_at:]

    # Existing imports: add into that block (keep it sorted within non-static imports only)
    existing = set()
    for i in range(first_import_idx, last_import_idx + 1):
        m = IMPORT_RE.match(lines[i])
        if m:
            existing.add(m.group(1))

    merged = sorted(existing.union(new_imports))
    new_block = [f"import {imp};\n" for imp in merged]
    return lines[:first_import_idx] + new_block + lines[last_import_idx + 1 :]


def refactor_file(path: Path) -> bool:
    txt = path.read_text(encoding="utf-8")
    lines = txt.splitlines(keepends=True)

    package = find_package(lines)
    existing_by_simple = parse_existing_imports(lines)

    matches = list(FQN_RE.finditer(txt))
    if not matches:
        return False

    # Build import list and replacements, skipping collisions.
    needed_imports: set[str] = set()
    replacements: dict[str, str] = {}

    for m in matches:
        fqn = m.group(1)
        s = simple_name(fqn)

        # If there's already an import for the same simple name but different FQN, skip.
        existing_fqns = existing_by_simple.get(s, set())
        if existing_fqns and fqn not in existing_fqns:
            continue

        # Avoid replacing if it looks like it's already part of an import/package line (regex boundaries help, but be safe)
        replacements[fqn] = s

        # Add import if not same package (best-effort: compare package to the class' direct package).
        class_pkg = fqn.rsplit(".", 1)[0]
        if package and class_pkg == package:
            continue
        needed_imports.add(fqn)

    if not replacements:
        return False

    # Apply replacements
    new_txt = txt
    for fqn, s in sorted(replacements.items(), key=lambda kv: -len(kv[0])):
        new_txt = new_txt.replace(fqn, s)

    new_lines = new_txt.splitlines(keepends=True)
    new_lines = insert_imports(new_lines, sorted(needed_imports))
    new_txt2 = "".join(new_lines)

    if new_txt2 == txt:
        return False

    path.write_text(new_txt2, encoding="utf-8")
    return True


def main() -> int:
    root = Path(".")
    changed = 0
    for path in root.rglob("*.java"):
        if "/build/" in str(path).replace("\\", "/"):
            continue
        if refactor_file(path):
            changed += 1
    print(f"refactored {changed} file(s)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

