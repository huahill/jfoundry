package org.jfoundry.integration.nativeimage.mybatisplus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = NativeMybatisPlusApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:native-mybatis-plus;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa"
        })
class NativeMybatisPlusApplicationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void createTable() {
        jdbcTemplate.execute("drop table if exists native_audit_record");
        jdbcTemplate.execute("drop table if exists jfoundry_outbox_event");
        jdbcTemplate.execute("drop table if exists jfoundry_inbox_message");
        jdbcTemplate.execute("""
                create table native_audit_record (
                    id varchar(64) primary key,
                    content varchar(64) not null,
                    created_at timestamp with time zone not null,
                    created_by varchar(64),
                    last_modified_at timestamp with time zone not null,
                    last_modified_by varchar(64)
                )
                """);
        jdbcTemplate.execute("""
                create table jfoundry_outbox_event (
                    event_id varchar(64) primary key,
                    topic varchar(255) not null,
                    payload_key varchar(255),
                    payload_type varchar(500) not null,
                    payload_json clob not null,
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
        jdbcTemplate.execute("""
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

    @Test
    void exposesAReadinessEndpointWithoutMutatingPersistenceState() throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/jfoundry/native/mybatis-plus/ready"))
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("ready");
    }

    @Test
    void persistsAndUpdatesAuditDataThroughTheMybatisPlusStarter() throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/jfoundry/native/mybatis-plus/audit-record"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build(), HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .contains("\"createdAtSet\":true")
                .contains("\"lastModifiedAtSet\":true")
                .contains("\"createdBy\":\"native-test\"")
                .contains("\"lastModifiedBy\":\"native-test\"")
                .contains("\"value\":\"updated\"");
    }

    @Test
    void exercisesOutboxAndInboxStoresThroughTheirMybatisPlusStarters() throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/jfoundry/native/mybatis-plus/technical-stores"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build(), HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .contains("\"outboxClaimed\":true")
                .contains("\"inboxCompleted\":true");
    }
}
