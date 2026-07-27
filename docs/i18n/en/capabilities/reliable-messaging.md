# Reliable Messaging: Outbox And Inbox

Use Transactional Outbox only when a domain event must reach another process or external system
reliably. In-process event handling does not require it. Inbox provides consumer-side idempotency
for a message and consumer combination.

![transactional-outbox.png](../../assets/outbox/transactional-outbox.png)

## Event Flow

```text
aggregate records domain event
  -> application service boundary drains events
  -> externalization selects topic, key, and payload
  -> Outbox row is written in the same database transaction
  -> dispatcher claims and sends through MessageSender
  -> consumer uses InboxTemplate for idempotency
```

Automatic externalization records only events explicitly marked as externalized; routing metadata
alone does not externalize an event. Use automatic externalization only for a deliberately stable
public event contract. Otherwise translate at the application boundary and append the versioned
integration contract explicitly with `OutboxTemplate`. The template participates in the caller's
transaction; it neither starts a transaction nor sends synchronously.

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
transaction. In the Spring Boot runtime, dispatch instead uses three independent short database
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

## Choose A Store

| Need | Guide |
|------|-------|
| MyBatis-Plus Outbox and Inbox stores | [MyBatis-Plus](../implementations/mybatis-plus.md) |
| JPA Outbox and Inbox stores, including database-specific Inbox claiming | [JPA](../implementations/jpa.md) |
| Quarkus Outbox runtime, automatic domain-event externalization, and Kafka delivery | [Quarkus](../implementations/quarkus.md) |
| Spring Boot capability assembly and dispatcher configuration | [Spring Boot](../implementations/spring-boot.md) |

Use [Spring Boot Auto-configuration](../reference/spring-boot-autoconfiguration.md) as the lookup
for starters, properties, and registration conditions.
