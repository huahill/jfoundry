#!/usr/bin/env python3

"""Verify documented runtime compatibility lines against BOM properties."""

from __future__ import annotations

import re
import sys
from pathlib import Path
import xml.etree.ElementTree as ET


PLATFORMS = [
    ("Spring Boot-only", "jfoundry-spring-boot-dependencies", "spring-boot.version"),
    ("Spring Cloud", "jfoundry-spring-cloud-dependencies", "spring-cloud.version"),
    ("Spring Cloud Alibaba", "jfoundry-spring-cloud-dependencies", "spring-cloud-alibaba.version"),
    ("Quarkus", "jfoundry-quarkus-dependencies", "quarkus.version"),
    ("Helidon MP", "jfoundry-helidon-dependencies", "helidon.version"),
]


def fail(message: str) -> None:
    print(f"Compatibility matrix is invalid: {message}", file=sys.stderr)
    raise SystemExit(1)


def local_name(tag: str) -> str:
    return tag.rsplit("}", 1)[-1]


def child(element: ET.Element | None, name: str) -> ET.Element | None:
    if element is None:
        return None
    return next((candidate for candidate in element if local_name(candidate.tag) == name), None)


def main() -> None:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").expanduser().resolve()
    matrix_path = root / "docs/release/compatibility.md"
    if not matrix_path.is_file():
        fail(f"missing document: {matrix_path}")

    rows: dict[str, list[str]] = {}
    in_table = False
    for line in matrix_path.read_text(encoding="utf-8").splitlines():
        if line.startswith("| Platform | Supported line | Version source |"):
            in_table = True
            continue
        if in_table and not line.strip():
            break
        if not in_table or not line.startswith("|"):
            continue
        columns = [column.strip() for column in line.split("|")[1:-1]]
        if not any(platform[0] == columns[0] for platform in PLATFORMS):
            continue
        if len(columns) != 3:
            fail(f"{columns[0]} row must have three columns")
        if columns[0] in rows:
            fail(f"duplicate platform row: {columns[0]}")
        rows[columns[0]] = columns

    for name, artifact_id, property_name in PLATFORMS:
        if name not in rows:
            fail(f"missing platform row: {name}")
        pom_path = root / "jfoundry-boms" / artifact_id / "pom.xml"
        try:
            project = ET.parse(pom_path).getroot()
        except FileNotFoundError:
            fail(f"missing runtime BOM: {pom_path}")
        except ET.ParseError as error:
            fail(f"{pom_path} is not valid XML: {error}")
        properties = child(project, "properties")
        version_element = child(properties, property_name)
        version = (version_element.text or "").strip() if version_element is not None else ""
        if not version:
            fail(f"{pom_path} must define {property_name}")
        match = re.match(r"^(\d+)\.(\d+)(?:\.|$)", version)
        if not match:
            fail(f"cannot derive a supported line from version {version}")
        expected_line = f"{match.group(1)}.{match.group(2)}.x"
        row = rows[name]
        if row[1] != expected_line:
            fail(f"{name} supported line {row[1]} must match BOM version line {expected_line}")
        documented_source = row[2].replace("`", "")
        if documented_source != artifact_id:
            fail(f"{name} version source {documented_source} must be {artifact_id}")
    print("Compatibility matrix verification passed.")


if __name__ == "__main__":
    main()
