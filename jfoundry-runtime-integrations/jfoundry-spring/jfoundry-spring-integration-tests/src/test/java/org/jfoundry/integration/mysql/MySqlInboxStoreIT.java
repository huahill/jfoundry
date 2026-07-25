package org.jfoundry.integration.mysql;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.jfoundry.application.inbox.InboxMessageStatus;
import org.jfoundry.infrastructure.inbox.mybatis.InboxMessageData;
import org.jfoundry.infrastructure.inbox.mybatis.InboxMessageMapper;
import org.jfoundry.infrastructure.inbox.mybatis.MybatisPlusInboxMessageStore;
import org.jfoundry.integration.support.OutboxInboxDatabaseConfig;
import org.jfoundry.integration.support.SqlScripts;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(
        classes = OutboxInboxDatabaseConfig.class,
        properties = "jfoundry.outbox.dispatcher.mode=none"
)
class MySqlInboxStoreIT {

    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("jfoundry")
            .withUsername("jfoundry")
            .withPassword("jfoundry");

    @Autowired
    private MybatisPlusInboxMessageStore store;

    @Autowired
    private InboxMessageMapper mapper;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.datasource.driver-class-name", mysql::getDriverClassName);
    }

    @BeforeAll
    static void createSchema(@Autowired DataSource dataSource) {
        SqlScripts.run(dataSource, "jfoundry/sql/inbox/common/create_inbox_message.sql");
    }

    @BeforeEach
    void cleanDb() {
        mapper.delete(null);
    }

    @Test
    void tryStartProcessingAllowsOnlyOneConcurrentHandler() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        AtomicInteger started = new AtomicInteger();
        try {
            for (int i = 0; i < 4; i++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        if (store.tryStartProcessing("evt-1", "projection")) {
                            started.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }

            start.countDown();
            pool.shutdown();

            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }

        assertThat(started.get()).isEqualTo(1);
        assertThat(mapper.selectCount(null)).isEqualTo(1);
        assertThat(load("evt-1", "projection").getStatus()).isEqualTo(InboxMessageStatus.PROCESSING.name());
    }

    @Test
    void markProcessedTransitionsProcessingRecord() {
        assertThat(store.tryStartProcessing("evt-1", "projection")).isTrue();

        store.markProcessed("evt-1", "projection");

        InboxMessageData data = load("evt-1", "projection");
        assertThat(data.getStatus()).isEqualTo(InboxMessageStatus.PROCESSED.name());
        assertThat(data.getProcessedAt()).isNotNull();
        assertThat(data.getErrorMessage()).isNull();
        assertThat(store.isProcessed("evt-1", "projection")).isTrue();
    }

    @Test
    void markFailedAllowsRetry() {
        assertThat(store.tryStartProcessing("evt-1", "projection")).isTrue();

        store.markFailed("evt-1", "projection", "boom");

        InboxMessageData failed = load("evt-1", "projection");
        assertThat(failed.getStatus()).isEqualTo(InboxMessageStatus.FAILED.name());
        assertThat(failed.getErrorMessage()).isEqualTo("boom");

        assertThat(store.tryStartProcessing("evt-1", "projection")).isTrue();
        InboxMessageData retrying = load("evt-1", "projection");
        assertThat(retrying.getStatus()).isEqualTo(InboxMessageStatus.PROCESSING.name());
        assertThat(retrying.getErrorMessage()).isNull();
    }

    @Test
    void processedMessageCannotBeStartedAgain() {
        assertThat(store.tryStartProcessing("evt-1", "projection")).isTrue();
        store.markProcessed("evt-1", "projection");

        assertThat(store.tryStartProcessing("evt-1", "projection")).isFalse();

        assertThat(mapper.selectCount(null)).isEqualTo(1);
        assertThat(load("evt-1", "projection").getStatus()).isEqualTo(InboxMessageStatus.PROCESSED.name());
    }

    @Test
    void differentConsumersCanProcessSameMessage() {
        assertThat(store.tryStartProcessing("evt-1", "projection-a")).isTrue();
        assertThat(store.tryStartProcessing("evt-1", "projection-b")).isTrue();

        store.markProcessed("evt-1", "projection-a");
        store.markProcessed("evt-1", "projection-b");

        assertThat(store.isProcessed("evt-1", "projection-a")).isTrue();
        assertThat(store.isProcessed("evt-1", "projection-b")).isTrue();
        assertThat(mapper.selectCount(null)).isEqualTo(2);
    }

    private InboxMessageData load(String messageId, String consumerName) {
        List<InboxMessageData> records = mapper.selectList(Wrappers.lambdaQuery(InboxMessageData.class)
                .eq(InboxMessageData::getMessageId, messageId)
                .eq(InboxMessageData::getConsumerName, consumerName));
        assertThat(records).hasSize(1);
        return records.get(0);
    }
}
