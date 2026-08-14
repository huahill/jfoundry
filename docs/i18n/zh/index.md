# jfoundry 中文文档

本文档以能力为主线。能力页定义契约和行为，持久化实现页与运行时集成页说明技术选择；运行时专属的配置参考由对应运行时指南链接，而不是作为技术选型入口。

## 快速开始

- [接入指南](integration/getting-started.md)
- [采用就绪度与已验证范围](integration/adoption-readiness.md)
- [`domain-architecture-skills`](https://github.com/xfoundries/domain-architecture-skills)：在 JFoundry 落地之前，可选使用的 AI 辅助领域建模与架构工作流。

## 能力

- [能力目录](capabilities/index.md)
- [聚合持久化](capabilities/aggregate-persistence.md)
- [Web](capabilities/web.md)
- [消息传输](capabilities/message-delivery.md)
- [可靠消息：Outbox 与 Inbox](capabilities/reliable-messaging.md)
- [应用事务](capabilities/application-transactions.md)
- [分布式锁](capabilities/distributed-locks.md)
- [可观测性](capabilities/observability.md)

## 持久化实现

- [MyBatis-Plus](implementations/mybatis-plus.md)
- [JPA](implementations/jpa.md)

## 运行时集成

- [Spring Boot 运行时装配](implementations/spring-boot.md)
- [Quarkus 运行时集成](implementations/quarkus.md)
- [Helidon MP 运行时集成](implementations/helidon.md)

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
