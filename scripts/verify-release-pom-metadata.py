#!/usr/bin/env python3

"""Verify release versions and SCM tags in the independent publication POMs."""

from __future__ import annotations

import re
import sys
from pathlib import Path
import xml.etree.ElementTree as ET


def fail(message: str) -> None:
    print(f"Release POM metadata is invalid: {message}", file=sys.stderr)
    raise SystemExit(1)


def local_name(tag: str) -> str:
    return tag.rsplit("}", 1)[-1]


def child(element: ET.Element | None, name: str) -> ET.Element | None:
    if element is None:
        return None
    return next((candidate for candidate in element if local_name(candidate.tag) == name), None)


def child_text(element: ET.Element | None, name: str) -> str:
    candidate = child(element, name)
    return (candidate.text or "").strip() if candidate is not None else ""


def main() -> None:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").expanduser().resolve()
    pom_paths = [root / "pom.xml", *sorted((root / "jfoundry-boms").glob("*/pom.xml"))]
    if not pom_paths[0].is_file():
        fail("root pom.xml does not exist")
    if len(pom_paths) == 1:
        fail("no independent BOM or parent POMs were found under jfoundry-boms")

    records: list[tuple[str, str, str]] = []
    for path in pom_paths:
        try:
            project = ET.parse(path).getroot()
        except ET.ParseError as error:
            fail(f"{path.relative_to(root)} is not valid XML: {error}")
        version = child_text(project, "version")
        scm_tag = child_text(child(project, "scm"), "tag")
        relative_path = str(path.relative_to(root))
        if not version:
            fail(f"{relative_path} must declare a direct project version")
        if not scm_tag:
            fail(f"{relative_path} must declare a direct scm/tag")
        records.append((relative_path, version, scm_tag))

    reactor_version = records[0][1]
    for path, version, _ in records:
        if version != reactor_version:
            fail(f"{path} version {version} must match reactor version {reactor_version}")

    if reactor_version.endswith("-SNAPSHOT"):
        match = re.fullmatch(r"(\d+)\.(\d+)\.0-SNAPSHOT", reactor_version)
        if not match:
            fail(f"unsupported SNAPSHOT development version {reactor_version}")
        major, minor = int(match.group(1)), int(match.group(2))
        if minor == 0:
            fail(f"SNAPSHOT development version must follow a stable minor release: {reactor_version}")
        expected_literal_tag = f"v{major}.{minor - 1}.0"
    else:
        expected_literal_tag = f"v{reactor_version}"

    for path, _, scm_tag in records:
        if scm_tag not in {"v${project.version}", expected_literal_tag}:
            fail(f"{path} scm/tag {scm_tag} must be v${{project.version}} or {expected_literal_tag}")
    print(f"Release POM metadata verification passed: {reactor_version}")


if __name__ == "__main__":
    main()
