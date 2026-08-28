#!/usr/bin/env bash

set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
converter_source="${repository_root}/scripts/support/ConvertMasonPom.java"

if [[ "$#" -ne 1 ]]; then
    echo "Usage: $0 <empty-destination-outside-repository>" >&2
    exit 2
fi

destination_input="$1"
mkdir -p "$(dirname "${destination_input}")"
destination_parent="$(cd "$(dirname "${destination_input}")" && pwd -P)"
destination="${destination_parent}/$(basename "${destination_input}")"

case "${destination}/" in
    "${repository_root}/"*)
        echo "Publication-tree destination must be outside the repository: ${destination}" >&2
        exit 1
        ;;
esac

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
    "${repository_root}/" "${destination}/"

maven_home_path="$(
    "${repository_root}/mvnw" --version |
        sed -n 's/^Maven home: //p' |
        head -n 1
)"
if [[ ! -d "${maven_home_path}/lib" ]]; then
    echo "Unable to locate the Maven 4 Wrapper runtime libraries." >&2
    exit 1
fi

maven_user_home_path="${MAVEN_USER_HOME:-${HOME}/.m2}"
local_repository="${maven_user_home_path}/repository"
classpath_entries=(
    "${maven_home_path}/lib/*"
    "${local_repository}/eu/maveniverse/maven/mason/mason/0.3.0/mason-0.3.0.jar"
    "${local_repository}/com/fasterxml/jackson/core/jackson-databind/2.21.1/jackson-databind-2.21.1.jar"
    "${local_repository}/com/fasterxml/jackson/core/jackson-core/2.21.1/jackson-core-2.21.1.jar"
    "${local_repository}/com/fasterxml/jackson/dataformat/jackson-dataformat-yaml/2.21.1/jackson-dataformat-yaml-2.21.1.jar"
    "${local_repository}/com/fasterxml/jackson/dataformat/jackson-dataformat-toml/2.21.1/jackson-dataformat-toml-2.21.1.jar"
    "${local_repository}/org/yaml/snakeyaml/2.5/snakeyaml-2.5.jar"
    "${local_repository}/com/typesafe/config/1.4.7/config-1.4.7.jar"
)

for entry in "${classpath_entries[@]:1}"; do
    if [[ ! -f "${entry}" ]]; then
        echo "Mason conversion dependency does not exist: ${entry}" >&2
        exit 1
    fi
done

converter_classpath="$(IFS=:; echo "${classpath_entries[*]}")"
converted_projects=(
    "."
    "jfoundry-core/jfoundry-domain"
    "jfoundry-runtime/jfoundry-spring/starters/jfoundry-spring-boot-starter"
    "jfoundry-boms/jfoundry-helidon-dependencies"
)

for project in "${converted_projects[@]}"; do
    if [[ "${project}" == "." ]]; then
        project_directory="${destination}"
    else
        project_directory="${destination}/${project}"
    fi
    java --class-path "${converter_classpath}" "${converter_source}" \
        "${project_directory}/pom.yaml" "${project_directory}/pom.xml"
done

xml_parent_paths=(
    "jfoundry-core/jfoundry-application/pom.xml:../../pom.yaml:../../pom.xml"
    "jfoundry-core/jfoundry-architecture/jfoundry-architecture-test/pom.xml:../../../pom.yaml:../../../pom.xml"
    "jfoundry-core/jfoundry-architecture/pom.xml:../../pom.yaml:../../pom.xml"
    "jfoundry-core/jfoundry-infrastructure/pom.xml:../../pom.yaml:../../pom.xml"
    "jfoundry-runtime/jfoundry-helidon/pom.xml:../../pom.yaml:../../pom.xml"
    "jfoundry-runtime/jfoundry-jakarta/pom.xml:../../pom.yaml:../../pom.xml"
    "jfoundry-runtime/jfoundry-quarkus/pom.xml:../../pom.yaml:../../pom.xml"
    "jfoundry-runtime/jfoundry-spring/pom.xml:../../pom.yaml:../../pom.xml"
)

for entry in "${xml_parent_paths[@]}"; do
    IFS=: read -r pom yaml_path xml_path <<< "${entry}"
    sed -i.bak \
        "s#<relativePath>${yaml_path}</relativePath>#<relativePath>${xml_path}</relativePath>#" \
        "${destination}/${pom}"
    rm "${destination}/${pom}.bak"
done

rm \
    "${destination}/pom.yaml" \
    "${destination}/jfoundry-core/jfoundry-domain/pom.yaml" \
    "${destination}/jfoundry-runtime/jfoundry-spring/starters/jfoundry-spring-boot-starter/pom.yaml" \
    "${destination}/jfoundry-boms/jfoundry-helidon-dependencies/pom.yaml" \
    "${destination}/.mvn/extensions.xml"

echo "Generated Maven 3 publication tree: ${destination}"
