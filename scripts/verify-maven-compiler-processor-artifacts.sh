#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
PROJECT_VERSION="$(ruby --disable-gems -e 'require "yaml"; puts YAML.safe_load(File.read(ARGV.fetch(0)), aliases: true).fetch("version")' "${REPO_ROOT}/pom.yaml")"

fail() {
    echo "$*" >&2
    exit 1
}

artifact_jar() {
    local module="$1"
    local artifact="$2"
    local jar

    [[ -n "${PROJECT_VERSION}" ]] || fail "Cannot determine the root project version."
    jar="${REPO_ROOT}/${module}/target/${artifact}-${PROJECT_VERSION}.jar"
    [[ -f "${jar}" ]] || fail "Missing packaged artifact for ${artifact} ${PROJECT_VERSION}; run Maven package first."

    printf '%s\n' "${jar}"
}

assert_entry() {
    local jar="$1"
    local entry="$2"

    unzip -Z1 "${jar}" | grep -Fx "${entry}" > /dev/null \
        || fail "${jar} does not contain ${entry}."
}

assert_content() {
    local jar="$1"
    local entry="$2"
    local expected="$3"

    unzip -p "${jar}" "${entry}" | grep -F "${expected}" > /dev/null \
        || fail "${jar}:${entry} does not contain ${expected}."
}

verify_spring_metadata() {
    local module="$1"
    local artifact="$2"
    local property="$3"
    local jar

    jar="$(artifact_jar "${module}" "${artifact}")"
    assert_entry "${jar}" 'META-INF/spring-configuration-metadata.json'
    assert_content "${jar}" 'META-INF/spring-configuration-metadata.json' "${property}"
}

verify_quarkus_build_steps() {
    local module="$1"
    local artifact="$2"
    local processor="$3"
    local jar

    jar="$(artifact_jar "${module}" "${artifact}")"
    assert_entry "${jar}" 'META-INF/quarkus-build-steps.list'
    assert_content "${jar}" 'META-INF/quarkus-build-steps.list' "${processor}"
}

verify_spring_metadata \
    'jfoundry-runtime/jfoundry-spring/autoconfigure/jfoundry-outbox-spring-boot-autoconfigure' \
    'jfoundry-outbox-spring-boot-autoconfigure' \
    'jfoundry.outbox.table-name'
verify_spring_metadata \
    'jfoundry-runtime/jfoundry-spring/autoconfigure/jfoundry-domain-event-spring-boot-autoconfigure' \
    'jfoundry-domain-event-spring-boot-autoconfigure' \
    'jfoundry.domain.event'
verify_spring_metadata \
    'jfoundry-runtime/jfoundry-spring/autoconfigure/jfoundry-restclient-spring-boot-autoconfigure' \
    'jfoundry-restclient-spring-boot-autoconfigure' \
    'jfoundry.web.rest-client.logging-level'
verify_spring_metadata \
    'jfoundry-runtime/jfoundry-spring/autoconfigure/jfoundry-webmvc-spring-boot-autoconfigure' \
    'jfoundry-webmvc-spring-boot-autoconfigure' \
    'jfoundry.web.mvc.logging-level'

verify_quarkus_build_steps \
    'jfoundry-runtime/jfoundry-quarkus/deployment/jfoundry-transaction-quarkus-deployment' \
    'jfoundry-transaction-quarkus-deployment' \
    'org.jfoundry.quarkus.transaction.deployment.TransactionProcessor'
verify_quarkus_build_steps \
    'jfoundry-runtime/jfoundry-quarkus/deployment/jfoundry-domain-event-quarkus-deployment' \
    'jfoundry-domain-event-quarkus-deployment' \
    'org.jfoundry.quarkus.domain.event.deployment.DomainEventProcessor'
verify_quarkus_build_steps \
    'jfoundry-runtime/jfoundry-quarkus/deployment/jfoundry-persistence-quarkus-deployment' \
    'jfoundry-persistence-quarkus-deployment' \
    'org.jfoundry.quarkus.persistence.deployment.PersistenceProcessor'
verify_quarkus_build_steps \
    'jfoundry-runtime/jfoundry-quarkus/deployment/jfoundry-restclient-quarkus-deployment' \
    'jfoundry-restclient-quarkus-deployment' \
    'org.jfoundry.quarkus.restclient.deployment.RestClientHttpLoggingProcessor'
verify_quarkus_build_steps \
    'jfoundry-runtime/jfoundry-quarkus/deployment/jfoundry-inbox-jpa-quarkus-deployment' \
    'jfoundry-inbox-jpa-quarkus-deployment' \
    'org.jfoundry.quarkus.inbox.jpa.deployment.InboxJpaProcessor'
verify_quarkus_build_steps \
    'jfoundry-runtime/jfoundry-quarkus/deployment/jfoundry-messaging-kafka-quarkus-deployment' \
    'jfoundry-messaging-kafka-quarkus-deployment' \
    'org.jfoundry.quarkus.messaging.kafka.deployment.KafkaMessageSenderProcessor'
verify_quarkus_build_steps \
    'jfoundry-runtime/jfoundry-quarkus/deployment/jfoundry-messaging-rabbitmq-quarkus-deployment' \
    'jfoundry-messaging-rabbitmq-quarkus-deployment' \
    'org.jfoundry.quarkus.messaging.rabbitmq.deployment.RabbitMqMessageSenderProcessor'
verify_quarkus_build_steps \
    'jfoundry-runtime/jfoundry-quarkus/deployment/jfoundry-outbox-jpa-quarkus-deployment' \
    'jfoundry-outbox-jpa-quarkus-deployment' \
    'org.jfoundry.quarkus.outbox.jpa.deployment.OutboxJpaProcessor'
verify_quarkus_build_steps \
    'jfoundry-runtime/jfoundry-quarkus/deployment/jfoundry-outbox-quarkus-deployment' \
    'jfoundry-outbox-quarkus-deployment' \
    'org.jfoundry.quarkus.outbox.deployment.OutboxProcessor'
verify_quarkus_build_steps \
    'jfoundry-runtime/jfoundry-quarkus/deployment/jfoundry-persistence-jpa-quarkus-deployment' \
    'jfoundry-persistence-jpa-quarkus-deployment' \
    'org.jfoundry.quarkus.persistence.jpa.deployment.PersistenceJpaProcessor'
verify_quarkus_build_steps \
    'jfoundry-runtime/jfoundry-quarkus/deployment/jfoundry-web-quarkus-deployment' \
    'jfoundry-web-quarkus-deployment' \
    'org.jfoundry.quarkus.web.deployment.ProblemDetailsProcessor'

echo "Maven compiler processor artifact verification passed."
