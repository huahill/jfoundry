# 接入指南

从满足当前业务用例的最小架构开始。jfoundry 最适合需要明确聚合、不变量、领域事件、架构边界或可靠外部集成的系统。短期 CRUD 原型如果没有这些需求，直接使用运行时框架和 ORM 可能更简单。

## 选择 Parent、BOM 与模块边界

请选择一个 Spring 平台线。两条线刻意互不兼容，应用不得同时导入两个运行时 BOM。

| 平台线 | Parent | 运行时 BOM | 平台基线 |
|------|--------|-------------|----------|
| 仅 Boot | `jfoundry-spring-boot-parent` | `jfoundry-spring-boot-dependencies` | JFoundry 管理的仅 Boot 平台线 |
| Cloud | 与受支持 Cloud 平台线兼容的自有或标准 Maven Parent | `jfoundry-spring-cloud-dependencies` | JFoundry 管理的 Cloud 平台线 |

各平台线的精确版本见[兼容矩阵](../../../release/compatibility.md)。

仅 Boot 的应用应将 `jfoundry-spring-boot-parent` 作为唯一 Maven Parent：

```xml
<parent>
    <groupId>io.github.xfoundries</groupId>
    <artifactId>jfoundry-spring-boot-parent</artifactId>
    <version>1.0.3</version>
</parent>
```

它继承受支持的 Spring Boot Parent，设置 Java 25 基线，并在 `jfoundry-dependencies` 前导入
仅 Boot 的运行时 BOM。Spring Boot 与 JFoundry 依赖无需声明版本，但每个 JFoundry 能力启动器仍须显式选择。
Cloud 应用使用与受支持 Cloud 平台线兼容的自有或标准 Maven Parent，并显式导入 Cloud BOM 与 `jfoundry-dependencies`。

必须保留不同 Maven Parent 的应用，应导入用于 JFoundry 模块版本的 `jfoundry-dependencies`。使用受支持运行时的应用还要额外且仅导入一个
对应的运行时 BOM：`jfoundry-spring-boot-dependencies`、`jfoundry-spring-cloud-dependencies`、`jfoundry-quarkus-dependencies` 或
`jfoundry-helidon-dependencies`。运行时 BOM 只管理各自的平台生态版本，不能替代核心 JFoundry BOM。必须先导入运行时 BOM，再导入 `jfoundry-dependencies`，以便优先采用运行时平台管理的约束。请选择目标发布线的版本；本项目当前使用下面的开发版本。

仅 Boot BOM 只管理 Spring Boot。Cloud BOM 将 Spring Cloud 和 Spring Cloud Alibaba 作为一个经过验证的平台线管理；Spring Boot 由应用的 Parent 或另一个显式 Boot BOM 管理。这样 Cloud 应用可以不写版本地添加所选 Cloud 启动器；它不会自动引入任何 Cloud 启动器，也不表示 JFoundry 已为该启动器提供适配器。

下面 XML 是无法使用 JFoundry Parent 的仅 Boot 应用的替代方式。使用 Cloud 平台线时，将第一个 import
替换为 `jfoundry-spring-cloud-dependencies`；使用 Quarkus 或 Helidon 时，替换为对应的运行时 BOM，并保留第二个核心 BOM：

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.github.xfoundries</groupId>
            <artifactId>jfoundry-spring-boot-dependencies</artifactId>
            <version>1.0.3</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
        <dependency>
            <groupId>io.github.xfoundries</groupId>
            <artifactId>jfoundry-dependencies</artifactId>
            <version>1.0.3</version>
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
| Infrastructure | 所选运行时无关能力启动器，例如 `jfoundry-persistence-mybatis-plus-starter` |
| Spring Boot 装配 | `jfoundry-spring-boot-starter` 加上实际需要的运行时能力启动器 |
| Quarkus 运行时集成 | `jfoundry-quarkus-runtime` |
| Helidon MP 运行时集成 | `jfoundry-helidon-runtime` |

Hexagonal 或 Onion 应依据领域和项目约束选择；jfoundry 不会为业务项目决定架构风格。在实现因偶然依赖而增长前，先添加 ArchUnit 测试。

## 装配最小 Spring Boot 运行时

使用业务 MyBatis-Plus 持久化的 Spring Boot 应用，在运行时模块中先引入基础与 MyBatis-Plus 运行时启动器：

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

配置应用的数据源，并将持久化适配器保留在基础设施模块。MyBatis-Plus 运行时启动器不会引入 Outbox 或 Inbox 存储。JPA 运行时则将 MyBatis-Plus 运行时启动器替换为 `jfoundry-persistence-jpa-spring-boot-starter`；它同样将 Outbox 和 Inbox 存储保持为显式选择。准确的实现边界见 [MyBatis-Plus](../implementations/mybatis-plus.md)、[JPA](../implementations/jpa.md)和 [Spring Boot 运行时装配](../implementations/spring-boot.md)。

## 仅在需要时追加能力

- 先从[能力目录](../capabilities/index.md)开始。它将业务需求映射到受支持的运行时入口，并链接到契约和组合说明。
- 用例需要时加入[应用事务](../capabilities/application-transactions.md)或[分布式锁](../capabilities/distributed-locks.md)。
- 应用需要直接消息代理生产者时加入[消息传输](../capabilities/message-delivery.md)；只有还需要持久化发布或消费端幂等时，才加入[可靠消息：Outbox 与 Inbox](../capabilities/reliable-messaging.md)。Web MVC 和调度启动器只为对应能力添加。

[Spring Boot 自动配置参考](../reference/spring-boot-autoconfiguration.md)详细说明单个 Spring 启动器、配置项和注册条件。

各运行时的依赖配置、能力组合与验证见 [Spring Boot 运行时装配](../implementations/spring-boot.md)、
[Quarkus 运行时集成](../implementations/quarkus.md) 和
[Helidon MP 运行时集成](../implementations/helidon.md)。

## 阅读路径

1. 先通过[架构风格指南](../framework/architecture-styles.md)和 [ArchUnit 架构规则](../framework/archunit-rules.md)确定边界。
2. 通过 [Repository 与读侧契约迁移指南](../modeling/repository-vs-read-contracts.md)建模聚合，并选择 Repository/读侧契约。
3. 通过所选实现应用[聚合持久化](../capabilities/aggregate-persistence.md)。

在生产环境依赖某项能力前，请查看[采用就绪度与已验证范围](adoption-readiness.md)。
