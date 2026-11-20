package io.huahill.jfoundry.infrastructure.persistence.mybatis;

import io.huahill.jfoundry.application.event.DomainEventContext;
import io.huahill.jfoundry.domain.event.EventRecordable;
import io.huahill.jfoundry.infrastructure.persistence.AggregatePersistenceContext;
import io.huahill.jfoundry.infrastructure.persistence.event.DomainEventAggregatePersistenceObserver;
import io.huahill.jfoundry.infrastructure.persistence.spring.SpringTransactionAggregatePersistenceContext;
import io.huahill.jfoundry.infrastructure.persistence.mybatis.support.TestOrderDataMapper;
import io.huahill.jfoundry.infrastructure.persistence.mybatis.support.TestOrderMapper;
import io.huahill.jfoundry.infrastructure.persistence.mybatis.support.TestOrderRepository;
import io.huahill.jfoundry.infrastructure.persistence.mybatis.support.TestVersionedOrderRepository;
import io.huahill.jfoundry.infrastructure.persistence.mybatis.support.VersionedOrderMapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/// Spring configuration for integration tests.
/// <p>
/// Mirrors the OutboxPersistenceTestConfig pattern in jfoundry-outbox-mybatis-plus:
/// embedded H2 plus explicit @SpringBootConfiguration and @MapperScan.
@SpringBootConfiguration
@EnableAutoConfiguration
@MapperScan(basePackageClasses = TestOrderMapper.class)
class PersistenceTestConfig {

    @Bean
    @Primary
    TestDomainEventContext testDomainEventContext() {
        return new TestDomainEventContext();
    }

    @Bean
    DomainEventContext domainEventContext(TestDomainEventContext context) {
        return context;
    }

    @Bean
    AggregatePersistenceContext aggregatePersistenceContext() {
        return new SpringTransactionAggregatePersistenceContext();
    }

    @Bean
    DataSource dataSource() {
        return new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("jfoundry-persistence-test")
                .addScript("classpath:test_order.sql")
                .build();
    }

    @Bean
    TestOrderRepository testOrderRepository(TestOrderMapper mapper,
                                             DomainEventContext domainEventContext,
                                             TestOrderDataMapper dataMapper) {
        TestOrderRepository repository = new TestOrderRepository(mapper, dataMapper);
        repository.setAggregatePersistenceObserver(
                new DomainEventAggregatePersistenceObserver(domainEventContext));
        return repository;
    }

    @Bean
    TestOrderDataMapper testOrderDataMapper() {
        return new TestOrderDataMapper();
    }

    @Bean
    TestVersionedOrderRepository testVersionedOrderRepository(
            VersionedOrderMapper mapper,
            AggregatePersistenceContext persistenceContext) {
        TestVersionedOrderRepository repository = new TestVersionedOrderRepository(mapper);
        repository.setAggregatePersistenceContext(persistenceContext);
        return repository;
    }

    static final class TestDomainEventContext implements DomainEventContext {

        private final List<EventRecordable> registered = new ArrayList<>();
        private final Map<EventRecordable, Boolean> seen = new IdentityHashMap<>();

        @Override
        public void register(EventRecordable aggregate) {
            if (aggregate == null) {
                throw new IllegalArgumentException("Aggregate must not be null.");
            }
            if (!seen.containsKey(aggregate)) {
                seen.put(aggregate, Boolean.TRUE);
                registered.add(aggregate);
            }
        }

        List<EventRecordable> drainRegistered() {
            List<EventRecordable> snapshot = List.copyOf(registered);
            registered.clear();
            seen.clear();
            return snapshot;
        }
    }
}
