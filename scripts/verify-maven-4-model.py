#!/usr/bin/env python3

"""Verify that every source POM uses the Maven 4.1 model."""

from __future__ import annotations

import sys
from pathlib import Path
import xml.etree.ElementTree as ET


MAVEN_4_NAMESPACE = "http://maven.apache.org/POM/4.1.0"


def fail(message: str) -> None:
    print(f"Maven 4.1 model verification failed: {message}", file=sys.stderr)
    raise SystemExit(1)


def local_name(tag: str) -> str:
    return tag.rsplit("}", 1)[-1]


def main() -> None:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").expanduser().resolve()
    expected_count = int(sys.argv[2] if len(sys.argv) > 2 else "122")
    if not root.is_dir():
        fail(f"root does not exist: {root}")
    if any(root.rglob("pom.yaml")):
        fail(f"source tree contains pom.yaml: {next(root.rglob('pom.yaml'))}")

    pom_files = sorted(
        path for path in root.rglob("pom.xml")
        if "target" not in path.relative_to(root).parts and "src/test" not in str(path.relative_to(root))
    )
    if len(pom_files) != expected_count:
        fail(f"expected {expected_count} source POMs, found {len(pom_files)}")

    aggregator_count = 0
    for path in pom_files:
        relative_path = path.relative_to(root)
        try:
            project = ET.parse(path).getroot()
        except ET.ParseError as error:
            fail(f"{relative_path} is not well-formed XML: {error}")
        if local_name(project.tag) != "project":
            fail(f"{relative_path} must have a project root element")
        if project.tag != f"{{{MAVEN_4_NAMESPACE}}}project":
            fail(f"{relative_path} must use the Maven 4.1.0 namespace")
        model_version = next((child.text or "" for child in project if local_name(child.tag) == "modelVersion"), "")
        if model_version != "4.1.0":
            fail(f"{relative_path} must declare modelVersion 4.1.0")
        schema_location = project.attrib.get("{http://www.w3.org/2001/XMLSchema-instance}schemaLocation", "").split()
        if expected_count == 122 and schema_location != [MAVEN_4_NAMESPACE, "https://maven.apache.org/xsd/maven-4.1.0.xsd"]:
            fail(f"{relative_path} must reference the Maven 4.1.0 XSD")

        subprojects = [element for element in project.iter() if local_name(element.tag) == "subprojects"]
        if any(local_name(element.tag) == "modules" for element in project.iter()):
            fail(f"{relative_path} must use subprojects instead of modules")
        if not subprojects:
            continue
        aggregator_count += 1
        for container in subprojects:
            for element in container:
                if local_name(element.tag) != "subproject":
                    fail(f"{relative_path} subprojects may contain only subproject elements")
                if not (element.text or "").strip():
                    fail(f"{relative_path} has an empty subproject path")

    if expected_count == 122 and aggregator_count != 8:
        fail(f"expected 8 subprojects aggregators, found {aggregator_count}")
    print(f"Maven 4.1 model verification passed: {len(pom_files)} source POMs, {aggregator_count} subprojects aggregators.")


if __name__ == "__main__":
    main()
