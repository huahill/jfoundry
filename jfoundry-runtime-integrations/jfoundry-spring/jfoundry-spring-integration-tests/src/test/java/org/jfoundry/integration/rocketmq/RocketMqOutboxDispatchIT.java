package org.jfoundry.integration.rocketmq;

import org.apache.rocketmq.client.consumer.DefaultLitePullConsumer;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageExt;
import org.jfoundry.application.messaging.SendResult;
import org.jfoundry.application.messaging.MessageSender;
import org.jfoundry.application.outbox.DefaultOutboxDispatchService;
import org.jfoundry.infrastructure.outbox.mybatis.MybatisPlusOutboxMessageStore;
import org.jfoundry.infrastructure.outbox.mybatis.OutboxData;
import org.jfoundry.infrastructure.outbox.mybatis.OutboxMapper;
import org.jfoundry.integration.support.OutboxInboxDatabaseConfig;
import org.jfoundry.integration.support.OutboxMessages;
import org.jfoundry.integration.support.SqlScripts;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.FixedHostPortGenericContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(
        classes = OutboxInboxDatabaseConfig.class,
        properties = "jfoundry.outbox.dispatcher.mode=none"
)
class RocketMqOutboxDispatchIT {

    private static final String TOPIC = "jfoundry_integration_outbox";
    private static final DockerImageName ROCKETMQ_IMAGE = DockerImageName.parse("apache/rocketmq:5.5.0");
    private static final Network NETWORK = Network.newNetwork();

    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("jfoundry")
            .withUsername("jfoundry")
            .withPassword("jfoundry");

    @Container
    static final GenericContainer<?> nameserver = new GenericContainer<>(ROCKETMQ_IMAGE)
            .withNetwork(NETWORK)
            .withNetworkAliases("nameserver")
            .withExposedPorts(9876)
            .withCommand("sh", "mqnamesrv")
            .waitingFor(Wait.forLogMessage(".*The Name Server boot success.*", 1));

    @SuppressWarnings("resource")
    @Container
    static final FixedHostPortGenericContainer<?> broker = new FixedHostPortGenericContainer<>("apache/rocketmq:5.5.0")
            .withNetwork(NETWORK)
            .withFixedExposedPort(10911, 10911)
            .withExposedPorts(10909)
            .withCopyFileToContainer(MountableFile.forClasspathResource("rocketmq/broker.conf"),
                    "/home/rocketmq/rocketmq-5.5.0/conf/broker.conf")
            .withCommand("sh", "mqbroker", "-c", "/home/rocketmq/rocketmq-5.5.0/conf/broker.conf")
            .waitingFor(Wait.forLogMessage(".*boot success.*", 1))
            .dependsOn(nameserver);

    @Autowired
    private MybatisPlusOutboxMessageStore store;

