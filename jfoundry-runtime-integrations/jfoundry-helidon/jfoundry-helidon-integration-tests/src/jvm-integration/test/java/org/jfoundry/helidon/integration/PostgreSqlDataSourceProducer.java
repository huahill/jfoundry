package org.jfoundry.helidon.integration;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;

/// Provides the named JTA datasource backed by a Testcontainers PostgreSQL instance.
@ApplicationScoped
public class PostgreSqlDataSourceProducer {

    private final PostgreSQLContainer<?> database = new PostgreSQLContainer<>("postgres:17-alpine");

    @Produces
    @Named("jfoundry")
    DataSource dataSource() {
        database.start();
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl(database.getJdbcUrl());
        dataSource.setUser(database.getUsername());
        dataSource.setPassword(database.getPassword());
        return dataSource;
    }

    @PreDestroy
    void stop() {
        database.stop();
    }
}
