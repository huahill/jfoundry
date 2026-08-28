#!/usr/bin/env bash

set -euo pipefail

readonly EN_DOCS_DIR="docs/i18n/en"
readonly ZH_DOCS_DIR="docs/i18n/zh"
readonly -a CAPABILITY_CATALOG_DOCUMENTS=(
    "${EN_DOCS_DIR}/capabilities/index.md"
    "${EN_DOCS_DIR}/capabilities/web.md"
    "${ZH_DOCS_DIR}/capabilities/index.md"
    "${ZH_DOCS_DIR}/capabilities/web.md"
)

if [[ ! -d "$EN_DOCS_DIR" || ! -d "$ZH_DOCS_DIR" ]]; then
    echo "Expected both $EN_DOCS_DIR and $ZH_DOCS_DIR to exist." >&2
    exit 1
fi

temp_dir="$(mktemp -d)"
trap 'rm -rf "$temp_dir"' EXIT

find "$EN_DOCS_DIR" -type f -name '*.md' | sed "s#^$EN_DOCS_DIR/##" | sort > "$temp_dir/en-docs"
find "$ZH_DOCS_DIR" -type f -name '*.md' | sed "s#^$ZH_DOCS_DIR/##" | sort > "$temp_dir/zh-docs"
ruby --disable-gems <<'RUBY' > "$temp_dir/reactor-artifacts"
require "find"
require "rexml/document"
require "yaml"

Find.find(".") do |entry|
  if File.directory?(entry) && %w[.git .worktrees target].include?(File.basename(entry))
    Find.prune
  end
  next unless File.file?(entry) && %w[pom.yaml pom.xml].include?(File.basename(entry))

  artifact_id = if File.basename(entry) == "pom.yaml"
                  YAML.safe_load(File.read(entry), aliases: true).fetch("artifactId", "")
                else
                  document = REXML::Document.new(File.read(entry))
                  REXML::XPath.first(document, "/*[local-name()='project']/*[local-name()='artifactId']")&.text
                end
  puts artifact_id unless artifact_id.to_s.empty?
end
RUBY

if ! diff -u "$temp_dir/en-docs" "$temp_dir/zh-docs"; then
    echo "English and Chinese documentation paths must stay aligned." >&2
    exit 1
fi

failures=0
while IFS= read -r document; do
    while IFS= read -r link; do
        [[ -z "$link" || "$link" == \#* || "$link" =~ ^[a-zA-Z][a-zA-Z0-9+.-]*: || "$link" == //* ]] && continue

        target="${link%%#*}"
        [[ -z "$target" ]] && continue

        path="$(dirname "$document")/$target"
        if [[ ! -e "$path" ]]; then
            echo "Missing local Markdown target: $document -> $link" >&2
            failures=1
        fi
    done < <(perl -ne 'while (/\]\(([^ )]+)(?:\s+"[^"]*")?\)/g) { print "$1\n" }' "$document")
done < <(find docs -type f -name '*.md' -print; find . -maxdepth 1 -type f -name 'README*.md' -print)

while IFS= read -r artifact; do
    if ! grep -Fxq "${artifact}" "$temp_dir/reactor-artifacts"; then
        echo "Documented JFoundry artifact does not exist in a reactor POM: ${artifact}" >&2
        failures=1
    fi
done < <(
    perl -ne 'while (/`(jfoundry-[a-z0-9-]+)`/g) { print "$1\n" }' \
        "${CAPABILITY_CATALOG_DOCUMENTS[@]}" | sort -u
)

exit "$failures"
