# 接入指南

从满足当前业务用例的最小架构开始。jfoundry 最适合需要明确聚合、不变量、领域事件、架构边界或可靠外部集成的系统。短期 CRUD 原型如果没有这些需求，直接使用运行时框架和 ORM 可能更简单。

## 选择 BOM 与模块边界

运行时无关的应用导入 `jfoundry-dependencies`；使用 Spring Boot 装配应用时导入
`jfoundry-spring-dependencies`；使用当前可用的 Quarkus 事务集成时导入
`jfoundry-quarkus-dependencies`。请选择目标发布线的版本；本项目当前使用下面的开发版本：

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.github.xfoundries</groupId>
            <artifactId>jfoundry-spring-dependencies</artifactId>
            <version>1.0.0-SNAPSHOT</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

将依赖放在拥有该职责的层：

| 模块 | 起始依赖 |
|------|----------|
| Domain | `jfoundry-domain-starter` |
| Application | `jfoundry-application-starter` |
| Infrastructure | 所选运行时无关能力 starter，例如 `jfoundry-persistence-mybatis-plus-starter` |
| Spring Boot 装配 | `jfoundry-spring-boot-starter` 加上实际需要的运行时能力 starter |
| Quarkus 事务集成 | `jfoundry-quarkus-runtime` |

Hexagonal 或 Onion 应依据领域和项目约束选择；jfoundry 不会为业务项目决定架构风格。在实现因偶然依赖而增长前，先添加 ArchUnit 测试。

## 装配最小 Spring Boot 运行时

使用业务 MyBatis-Plus 持久化的 Spring Boot 应用，在运行时模块中先引入基础与 MyBatis-Plus runtime starter：

```xml
<dependencies>
    <dependency>
        <groupId>io.github.xfoundries</groupId>
        <artifactId>jfoundry-spring-boot-starter</artifactId>
    </dependency>
    <dependency>
        <groupId>io.github.xfoundries</groupId>
        <artifactId>jfoundry-persistence-mybatis-plus-spring-boot-starter</artifactId>
    </dependency>
</dependencies>
```

配置应用的数据源，并将持久化 adapter 保留在 infrastructure 模块。MyBatis-Plus runtime starter 不会引入 Outbox 或 Inbox store。JPA 运行时则将 MyBatis-Plus runtime starter 替换为 `jfoundry-persistence-jpa-spring-boot-starter`；它同样将 Outbox 和 Inbox store 保持为显式选择。准确的实现边界见 [MyBatis-Plus](../implementations/mybatis-plus.md)、[JPA](../implementations/jpa.md)和 [Spring Boot 运行时装配](../implementations/spring-boot.md)。

## 仅在需要时追加能力

- 用例需要时加入[应用事务](../capabilities/application-transactions.md)或[分布式锁](../capabilities/distributed-locks.md)。
- 只有需要跨进程投递或消费端幂等时，才加入[可靠消息：Outbox 与 Inbox](../capabilities/reliable-messaging.md)，并显式选择 store。
- messaging、broker、Web MVC 和 scheduling starter 只为对应能力添加。

[Spring Boot 自动配置参考](../reference/spring-boot-autoconfiguration.md)是单个 starter、配置项和注册条件的唯一目录。

Quarkus 的依赖配置、事务传播、应用服务领域事件分发和 Native Image 验证见
[Quarkus 运行时集成](../implementations/quarkus.md)。

## 阅读路径

1. 先通过[架构风格指南](../framework/architecture-styles.md)和 [ArchUnit 架构规则](../framework/archunit-rules.md)确定边界。
2. 通过 [Repository 与读侧契约迁移指南](../modeling/repository-vs-read-contracts.md)建模聚合，并选择 Repository/读侧契约。
3. 通过所选实现应用[聚合持久化](../capabilities/aggregate-persistence.md)。

在生产环境依赖某项能力前，请查看[采用就绪度与已验证范围](adoption-readiness.md)。
