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
}
