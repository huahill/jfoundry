package org.jfoundry.integration.rabbitmq;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.GetResponse;
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
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(
        classes = OutboxInboxDatabaseConfig.class,
        properties = "jfoundry.outbox.dispatcher.mode=none"
)
class RabbitMqOutboxDispatchIT {

    private static final String EXCHANGE = "jfoundry.integration.outbox";
    private static final String ROUTING_KEY = "order.created";
    private static final String QUEUE = "jfoundry.integration.outbox.order.created";

    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("jfoundry")
            .withUsername("jfoundry")
            .withPassword("jfoundry");

    @Container
    static final RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

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
    void cleanDb() throws Exception {
        mapper.delete(null);
        try (Connection connection = connectionFactory().newConnection();
             Channel channel = connection.createChannel()) {
            channel.exchangeDeclare(EXCHANGE, "direct", true, false, null);
            channel.queueDeclare(QUEUE, true, false, false, null);
            channel.queueBind(QUEUE, EXCHANGE, ROUTING_KEY);
            channel.queuePurge(QUEUE);
        }
    }

    @Test
    void dispatchPublishesRabbitMqMessageAndMarksOutboxPublished() throws Exception {
        store.append(OutboxMessages.pending(
                "evt-rabbit-1",
                EXCHANGE,
                ROUTING_KEY,
                "{\"event\":\"created\"}"));

        try (Connection connection = connectionFactory().newConnection();
             Channel channel = connection.createChannel()) {
            new DefaultOutboxDispatchService(
                    store,
                    rabbitMqSender(channel),
                    3,
                    retry -> Duration.ofMillis(10),
                    "it-pod").dispatch(10);
        }

        String payload = singleMessage();

        assertThat(payload).isEqualTo("{\"event\":\"created\"}");
        OutboxData data = mapper.selectById("evt-rabbit-1");
        assertThat(data.getStatus()).isEqualTo("PUBLISHED");
        assertThat(data.getClaimToken()).isNull();
        assertThat(data.getClaimedAt()).isNull();
        assertThat(data.getClaimedBy()).isNull();
    }

    @Test
    void dispatchFailureMarksOutboxFailedAndKeepsRetryMetadata() {
        store.append(OutboxMessages.pending(
                "evt-rabbit-fail",
                EXCHANGE,
                ROUTING_KEY,
                "{\"event\":\"created\"}"));

        new DefaultOutboxDispatchService(
                store,
                (topic, key, payload) -> SendResult.fail("broker down"),
                3,
                retry -> Duration.ofMillis(10),
                "it-pod").dispatch(10);

        OutboxData data = mapper.selectById("evt-rabbit-fail");
        assertThat(data.getStatus()).isEqualTo("FAILED");
        assertThat(data.getRetryCount()).isEqualTo(1);
        assertThat(data.getErrorMessage()).contains("broker down");
        assertThat(data.getNextRetryAt()).isNotNull();
        assertThat(data.getClaimToken()).isNull();
        assertThat(data.getClaimedAt()).isNull();
        assertThat(data.getClaimedBy()).isNull();
    }

    private static ConnectionFactory connectionFactory() {
        ConnectionFactory connectionFactory = new ConnectionFactory();
        connectionFactory.setHost(rabbitmq.getHost());
        connectionFactory.setPort(rabbitmq.getAmqpPort());
        connectionFactory.setUsername(rabbitmq.getAdminUsername());
        connectionFactory.setPassword(rabbitmq.getAdminPassword());
        return connectionFactory;
    }

    private static MessageSender rabbitMqSender(Channel channel) {
        return (topic, key, payload) -> {
            try {
                channel.basicPublish(topic, key == null ? "" : key, null, payload.getBytes(StandardCharsets.UTF_8));
                return SendResult.ok();
            } catch (Exception exception) {
                Throwable cause = exception.getCause() != null ? exception.getCause() : exception;
                return SendResult.fail(cause.getMessage());
            }
        };
    }

    private static String singleMessage() throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        try (Connection connection = connectionFactory().newConnection();
             Channel channel = connection.createChannel()) {
            while (System.nanoTime() < deadline) {
                GetResponse response = channel.basicGet(QUEUE, true);
                if (response != null) {
                    return new String(response.getBody(), StandardCharsets.UTF_8);
                }
                Thread.sleep(250);
            }
        }
        throw new AssertionError("No RabbitMQ message received from queue " + QUEUE);
    }
}
