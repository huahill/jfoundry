package org.jfoundry.integration.kafka;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(
        classes = OutboxInboxDatabaseConfig.class,
        properties = "jfoundry.outbox.dispatcher.mode=none"
)
class KafkaOutboxDispatchIT {

    private static final String TOPIC = "jfoundry.integration.outbox";

    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("jfoundry")
            .withUsername("jfoundry")
            .withPassword("jfoundry");

    @Container
    static final KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("apache/kafka:3.8.0"));

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
    void dispatchPublishesKafkaRecordAndMarksOutboxPublished() {
        store.append(OutboxMessages.pending(
                "evt-kafka-1",
                TOPIC,
                "order-1",
                "{\"event\":\"created\"}"));
        try (Producer<String, String> producer = producer()) {
            new DefaultOutboxDispatchService(
                    store,
                    kafkaSender(producer),
                    3,
                    retry -> Duration.ofMillis(10),
                    "it-pod").dispatch(10);

            try (Consumer<String, String> consumer = consumer("dispatch-success")) {
                consumer.subscribe(java.util.List.of(TOPIC));
                ConsumerRecord<String, String> record = singleRecord(consumer);

                assertThat(record.key()).isEqualTo("order-1");
                assertThat(record.value()).isEqualTo("{\"event\":\"created\"}");
            }
        }

        OutboxData data = mapper.selectById("evt-kafka-1");
        assertThat(data.getStatus()).isEqualTo("PUBLISHED");
        assertThat(data.getClaimToken()).isNull();
        assertThat(data.getClaimedAt()).isNull();
        assertThat(data.getClaimedBy()).isNull();
    }

    @Test
    void dispatchFailureMarksOutboxFailedAndKeepsRetryMetadata() {
        store.append(OutboxMessages.pending(
                "evt-kafka-fail",
                TOPIC,
                "order-1",
                "{\"event\":\"created\"}"));

        new DefaultOutboxDispatchService(
                store,
                (topic, key, payload) -> SendResult.fail("broker down"),
                3,
                retry -> Duration.ofMillis(10),
                "it-pod").dispatch(10);

        OutboxData data = mapper.selectById("evt-kafka-fail");
        assertThat(data.getStatus()).isEqualTo("FAILED");
        assertThat(data.getRetryCount()).isEqualTo(1);
        assertThat(data.getErrorMessage()).contains("broker down");
        assertThat(data.getNextRetryAt()).isNotNull();
        assertThat(data.getClaimToken()).isNull();
        assertThat(data.getClaimedAt()).isNull();
        assertThat(data.getClaimedBy()).isNull();
    }

    private Producer<String, String> producer() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        return new KafkaProducer<>(props);
    }

    private static MessageSender kafkaSender(Producer<String, String> producer) {
        return (topic, key, payload) -> {
            try {
                producer.send(new org.apache.kafka.clients.producer.ProducerRecord<>(topic, key, payload))
                        .get(10, java.util.concurrent.TimeUnit.SECONDS);
                return SendResult.ok();
            } catch (Exception exception) {
                Throwable cause = exception.getCause() != null ? exception.getCause() : exception;
                return SendResult.fail(cause.getMessage());
            }
        };
    }

    private Consumer<String, String> consumer(String groupId) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        return new KafkaConsumer<>(props);
    }

    private ConsumerRecord<String, String> singleRecord(Consumer<String, String> consumer) {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline) {
            var records = consumer.poll(Duration.ofMillis(250));
            if (!records.isEmpty()) {
                assertThat(records.count()).isEqualTo(1);
                return records.iterator().next();
            }
        }
        throw new AssertionError("No Kafka record received from topic " + TOPIC);
    }
}
