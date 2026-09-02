#!/usr/bin/env bash

set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"

if [[ "$#" -lt 1 || "$#" -gt 2 ]]; then
    echo "Usage: $0 <empty-destination-outside-source> [source-root]" >&2
    exit 2
fi

destination_input="$1"
source_input="${2:-${repository_root}}"
if [[ ! -d "${source_input}" ]]; then
    echo "Publication-tree source root does not exist: ${source_input}" >&2
    exit 1
fi
source_root="$(cd "${source_input}" && pwd -P)"
if [[ ! -d "$(dirname "${destination_input}")" ]]; then
    echo "Publication-tree destination parent must already exist: $(dirname "${destination_input}")" >&2
    exit 1
fi
destination_parent="$(cd "$(dirname "${destination_input}")" && pwd -P)"
destination="${destination_parent}/$(basename "${destination_input}")"

if [[ "${destination}" == "${source_root}" || "${destination}" == "${source_root}/"* ]]; then
    echo "Publication-tree destination must be outside the source root: ${destination}" >&2
    exit 1
fi

if [[ -e "${destination}" ]] && find "${destination}" -mindepth 1 -print -quit | grep -q .; then
    echo "Publication-tree destination must be empty: ${destination}" >&2
    exit 1
fi
mkdir -p "${destination}"

rsync -a \
    --exclude '/.git/' \
    --exclude '/.idea/' \
    --exclude '/.vscode/' \
    --exclude '/.codegraph/' \
    --exclude '/.worktrees/' \
    --exclude '/graphify-out/' \
    --exclude '/target/' \
    --exclude '*/target/' \
    "${source_root}/" "${destination}/"

python3 - "${destination}" <<'PY'
import os
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

destination = Path(sys.argv[1]).resolve()
maven_4_namespace = 'http://maven.apache.org/POM/4.1.0'
maven_3_namespace = 'http://maven.apache.org/POM/4.0.0'
xsi_namespace = 'http://www.w3.org/2001/XMLSchema-instance'

ET.register_namespace('', maven_3_namespace)
ET.register_namespace('xsi', xsi_namespace)

def local_name(tag):
    return tag.rsplit('}', 1)[-1]

def qualified(name, namespace=maven_3_namespace):
    return f'{{{namespace}}}{name}'

pom_paths = sorted(destination.glob('**/pom.xml'))
documents = {path.resolve(): ET.parse(path) for path in pom_paths}

def element_text(element, name):
    child = element.find(qualified(name))
    return child.text if child is not None else None

coordinate_cache = {}

def coordinates(path):
    if path in coordinate_cache:
        return coordinate_cache[path]

    root = documents[path].getroot()
    parent = root.find(qualified('parent'))
    parent_path = None
    inherited = {}
    if parent is not None:
        relative_path_element = parent.find(qualified('relativePath'))
        relative_path = (relative_path_element.text or '').strip() if relative_path_element is not None else '../pom.xml'
        relative_path = relative_path or '../pom.xml'
        parent_path = (path.parent / relative_path).resolve()
        if parent_path in documents:
            inherited = coordinates(parent_path)

    result = {
        'groupId': element_text(root, 'groupId') or inherited.get('groupId'),
        'artifactId': element_text(root, 'artifactId'),
        'version': element_text(root, 'version') or inherited.get('version'),
    }
    coordinate_cache[path] = result
    return result

for document in documents.values():
    root = document.getroot()
    for element in root.iter():
        if isinstance(element.tag, str):
            name = local_name(element.tag)
            if name == 'subprojects':
                name = 'modules'
            elif name == 'subproject':
                name = 'module'
            element.tag = qualified(name)
        for attribute in list(element.attrib):
            if local_name(attribute).startswith('child.'):
                del element.attrib[attribute]

    root.set('{' + xsi_namespace + '}schemaLocation',
             f'{maven_3_namespace} https://maven.apache.org/xsd/maven-4.0.0.xsd')
    root.find(qualified('modelVersion')).text = '4.0.0'

for path, document in documents.items():
    root = document.getroot()
    parent = root.find(qualified('parent'))
    if parent is not None:
        relative_path_element = parent.find(qualified('relativePath'))
        relative_path = (relative_path_element.text or '').strip() if relative_path_element is not None else '../pom.xml'
        relative_path = relative_path or '../pom.xml'
        parent_path = (path.parent / relative_path).resolve()
        if parent_path in documents:
            for name in ('groupId', 'artifactId', 'version'):
                if parent.find(qualified(name)) is None:
                    value = coordinates(parent_path).get(name)
                    if value is not None:
                        ET.SubElement(parent, qualified(name)).text = value

    ET.indent(document, space='  ')
    document.write(path, encoding='UTF-8', xml_declaration=True)
PY

echo "Generated Maven 3 publication tree: ${destination}"
