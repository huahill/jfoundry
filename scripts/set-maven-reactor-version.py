#!/usr/bin/env python3

"""Update the version references owned by the Maven reactor."""

from __future__ import annotations

import re
import sys
from pathlib import Path
import xml.etree.ElementTree as ET


VERSION_PATTERN = re.compile(r"<(?:version|jfoundry\.version)>[^<]+</(?:version|jfoundry\.version)>")
VALID_VERSION = re.compile(r"^[0-9A-Za-z][0-9A-Za-z.+_-]*$")
EXCLUDED_DIRECTORIES = {".git", ".worktrees", "graphify-out", "target"}


def local_name(tag: str) -> str:
    return tag.rsplit("}", 1)[-1]


def child(element: ET.Element | None, name: str) -> ET.Element | None:
    if element is None:
        return None
    return next((candidate for candidate in element if local_name(candidate.tag) == name), None)


def text(element: ET.Element | None, name: str) -> str:
    candidate = child(element, name)
    return (candidate.text or "").strip() if candidate is not None else ""


def fail(message: str, code: int = 1) -> None:
    print(message, file=sys.stderr)
    raise SystemExit(code)


def pom_files(root: Path) -> list[Path]:
    return sorted(
        path
        for path in root.rglob("pom.xml")
        if not any(part in EXCLUDED_DIRECTORIES or part == "test" for part in path.relative_to(root).parts)
    )


def main() -> None:
    if len(sys.argv) != 3:
        fail(f"Usage: {Path(sys.argv[0]).name} <repository-root> <new-version>", 2)

    repository_root = Path(sys.argv[1]).expanduser().resolve()
    new_version = sys.argv[2]
    if not VALID_VERSION.fullmatch(new_version):
        fail(f"Invalid Maven project version: {new_version}", 2)

    root_pom = repository_root / "pom.xml"
    if not root_pom.is_file():
        fail(f"Maven reactor root does not exist: {root_pom}")

    try:
        root_project = ET.parse(root_pom).getroot()
    except ET.ParseError as error:
        fail(f"Maven reactor root is not valid XML: {error}")

    old_version = text(root_project, "version")
    if not old_version:
        fail(f"Maven reactor root has no project version: {root_pom}")
    if old_version == new_version:
        fail(f"New version matches the current reactor version: {new_version}", 2)
    root_group_id = text(root_project, "groupId")

    edits: dict[Path, str] = {}
    updated_references = 0
    for pom in pom_files(repository_root):
        content = pom.read_text(encoding="utf-8")
        try:
            project = ET.fromstring(content)
        except ET.ParseError as error:
            fail(f"{pom.relative_to(repository_root)} is not valid XML: {error}")

        targets = 1 if text(project, "version") == old_version else 0
        parent = child(project, "parent")
        if parent is not None and text(parent, "groupId") == root_group_id and text(parent, "version") == old_version:
            targets += 1
        properties = child(project, "properties")
        if text(properties, "jfoundry.version") == old_version:
            targets += 1

        occurrences = sum(1 for match in VERSION_PATTERN.finditer(content) if match.group(0).split(">", 1)[1].split("<", 1)[0] == old_version)
        if occurrences != targets:
            if targets == 0 and occurrences == 0:
                continue
            fail(f"{pom.relative_to(repository_root)}: unclassified {old_version} occurrence(s); expected {targets}, found {occurrences}")
        if targets == 0:
            continue

        old_element = re.compile(
            rf"(<(?:version|jfoundry\.version)>)" + re.escape(old_version) + rf"(</(?:version|jfoundry\.version)>)"
        )
        edits[pom] = old_element.sub(rf"\g<1>{new_version}\g<2>", content)
        updated_references += targets

    if not edits:
        fail(f"No reactor version references matched {old_version}")
    for pom, content in edits.items():
        pom.write_text(content, encoding="utf-8")
    print(f"Updated {updated_references} reactor version references across {len(edits)} XML POMs: {old_version} -> {new_version}")


if __name__ == "__main__":
    main()
