# Spring Boot Auto-configuration

This is lookup material for Spring Boot entry points, configuration properties, and bean conditions.
For behavioral contracts, start with [capabilities](../capabilities/aggregate-persistence.md); for
technology-specific setup, use the [implementation guides](../implementations/spring-boot.md).

## Starter Entry Points

| Starter | Adds | Does not add |
|---------|------|--------------|
| `jfoundry-spring-boot-starter` | Minimal Spring Boot baseline for jfoundry capability starters | Transaction integration, Outbox, Inbox, persistence, broker clients, JobRunr |
| `jfoundry-transaction-spring-boot-starter` | Spring `TransactionRunner` integration | A transaction manager; it adapts one supplied by Spring Boot or the application |
| `jfoundry-observability-spring-boot-starter` | Micrometer Observation for eligible Outbox, Inbox, and lock operations | A telemetry exporter, collector, or direct OpenTelemetry decorator |
| `jfoundry-lock-redisson-spring-boot-starter` | Distributed lock core, Spring `@DistributedLock` interception, Redisson adapter, Redisson Spring Boot starter | Outbox, Inbox, broker delivery |
| `jfoundry-event-spring-boot-starter` | Domain event dispatch and Spring application event publishing | Outbox persistence or broker delivery |
| `jfoundry-messaging-spring-boot-starter` | Messaging SPI, Jackson payload serializer, and Spring messaging runtime | Any `MessageSender` or broker client |
| `jfoundry-messaging-kafka-spring-boot-starter` | Kafka `MessageSender` adapter, selected after Boot creates `KafkaOperations` | Outbox store |
| `jfoundry-messaging-rabbitmq-spring-boot-starter` | RabbitMQ `MessageSender` adapter | Outbox store |
| `jfoundry-messaging-rocketmq-spring-boot-starter` | RocketMQ `MessageSender` adapter | Outbox store |
| `jfoundry-outbox-spring-boot-starter` | Outbox core, `OutboxTemplate`, domain-event externalization, scheduled dispatch integration | Outbox table store, JobRunr |
| `jfoundry-outbox-mybatis-plus-spring-boot-starter` | MyBatis-Plus `OutboxMessageStore` adapter | Database migration execution |
| `jfoundry-outbox-jpa-spring-boot-starter` | JPA `OutboxMessageStore` adapter | Database migration execution |
| `jfoundry-outbox-jobrunr-spring-boot-starter` | JobRunr Outbox dispatcher | Outbox table store |
| `jfoundry-inbox-spring-boot-starter` | Inbox core and `InboxTemplate` | Inbox table store |
| `jfoundry-inbox-mybatis-plus-spring-boot-starter` | MyBatis-Plus `InboxMessageStore` adapter | Database migration execution |
| `jfoundry-inbox-jpa-spring-boot-starter` | JPA `InboxMessageStore` adapter and supported-database claim strategy | Database migration execution, claim support for database products other than PostgreSQL and MySQL |
| `jfoundry-persistence-mybatis-plus-spring-boot-starter` | Business MyBatis-Plus persistence entry point: base auto-configuration, shared persistence runtime support, and the MyBatis-Plus Boot starter | The runtime-neutral `jfoundry-persistence-mybatis-plus-starter`, Outbox/Inbox stores |
| `jfoundry-persistence-jpa-spring-boot-starter` | jfoundry JPA adapter for one managed entity graph per aggregate, shared Spring transaction persistence context, Spring Boot JPA runtime | Detached aggregate merge, manual multi-table or multi-graph synchronization algorithms, Outbox and Inbox stores |
| `jfoundry-webmvc-spring-boot-starter` | Web MVC `ProblemDetail` exception handling | Messaging, Outbox, Inbox |

## Configuration Properties

