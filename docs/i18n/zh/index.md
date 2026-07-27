# jfoundry 中文文档

本文档以能力为主线。能力页定义契约和行为，实现页说明技术选择，参考页提供查询材料。

## 快速开始

- [接入指南](integration/getting-started.md)
- [采用就绪度与已验证范围](integration/adoption-readiness.md)

## 能力

- [聚合持久化](capabilities/aggregate-persistence.md)
- [可靠消息：Outbox 与 Inbox](capabilities/reliable-messaging.md)
- [应用事务](capabilities/application-transactions.md)
- [分布式锁](capabilities/distributed-locks.md)
- [可观测性](capabilities/observability.md)

## 实现

- [JPA](implementations/jpa.md)
- [MyBatis-Plus](implementations/mybatis-plus.md)
- [Spring Boot 运行时装配](implementations/spring-boot.md)
- [Quarkus 运行时集成](implementations/quarkus.md)
- [Helidon MP 运行时集成](implementations/helidon.md)

## 参考

- [Spring Boot 自动配置](reference/spring-boot-autoconfiguration.md)

## 框架语义

- [架构风格指南](framework/architecture-styles.md)
- [ArchUnit 架构规则](framework/archunit-rules.md)
- [框架边界设计](framework/framework-boundaries.md)

## 建模

- [值对象规范](modeling/value-object.md)
- [Repository 与读侧契约迁移指南](modeling/repository-vs-read-contracts.md)

## 发布与兼容

发布、兼容矩阵和 Maven Central 说明是维护者文档，当前只维护一份：

- [Compatibility Matrix](../../release/compatibility.md)
- [Maven Central Publishing](../../release/maven-central.md)