    @Autowired
    private OutboxMapper mapper;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.datasource.driver-class-name", mysql::getDriverClassName);
    }

    @BeforeAll
    static void createSchema(@Autowired DataSource dataSource) {
        SqlScripts.run(dataSource, "jfoundry/sql/outbox/mysql/create_outbox_event.sql");
    }

    @BeforeEach
    void cleanDb() {
        mapper.delete(null);
    }

    @Test
    void dispatchPublishesRocketMqMessageAndMarksOutboxPublished() throws Exception {
        store.append(OutboxMessages.pending(
                "evt-rocket-1",
                TOPIC,
                "order-1",
                "{\"event\":\"created\"}"));
        DefaultMQProducer producer = producer();
        DefaultLitePullConsumer consumer = null;
        try {
            createTopicWhenBrokerRouteIsReady(producer);
            consumer = consumer();
            consumer.poll(1_000);

            new DefaultOutboxDispatchService(
                    store,
                    rocketMqSender(producer),
                    3,
                    retry -> Duration.ofMillis(10),
                    "it-pod").dispatch(10);

            MessageExt message = pollSingleMessage(consumer);

            assertThat(message.getTopic()).isEqualTo(TOPIC);
            assertThat(message.getKeys()).isEqualTo("order-1");
            assertThat(new String(message.getBody(), StandardCharsets.UTF_8)).isEqualTo("{\"event\":\"created\"}");
        } finally {
            if (consumer != null) {
                consumer.shutdown();
            }
            producer.shutdown();
        }

        OutboxData data = mapper.selectById("evt-rocket-1");
        assertThat(data.getStatus()).isEqualTo("PUBLISHED");
        assertThat(data.getClaimToken()).isNull();
        assertThat(data.getClaimedAt()).isNull();
        assertThat(data.getClaimedBy()).isNull();
    }

    @Test
    void dispatchFailureMarksOutboxFailedAndKeepsRetryMetadata() {
        store.append(OutboxMessages.pending(
                "evt-rocket-fail",
                TOPIC,
                "order-1",
                "{\"event\":\"created\"}"));

        new DefaultOutboxDispatchService(
                store,
                (topic, key, payload) -> SendResult.fail("broker down"),
                3,
                retry -> Duration.ofMillis(10),
                "it-pod").dispatch(10);

        OutboxData data = mapper.selectById("evt-rocket-fail");
        assertThat(data.getStatus()).isEqualTo("FAILED");
        assertThat(data.getRetryCount()).isEqualTo(1);
        assertThat(data.getErrorMessage()).contains("broker down");
        assertThat(data.getNextRetryAt()).isNotNull();
        assertThat(data.getClaimToken()).isNull();
        assertThat(data.getClaimedAt()).isNull();
        assertThat(data.getClaimedBy()).isNull();
    }

    private static DefaultMQProducer producer() throws Exception {
        DefaultMQProducer producer = new DefaultMQProducer("jfoundry-it-producer-" + UUID.randomUUID());
        producer.setNamesrvAddr(nameserverAddress());
        producer.setRetryTimesWhenSendFailed(0);
        producer.start();
        return producer;
    }

    private static MessageSender rocketMqSender(DefaultMQProducer producer) {
        return (topic, key, payload) -> {
            try {
                org.apache.rocketmq.common.message.Message message = new org.apache.rocketmq.common.message.Message(
                        topic, payload.getBytes(StandardCharsets.UTF_8));
                if (key != null) {
                    message.setKeys(key);
                }
                producer.send(message, 10_000L);
                return SendResult.ok();
            } catch (Exception exception) {
                Throwable cause = exception.getCause() != null ? exception.getCause() : exception;
                return SendResult.fail(cause.getMessage());
            }
        };
    }

    private static DefaultLitePullConsumer consumer() throws Exception {
        DefaultLitePullConsumer consumer = new DefaultLitePullConsumer("jfoundry-it-consumer-" + UUID.randomUUID());
        consumer.setNamesrvAddr(nameserverAddress());
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_FIRST_OFFSET);
        consumer.subscribe(TOPIC, "*");
        consumer.start();
        return consumer;
    }

    private static MessageExt pollSingleMessage(DefaultLitePullConsumer consumer) {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (System.nanoTime() < deadline) {
            List<MessageExt> messages = consumer.poll(1_000);
            if (!messages.isEmpty()) {
                assertThat(messages).hasSize(1);
                return messages.get(0);
            }
        }
        throw new AssertionError("No RocketMQ message received from topic " + TOPIC);
    }

    private static void createTopicWhenBrokerRouteIsReady(DefaultMQProducer producer) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        Exception last = null;
        while (System.nanoTime() < deadline) {
            try {
                producer.createTopic("broker-a", TOPIC, 1, Map.of());
                if (!producer.fetchPublishMessageQueues(TOPIC).isEmpty()) {
                    return;
                }
            } catch (Exception e) {
                last = e;
            }
            Thread.sleep(500);
        }
        AssertionError error = new AssertionError("RocketMQ topic route was not ready for " + TOPIC);
        if (last != null) {
            error.initCause(last);
        }
        throw error;
    }

    private static String nameserverAddress() {
        return nameserver.getHost() + ":" + nameserver.getMappedPort(9876);
    }
}
