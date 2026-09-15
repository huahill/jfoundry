# 建模

本节记录 JFoundry 的领域建模约定：如何编写聚合、值对象、领域事件和仓储契约。
它不是运行时能力目录，也不负责选择启动器、消息代理或持久化适配器。

- [值对象规范](value-object.md) — 不可变值、相等性与 ArchUnit 检查。
- [领域事件](domain-event.md) — 由聚合记录的显式业务事实。
- [Repository 与读侧契约迁移指南](repository-vs-read-contracts.md) — 写侧仓储与读侧查询。

相关运行时主题在其他章节：

- 聚合映射与持久化适配器：[聚合持久化](../capabilities/aggregate-persistence.md)
- Transactional Outbox 与 Inbox：[可靠消息](../capabilities/reliable-messaging.md)
- 运行时事件分发装配：[Spring Boot](../implementations/spring-boot.md)、
  [Quarkus](../implementations/quarkus.md) 与 [Helidon MP](../implementations/helidon.md)