| Property | Default | Effect |
|----------|---------|--------|
| `jfoundry.lock.annotation.enabled` | `true` | Enables `@DistributedLock` advisor when a `DistributedLockClient` bean exists. |
| `jfoundry.domain.event.dispatch.enabled` | `true` | Enables application-service boundary domain event dispatch. |
| `jfoundry.domain.event.dispatch.spring.enabled` | `true` | Enables Spring `ApplicationEventPublisher` dispatch when the Spring event adapter is present. |
| `jfoundry.domain.event.dispatch.outbox.enabled` | `false` | Enables Outbox-backed domain event dispatch when a `DomainEventOutboxRecorder` bean exists. |
| `jfoundry.outbox.table-name` | `jfoundry_outbox_event` | Rewrites the MyBatis-Plus Outbox physical table name. Applications must create the table. |
| `jfoundry.outbox.dispatcher.mode` | `scheduled` | Selects `scheduled`, `jobrunr`, or `none`. |
| `jfoundry.outbox.dispatcher.interval-ms` | `5000` | Fixed-delay interval for scheduled dispatch. |
| `jfoundry.outbox.dispatcher.cron` | `*/10 * * * * *` | JobRunr recurring dispatch cron expression. |
| `jfoundry.outbox.dispatcher.batch-size` | `50` | Maximum records claimed per dispatch run. |
| `jfoundry.outbox.dispatcher.max-retries` | `5` | Maximum dispatch attempts before dead-lettering. |
| `jfoundry.outbox.dispatcher.backoff-base-ms` | `1000` | Base retry backoff. |
| `jfoundry.outbox.dispatcher.backoff-max-ms` | `300000` | Maximum retry backoff. |
| `jfoundry.outbox.recovery.enabled` | follows dispatcher mode | Enables stuck `DISPATCHING` recovery for `scheduled` and `jobrunr`; always disabled for `none`. |
| `jfoundry.outbox.recovery.interval` | `60s` | Recovery job interval. |
| `jfoundry.outbox.recovery.stuck-timeout` | `5m` | Age after which `DISPATCHING` rows are considered stuck. |
| `jfoundry.outbox.cleanup.enabled` | follows dispatcher mode | Enables terminal-row cleanup for `scheduled` and `jobrunr`; always disabled for `none`. |
| `jfoundry.outbox.cleanup.interval` | `24h` | Cleanup job interval. |
| `jfoundry.outbox.cleanup.published-retention-days` | `7` | Retention for `PUBLISHED` rows. |
| `jfoundry.outbox.cleanup.dead-lettered-retention-days` | `30` | Retention for `DEAD_LETTERED` rows. |
| `jfoundry.outbox.cleanup.batch-size` | `1000` | Maximum rows deleted per cleanup batch. |

## Auto-configuration Conditions

