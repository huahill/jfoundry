# Reliable Messaging: Outbox And Inbox

Use Transactional Outbox only when a domain event must reach another process or external system
reliably. In-process event handling does not require it. Inbox provides consumer-side idempotency
for a message and consumer combination.

For direct broker publication and transport selection, see [Message Delivery](message-delivery.md).
Reliable messaging composes that selected transport with Outbox recording and optional Inbox
idempotency; it does not itself select a broker.

![transactional-outbox.png](../../assets/outbox/transactional-outbox.png)

## Compose The Capability

Outbox is assembled from separate choices. An ORM or scheduler suffix identifies one adapter for
the capability; it does not identify a complete Outbox solution.

| Decision | Purpose | Spring Boot selection |
|---|---|---|
| Outbox capability | Records, externalizes, recovers, cleans up, and coordinates dispatch | `jfoundry-outbox-spring-boot-starter` |
| Store adapter | Persists `OutboxMessageStore` records | `jfoundry-outbox-jpa-spring-boot-starter`, `jfoundry-outbox-mybatis-plus-spring-boot-starter`, or an application implementation |
| Dispatch trigger | Starts dispatch work | Built-in scheduled mode, optional `jfoundry-outbox-jobrunr-spring-boot-starter`, or an application dispatcher |
| Message transport | Sends the claimed payload | A broker-specific `jfoundry-messaging-*-spring-boot-starter` or an application `MessageSender` |

Aggregate persistence is a separate choice. A `jfoundry-persistence-*-spring-boot-starter` persists
business aggregates; a `jfoundry-outbox-*-spring-boot-starter` persists Outbox records. Selecting one
does not select the other.

These are separate responsibilities, not necessarily separate direct Maven declarations. The
built-in store starters and the JobRunr starter include `jfoundry-outbox-spring-boot-starter`
transitively, so an application does not declare it again. That dependency is Spring Boot assembly
convenience; the store and dispatcher remain replaceable adapters.

## Event Flow

```text
aggregate explicitly records domain event
  -> automatic runtime drains events after the successful outermost application-service boundary
     (or a manual dispatcher drains them)
  -> externalization selects topic, key, and payload
  -> Outbox row is written in the same database transaction
  -> dispatcher claims and sends through MessageSender
  -> consumer uses InboxTemplate for idempotency
```

Automatic event collection does not infer domain facts from persistence changes or object state.
The aggregate's business behavior explicitly records a fact with `recordEvent(...)`; in an automatic
runtime, application business code does not normally call `drainEvents()`. That method remains the
framework-neutral handoff SPI for runtime integrations and deliberate manual dispatch.

There are two automatic externalization paths. Mark a deliberately stable public domain-event
contract with `@Externalized` to serialize that event directly. For a versioned integration contract,
provide a `DomainEventExternalizer<E>` bean: it maps an automatically captured domain event to zero or
more `ExternalizedEvent` values, and the framework serializes and appends them in the current
transaction. Each mapped value supplies a stable `payloadType`, payload, topic, key, and optional
aggregate metadata; the source event supplies the Outbox event id and occurrence time.

A matching externalizer takes precedence over `@Externalized`, including when it deliberately returns
no messages, so a domain event is never written twice through both paths. When no externalizer matches,
the existing opt-in annotation path remains unchanged. Mapping failures and invalid mapped metadata fail
the business transaction. `OutboxTemplate` remains available for integration messages not derived from
a captured domain event; it participates in the caller's transaction and neither starts one nor sends
synchronously.

## Payload Contract

Treat `payloadType` as a stable contract name rather than a Java class name. Consumers should
deserialize the envelope into their own versioned contract. Select a payload serializer that keeps
the wire format portable and does not expose JVM type names.

## Outbox State Machine

- `PENDING`: written and waiting for dispatch.
- `DISPATCHING`: claimed by a dispatcher.
- `PUBLISHED`: sent successfully.
- `FAILED`: this attempt failed and the message awaits retry.
- `DEAD_LETTERED`: retry limits were exceeded.

Recovery returns stuck `DISPATCHING` messages to `PENDING`. Cleanup deletes expired terminal
records only. Runtime dispatch triggering and maintenance scheduling are implementation concerns.

## Runtime Transaction Boundaries

`OutboxTemplate.append(...)` joins the business transaction; it never starts an independent
transaction. Normally select the same persistence technology for business data and the Outbox
store, and ensure both writes participate in the same local transaction.

In the Spring Boot runtime, dispatch uses three independent short database
transactions: claim records, send each claimed payload outside a database transaction, then mark
the result. Recovery and each cleanup batch also run in independent transactions. This applies to
both JPA and MyBatis-Plus stores.

`InboxTemplate` first claims a delivery in a new transaction. The handler and its `PROCESSED`
transition run in a second independent transaction. When the handler fails, that transaction rolls
back and a new transaction records `FAILED` before the original exception is rethrown. Boot creates
the transactional template only when a `TransactionRunner` is available. Direct construction with
`new InboxTemplate(store)` remains a manual-runtime API: the caller must provide the transaction
boundaries required by its store.

## Inbox Ownership And Recovery

Inbox persists a lease (`claimed_at`) and opaque claim token (`claim_token`) while a delivery is
`PROCESSING`. The handler owner must present that token to record either `PROCESSED` or `FAILED`.
This prevents an expired worker from overwriting the result of a newer owner. A redelivery claims a
`FAILED` record immediately; an active `PROCESSING` record is skipped until its lease has expired,
at which point it can be reclaimed with a new token. A handler failure is recorded as `FAILED` and
then rethrown so the broker's retry, negative-acknowledgement, and dead-letter policy remains in
control. The table change requires application migrations to add `claimed_at`, `claim_token`, and
the `(status, claimed_at)` lookup index.

## SQL Templates

SQL is supplied only as a copyable template and is never run by jfoundry. `jfoundry-outbox-core`
owns the canonical Outbox paths and `jfoundry-inbox-core` owns the canonical Inbox path. Copy the
needed template into the application's migration process:

```text
jfoundry/sql/outbox/mysql/create_outbox_event.sql
jfoundry/sql/outbox/postgresql/create_outbox_event.sql
jfoundry/sql/inbox/common/create_inbox_message.sql
```

## Implementation Guides

| Need | Guide |
|------|-------|
| JPA Outbox and Inbox stores, including database-specific Inbox claiming | [JPA](../implementations/jpa.md) |
| MyBatis-Plus Outbox and Inbox stores | [MyBatis-Plus](../implementations/mybatis-plus.md) |
| Quarkus Outbox runtime, automatic domain-event externalization, and Kafka delivery | [Quarkus](../implementations/quarkus.md) |
| Spring Boot capability assembly and dispatcher configuration | [Spring Boot](../implementations/spring-boot.md) |

Use [Spring Boot Auto-configuration](../reference/spring-boot-autoconfiguration.md) as the lookup
for starters, properties, and registration conditions.
