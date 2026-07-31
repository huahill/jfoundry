package org.jfoundry.integration.nativeimage.mybatisplus;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class NativeMybatisPlusIT {

    private static final Path APPLICATION = Path.of(
            "target/jfoundry-spring-mybatis-plus-native").toAbsolutePath();

    @Container
    static final PostgreSQLContainer<?> postgresql = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("jfoundry")
            .withUsername("jfoundry")
            .withPassword("jfoundry");

    @Test
    void nativeImagePersistsAndUpdatesAuditDataAgainstPostgreSql() throws Exception {
        createTables();
        assertThat(Files.isExecutable(APPLICATION))
                .as("MyBatis-Plus Native Image executable")
                .isTrue();

        int port = availablePort();
        Process application = new ProcessBuilder(
                APPLICATION.toString(),
                "--server.port=" + port,
                "--spring.datasource.url=" + postgresql.getJdbcUrl(),
                "--spring.datasource.username=" + postgresql.getUsername(),
                "--spring.datasource.password=" + postgresql.getPassword())
                .redirectErrorStream(true)
                .redirectOutput(Path.of("target/native-mybatis-plus.log").toFile())
                .start();

        try {
            awaitReady(port);
            assertThat(persistAndUpdateAuditRecord(port)).contains(
                    "\"createdAtSet\":true",
                    "\"lastModifiedAtSet\":true",
                    "\"createdBy\":\"native-test\"",
                    "\"lastModifiedBy\":\"native-test\"",
                    "\"value\":\"updated\"");
            assertThat(exerciseTechnicalStores(port)).contains(
                    "\"outboxClaimed\":true",
                    "\"inboxCompleted\":true");
        } finally {
            stop(application);
        }
    }

    private static void createTables() throws Exception {
        try (var connection = DriverManager.getConnection(
                postgresql.getJdbcUrl(), postgresql.getUsername(), postgresql.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("""
                    create table native_audit_record (
                        id varchar(64) primary key,
                        content varchar(64) not null,
                        created_at timestamp with time zone not null,
                        created_by varchar(64),
                        last_modified_at timestamp with time zone not null,
                        last_modified_by varchar(64)
                    )
                    """);
            statement.execute("""
                    create table jfoundry_outbox_event (
                        event_id varchar(64) primary key,
                        topic varchar(255) not null,
                        payload_key varchar(255),
                        payload_type varchar(500) not null,
                        payload_json text not null,
                        traceparent varchar(512),
                        tracestate varchar(512),
                        aggregate_type varchar(255),
                        aggregate_id varchar(255),
                        aggregate_version bigint,
                        status varchar(32) not null,
                        retry_count integer not null,
                        error_message varchar(2000),
                        occurred_at timestamp not null,
                        last_attempt_at timestamp,
                        next_retry_at timestamp,
                        created_at timestamp not null,
                        updated_at timestamp not null,
                        claimed_at timestamp,
                        claimed_by varchar(100),
                        claim_token varchar(36)
                    )
                    """);
            statement.execute("""
                    create table jfoundry_inbox_message (
                        id varchar(64) primary key,
                        message_id varchar(128) not null,
                        consumer_name varchar(255) not null,
                        status varchar(32) not null,
                        processed_at timestamp,
                        created_at timestamp not null,
                        updated_at timestamp not null,
                        claimed_at timestamp,
                        claim_token varchar(36),
                        error_message varchar(2000),
                        unique (consumer_name, message_id)
                    )
                    """);
        }
    }

    private static void awaitReady(int port) throws Exception {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(45));
        IOException lastFailure = null;
        while (Instant.now().isBefore(deadline)) {
            try {
                var connection = (java.net.HttpURLConnection) new java.net.URI(
                        "http://127.0.0.1:" + port + "/jfoundry/native/mybatis-plus/ready")
                        .toURL()
                        .openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(1_000);
                connection.setReadTimeout(1_000);
                if (connection.getResponseCode() == 200) {
                    return;
                }
            } catch (IOException failure) {
                lastFailure = failure;
            }
            Thread.sleep(250);
        }
        throw new IOException("Native MyBatis-Plus application did not become ready", lastFailure);
    }

    private static String persistAndUpdateAuditRecord(int port) throws Exception {
        var connection = (java.net.HttpURLConnection) new java.net.URI(
                "http://127.0.0.1:" + port + "/jfoundry/native/mybatis-plus/audit-record")
                .toURL()
                .openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Accept", "application/json");
        connection.setConnectTimeout(1_000);
        connection.setReadTimeout(5_000);
        if (connection.getResponseCode() != 200) {
            throw new IOException("Native MyBatis-Plus audit operation failed with HTTP " + connection.getResponseCode());
        }
        return new String(connection.getInputStream().readAllBytes());
    }

    private static String exerciseTechnicalStores(int port) throws Exception {
        var connection = (java.net.HttpURLConnection) new java.net.URI(
                "http://127.0.0.1:" + port + "/jfoundry/native/mybatis-plus/technical-stores")
                .toURL()
                .openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Accept", "application/json");
        connection.setConnectTimeout(1_000);
        connection.setReadTimeout(5_000);
        if (connection.getResponseCode() != 200) {
            throw new IOException("Native MyBatis-Plus technical store operation failed with HTTP "
                    + connection.getResponseCode());
        }
        return new String(connection.getInputStream().readAllBytes());
    }

    private static int availablePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static void stop(Process application) throws InterruptedException {
        application.destroy();
        if (!application.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) {
            application.destroyForcibly();
            application.waitFor();
        }
    }
}