| Auto-configuration | Registers | Main conditions |
|--------------------|-----------|-----------------|
| `TransactionRunnerAutoConfiguration` | `SpringTransactionRunner` | `TransactionRunner` and `TransactionTemplate` are available, Spring Boot has configured a `PlatformTransactionManager`, and no existing `TransactionRunner` exists. |
| `DistributedLockAutoConfiguration` | `LockExecutor`, optional Redisson `DistributedLockClient`, optional `@DistributedLock` advisor | `jfoundry-lock-core` is present. Redisson adapter requires `RedissonClient`; annotation advisor requires `DistributedLockClient` and annotation support enabled. |
| `MicrometerObservationAutoConfiguration` | Micrometer advisor for original JFoundry operation beans | An `ObservationRegistry` is available (provided by Actuator when using the observability starter); Micrometer Observation and Spring AOP are present; plus at least one eligible Outbox, Inbox, or lock operation bean. |
| `DomainEventPersistenceAutoConfiguration` | Repository `DomainEventContext` injector | `DomainEventContext` and `AbstractAggregateRepository` are on the classpath. |
| `PersistenceFailureAutoConfiguration` | Default Spring `PersistenceFailureTranslator` and repository injector | `AbstractAggregateRepository`, Spring data-access exceptions, and `jfoundry-persistence-spring` are present; no user-defined translator. |
| `AggregatePersistenceContextAutoConfiguration` | Transaction-bound `AggregatePersistenceContext` and aware-repository injector | Persistence context SPI, Spring transaction support, and `jfoundry-persistence-spring` are present; no user-defined context. |
| `DomainEventDispatchAutoConfiguration` | `DomainEventScope`, `DomainEventContext`, dispatch interceptor, Spring event dispatcher, optional Outbox dispatcher | Application service and dispatcher types are present; dispatch properties allow the selected path. |
| `DomainEventOutboxRecorderAutoConfiguration` | `PayloadSerializer`, `OutboxTemplate`, externalization resolvers, `DomainEventOutboxRecorder` | Outbox store and serializer dependencies are available; no user-defined replacement for each bean. |
| `KafkaMessageSenderAutoConfiguration` | `SpringKafkaMessageSender` | `KafkaOperations` class and bean exist; no existing `MessageSender`. |
| `RabbitMqMessageSenderAutoConfiguration` | `SpringRabbitMqMessageSender` | `RabbitTemplate` class and `RabbitOperations` bean exist; no existing `MessageSender`. |
| `RocketMqMessageSenderAutoConfiguration` | `SpringRocketMqMessageSender` | RocketMQ producer class and `MQProducer` bean exist; no existing `MessageSender`. |
| `OutboxMybatisPlusAutoConfiguration` | Outbox table-name customizer, `MybatisPlusInterceptor`, `OutboxMessageStore` | MyBatis-Plus and Outbox store adapter classes are present. SQL templates are not run automatically. |
| `OutboxJpaAutoConfiguration` | JPA `OutboxMessageStore` | `EntityManagerFactory` and the JPA Outbox adapter are present; no user-defined `OutboxMessageStore` exists. |
| `OutboxDispatcherAutoConfiguration` | `BackoffStrategy`, scheduled dispatcher, recovery job, cleanup job | An Outbox store, message sender, and `TransactionRunner` exist; mode is `scheduled` or maintenance is enabled by managed modes. |
| `JobRunrDispatcherAutoConfiguration` | `JobRunrOutboxDispatcher` | JobRunr and jfoundry JobRunr adapter classes are present; `mode=jobrunr`; store, sender, backoff, and `TransactionRunner` beans exist. |
| `InboxMybatisPlusAutoConfiguration` | MyBatis-Plus `InboxMessageStore` | `SqlSessionFactory`, mapper scanning, and Inbox store adapter are present; no existing store. |
| `InboxJpaAutoConfiguration` | `JpaInboxClaimStrategy`, JPA `InboxMessageStore` | `EntityManagerFactory` and the JPA Inbox adapter are present. A user `InboxMessageStore` or `JpaInboxClaimStrategy` takes precedence; built-in claim strategies support only PostgreSQL and MySQL, and an unknown database product fails fast unless the application supplies a strategy. |
| `InboxAutoConfiguration` | `InboxTemplate` | `InboxTemplate` is on the classpath and `InboxMessageStore` plus `TransactionRunner` beans exist. |
| `WebMvcProblemDetailAutoConfiguration` | `ProblemDetailExceptionHandler` | Servlet Web MVC application and handler class are present; no existing handler. |

## Notes

- Kafka sender auto-configuration runs after Spring Boot's `KafkaAutoConfiguration`, so a
  Boot-created `KafkaOperations` bean is visible before jfoundry evaluates the sender condition.
- jfoundry registers no fallback `MessageSender`. Add a broker-specific starter or define an
  application `MessageSender` before enabling Outbox delivery.
- `TransactionRunnerAutoConfiguration` runs after Spring Boot transaction auto-configuration so
  JDBC, JPA, or JTA transaction managers are visible before its bean conditions are evaluated.
- Transaction support is explicit. Inbox and Outbox starters include the transaction starter because
  their templates and dispatchers require a `TransactionRunner`; the baseline starter does not.
- Domain-event, distributed-lock, and observability starters use Spring Boot's standard AOP
  auto-configuration through `spring-boot-starter-aop`. Proxy strategy and AOP opt-out therefore
  follow Spring Boot's `spring.aop.*` properties; jfoundry does not register its own proxy creator.
- Distributed lock support is explicit. The default Spring Boot starter does not pull Redisson.
- `mode=none` means no dispatcher, recovery job, or cleanup job is registered, even when recovery
  or cleanup is explicitly enabled.
